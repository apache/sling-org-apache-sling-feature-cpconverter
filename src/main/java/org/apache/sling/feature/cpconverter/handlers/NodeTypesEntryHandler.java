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
package org.apache.sling.feature.cpconverter.handlers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.shared.NodeTypeUtil;
import org.jetbrains.annotations.NotNull;

public class NodeTypesEntryHandler extends AbstractRegexEntryHandler {

    public NodeTypesEntryHandler() {
        super("/META-INF/vault/.*\\.cnd");
    }

    public static NodeTypesEntryHandler forCndPattern(@NotNull Pattern pattern) {
        // as the pattern refers to an absolute repository path, the prefix jcr_root needs to be manually added
        // should work for most of the patterns
        String originalCndRegex = pattern.pattern();
        if (originalCndRegex.startsWith("^")) {
            originalCndRegex = originalCndRegex.substring(1);
        }
        if (originalCndRegex.startsWith("/")) {
            originalCndRegex = originalCndRegex.substring(1);
        }
        return new NodeTypesEntryHandler(Pattern.compile("/"+Constants.ROOT_DIR+"/"+originalCndRegex));
    }

    private NodeTypesEntryHandler(@NotNull Pattern pattern) {
        super(pattern);
    }

    @Override
    public void handle(@NotNull String path, @NotNull Archive archive, @NotNull Entry entry, @NotNull ContentPackage2FeatureModelConverter converter)
            throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(archive.openInputStream(entry))))) {
            AclManager aclManager = Objects.requireNonNull(converter.getAclManager());
            for (String line : NodeTypeUtil.generateRepoInitLines(reader)) {
                aclManager.addNodetypeRegistrationSentence(line);
            }
        }
    }

}
