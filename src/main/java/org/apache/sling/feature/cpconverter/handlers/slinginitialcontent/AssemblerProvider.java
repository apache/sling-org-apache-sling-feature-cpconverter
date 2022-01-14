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

import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageUtils;
import org.apache.sling.jcr.contentloader.PathEntry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates the VaultPackage assembler logic for the sling initial content extraction
 */
public class AssemblerProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(AssemblerProvider.class);

    private final Map<PackageType, VaultPackageAssembler> packageAssemblers = new EnumMap<>(PackageType.class);

    /**
     * Lazily initializes the cache with the necessary VaultPackageAssemblers
     * @param repositoryPath
     * @return the VaultPackageAssembler from the cache to use for the given repository path
     */
    public VaultPackageAssembler initPackageAssemblerForPath(@NotNull BundleSlingInitialContentExtractorContext context, @NotNull String repositoryPath, @NotNull PathEntry pathEntry)
            throws ConverterException {

        ArtifactId bundleArtifactId = context.getBundleArtifactId();
        PackageType packageType = VaultPackageUtils.detectPackageType(repositoryPath);
        VaultPackageAssembler assembler = lazyConstruct(context, repositoryPath, bundleArtifactId, packageType);
        addPathFilterSetToAssemblerFilter(pathEntry, assembler);
        return assembler;
    }

    public Set<Map.Entry<PackageType, VaultPackageAssembler>> getPackageAssemblerEntrySet() {
        return packageAssemblers.entrySet();
    }

    @NotNull
    private VaultPackageAssembler lazyConstruct(@NotNull BundleSlingInitialContentExtractorContext context, @NotNull String repositoryPath, ArtifactId bundleArtifactId, PackageType packageType) throws ConverterException {
        VaultPackageAssembler assembler = packageAssemblers.get(packageType);
        if (assembler == null) {
            final String packageNameSuffix;
            switch (packageType) {
                case APPLICATION:
                    packageNameSuffix = "-apps";
                    break;
                case CONTENT:
                    packageNameSuffix = "-content";
                    break;
                default:
                    throw new ConverterException("Unexpected package type " + packageType + " detected for path " + repositoryPath);
            }
            final PackageId packageId = new PackageId(bundleArtifactId.getGroupId(), bundleArtifactId.getArtifactId()+packageNameSuffix, bundleArtifactId.getVersion());
            assembler = VaultPackageAssembler.create(context.getConverter().getTempDirectory(), packageId, "Generated out of Sling Initial Content from bundle " + bundleArtifactId + " by cp2fm");
            packageAssemblers.put(packageType, assembler);
            logger.info("Created package {} out of Sling-Initial-Content from '{}'", packageId, bundleArtifactId);
        }
        return assembler;
    }

    private void addPathFilterSetToAssemblerFilter(@NotNull PathEntry pathEntry, VaultPackageAssembler assembler) {
        ImportMode importMode;
        if (pathEntry.isOverwrite()) {
            importMode = ImportMode.UPDATE;
        } else {
            importMode = ImportMode.MERGE;
        }

        DefaultWorkspaceFilter filter = assembler.getFilter();
        if (filter.getFilterSets().stream().noneMatch(set -> set.getRoot().equals(pathEntry.getTarget() != null ? pathEntry.getTarget() : "/") &&
                set.getImportMode() == importMode)) {
            PathFilterSet pathFilterSet = new PathFilterSet(pathEntry.getTarget() != null ? pathEntry.getTarget() : "/");
            // TODO: add handling for merge, mergeProperties and overwriteProperties (https://issues.apache.org/jira/browse/SLING-10318)
            pathFilterSet.setImportMode(importMode);
            filter.add(pathFilterSet);
        }
    }


    public void clear() {
        this.packageAssemblers.clear();
    }
}
