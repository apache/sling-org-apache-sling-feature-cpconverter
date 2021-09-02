/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.feature.cpconverter.accesscontrol;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.PrivilegeDefinition;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.spi.PrivilegeDefinitions;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.repoinit.NoOpVisitor;
import org.apache.sling.feature.cpconverter.repoinit.OperationProcessor;
import org.apache.sling.feature.cpconverter.shared.ConverterConstants;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.repoinit.parser.impl.RepoInitParserService;
import org.apache.sling.repoinit.parser.operations.CreateServiceUser;
import org.apache.sling.repoinit.parser.operations.DisableServiceUser;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.apache.sling.repoinit.parser.impl.WithPathOptions;
import org.apache.sling.repoinit.parser.operations.AclLine;
import org.apache.sling.repoinit.parser.operations.CreatePath;
import org.apache.sling.repoinit.parser.operations.RegisterNodetypes;
import org.apache.sling.repoinit.parser.operations.RegisterPrivilege;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipalBased;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipals;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NamespaceException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.jackrabbit.vault.util.Constants.DOT_CONTENT_XML;

public class DefaultAclManager implements AclManager, EnforceInfo {

    private static final Logger log = LoggerFactory.getLogger(DefaultAclManager.class);
    
    private final RepoPath enforcePrincipalBasedSupportedPath;
    private final String systemRelPath;

    private final OperationProcessor processor = new OperationProcessor();

    private final Set<SystemUser> systemUsers = new LinkedHashSet<>();
    private final Set<String> systemUserIds = new LinkedHashSet<>();

    private final Set<Group> groups = new LinkedHashSet<>();
    private final Set<User> users = new LinkedHashSet<>();
    private final Set<Mapping> mappings = new HashSet<>();
    private final Set<String> mappedById = new HashSet<>();

    private final Map<String, List<AccessControlEntry>> acls = new HashMap<>();

    private final List<RegisterNodetypes> nodetypeOperations = new LinkedList<>();

    private volatile PrivilegeDefinitions privilegeDefinitions;
    
    private RepoPath userRootPath;

    public DefaultAclManager() throws ConverterException {
        this(null, ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT);
    }

    public DefaultAclManager(@Nullable String enforcePrincipalBasedSupportedPath, @NotNull String systemRelPath) throws ConverterException {
        if (enforcePrincipalBasedSupportedPath != null && !enforcePrincipalBasedSupportedPath.contains(systemRelPath)) {
            throw new ConverterException("Relative path for system users "+ systemRelPath + " not included in " + enforcePrincipalBasedSupportedPath);
        }
        this.enforcePrincipalBasedSupportedPath = (enforcePrincipalBasedSupportedPath == null) ? null : new RepoPath(enforcePrincipalBasedSupportedPath);
        this.systemRelPath = systemRelPath;
    }

    @Override
    public boolean addUser(@NotNull User user) {
        return users.add(user);
    }

    @Override
    public boolean addGroup(@NotNull Group group) {
        return groups.add(group);
    }

    @Override
    public boolean addSystemUser(@NotNull SystemUser systemUser) {
        if (systemUsers.add(systemUser)) {
            recordSystemUserIds(systemUser.getId());
            setUserRoot(systemUser.getPath());
            return true;
        } else {
            return false;
        }

    }

    @Override
    public void addMapping(@NotNull Mapping mapping) {
        if (mappings.add(mapping)) {
            for (SystemUser user : systemUsers) {
                if (mapping.mapsUser(user.getId())) {
                    mappedById.add(user.getId());
                }
            }
        }
    }

    @Override
    public boolean addAccessControlEntry(@NotNull String systemUser, @NotNull AccessControlEntry acl) {
        if (getSystemUser(systemUser).isPresent()) {
            acls.computeIfAbsent(systemUser, k -> new LinkedList<>()).add(acl);
            return true;
        }
        return false;
    }

    @Override
    public void addRepoinitExtension(@NotNull List<VaultPackageAssembler> packageAssemblers, @NotNull FeaturesManager featureManager)
    throws IOException, ConverterException {
        try (Formatter formatter = new Formatter()) {

            if (privilegeDefinitions != null) {
                registerPrivileges(privilegeDefinitions, formatter);
            }

            for (RegisterNodetypes op : nodetypeOperations) {
                formatter.format("%s", op.asRepoInitString());
            }

            addUsersAndGroups(formatter);
            addPaths(formatter, packageAssemblers);

            // add the acls
            acls.forEach((systemUserID, authorizations) ->
                    getSystemUser(systemUserID).ifPresent(systemUser ->
                            addStatements(systemUser, authorizations, formatter)
                    ));

            String text = formatter.toString();

            if (!text.isEmpty()) {
                featureManager.addOrAppendRepoInitExtension(text, null);
            }
        }
    }

    @Override
    public void addRepoinitExtention(@Nullable String repoInitText, @Nullable String runMode, @NotNull FeaturesManager featuresManager)
    throws IOException, ConverterException {
        if (repoInitText == null || repoInitText.trim().isEmpty()) {
            return;
        }

        if ("seed".equalsIgnoreCase(runMode)) {
            try {
                List<Operation> ops = new RepoInitParserService().parse(new StringReader(repoInitText));
                for (Operation op : ops) {
                    op.accept(new NoOpVisitor() {
                        @Override
                        public void visitCreateServiceUser(CreateServiceUser createServiceUser) {
                            recordSystemUserIds(createServiceUser.getUsername());
                        }
                    });
                }
            } catch (RepoInitParsingException e) {
                throw new ConverterException(e.getMessage(), e);
            }
            return;
        }
        try (Formatter formatter = new Formatter()) {
            if (enforcePrincipalBased()) {
                List<Operation> ops = new RepoInitParserService().parse(new StringReader(repoInitText));
                processor.apply(ops, formatter,this);
            } else {
                formatter.format("%s", repoInitText);
            }

            String text = formatter.toString().trim();
            if (!text.isEmpty()) {
                featuresManager.addOrAppendRepoInitExtension(text, runMode);
            }
        } catch (RepoInitParsingException e) {
            throw new ConverterException(e.getMessage(), e);
        }
    }

    private void addUsersAndGroups(@NotNull Formatter formatter) throws ConverterException {
        for (SystemUser systemUser : systemUsers) {
            // make sure all system users are created first
            CreateServiceUser operation = new CreateServiceUser(systemUser.getId(), new WithPathOptions(calculateIntermediatePath(systemUser), enforcePrincipalBased(systemUser)));
            formatter.format("%s", operation.asRepoInitString());
            if (systemUser.getDisabledReason() != null) {
                DisableServiceUser disable = new DisableServiceUser(systemUser.getId(), systemUser.getDisabledReason());
                disable.setServiceUser(true);
                formatter.format("%s", disable.asRepoInitString());
            }

            if (aclIsBelow(systemUser.getPath())) {
                throw new ConverterException("Detected policy on subpath of system-user: " + systemUser);
            }
        }

        // abort the conversion if an access control entry takes effect at or below a user/group which is not
        // created by repo-init statements generated here.
        for(final Group g : groups) {
            if (aclStartsWith(g.getPath())) {
                throw new ConverterException("Detected policy on group: " + g);
            }
        }
        for(final User u : users) {
            if (aclStartsWith(u.getPath())) {
                throw new ConverterException("Detected policy on user: " + u);
            }
        }
    }

    @NotNull
    private String calculateIntermediatePath(@NotNull SystemUser systemUser) throws ConverterException {
        RepoPath intermediatePath = systemUser.getIntermediatePath();
        if (enforcePrincipalBased(systemUser)) {
            return calculateEnforcedIntermediatePath(intermediatePath.toString());
        } else {
            return getRelativeIntermediatePath(intermediatePath.toString());
        }
    }

    private void addPaths(@NotNull Formatter formatter, @NotNull List<VaultPackageAssembler> packageAssemblers) {
        Set<RepoPath> paths = acls.entrySet().stream()
                // filter paths if service user does not exist or will have principal-based ac setup enforced
                .filter(entry -> {
                    Optional<SystemUser> su = getSystemUser(entry.getKey());
                    return su.isPresent() && !enforcePrincipalBased(su.get());
                })
                .map(Entry::getValue)
                .flatMap(Collection::stream)
                // paths only should/need to be create with resource-based access control
                .filter(((Predicate<AccessControlEntry>) AccessControlEntry::isPrincipalBased).negate())
                .map(AccessControlEntry::getRepositoryPath)
                .collect(Collectors.toSet());

        paths.stream()
                .filter(path -> paths.stream().noneMatch(other -> !other.equals(path) && other.startsWith(path)))
                .filter(((Predicate<RepoPath>)RepoPath::isRepositoryPath).negate())
                .filter(path -> Stream.of(systemUsers, users, groups).flatMap(Collection::stream)
                        .noneMatch(user -> user.getPath().startsWith(path)))
                .map(path -> getCreatePath(path, packageAssemblers))
                .filter(Objects::nonNull)
                .forEach(
                        path -> formatter.format("%s", path.asRepoInitString())
                );
    }

    private boolean aclStartsWith(@NotNull RepoPath path) {
        return acls.values().stream().flatMap(List::stream).anyMatch(acl -> acl.getRepositoryPath().startsWith(path));
    }

    private boolean aclIsBelow(@NotNull RepoPath path) {
        return acls.values().stream().flatMap(List::stream).anyMatch(acl -> acl.getRepositoryPath().startsWith(path) && !acl.getRepositoryPath().equals(path));
    }

    private void addStatements(@NotNull SystemUser systemUser,
                               @NotNull List<AccessControlEntry> authorizations,
                               @NotNull Formatter formatter) {
        Map<AccessControlEntry, String> resourceEntries = new LinkedHashMap<>();
        Map<AccessControlEntry, String> principalEntries = new LinkedHashMap<>();

        authorizations.forEach(entry -> {
            String path = getRepoInitPath(entry.getRepositoryPath(), systemUser);
            if (entry.isPrincipalBased() || enforcePrincipalBased(systemUser)) {
                principalEntries.put(entry, path);
            } else {
                resourceEntries.put(entry, path);
            }
        });

        if (!principalEntries.isEmpty()) {
            SetAclPrincipalBased operation = new SetAclPrincipalBased(Collections.singletonList(systemUser.getId()), asAcLines(principalEntries));
            formatter.format("%s", operation.asRepoInitString());
        }
        if (!resourceEntries.isEmpty()) {
            SetAclPrincipals operation = new SetAclPrincipals(Collections.singletonList(systemUser.getId()), asAcLines(resourceEntries));
            formatter.format("%s", operation.asRepoInitString());
        }
    }

    private static List<AclLine> asAcLines(@NotNull Map<AccessControlEntry, String> entries) {
        List<AclLine> lines = new ArrayList<>();
        entries.forEach((entry, path) -> lines.add(entry.asAclLine(path)));
        return lines;
    }

    private boolean enforcePrincipalBased() {
        return enforcePrincipalBasedSupportedPath != null;
    }

    private boolean enforcePrincipalBased(@NotNull SystemUser systemUser) {
        return enforcePrincipalBased(systemUser.getId());
    }

    private @NotNull Optional<SystemUser> getSystemUser(@NotNull String id) {
        return systemUsers.stream().filter(systemUser ->  systemUser.getId().equals(id)).findFirst();
    }

    @Override
    public void addNodetypeRegistration(@NotNull String cndStatements) {
        nodetypeOperations.add(new RegisterNodetypes(cndStatements));
    }

    @Override
    public void addPrivilegeDefinitions(@NotNull PrivilegeDefinitions privilegeDefinitions) {
        this.privilegeDefinitions = privilegeDefinitions;
    }

    @Override
    public void reset() {
        systemUsers.clear();
        acls.clear();
        nodetypeOperations.clear();
        privilegeDefinitions = null;
    }

    @Override
    public void recordSystemUserIds(@NotNull String... systemUserIds) {
        for (String id : systemUserIds) {
            if (this.systemUserIds.add(id) && mappings.stream().anyMatch(mapping -> mapping.mapsUser(id))) {
                mappedById.add(id);
            }
        }
    }

    @Override
    public boolean enforcePrincipalBased(@NotNull String systemUserId) {
        if (enforcePrincipalBased() && systemUserIds.contains(systemUserId)) {
            if (mappedById.contains(systemUserId)) {
                log.warn("Skip enforcing principal-based access control setup for system user '{}' due to existing mapping by id.", systemUserId);
                return false;
            } else {
                return true;
            }
        }
        return false;
    }

    @Override
    @NotNull
    public String calculateEnforcedIntermediatePath(@Nullable String intermediatePath) throws ConverterException {
        if (enforcePrincipalBasedSupportedPath == null) {
            throw new IllegalStateException("No supported path configured");
        }
        String supportedPath = getRelativeIntermediatePath(enforcePrincipalBasedSupportedPath.toString());
        if (intermediatePath == null || intermediatePath.isEmpty()) {
            return supportedPath;
        }

        String relIntermediate = getRelativeIntermediatePath(intermediatePath);
        if (Text.isDescendantOrEqual(supportedPath, relIntermediate)) {
            return relIntermediate;
        } else {
            String parent = Text.getRelativeParent(relIntermediate, 1);
            while (!parent.isEmpty() && !"/".equals(parent)) {
                if (Text.isDescendantOrEqual(parent, supportedPath)) {
                    String relpath = relIntermediate.substring(parent.length());
                    return supportedPath + relpath;
                }
                parent = Text.getRelativeParent(parent, 1);
            }
            throw new ConverterException("Cannot calculate intermediate path for service user. Configured Supported path " +enforcePrincipalBasedSupportedPath+" has no common ancestor with "+intermediatePath);
        }
    }

    @NotNull
    private String getRelativeIntermediatePath(@NotNull String intermediatePath) throws ConverterException {
        if (intermediatePath.equals(systemRelPath) || intermediatePath.startsWith(systemRelPath+"/")) {
            return intermediatePath;
        } else {
            String p = intermediatePath + "/";
            String rel = "/" + systemRelPath + "/";
            int i = p.indexOf(rel);
            if (i == -1) {
                throw new ConverterException("Invalid intermediate path for system user " + intermediatePath + ". Must include "+ systemRelPath);
            }
            return intermediatePath.substring(i + 1);
        }
    }

    protected @Nullable CreatePath getCreatePath(@NotNull RepoPath path, @NotNull List<VaultPackageAssembler> packageAssemblers) {
        if (path.getParent() == null) {
            log.debug("Omit create path statement for path '{}'", path);
            return null;
        }
        
        CreatePath cp = new CreatePath(null);
        boolean foundType = processSegments(path, packageAssemblers, cp);
        
        if (!foundType && isBelowUserRoot(path)) {
            // if no type information has been detected, don't issue a 'create path' statement for nodes below the 
            // user-root 
            log.warn("Failed to extract primary type information for node at path '{}'", path);
            return null;
        } else {
            // assume that primary type information is present or can be extracted from default primary type definition 
            // of the the top-level nodes (i.e. their effective node type definition).
            return cp;
        }
    }
    
    private static boolean processSegments(@NotNull RepoPath path, @NotNull List<VaultPackageAssembler> packageAssemblers, @NotNull CreatePath cp) {
        String platformPath = "";
        boolean foundType = false;
        for (String part : path.getSegments()) {
            String platformname = PlatformNameFormat.getPlatformName(part);
            platformPath += platformPath.isEmpty() ? platformname : "/" + platformname;
            boolean segmentAdded = false;
            for (VaultPackageAssembler packageAssembler : packageAssemblers) {
                File currentContent = packageAssembler.getEntry(platformPath + "/" + DOT_CONTENT_XML);
                if (currentContent.isFile()) {
                    segmentAdded =  addSegment(cp, part, currentContent);
                    if (segmentAdded) {
                        foundType = true;
                        break;
                    }
                }
            }
            if (!segmentAdded) {
                cp.addSegment(part, null);
            }
        }
        return foundType;
    }

    private static boolean addSegment(@NotNull CreatePath cp, @NotNull String part, @NotNull File currentContent) {
        try (FileInputStream input = new FileInputStream(currentContent);
             FileInputStream input2 = new FileInputStream(currentContent)) {
            String primary = new PrimaryTypeParser().parse(input);
            if (primary != null) {
                List<String> mixins = new ArrayList<>();
                String mixin = new MixinParser().parse(input2);
                if (mixin != null) {
                    mixin = mixin.trim();
                    if (mixin.startsWith("[")) {
                        mixin = mixin.substring(1, mixin.length() - 1);
                    }
                    for (String m : mixin.split(",")) {
                        String mixinName = m.trim();
                        if (!mixinName.isEmpty()) {
                            mixins.add(mixinName);
                        }
                    }
                }
                cp.addSegment(part, primary, mixins);
                return true;
            }
        } catch (Exception e) {
            throw new RuntimeException("A fatal error occurred while parsing the '"
                    + currentContent
                    + "' file, see nested exceptions: "
                    + e);
        }
        return false;
    }

    @NotNull
    private String getRepoInitPath(@NotNull RepoPath path, @NotNull SystemUser systemUser) {
        if (path.isRepositoryPath()) {
            return ":repository";
        } else if (isHomePath(path, systemUser.getPath())) {
            return getHomePath(systemUser);
        } else {
            AbstractUser other = getOtherUser(path, Stream.of(systemUsers, groups).flatMap(Collection::stream));
            if (other != null) {
                return getHomePath(other);
            }
            // not a special path
            return path.toString();
        }
    }

    private static boolean isHomePath(@NotNull RepoPath path, @NotNull RepoPath systemUserPath) {
        // ACE located in the subtree are not supported
        return path.equals(systemUserPath);
    }

    @Nullable
    private static AbstractUser getOtherUser(@NotNull RepoPath path, @NotNull Stream<? extends AbstractUser> abstractUsers) {
        return abstractUsers.filter(au -> path.startsWith(au.getPath())).findFirst().orElse(null);
    }

    @NotNull
    private static String getHomePath(@NotNull AbstractUser abstractUser) {
        // since ACEs located in the subtree of a user are not supported by the converter,
        // there is no need to calculate a potential sub-path to be appended.
        return "home("+abstractUser.getId()+")";
    }

    private static void registerPrivileges(@NotNull PrivilegeDefinitions definitions, @NotNull Formatter formatter) {
        NameResolver nameResolver = new DefaultNamePathResolver(definitions.getNamespaceMapping());
        for (PrivilegeDefinition privilege : definitions.getDefinitions()) {
            try {
                RegisterPrivilege operation = new RegisterPrivilege(nameResolver.getJCRName(privilege.getName()), privilege.isAbstract(), getAggregatedNames(privilege, nameResolver));
                formatter.format("%s", operation.asRepoInitString());
            } catch (NamespaceException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @NotNull
    private static List<String> getAggregatedNames(@NotNull PrivilegeDefinition definition, @NotNull NameResolver nameResolver) {
        Set<Name> aggregatedNames = definition.getDeclaredAggregateNames();
        if (aggregatedNames.isEmpty()) {
            return Collections.emptyList();
        } else {
            return aggregatedNames.stream().map(name -> {
                try {
                    return nameResolver.getJCRName(name);
                } catch (NamespaceException e) {
                    throw new IllegalStateException(e);
                }
            }).collect(Collectors.toList());
        }
    }

    /**
     * Record the root path for all users/groups assuming that their common ancestor is a top-level node
     * 
     * @param userPath A user path
     */
    private void setUserRoot(@NotNull RepoPath userPath) {
        if (userRootPath == null) {
            userRootPath = new RepoPath(Text.getAbsoluteParent(userPath.toString(), 0));
        }
    }
    
    private boolean isBelowUserRoot(@NotNull RepoPath path) {
        return userRootPath != null && path.startsWith(userRootPath);
    }
}
