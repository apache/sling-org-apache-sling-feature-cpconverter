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
package org.apache.sling.feature.cpconverter;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.CyclicDependencyException;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.sling.feature.cpconverter.acl.AclManager;
import org.apache.sling.feature.cpconverter.artifacts.ArtifactsDeployer;
import org.apache.sling.feature.cpconverter.artifacts.FileArtifactWriter;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.filtering.ResourceFilter;
import org.apache.sling.feature.cpconverter.handlers.EntryHandler;
import org.apache.sling.feature.cpconverter.vltpkg.BaseVaultPackageScanner;
import org.apache.sling.feature.cpconverter.vltpkg.RecollectorVaultPackageScanner;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;

import com.google.inject.Inject;

public class ContentPackage2FeatureModelConverter extends BaseVaultPackageScanner {

    public static final String ZIP_TYPE = "zip";

    public static final String PACKAGE_CLASSIFIER = "cp2fm-converted";

    private static final String DEFEAULT_VERSION = "0.0.0";

    @Inject
    private Map<PackageId, String> subContentPackages;

    @Inject
    private AclManager aclManager;

    @Inject
    private FeaturesManager featuresManager;

    @Inject
    private ResourceFilter resourceFilter;

    @Inject
    private ArtifactsDeployer artifactsDeployer;

    @Inject
    private Set<EntryHandler> entryHandlers;

    @Inject
    private RecollectorVaultPackageScanner recollectorVaultPackageScanner;

    private VaultPackageAssembler mainPackageAssembler = null;

    public VaultPackageAssembler getMainPackageAssembler() {
        return mainPackageAssembler;
    }

    public void convert(File...contentPackages) throws Exception {
        requireNonNull(contentPackages , "Null content-package(s) can not be converted.");
        secondPass(firstPass(contentPackages));
    }

    protected Collection<VaultPackage> firstPass(File...contentPackages) throws Exception {
        Map<PackageId, VaultPackage> idFileMap = new LinkedHashMap<>();
        Map<PackageId, VaultPackage> idPackageMapping = new ConcurrentHashMap<>();

        for (File contentPackage : contentPackages) {
            requireNonNull(contentPackage, "Null content-package can not be converted.");

            if (!contentPackage.exists() || !contentPackage.isFile()) {
                throw new IllegalArgumentException("File " + contentPackage + " does not exist or it is a directory");
            }

            logger.info("Reading content-package '{}'...", contentPackage);

            VaultPackage pack = open(contentPackage);
            idPackageMapping.put(pack.getId(), pack);

            // analyze sub-content packages in order to filter out
            // possible outdated conflictring packages
            recollectorVaultPackageScanner.traverse(pack);

            logger.info("content-package '{}' successfully read!", contentPackage);
        }

        logger.info("Ordering input content-package(s) {}...", idPackageMapping.keySet());

        for (VaultPackage pack : idPackageMapping.values()) {
            orderDependencies(idFileMap, idPackageMapping, pack, new HashSet<PackageId>());
        }

        logger.info("New content-package(s) order: {}", idFileMap.keySet());

        return idFileMap.values();
    }

    protected void secondPass(Collection<VaultPackage> orderedContentPackages) throws Exception {
        for (VaultPackage vaultPackage : orderedContentPackages) {
            try {
                mainPackageAssembler = VaultPackageAssembler.create(vaultPackage);
                PackageId packageProperties = vaultPackage.getId();

                String group = requireNonNull(packageProperties.getGroup(),
                                              PackageProperties.NAME_GROUP
                                              + " property not found in content-package "
                                              + vaultPackage
                                              + ", please check META-INF/vault/properties.xml")
                                              .replace('/', '.');

                String name = requireNonNull(packageProperties.getName(),
                                            PackageProperties.NAME_NAME
                                            + " property not found in content-package "
                                            + vaultPackage
                                            + ", please check META-INF/vault/properties.xml");

                String version = packageProperties.getVersionString();
                if (version == null || version.isEmpty()) {
                    version = DEFEAULT_VERSION;
                }

                featuresManager.init(group, name, version);

                logger.info("Converting content-package '{}'...", vaultPackage.getId());

                traverse(vaultPackage);

                // attach all unmatched resources as new content-package

                File contentPackageArchive = mainPackageAssembler.createPackage();

                // deploy the new zip content-package to the local mvn bundles dir

                artifactsDeployer.deploy(new FileArtifactWriter(contentPackageArchive),
                                         featuresManager.getTargetFeature().getId().getGroupId(),
                                         featuresManager.getTargetFeature().getId().getArtifactId(),
                                         featuresManager.getTargetFeature().getId().getVersion(),
                                         PACKAGE_CLASSIFIER,
                                         ZIP_TYPE);

                featuresManager.addArtifact(null,
                                            featuresManager.getTargetFeature().getId().getGroupId(),
                                            featuresManager.getTargetFeature().getId().getArtifactId(),
                                            featuresManager.getTargetFeature().getId().getVersion(),
                                            PACKAGE_CLASSIFIER,
                                            ZIP_TYPE);

                // finally serialize the Feature Model(s) file(s)

                aclManager.addRepoinitExtension(mainPackageAssembler, featuresManager.getTargetFeature());

                logger.info("Conversion complete!");

                featuresManager.serialize();
            } finally {
                aclManager.reset();

                try {
                    vaultPackage.close();
                } catch (Exception e) {
                    // close quietly
                }
            }
        }
    }

    private void orderDependencies(Map<PackageId, VaultPackage> idFileMap,
                                   Map<PackageId, VaultPackage> idPackageMapping,
                                   VaultPackage pack,
                                   Set<PackageId> visited) throws CyclicDependencyException {
        if (!visited.add(pack.getId())) {
            throw new CyclicDependencyException("Cyclic dependency detected, " + pack.getId() + " was previously visited already");
        }

        for (Dependency dep : pack.getDependencies()) {
            for (PackageId id : idPackageMapping.keySet()) {
                if (dep.matches(id)) {
                    orderDependencies(idFileMap, idPackageMapping, idPackageMapping.get(id), visited);
                    break;
                }
            }
        }

        idFileMap.put(pack.getId(), pack);
        idPackageMapping.remove(pack.getId());
    }

    public void processSubPackage(String path, VaultPackage vaultPackage) throws Exception {
        requireNonNull(path, "Impossible to process a null vault package");
        requireNonNull(vaultPackage, "Impossible to process a null vault package");

        if (!isSubContentPackageIncluded(path)) {
            logger.info("Sub content-package {} is filtered out, so it won't be processed.", path);
            return;
        }

        // scan the detected package, first
        traverse(vaultPackage);

        // merge filters to the main new package
        mainPackageAssembler.mergeFilters(vaultPackage.getMetaInf().getFilter());

        // add the metadata-only package one to the main package with overriden filter
        File clonedPackage = VaultPackageAssembler.createSynthetic(vaultPackage);
        mainPackageAssembler.addEntry(path, clonedPackage);
    }

    protected boolean isSubContentPackageIncluded(String path) {
        return subContentPackages.containsValue(path);
    }

    @Override
    protected void onFile(String entryPath, Archive archive, Entry entry) throws Exception {
        if (resourceFilter != null && resourceFilter.isFilteredOut(entryPath)) {
            throw new IllegalArgumentException("Path '"
                                               + entryPath
                                               + "' in archive "
                                               + archive.getMetaInf().getProperties()
                                               + " not allowed by user configuration, please check configured filtering patterns");
        }

        EntryHandler entryHandler = null;

        dance : for (EntryHandler currentHandler : entryHandlers) {
            if (currentHandler.matches(entryPath)) {
                entryHandler = currentHandler;
                break dance;
            }
        }

        if (entryHandler == null) {
            entryHandler = mainPackageAssembler;
        }

        entryHandler.handle(entryPath, archive, entry);
    }

}
