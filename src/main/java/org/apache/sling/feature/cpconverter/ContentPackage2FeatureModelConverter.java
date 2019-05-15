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

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.PackageManager;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.apache.sling.feature.cpconverter.acl.AclManager;
import org.apache.sling.feature.cpconverter.artifacts.ArtifactsDeployer;
import org.apache.sling.feature.cpconverter.artifacts.FileArtifactWriter;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.filtering.ResourceFilter;
import org.apache.sling.feature.cpconverter.handlers.EntryHandler;
import org.apache.sling.feature.cpconverter.handlers.EntryHandlersManager;
import org.apache.sling.feature.cpconverter.vltpkg.BaseVaultPackageScanner;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContentPackage2FeatureModelConverter extends BaseVaultPackageScanner {

    public static final String ZIP_TYPE = "zip";

    public static final String PACKAGE_CLASSIFIER = "cp2fm-converted";

    private static final String DEFEAULT_VERSION = "0.0.0";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final PackageManager packageManager = new PackageManagerImpl();

    private EntryHandlersManager handlersManager;

    private AclManager aclManager;

    private FeaturesManager featuresManager;

    private ResourceFilter resourceFilter;

    private ArtifactsDeployer artifactsDeployer;

    private VaultPackageAssembler mainPackageAssembler = null;

    public ContentPackage2FeatureModelConverter() {
        this(false);
    }

    public ContentPackage2FeatureModelConverter(boolean strictValidation) {
        super(strictValidation);
    }

    public ContentPackage2FeatureModelConverter setEntryHandlersManager(EntryHandlersManager handlersManager) {
        this.handlersManager = handlersManager;
        return this;
    }

    public FeaturesManager getFeaturesManager() {
        return featuresManager;
    }

    public ContentPackage2FeatureModelConverter setFeaturesManager(FeaturesManager featuresManager) {
        this.featuresManager = featuresManager;
        return this;
    }

    public ContentPackage2FeatureModelConverter setResourceFilter(ResourceFilter resourceFilter) {
        this.resourceFilter = resourceFilter;
        return this;
    }

    public ArtifactsDeployer getArtifactsDeployer() {
        return artifactsDeployer;
    }

    public ContentPackage2FeatureModelConverter setBundlesDeployer(ArtifactsDeployer bundlesDeployer) {
        this.artifactsDeployer = bundlesDeployer;
        return this;
    }

    public AclManager getAclManager() {
        return aclManager;
    }

    public ContentPackage2FeatureModelConverter setAclManager(AclManager aclManager) {
        this.aclManager = aclManager;
        return this;
    }

    public VaultPackageAssembler getMainPackageAssembler() {
        return mainPackageAssembler;
    }

    public void convert(File contentPackage) throws Exception {
        requireNonNull(contentPackage , "Null content-package can not be converted.");

        if (!contentPackage.exists() || !contentPackage.isFile()) {
            throw new IllegalArgumentException("Content-package "
                                            + contentPackage
                                            + " does not exist or it is not a valid file.");
        }

        logger.info("Reading content-package '{}'...", contentPackage);

        try (VaultPackage vaultPackage = packageManager.open(contentPackage, isStrictValidation())) {
            logger.info("content-package '{}' successfully read!", contentPackage);

            mainPackageAssembler = VaultPackageAssembler.create(vaultPackage);
            PackageProperties packageProperties = vaultPackage.getProperties();

            String group = requireNonNull(packageProperties.getProperty(PackageProperties.NAME_GROUP),
                                          PackageProperties.NAME_GROUP
                                          + " property not found in content-package "
                                          + contentPackage
                                          + ", please check META-INF/vault/properties.xml")
                                          .replace('/', '.');

            String name = requireNonNull(packageProperties.getProperty(PackageProperties.NAME_NAME),
                                        PackageProperties.NAME_NAME
                                        + " property not found in content-package "
                                        + contentPackage
                                        + ", please check META-INF/vault/properties.xml");

            String version = packageProperties.getProperty(PackageProperties.NAME_VERSION);
            if (version == null || version.isEmpty()) {
                version = DEFEAULT_VERSION;
            }

            String description = packageProperties.getDescription();

            featuresManager.init(group,
                                 name,
                                 version,
                                 description);

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

            aclManager.reset();
        }
    }

    public void processSubPackage(String path, File contentPackage) throws Exception {
        requireNonNull(path, "Impossible to process a null vault package");
        requireNonNull(contentPackage, "Impossible to process a null vault package");

        try (VaultPackage vaultPackage = packageManager.open(contentPackage, isStrictValidation())) {
            // scan the detected package, first
            traverse(vaultPackage);

            // merge filters to the main new package
            mainPackageAssembler.mergeFilters(vaultPackage.getMetaInf().getFilter());

            // add the metadata-only package one to the main package
            File clonedPackage = VaultPackageAssembler.create(vaultPackage).createPackage();
            mainPackageAssembler.addEntry(path, clonedPackage);
        }
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

        EntryHandler entryHandler = handlersManager.getEntryHandlerByEntryPath(entryPath);
        if (entryHandler == null) {
            entryHandler = mainPackageAssembler;
        }

        entryHandler.handle(entryPath, archive, entry, this);
    }

}
