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
package org.apache.sling.feature.cpconverter.acl;

import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;

import java.io.File;
import java.io.FileInputStream;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class DefaultAclManager implements AclManager {

    private static final String CONTENT_XML_FILE_NAME = ".content.xml";

    private static final String DEFAULT_TYPE = "sling:Folder";

    private final Set<SystemUser> preProvidedSystemUsers = new LinkedHashSet<>();

    private final Set<RepoPath> preProvidedSystemPaths = new HashSet<>();

    private final Set<RepoPath> preProvidedPaths = new HashSet<>();

    private final Set<SystemUser> systemUsers = new LinkedHashSet<>();

    private final Map<String, List<Acl>> acls = new HashMap<>();

    private List<String> nodetypeRegistrationSentences = new LinkedList<>();

    private Set<String> privileges = new LinkedHashSet<>();

    public boolean addSystemUser(SystemUser systemUser) {
        if (preProvidedSystemUsers.add(systemUser)) {
            return systemUsers.add(systemUser);
        }
        return false;
    }

    public boolean addAcl(String systemUser, Acl acl) {
        if (getSystemUser(systemUser).isPresent()) {
            acls.computeIfAbsent(systemUser, k -> new LinkedList<>()).add(acl);
            return true;
        }
        return false;
    }

    private void addPath(RepoPath path, Set<RepoPath> paths) {
        if (preProvidedPaths.add(path)) {
            paths.add(path);
        }

        RepoPath parent = path.getParent();
        if (parent != null && parent.getSegmentCount() > 0) {
            addPath(parent, paths);
        }
    }

    public void addRepoinitExtension(List<VaultPackageAssembler> packageAssemblers, FeaturesManager featureManager) {
        Formatter formatter = null;
        try {
            formatter = new Formatter();

            if (!privileges.isEmpty()) {
                for (String privilege : privileges) {
                    formatter.format("register privilege %s%n", privilege);
                }
            }

            for (String nodetypeRegistrationSentence : nodetypeRegistrationSentences) {
                formatter.format("%s%n", nodetypeRegistrationSentence);
            }

            // system users

            for (SystemUser systemUser : systemUsers) {
                // TODO does it harm?!?
                addSystemUserPath(formatter, systemUser.getPath());

                // make sure all users are created first

                formatter.format("create service user %s with path %s%n", systemUser.getId(), systemUser.getPath());

                // clean the unneeded ACLs, see SLING-8561

                List<Acl> authorizations = acls.remove(systemUser.getId());

                addStatements(systemUser, authorizations, packageAssemblers, formatter);
            }

            // all the resting ACLs can now be set

            for (Entry<String, List<Acl>> currentAcls : acls.entrySet()) {
                Optional<SystemUser> systemUser = getSystemUser(currentAcls.getKey());

                if (systemUser.isPresent()) {
                    List<Acl> authorizations = currentAcls.getValue();

                    addStatements(systemUser.get(), authorizations, packageAssemblers, formatter);
                }
            }

            String text = formatter.toString();

            if (!text.isEmpty()) {
                featureManager.addOrAppendRepoInitExtension(text, null);
            }
        } finally {
            if (formatter != null) {
                formatter.close();
            }
        }
    }

    private void addStatements(SystemUser systemUser,
                               List<Acl> authorizations,
                               List<VaultPackageAssembler> packageAssemblers,
                               Formatter formatter) {
        // clean the unneeded ACLs, see SLING-8561
        if (authorizations != null) {
            Iterator<Acl> authorizationsIterator = authorizations.iterator();
            while (authorizationsIterator.hasNext()) {
                Acl acl = authorizationsIterator.next();

                if (acl.getPath().startsWith(systemUser.getPath())) {
                    authorizationsIterator.remove();
                }
            }
        }

        // make sure all paths are created first

        addPaths(authorizations, packageAssemblers, formatter);

        // finally add ACLs

        addAclStatement(formatter, systemUser.getId(), authorizations);
    }

    private Optional<SystemUser> getSystemUser(String id) {
        for (SystemUser systemUser : preProvidedSystemUsers) {
            if (id.equals(systemUser.getId())) {
                return Optional.of(systemUser);
            }
        }
        return Optional.empty();
    }

    private final void addSystemUserPath(Formatter formatter, RepoPath path) {
        if (preProvidedSystemPaths.add(path)) {
            formatter.format("create path (rep:AuthorizableFolder) %s%n", path);
        }
    }

    @Override
    public void addNodetypeRegistrationSentence(String nodetypeRegistrationSentence) {
        if (nodetypeRegistrationSentence != null) {
            nodetypeRegistrationSentences.add(nodetypeRegistrationSentence);
        }
    }

    @Override
    public void addPrivilege(String privilege) {
        privileges.add(privilege);
    }

    public void reset() {
        systemUsers.clear();
        acls.clear();
        nodetypeRegistrationSentences.clear();
        privileges.clear();
    }

    private void addPaths(List<Acl> authorizations, List<VaultPackageAssembler> packageAssemblers, Formatter formatter) {
        if (areEmpty(authorizations)) {
            return;
        }

        Set<RepoPath> paths = new TreeSet<>();
        for (Acl authorization : authorizations) {
            addPath(authorization.getRepositoryPath(), paths);
        }

        for (RepoPath path : paths) {
            String type = computePathType(path, packageAssemblers);

            formatter.format("create path (%s) %s%n", type, path);
        }
    }

	private static String computePathType(RepoPath path, List<VaultPackageAssembler> packageAssemblers) {
        path = new RepoPath(PlatformNameFormat.getPlatformPath(path.toString()));

        for (VaultPackageAssembler packageAssembler: packageAssemblers) {
            File currentDir = packageAssembler.getEntry(path.toString());

            if (currentDir.exists()) {
                File currentContent = new File(currentDir, CONTENT_XML_FILE_NAME);
                if (currentContent.exists()) {
                    try (FileInputStream input = new FileInputStream(currentContent)) {
                        return new PrimaryTypeParser(DEFAULT_TYPE).parse(input);
                    } catch (Exception e) {
                        throw new RuntimeException("A fatal error occurred while parsing the '"
                            + currentContent
                            + "' file, see nested exceptions: "
                            + e);
                    }
                }
            }
        }

        return DEFAULT_TYPE;
    }

    private static void addAclStatement(Formatter formatter, String systemUser, List<Acl> authorizations) {
        if (authorizations == null || areEmpty(authorizations)) {
            return;
        }

        formatter.format("set ACL for %s%n", systemUser);

        for (Acl authorization : authorizations) {
            formatter.format("%s %s on %s",
                             authorization.getOperation(),
                             authorization.getPrivileges(),
                             authorization.getRepositoryPath());

            if (!authorization.getRestrictions().isEmpty()) {
                formatter.format(" restriction(%s)",
                                 authorization.getRestrictions().stream().collect(Collectors.joining(",")));
            }

            formatter.format("%n");
        }

        formatter.format("end%n");
    }

    private static boolean areEmpty(List<Acl> authorizations) {
        return authorizations == null || authorizations.isEmpty();
    }
}
