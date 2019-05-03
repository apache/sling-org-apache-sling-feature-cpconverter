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
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;

/**
 * The Manager able to collect and build System Users and related ACL policies.
 */
public final class AclManager {

    private static final String CONTENT_XML_FILE_NAME = ".content.xml";

    private static final String DEFAULT_TYPE = "sling:Folder";

    private final Set<String> systemUsers = new LinkedHashSet<>();

    private final Set<String> paths = new TreeSet<String>();

    private final Map<String, List<Acl>> acls = new HashMap<>();

    public boolean addSystemUser(String systemUser) {
        if (systemUser != null && !systemUser.isEmpty()) {
            return systemUsers.add(systemUser);
        }
        return false;
    }

    public Acl addAcl(String systemUser, String operation, String privileges, String path) {
        addPath(path);

        Acl acl = new Acl(operation, privileges, path);
        acls.computeIfAbsent(systemUser, k -> new LinkedList<>()).add(acl);
        return acl;
    }

    private void addPath(String path) {
        paths.add(path);

        int endIndex = path.lastIndexOf('/');
        if (endIndex > 0) {
            addPath(path.substring(0, endIndex));
        }
    }

    public void addRepoinitExtension(VaultPackageAssembler packageAssembler, Feature feature) {
        if (systemUsers.isEmpty()) {
            return;
        }

        Extension repoInitExtension = new Extension(ExtensionType.TEXT, Extension.EXTENSION_NAME_REPOINIT, true);

        Formatter formatter = new Formatter();

        // make sure all paths are created first

        for (String path : paths) {
            File currentDir = packageAssembler.getEntry(path);
            String type = DEFAULT_TYPE;

            if (currentDir.exists()) {
                File currentContent = new File(currentDir, CONTENT_XML_FILE_NAME);
                if (currentContent.exists()) {
                    try (FileInputStream input = new FileInputStream(currentContent)) {
                        type = new PrimaryTypeParser(DEFAULT_TYPE).parse(input);
                    } catch (Exception e) {
                        throw new RuntimeException("A fatal error occurred while parsing the '"
                                                   + currentContent
                                                   + "' file, see nested exceptions: "
                                                   + e);
                    } finally {
                        formatter.close();
                    }
                }
            }

            formatter.format("create path (%s) %s%n", type, path);
        }

        for (String systemUser : systemUsers) {
            // create then the users

            formatter.format("create service user %s%n", systemUser);

            // ACL can now be set

            List<Acl> authorizations = acls.get(systemUser);
            if (authorizations != null && !authorizations.isEmpty()) {
                formatter.format("set ACL for %s%n", systemUser);

                for (Acl authorization : authorizations) {
                    authorization.addAclStatement(formatter);
                }

                formatter.format("end%n");
            }
        }

        String text = formatter.toString();
        formatter.close();
        repoInitExtension.setText(text);

        feature.getExtensions().add(repoInitExtension);
    }

}
