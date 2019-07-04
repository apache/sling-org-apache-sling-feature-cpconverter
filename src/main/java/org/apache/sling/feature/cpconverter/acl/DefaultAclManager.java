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

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;

public final class DefaultAclManager implements AclManager {

    private static final String CONTENT_XML_FILE_NAME = ".content.xml";

    private static final String DEFAULT_TYPE = "sling:Folder";

    private final Set<SystemUser> preProvidedSystemUsers = new LinkedHashSet<>();

    private final Set<Path> preProvidedPaths = new HashSet<>();

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

    public Acl addAcl(String systemUser, Acl acl) {
        acls.computeIfAbsent(systemUser, k -> new LinkedList<>()).add(acl);
        return acl;
    }

    private void addPath(Path path, Set<Path> paths) {
        if (preProvidedPaths.add(path)) {
            paths.add(path);
        }

        Path parent = path.getParent();
        if (parent != null && parent.getNameCount() > 0) {
            addPath(parent, paths);
        }
    }

    public void addRepoinitExtension(List<VaultPackageAssembler> packageAssemblers, Feature feature) {
        Formatter formatter = null;
        try {
            formatter = new Formatter();

            if (!privileges.isEmpty()) {
                for (String privilege : privileges) {
                    formatter.format("register privilege %s%n", privilege);
                }
            }

            if (!nodetypeRegistrationSentences.isEmpty()) {
                formatter.format("register nodetypes%n")
                         .format("<<===%n");

                for (String nodetypeRegistrationSentence : nodetypeRegistrationSentences) {
                    if (nodetypeRegistrationSentence.isEmpty()) {
                        formatter.format("%n");
                    } else {
                        formatter.format("<< %s%n", nodetypeRegistrationSentence);
                    }
                }

                formatter.format("===>>%n");
            }

            // system users

            for (SystemUser systemUser : systemUsers) {
                // make sure all users are created first

                formatter.format("create service user %s with path %s%n", systemUser.getId(), systemUser.getPath());

                // clean the unneeded ACLs, see SLING-8561

                List<Acl> authorizations = acls.remove(systemUser.getId());
                if (authorizations != null) {
                    Iterator<Acl> authorizationsIterator = authorizations.iterator();
                    while (authorizationsIterator.hasNext()) {
                        Acl acl = authorizationsIterator.next();

                        if (acl.getPath().startsWith(systemUser.getPath())) {
                            authorizationsIterator.remove();
                        }
                    }
                }

                // create then the paths

                addPaths(authorizations, packageAssemblers, formatter);

                // finally add ACLs

                addAclStatement(formatter, systemUser.getId(), authorizations);
            }

            // all the resting ACLs can now be set

            for (Entry<String, List<Acl>> currentAcls : acls.entrySet()) {
                String systemUser = currentAcls.getKey();

                if (isKnownSystemUser(systemUser)) {
                    List<Acl> authorizations = currentAcls.getValue();

                    // make sure all paths are created first

                    addPaths(authorizations, packageAssemblers, formatter);

                    // finally add ACLs

                    addAclStatement(formatter, systemUser, authorizations);
                }
            }

            String text = formatter.toString();

            if (!text.isEmpty()) {
                Extension repoInitExtension = new Extension(ExtensionType.TEXT, Extension.EXTENSION_NAME_REPOINIT, true);
                repoInitExtension.setText(text);
                feature.getExtensions().add(repoInitExtension);
            }
        } finally {
            if (formatter != null) {
                formatter.close();
            }
        }
    }

    private boolean isKnownSystemUser(String id) {
        for (SystemUser systemUser : preProvidedSystemUsers) {
            if (id.equals(systemUser.getId())) {
                return true;
            }
        }
        return false;
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

        Set<Path> paths = new TreeSet<>();
        for (Acl authorization : authorizations) {
            addPath(authorization.getPath(), paths);
        }

        for (Path path : paths) {
            String type = computePathType(path, packageAssemblers);

            formatter.format("create path (%s) %s%n", type, path);
        }
    }

    private static String computePathType(Path path, List<VaultPackageAssembler> packageAssemblers) {
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
        if (areEmpty(authorizations)) {
            return;
        }

        formatter.format("set ACL for %s%n", systemUser);

        for (Acl authorization : authorizations) {
            formatter.format("%s %s on %s",
                             authorization.getOperation(),
                             authorization.getPrivileges(),
                             authorization.getPath());

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
