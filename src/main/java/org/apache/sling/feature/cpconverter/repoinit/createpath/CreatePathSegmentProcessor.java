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
package org.apache.sling.feature.cpconverter.repoinit.createpath;

import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.cpconverter.shared.ConverterConstants;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.repoinit.parser.operations.CreatePath;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.apache.jackrabbit.vault.util.Constants.DOT_CONTENT_XML;

public class CreatePathSegmentProcessor {

    private CreatePathSegmentProcessor() {
    }

    /**
     * Process segments of a repopath to createpath, checking packageassemblers for existing primaryType definitions.
     *
     * @param path
     * @param packageAssemblers
     * @param cp
     * @return
     */
    public static boolean processSegments(@NotNull RepoPath path, @NotNull Collection<VaultPackageAssembler> packageAssemblers, @NotNull CreatePath cp) {
        String repositoryPath = "";
        boolean foundType = false;
        for (final String part : path.getSegments()) {
            final String platformName = PlatformNameFormat.getPlatformName(part);
            repositoryPath = repositoryPath.concat(ConverterConstants.SLASH).concat(platformName);

            boolean segmentAdded = false;
            //loop all package assemblers and check if .content.xml is defined
            for (VaultPackageAssembler packageAssembler : packageAssemblers) {
                File currentContent = packageAssembler.getFileEntry(repositoryPath.concat(ConverterConstants.SLASH).concat(DOT_CONTENT_XML));
                if (currentContent.exists() && currentContent.isFile()) {
                    //add segment if jcr:primaryType is defined.
                    segmentAdded = addSegment(cp, part, currentContent);
                    if (segmentAdded) {
                        foundType = true;
                        break;
                    }
                }
            }
            if (!segmentAdded) {
                //use sling:Folder (defined by repo-init runtime module)
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

}
