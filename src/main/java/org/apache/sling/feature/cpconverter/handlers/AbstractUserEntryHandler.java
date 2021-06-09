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

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;

abstract class AbstractUserEntryHandler extends AbstractRegexEntryHandler {
    
    // FIXME: use first segment of 'systemUserRelPath' config value instead of hardcoding 
    private static final String SYSTEM_USER_SEGMENT = "/system/";

    AbstractUserEntryHandler(@NotNull String rexex) {
        super(rexex);
    }

    @Override
    public void handle(@NotNull String path, @NotNull Archive archive, @NotNull Entry entry, @NotNull ContentPackage2FeatureModelConverter converter)
            throws Exception {
        Matcher matcher = getPattern().matcher(path);
        if (matcher.matches()) {
            RepoPath originalPath = new RepoPath(PlatformNameFormat.getRepositoryPath(matcher.group(1)));
            RepoPath intermediatePath = originalPath.getParent();

            byte[] tmp = IOUtils.toByteArray((archive.openInputStream(entry)));
            AbstractUserParser parser = createParser(converter, originalPath, intermediatePath);
            boolean converted = parser.parse(new ByteArrayInputStream(tmp));
            if (!converted && !path.contains(SYSTEM_USER_SEGMENT)) {
                // write back regular users, groups and their intermediate folders that did not get converted into
                // repo-init statements to the content package
                VaultPackageAssembler assembler = converter.getMainPackageAssembler();
                // FIXME: assembler is null when handler is called from RecollectorVaultPackageScanner (???)
                if (assembler != null) {
                    try (InputStream input = new ByteArrayInputStream(tmp);
                         OutputStream output = assembler.createEntry(path)){
                        IOUtils.copy(input, output);
                    }
                }
            }
        }
    }

    abstract AbstractUserParser createParser(@NotNull ContentPackage2FeatureModelConverter converter, @NotNull RepoPath originalPath, @NotNull RepoPath intermediatePath);

}
