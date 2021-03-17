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
import static org.apache.sling.feature.cpconverter.vltpkg.VaultPackageUtils.detectPackageType;
import static org.apache.sling.feature.cpconverter.vltpkg.VaultPackageUtils.getDependencies;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.CyclicDependencyException;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.artifacts.ArtifactsDeployer;
import org.apache.sling.feature.cpconverter.artifacts.FileArtifactWriter;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.filtering.ResourceFilter;
import org.apache.sling.feature.cpconverter.handlers.EntryHandler;
import org.apache.sling.feature.cpconverter.handlers.EntryHandlersManager;
import org.apache.sling.feature.cpconverter.handlers.NodeTypesEntryHandler;
import org.apache.sling.feature.cpconverter.vltpkg.BaseVaultPackageScanner;
import org.apache.sling.feature.cpconverter.vltpkg.PackagesEventsEmitter;
import org.apache.sling.feature.cpconverter.vltpkg.RecollectorVaultPackageScanner;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ContentPackage2FeatureModelConverter extends BaseVaultPackageScanner {

    public static final String ZIP_TYPE = "zip";

    public static final String PACKAGE_CLASSIFIER = "cp2fm-converted";

    private static final String DEFEAULT_VERSION = "0.0.0";

    private final Map<PackageId, String> subContentPackages = new HashMap<>();

    private final List<VaultPackageAssembler> assemblers = new LinkedList<>();

    private final Map<PackageId, Set<Dependency>> mutableContentsIds = new LinkedHashMap<>();

    private EntryHandlersManager handlersManager;

    private AclManager aclManager;

    private FeaturesManager featuresManager;

    private ResourceFilter resourceFilter;

    private ArtifactsDeployer artifactsDeployer;

    private VaultPackageAssembler mainPackageAssembler;

    private final RecollectorVaultPackageScanner recollectorVaultPackageScanner;

    private PackagesEventsEmitter emitter;

    private boolean failOnMixedPackages = false;

    private boolean dropContent = false;

    private boolean removeInstallHooks = false;

    private final File tmpDirectory;

    public ContentPackage2FeatureModelConverter() {
        this(false);
    }

    public ContentPackage2FeatureModelConverter(boolean strictValidation) {
        super(strictValidation);
        this.recollectorVaultPackageScanner = new RecollectorVaultPackageScanner(this, this.packageManager, strictValidation, subContentPackages);
        try {
            this.tmpDirectory = Files.createTempDirectory("cp2fm-converter").toFile();
        } catch ( final IOException io) {
            throw new RuntimeException("Unable to create a temporary directory", io);
        }
    }

    public @NotNull ContentPackage2FeatureModelConverter setEntryHandlersManager(@Nullable EntryHandlersManager handlersManager) {
        this.handlersManager = handlersManager;
        return this;
    }

    public @Nullable FeaturesManager getFeaturesManager() {
        return featuresManager;
    }

    public @NotNull ContentPackage2FeatureModelConverter setFeaturesManager(@Nullable FeaturesManager featuresManager) {
        this.featuresManager = featuresManager;
        return this;
    }

    public @NotNull ContentPackage2FeatureModelConverter setResourceFilter(@Nullable ResourceFilter resourceFilter) {
        this.resourceFilter = resourceFilter;
        return this;
    }

    public @Nullable ArtifactsDeployer getArtifactsDeployer() {
        return artifactsDeployer;
    }

    public @NotNull ContentPackage2FeatureModelConverter setBundlesDeployer(@Nullable ArtifactsDeployer bundlesDeployer) {
        this.artifactsDeployer = bundlesDeployer;
        return this;
    }

    public @Nullable AclManager getAclManager() {
        return aclManager;
    }

    public @NotNull ContentPackage2FeatureModelConverter setAclManager(@Nullable AclManager aclManager) {
        this.aclManager = aclManager;
        return this;
    }

    public @Nullable VaultPackageAssembler getMainPackageAssembler() {
        return mainPackageAssembler;
    }

    public @NotNull ContentPackage2FeatureModelConverter setEmitter(@Nullable PackagesEventsEmitter emitter) {
        this.emitter = emitter;
        return this;
    }
    
    public @NotNull ContentPackage2FeatureModelConverter setDropContent(boolean dropContent) {
        this.dropContent = dropContent;
        return this;
    }

    public @NotNull ContentPackage2FeatureModelConverter setFailOnMixedPackages(boolean failOnMixedPackages) {
        this.failOnMixedPackages = failOnMixedPackages;
        return this;
    }

    public @NotNull ContentPackage2FeatureModelConverter setRemoveInstallHooks(boolean removeInstallHook) {
        this.removeInstallHooks = removeInstallHook;
        return this;
    }

    public File getTempDirectory() {
        return this.tmpDirectory;
    }
    
    public void cleanup() {
        if ( this.tmpDirectory.exists() ) {
            logger.info( "Cleaning up tmp directory {}", this.tmpDirectory);

            try {
                FileUtils.deleteDirectory( this.tmpDirectory );
            } catch (IOException e) {
                logger.error( "Error Deleting {}", this.tmpDirectory );
            }
        }
    }
    
    public void convert(@NotNull File...contentPackages) throws Exception {
        requireNonNull(contentPackages , "Null content-package(s) can not be converted.");
        secondPass(firstPass(contentPackages));
    }

    protected @NotNull Collection<VaultPackage> firstPass(@NotNull File...contentPackages) throws Exception {
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
            // possible outdated conflicting packages
            recollectorVaultPackageScanner.traverse(pack);

            logger.info("content-package '{}' successfully read!", contentPackage);
        }

        logger.info("Ordering input content-package(s) {}...", idPackageMapping.keySet());

        for (VaultPackage pack : idPackageMapping.values()) {
            orderDependencies(idFileMap, idPackageMapping, pack, new HashSet<>());
        }

        logger.info("New content-package(s) order: {}", idFileMap.keySet());

        return idFileMap.values();
    }

    protected void secondPass(@NotNull Collection<VaultPackage> orderedContentPackages) throws Exception {
        emitter.start();

        for (VaultPackage vaultPackage : orderedContentPackages) {
            try {
                emitter.startPackage(vaultPackage);
                mainPackageAssembler = VaultPackageAssembler.create(this.getTempDirectory(), vaultPackage, removeInstallHooks);
                assemblers.add(mainPackageAssembler);

                ArtifactId mvnPackageId = toArtifactId(vaultPackage);

                featuresManager.init(mvnPackageId.getGroupId(), mvnPackageId.getArtifactId(), mvnPackageId.getVersion());

                logger.info("Converting content-package '{}'...", vaultPackage.getId());

                traverse(vaultPackage);

                // make sure

                mainPackageAssembler.updateDependencies(mutableContentsIds);

                // attach all unmatched resources as new content-package

                File contentPackageArchive = mainPackageAssembler.createPackage();

                // deploy the new zip content-package to the local mvn bundles dir

                processContentPackageArchive(contentPackageArchive, null, mvnPackageId, vaultPackage.getId());

                // finally serialize the Feature Model(s) file(s)

                aclManager.addRepoinitExtension(assemblers, featuresManager);

                logger.info("Conversion complete!");

                featuresManager.serialize();
                emitter.endPackage();
            } finally {
                aclManager.reset();
                assemblers.clear();

                try {
                    vaultPackage.close();
                } catch (Exception e) {
                    // close quietly
                }
            }
        }

        mutableContentsIds.clear();

        emitter.end();
    }

    private void orderDependencies(@NotNull Map<PackageId, VaultPackage> idFileMap,
                                   @NotNull Map<PackageId, VaultPackage> idPackageMapping,
                                   @NotNull VaultPackage pack,
                                   @NotNull Set<PackageId> visited) throws CyclicDependencyException {
        if (!visited.add(pack.getId())) {
            throw new CyclicDependencyException("Cyclic dependency detected, " + pack.getId() + " was previously visited already");
        }

        for (Dependency dep : pack.getDependencies()) {
            for (java.util.Map.Entry<PackageId, VaultPackage> entry : idPackageMapping.entrySet()) {
                if (dep.matches(entry.getKey())) {
                    orderDependencies(idFileMap, idPackageMapping, entry.getValue(), visited);
                    break;
                }
            }
        }

        idFileMap.put(pack.getId(), pack);
        idPackageMapping.remove(pack.getId());
    }

    public void processSubPackage(@NotNull String path, @Nullable String runMode, @NotNull VaultPackage vaultPackage, boolean isEmbeddedPackage) throws Exception {
        requireNonNull(path, "Impossible to process a null vault package");
        requireNonNull(vaultPackage, "Impossible to process a null vault package");

        if (!isSubContentPackageIncluded(path)) {
            logger.info("Sub content-package {} is filtered out, so it won't be processed.", path);
            return;
        }

        emitter.startSubPackage(path, vaultPackage);

        PackageId originalPackageId = vaultPackage.getId();
        ArtifactId mvnPackageId = toArtifactId(vaultPackage);
        VaultPackageAssembler clonedPackage = VaultPackageAssembler.create(this.getTempDirectory(), vaultPackage, removeInstallHooks);

        // Please note: THIS IS A HACK to meet the new requirement without drastically change the original design
        // temporary swap the main handler to collect stuff
        VaultPackageAssembler handler = mainPackageAssembler;
        assemblers.add(handler);
        Properties parentProps = handler.getPackageProperties();
        boolean isContainerPackage = PackageType.CONTAINER.equals(parentProps.get(PackageProperties.NAME_PACKAGE_TYPE));
        mainPackageAssembler = clonedPackage;

        // scan the detected package, first
        traverse(vaultPackage);

        clonedPackage.updateDependencies(mutableContentsIds);
        
        //set dependency to parent package if the parent package is an application package & subpackage is embedded
        if (isEmbeddedPackage && !isContainerPackage) {
            PackageId parentId = new PackageId((String)parentProps.get(PackageProperties.NAME_GROUP), 
                                                (String)parentProps.get(PackageProperties.NAME_NAME),
                                                (String)parentProps.get(PackageProperties.NAME_VERSION));
            clonedPackage.addDependency(new Dependency(parentId));
        }

        File contentPackageArchive = clonedPackage.createPackage();

        // deploy the new content-package to the local mvn bundles dir and attach it to the feature
        processContentPackageArchive(contentPackageArchive, runMode, mvnPackageId, originalPackageId);

        // restore the previous assembler
        mainPackageAssembler = handler;

        emitter.endSubPackage();
    }

    private void processContentPackageArchive(@NotNull File contentPackageArchive,
                                              @Nullable String runMode,
                                              @NotNull ArtifactId mvnPackageId,
                                              @NotNull PackageId originalPackageId) throws Exception {
        try (VaultPackage vaultPackage = open(contentPackageArchive)) {
            PackageType packageType = detectPackageType(vaultPackage);

            // SLING-8608 - Fail the conversion if the resulting attached content-package is MIXED type
            if (PackageType.MIXED == packageType && failOnMixedPackages) {
                throw new Exception("Generated content-package '"
                                    + originalPackageId
                                    + "' located in file "
                                    + contentPackageArchive
                                    + " is of MIXED type");
            }

            // don't deploy & add content-packages of type content to featuremodel if dropContent is set
            if (PackageType.CONTENT != packageType || !dropContent) {
                // deploy the new content-package to the local mvn bundles dir and attach it to the feature
                artifactsDeployer.deploy(new FileArtifactWriter(contentPackageArchive), mvnPackageId);
                featuresManager.addArtifact(runMode, mvnPackageId);
            } else {
                mutableContentsIds.put(originalPackageId, getDependencies(vaultPackage));
                logger.info("Dropping package of PackageType.CONTENT {} (content-package id: {})",
                            mvnPackageId.getArtifactId(), originalPackageId);
            }
        }
    }

    protected boolean isSubContentPackageIncluded(@NotNull String path) {
        return subContentPackages.containsValue(path);
    }

    @Override
    protected void onFile(@NotNull String entryPath, @NotNull Archive archive, @NotNull Entry entry) throws Exception {
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

    private static @NotNull ArtifactId toArtifactId(@NotNull VaultPackage vaultPackage) {
        PackageId packageId = vaultPackage.getId();
        String groupId = requireNonNull(packageId.getGroup(),
            PackageProperties.NAME_GROUP
                + " property not found in content-package "
                + vaultPackage
                + ", please check META-INF/vault/properties.xml").replace('/', '.');
        // Replace any space with an underscore to adhere to Maven Group Id specification
        groupId = groupId.replaceAll(" ", "_");

        String artifactid = requireNonNull(packageId.getName(),
            PackageProperties.NAME_NAME
                + " property not found in content-package "
                + vaultPackage
                + ", please check META-INF/vault/properties.xml");
        // Replace any space with an underscore to adhere to Maven Artifact Id specification
        artifactid = artifactid.replaceAll(" ", "_");

        String version = packageId.getVersionString();
        if (version == null || version.isEmpty()) {
            version = DEFEAULT_VERSION;
        }

        return new ArtifactId(groupId, artifactid, version, PACKAGE_CLASSIFIER, ZIP_TYPE);
    }

    @Override
    protected void addCdnPattern(@NotNull Pattern cndPattern) {
        handlersManager.addEntryHandler(NodeTypesEntryHandler.forCndPattern(cndPattern));
    }

}
