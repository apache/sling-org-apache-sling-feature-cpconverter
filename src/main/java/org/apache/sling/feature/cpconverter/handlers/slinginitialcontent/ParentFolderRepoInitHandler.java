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
package org.apache.sling.feature.cpconverter.handlers.slinginitialcontent;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.repoinit.createpath.CreatePathSegmentProcessor;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.repoinit.parser.operations.CreatePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.apache.jackrabbit.vault.util.Constants.DOT_CONTENT_XML;

/**
 * Handles creating the parent folders for sling initial content entries from the bundle
 */
class ParentFolderRepoInitHandler {

    private static final Logger logger = LoggerFactory.getLogger(ParentFolderRepoInitHandler.class);

    private final Set<RepoPath> parentFolderPaths = new HashSet<>();

    void addParentsForPath(@NotNull String contentPackageEntryPath) {

        String parentFolder = contentPackageEntryPath;
        if (StringUtils.endsWith(contentPackageEntryPath, DOT_CONTENT_XML)) {
            parentFolder = StringUtils.substringBeforeLast(parentFolder, "/" + DOT_CONTENT_XML);
        }
        parentFolder = StringUtils.substringBeforeLast(parentFolder, "/");
        parentFolder = StringUtils.substringAfter(parentFolder, "/jcr_root");

        parentFolderPaths.add(new RepoPath(parentFolder));
    }

    void reset() {
        parentFolderPaths.clear();
    }

    void addRepoinitExtension(@NotNull List<VaultPackageAssembler> assemblers,
                              @NotNull FeaturesManager featureManager) throws IOException, ConverterException {

        try (Formatter formatter = new Formatter()) {
            parentFolderPaths.stream()
                    .filter(entry -> parentFolderPaths.stream()
                            .noneMatch(other -> !other.equals(
                                    entry) &&
                                    other.startsWith(entry)
                            )
                    )
                    .filter(entry ->
                            !entry.isRepositoryPath()
                    )
                    .map(entry ->
                            // we want to make sure of all our entries that are repositoryPaths, 
                            // we create repoinit statements to create the parent folders with proper types.
                            // if we don't do this we will end up with constraintViolationExceptions.
                            getCreatePath(entry, assemblers)
                    )
                    .filter(Objects::nonNull)

                    .forEach(
                            createPath -> formatter.format("%s", createPath.asRepoInitString())
                    );

            String text = formatter.toString();

            if (!text.isEmpty()) {
                featureManager.addOrAppendRepoInitExtension("content-package", text, null);
            }
        }

    }


    @Nullable
    CreatePath getCreatePath(@NotNull RepoPath path, @NotNull Collection<VaultPackageAssembler> packageAssemblers) {
        if (path.getParent() == null) {
            logger.debug("Omit create path statement for path '{}'", path);
            return null;
        }

        CreatePath cp = new CreatePath("sling:Folder");
        CreatePathSegmentProcessor.processSegments(path, packageAssemblers, cp);
        return cp;
    }

}
