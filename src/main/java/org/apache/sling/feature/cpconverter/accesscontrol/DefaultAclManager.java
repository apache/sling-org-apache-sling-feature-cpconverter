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
import org.apache.jackrabbit.vault.fs.spi.PrivilegeDefinitions;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.jcr.NamespaceException;
import java.io.File;
import java.io.FileInputStream;
import java.util.Optional;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DefaultAclManager implements AclManager {

    private static final String CONTENT_XML_FILE_NAME = ".content.xml";

    private final boolean enforcePrincipalBased;
    private RepoPath supportedPrincipalBasedPath;

    private final Set<SystemUser> systemUsers = new LinkedHashSet<>();
    private final Set<Group> groups = new LinkedHashSet<>();
    private final Set<User> users = new LinkedHashSet<>();

    private final Map<String, List<AccessControlEntry>> acls = new HashMap<>();

    private final List<String> nodetypeRegistrationSentences = new LinkedList<>();

    private volatile PrivilegeDefinitions privilegeDefinitions;

    public DefaultAclManager() {
        this(false, null);
    }
    public DefaultAclManager(boolean enforcePrincipalBased, @Nullable String supportedPrincipalBasedPath) {
        this.enforcePrincipalBased = enforcePrincipalBased;
        this.supportedPrincipalBasedPath = (supportedPrincipalBasedPath == null) ? null : new RepoPath(supportedPrincipalBasedPath);
    }


    @Override
    public boolean addUser(@NotNull User user) {
        return users.add(user);
    }

    public boolean addGroup(@NotNull Group group) {
        return groups.add(group);
    }

    public boolean addSystemUser(@NotNull SystemUser systemUser) {
        return systemUsers.add(systemUser);
    }

    public boolean addAcl(@NotNull String systemUser, @NotNull AccessControlEntry acl) {
        if (getSystemUser(systemUser).isPresent()) {
            acls.computeIfAbsent(systemUser, k -> new LinkedList<>()).add(acl);
            return true;
        }
        return false;
    }

    public void addRepoinitExtension(@NotNull List<VaultPackageAssembler> packageAssemblers, @NotNull FeaturesManager featureManager) {
        try (Formatter formatter = new Formatter()) {

            if (privilegeDefinitions != null) {
                registerPrivileges(privilegeDefinitions, formatter);
            }

            for (String nodetypeRegistrationSentence : nodetypeRegistrationSentences) {
                formatter.format("%s%n", nodetypeRegistrationSentence);
            }

            // system users

            for (SystemUser systemUser : systemUsers) {
                // make sure all users are created first
                formatter.format("create service user %s with path %s%n", systemUser.getId(), systemUser.getIntermediatePath());
                if (aclIsBelow(systemUser.getPath())) {
                    throw new IllegalStateException("Detected policy on subpath of system-user: " + systemUser);
                }
            }

            for (Group group : groups) {
                if (aclStartsWith(group.getPath())) {
                    formatter.format("create group %s with path %s%n", group.getId(), group.getIntermediatePath());
                }
                if (aclIsBelow(group.getPath())) {
                    throw new IllegalStateException("Detected policy on subpath of group: " + group);
                }
            }

            for (User user : users) {
                if (aclStartsWith(user.getPath())) {
                    throw new IllegalStateException("Detected policy on user: " + user);
                }
            }

            // TODO: paths only should/need to be create with resource-based access control
            Set<RepoPath> paths = acls.entrySet().stream()
                    .filter(entry -> getSystemUser(entry.getKey()).isPresent())
                    .map(Entry::getValue)
                    .flatMap(Collection::stream)
                    .map(AccessControlEntry::getRepositoryPath)
                    .collect(Collectors.toSet());

            paths.stream()
                    .filter(path -> !paths.stream().anyMatch(other -> !other.equals(path) && other.startsWith(path)))
                    .filter(((Predicate<RepoPath>)RepoPath::isRepositoryPath).negate())
                    .filter(path -> Stream.of(systemUsers, users, groups).flatMap(Collection::stream)
                            .noneMatch(user -> user.getPath().startsWith(path)))
                    .map(path -> computePathWithTypes(path, packageAssemblers))
                    .filter(Objects::nonNull)
                    .forEach(
                            path -> formatter.format("create path %s%n", path)
                    );

            // TODO: generate 2 set of access control entries: principal-based und resource-based.
            // TODO: if 'enforce-principal-based' is turned on all entries should be generated as prinicpal-based
            // add the acls
            acls.forEach((systemUserID, authorizations) ->
                getSystemUser(systemUserID).ifPresent(systemUser ->
                    addStatements(systemUser, authorizations, packageAssemblers, formatter)
                ));

            String text = formatter.toString();

            if (!text.isEmpty()) {
                featureManager.addOrAppendRepoInitExtension(text, null);
            }
        }
    }

    private boolean aclStartsWith(RepoPath path) {
        return acls.values().stream().flatMap(List::stream).anyMatch(acl -> acl.getRepositoryPath().startsWith(path));
    }

    private boolean aclIsBelow(RepoPath path) {
        return acls.values().stream().flatMap(List::stream).anyMatch(acl -> acl.getRepositoryPath().startsWith(path) && !acl.getRepositoryPath().equals(path));
    }

    private void addStatements(@NotNull SystemUser systemUser,
                               @NotNull List<AccessControlEntry> authorizations,
                               @NotNull List<VaultPackageAssembler> packageAssemblers,
                               @NotNull Formatter formatter) {
        if (authorizations.isEmpty()) {
            return;
        }

        Map<AccessControlEntry, String> entries = new LinkedHashMap<>();
        authorizations.forEach(entry -> {
            String path = getRepoInitPath(entry.getRepositoryPath(), systemUser);
            if (path != null) {
                entries.put(entry, path);
            }
        });
        if (!entries.isEmpty()) {
            formatter.format("set ACL for %s%n", systemUser.getId());
            entries.forEach((entry, path) -> {
                formatter.format("%s %s on %s",
                        entry.getOperation(),
                        entry.getPrivileges(),
                        path);

                if (!entry.getRestrictions().isEmpty()) {
                    formatter.format(" restriction(%s)",
                            String.join(",", entry.getRestrictions()));
                }

                formatter.format("%n");
            });
            formatter.format("end%n");
        }
    }

    private @NotNull Optional<SystemUser> getSystemUser(@NotNull String id) {
        return systemUsers.stream().filter(systemUser ->  systemUser.getId().equals(id)).findFirst();
    }

    @Override
    public void addNodetypeRegistrationSentence(@Nullable String nodetypeRegistrationSentence) {
        if (nodetypeRegistrationSentence != null) {
            nodetypeRegistrationSentences.add(nodetypeRegistrationSentence);
        }
    }

    @Override
    public void addPrivilegeDefinitions(@NotNull PrivilegeDefinitions privilegeDefinitions) {
        this.privilegeDefinitions = privilegeDefinitions;
    }

    public void reset() {
        systemUsers.clear();
        acls.clear();
        nodetypeRegistrationSentences.clear();
        privilegeDefinitions = null;
    }

    private static @Nullable String computePathWithTypes(@NotNull RepoPath path, @NotNull List<VaultPackageAssembler> packageAssemblers) {
        path = new RepoPath(PlatformNameFormat.getPlatformPath(path.toString()));

        boolean type = false;
        String current = "";
        for (String part : path.toString().substring(1).split("/")) {
            current += current.isEmpty() ? part : "/" + part;
            for (VaultPackageAssembler packageAssembler : packageAssemblers) {
                File currentContent = packageAssembler.getEntry(current + "/" + CONTENT_XML_FILE_NAME);
                if (currentContent.isFile()) {
                    String primary;
                    String mixin;
                    try (FileInputStream input = new FileInputStream(currentContent);
                        FileInputStream input2 = new FileInputStream(currentContent)) {
                        primary = new PrimaryTypeParser().parse(input);
                        mixin = new MixinParser().parse(input2);
                        current += "(" + primary;
                        if (mixin != null) {
                            mixin = mixin.trim();
                            if (mixin.startsWith("[")) {
                                mixin = mixin.substring(1, mixin.length() - 1);
                            }
                            current += " mixin " + mixin;
                        }
                        current += ")";
                        type = true;
                    } catch (Exception e) {
                        throw new RuntimeException("A fatal error occurred while parsing the '"
                                + currentContent
                                + "' file, see nested exceptions: "
                                + e);
                    }
                }
            }
        }

        return type ? new RepoPath(current).toString() : null;
    }

    @Nullable
    private String getRepoInitPath(@NotNull RepoPath path, @NotNull SystemUser systemUser) {
        if (path.isRepositoryPath()) {
            return ":repository";
        } else if (isHomePath(path, systemUser.getPath())) {
            return getHomePath(path, systemUser);
        } else {
            AbstractUser other = getOtherUser(path, Stream.of(systemUsers, groups).flatMap(Collection::stream));
            if (other != null) {
                return getHomePath(path, other);
            }
            // not a special path
            return path.toString();
        }
    }

    private static boolean isHomePath(@NotNull RepoPath path, @NotNull RepoPath systemUserPath) {
        return path.startsWith(systemUserPath);
    }

    @Nullable
    private static AbstractUser getOtherUser(@NotNull RepoPath path, @NotNull Stream<? extends AbstractUser> abstractUsers) {
        return abstractUsers.filter(au -> path.startsWith(au.getPath())).findFirst().orElse(null);
    }

    @NotNull
    private static String getHomePath(@NotNull RepoPath path, @NotNull AbstractUser abstractUser) {
        return getHomePath(path, abstractUser.getPath(), abstractUser.getId());
    }

    @NotNull
    private static String getHomePath(@NotNull RepoPath path, @NotNull RepoPath userPath, @NotNull String id) {
        String subpath = (path.equals(userPath) ? "" : path.toString().substring(userPath.toString().length()));
        return "home("+id+")"+subpath;
    }

    private static void registerPrivileges(@NotNull PrivilegeDefinitions definitions, @NotNull Formatter formatter) {
        NameResolver nameResolver = new DefaultNamePathResolver(definitions.getNamespaceMapping());
        for (PrivilegeDefinition privilege : definitions.getDefinitions()) {
            try {
                String name = nameResolver.getJCRName(privilege.getName());
                String aggregates = getAggregatedNames(privilege, nameResolver);
                if (privilege.isAbstract()) {
                    formatter.format("register abstract privilege %s%s%n", name, aggregates);
                } else {
                    formatter.format("register privilege %s%s%n", name, aggregates);
                }
            } catch (NamespaceException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @NotNull
    private static String getAggregatedNames(@NotNull PrivilegeDefinition definition, @NotNull NameResolver nameResolver) {
        Set<Name> aggregatedNames = definition.getDeclaredAggregateNames();
        if (aggregatedNames.isEmpty()) {
            return "";
        } else {
            Set<String> names = aggregatedNames.stream().map(name -> {
                try {
                    return nameResolver.getJCRName(name);
                } catch (NamespaceException e) {
                    throw new IllegalStateException(e);
                }
            }).collect(Collectors.toSet());
            return " with "+String.join(",", names);
        }
    }
}
