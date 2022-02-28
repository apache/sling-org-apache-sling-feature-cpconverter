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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
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
import org.apache.sling.feature.cpconverter.handlers.DefaultHandler;
import org.apache.sling.feature.cpconverter.handlers.EntryHandler;
import org.apache.sling.feature.cpconverter.handlers.EntryHandlersManager;
import org.apache.sling.feature.cpconverter.handlers.NodeTypesEntryHandler;
import org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.BundleSlingInitialContentExtractor;
import org.apache.sling.feature.cpconverter.vltpkg.BaseVaultPackageScanner;
import org.apache.sling.feature.cpconverter.vltpkg.PackagesEventsEmitter;
import org.apache.sling.feature.cpconverter.vltpkg.RecollectorVaultPackageScanner;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ContentPackage2FeatureModelConverter extends BaseVaultPackageScanner implements Closeable {

    public static final String ZIP_TYPE = "zip";

    public static final String PACKAGE_CLASSIFIER = "cp2fm-converted";

    private static final String DEFAULT_VERSION = "0.0.0";

    private final Map<PackageId, String> subContentPackages = new HashMap<>();

    private final List<VaultPackageAssembler> assemblers = new LinkedList<>();

    private final Map<PackageId, Set<Dependency>> mutableContentsIds = new LinkedHashMap<>();

    private EntryHandlersManager handlersManager;

    private AclManager aclManager;

    private FeaturesManager featuresManager;

    private ResourceFilter resourceFilter;

    private ArtifactsDeployer artifactsDeployer;

    private ArtifactsDeployer unreferencedArtifactsDeployer;

    private VaultPackageAssembler mainPackageAssembler;

    private final RecollectorVaultPackageScanner recollectorVaultPackageScanner;

    private final List<PackagesEventsEmitter> emitters = new ArrayList<>();

    private final List<Runnable> deployTasks = new ArrayList<>();

    private final File tmpDirectory;

    private boolean failOnMixedPackages = false;

    private PackagePolicy contentTypePackagePolicy = PackagePolicy.REFERENCE;

    private boolean removeInstallHooks = false;
    
    private boolean disablePackageTypeRecalculation = false;
    
    private BundleSlingInitialContentExtractor bundleSlingInitialContentExtractor = new BundleSlingInitialContentExtractor();

    public enum PackagePolicy {
        /**
         * References the content package in the feature model and deploys via the {@link ContentPackage2FeatureModelConverter#artifactsDeployer}
         */
        REFERENCE,
        /**
         * Drops the content package completely (i.e. neither reference it in the feature model nor deploy anywhere)
         *
         * @deprecated
         */
        DROP,
        /**
         * Deploys the content package via the {@link ContentPackage2FeatureModelConverter#unreferencedArtifactsDeployer}
         */
        PUT_IN_DEDICATED_FOLDER
    }

    public enum SlingInitialContentPolicy {
        /**
         * Keep in bundle and don't extract
         */
        KEEP,
        /**
         * Extract from bundle into content-packages and feature model
         */
        EXTRACT_AND_REMOVE,
        /**
         * Extract from bundle into content-packages and feature model but keep in bundle as well
         */
        EXTRACT_AND_KEEP
    }

    public ContentPackage2FeatureModelConverter() throws IOException {
        this(false, SlingInitialContentPolicy.KEEP, false);
    }

    public ContentPackage2FeatureModelConverter(boolean strictValidation, @NotNull SlingInitialContentPolicy slingInitialContentPolicy) throws IOException {
        this(strictValidation, slingInitialContentPolicy, false);
    }

    public ContentPackage2FeatureModelConverter(boolean strictValidation, @NotNull SlingInitialContentPolicy slingInitialContentPolicy, boolean disablePackageTypeRecalculation) throws IOException {
        super(strictValidation);
        this.disablePackageTypeRecalculation = disablePackageTypeRecalculation;
        this.recollectorVaultPackageScanner = new RecollectorVaultPackageScanner(this, this.packageManager, strictValidation, subContentPackages, slingInitialContentPolicy);
        this.tmpDirectory = Files.createTempDirectory("cp2fm-converter").toFile();
    }

    public @NotNull ContentPackage2FeatureModelConverter setEntryHandlersManager(@Nullable EntryHandlersManager handlersManager) {
        this.handlersManager = handlersManager;
        return this;
    }

    public @Nullable FeaturesManager getFeaturesManager() {
        return featuresManager;
    }

    public @NotNull ContentPackage2FeatureModelConverter setFeaturesManager(@NotNull FeaturesManager featuresManager) {
        this.featuresManager = featuresManager;
        if (featuresManager instanceof PackagesEventsEmitter) {
            this.emitters.add((PackagesEventsEmitter) featuresManager);
        }
        return this;
    }

    public @NotNull ContentPackage2FeatureModelConverter setResourceFilter(@Nullable ResourceFilter resourceFilter) {
        this.resourceFilter = resourceFilter;
        return this;
    }

    public @Nullable ArtifactsDeployer getArtifactsDeployer() {
        return artifactsDeployer;
    }

    public @NotNull ContentPackage2FeatureModelConverter setBundlesDeployer(@NotNull ArtifactsDeployer bundlesDeployer) {
        this.artifactsDeployer = bundlesDeployer;
        return this;
    }

    public @NotNull ContentPackage2FeatureModelConverter setUnreferencedArtifactsDeployer(@NotNull ArtifactsDeployer unreferencedArtifactsDeployer) {
        this.unreferencedArtifactsDeployer = unreferencedArtifactsDeployer;
        return this;
    }

    public @Nullable AclManager getAclManager() {
        return aclManager;
    }

    public @NotNull ContentPackage2FeatureModelConverter setAclManager(@NotNull AclManager aclManager) {
        this.aclManager = aclManager;
        return this;
    }
    
    public boolean hasMainPackageAssembler() {
        return mainPackageAssembler != null;
    }

    public @NotNull VaultPackageAssembler getMainPackageAssembler() {
        // verify that mainPackageAssembler has been set before retrieving it
        return Objects.requireNonNull(mainPackageAssembler);
    }

    public @NotNull ContentPackage2FeatureModelConverter setMainPackageAssembler(@NotNull VaultPackageAssembler assembler) {
        this.mainPackageAssembler = assembler;
        return this;
    }

    public @NotNull ContentPackage2FeatureModelConverter setEmitter(@NotNull PackagesEventsEmitter emitter) {
        this.emitters.add(emitter);
        return this;
    }

    public @NotNull ContentPackage2FeatureModelConverter setContentTypePackagePolicy(@NotNull PackagePolicy contentTypePackagePolicy) {
        this.contentTypePackagePolicy = contentTypePackagePolicy;
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

    public @NotNull File getTempDirectory() {
        return this.tmpDirectory;
    }
    
    public void cleanup() throws IOException {
        if (this.tmpDirectory.exists()) {
            logger.info("Cleaning up tmp directory {}", this.tmpDirectory);

            FileUtils.deleteDirectory(this.tmpDirectory);
        }
    }

    public void convert(@NotNull File... contentPackages) throws IOException, ConverterException {
        requireNonNull(contentPackages, "Null content-package(s) can not be converted.");
        secondPass(firstPass(contentPackages));
    }

    protected @NotNull Collection<VaultPackage> firstPass(@NotNull File... contentPackages) throws IOException, ConverterException {
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

            aclManager.reset();
            bundleSlingInitialContentExtractor.reset();
            
        }

        logger.info("Ordering input content-package(s) {}...", idPackageMapping.keySet());

        for (VaultPackage pack : idPackageMapping.values()) {
            orderDependencies(idFileMap, idPackageMapping, pack, new HashSet<>());
        }

        logger.info("New content-package(s) order: {}", idFileMap.keySet());

        return idFileMap.values();
    }

    private void secondPass(@NotNull Collection<VaultPackage> orderedContentPackages) throws IOException, ConverterException {
        emitters.stream().forEach(PackagesEventsEmitter::start);

        for (VaultPackage vaultPackage : orderedContentPackages) {
            try {
                emitters.stream().forEach(e -> e.startPackage(vaultPackage));
                setMainPackageAssembler(VaultPackageAssembler.create(this.getTempDirectory(), vaultPackage, removeInstallHooks, disablePackageTypeRecalculation));
                assemblers.add(getMainPackageAssembler());

                ArtifactId mvnPackageId = toArtifactId(vaultPackage.getId(), vaultPackage.getFile());

                featuresManager.init(mvnPackageId);

                logger.info("Converting content-package '{}'...", vaultPackage.getId());

                traverse(vaultPackage);

                // retrieve the resulting zip-content-package and deploy it to the local mvn bundles dir.
                try (VaultPackage result = processContentPackageArchive(getMainPackageAssembler(), null)) {

                    // finally serialize the Feature Model(s) file(s)

                    aclManager.addRepoinitExtension(assemblers, featuresManager);
                    bundleSlingInitialContentExtractor.addRepoInitExtension(assemblers, featuresManager);
                    
                    logger.info("Conversion complete!");

                    featuresManager.serialize();

                    emitters.stream().forEach(e -> e.endPackage(vaultPackage.getId(), result));
                }
            } finally {
                
                aclManager.reset();
                bundleSlingInitialContentExtractor.reset();
                assemblers.clear();

                try {
                    vaultPackage.close();
                } catch (Exception e) {
                    // close quietly
                }
            }
        }

        deployPackages();
        mutableContentsIds.clear();

        emitters.stream().forEach(PackagesEventsEmitter::end);
    }

    private static void orderDependencies(@NotNull Map<PackageId, VaultPackage> idFileMap,
                                          @NotNull Map<PackageId, VaultPackage> idPackageMapping,
                                          @NotNull VaultPackage pack,
                                          @NotNull Set<PackageId> visited) throws IOException, ConverterException {
        if (!visited.add(pack.getId())) {
            throw new ConverterException("Cyclic dependency detected, " + pack.getId() + " was previously visited already");
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

    public void processSubPackage(@NotNull String path, @Nullable String runMode, @NotNull VaultPackage vaultPackage, boolean isEmbeddedPackage) throws IOException, ConverterException {
        requireNonNull(path, "Impossible to process a null vault package");
        requireNonNull(vaultPackage, "Impossible to process a null vault package");

        if (!isSubContentPackageIncluded(path)) {
            logger.info("Sub content-package {} is filtered out, so it won't be processed.", path);
            return;
        }

        emitters.stream().forEach(e -> e.startSubPackage(path, vaultPackage));

        VaultPackageAssembler clonedPackage = VaultPackageAssembler.create(this.getTempDirectory(), vaultPackage, removeInstallHooks,disablePackageTypeRecalculation);

        // Please note: THIS IS A HACK to meet the new requirement without drastically change the original design
        // temporary swap the main handler to collect stuff
        VaultPackageAssembler handler = getMainPackageAssembler();
      
        Properties parentProps = handler.getPackageProperties();
        boolean isContainerPackage = PackageType.CONTAINER.equals(parentProps.get(PackageProperties.NAME_PACKAGE_TYPE));
        setMainPackageAssembler(clonedPackage);
        assemblers.add(clonedPackage);

        // scan the detected package, first
        traverse(vaultPackage);
        
        //set dependency to parent package if the parent package is an application package & subpackage is embedded
        if (isEmbeddedPackage && !isContainerPackage) {
            PackageId parentId = new PackageId((String) parentProps.get(PackageProperties.NAME_GROUP),
                    (String) parentProps.get(PackageProperties.NAME_NAME),
                    (String) parentProps.get(PackageProperties.NAME_VERSION));
            clonedPackage.addDependency(new Dependency(parentId));
        }

        // deploy the new content-package to the local mvn bundles dir and attach it to the feature
        try (VaultPackage result = processContentPackageArchive(clonedPackage, runMode)) {
            emitters.stream().forEach(e -> e.endSubPackage(path, vaultPackage.getId(), result));
        }

        // restore the previous assembler
        setMainPackageAssembler(handler);
    }

    private @NotNull VaultPackage processContentPackageArchive(@NotNull VaultPackageAssembler assembler,
                                             @Nullable String runMode) throws IOException, ConverterException {
        File contentPackageArchive = assembler.createPackage();

        VaultPackage vaultPackage = open(contentPackageArchive);

        PackageType packageType = detectPackageType(vaultPackage);

        // SLING-8608 - Fail the conversion if the resulting attached content-package is MIXED type
        if (PackageType.MIXED == packageType && failOnMixedPackages) {
            throw new ConverterException("Generated content-package '"
                    + vaultPackage.getId()
                    + "' located in file "
                    + contentPackageArchive
                    + " is of MIXED type");
        }

        ArtifactId mvnPackageId = toArtifactId(vaultPackage.getId(), contentPackageArchive);
        // special handling for converted packages of type content
        if (PackageType.CONTENT == packageType) {
            switch (contentTypePackagePolicy) {
                case DROP:
                    mutableContentsIds.put(vaultPackage.getId(), getDependencies(vaultPackage));
                    logger.info("Dropping package of PackageType.CONTENT {} (content-package id: {})",
                            mvnPackageId.getArtifactId(), vaultPackage.getId());
                    break;
                case PUT_IN_DEDICATED_FOLDER:
                    mutableContentsIds.put(vaultPackage.getId(), getDependencies(vaultPackage));
                    // deploy the new content-package to the unreferenced artifacts deployer
                    if (unreferencedArtifactsDeployer == null) {
                        throw new IllegalStateException("ContentTypePackagePolicy PUT_IN_DEDICATED_FOLDER requires a valid deployer ");
                    }
                    unreferencedArtifactsDeployer.deploy(new FileArtifactWriter(contentPackageArchive), mvnPackageId);
                    logger.info("Put converted package of PackageType.CONTENT {} (content-package id: {}) in {} (not referenced in feature model)",
                            mvnPackageId.getArtifactId(), vaultPackage.getId(), unreferencedArtifactsDeployer.getBaseDirectory());
                    break;
                case REFERENCE:
                    deploy(assembler, mvnPackageId, runMode);
            }
        } else {
            deploy(assembler, mvnPackageId, runMode);
        }
        return vaultPackage;
    }

    public void deployPackages() {
        try {
            mutableContentsIds.values().forEach(
                    value -> value.removeIf(dep -> mutableContentsIds.keySet().stream().anyMatch(dep::matches)));

            deployTasks.forEach(Runnable::run);
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof Exception) {
                throw ex;
            }
            throw ex;
        }
        deployTasks.clear();
    }

    private void deploy(@NotNull VaultPackageAssembler assembler, @NotNull ArtifactId mvnPackageId, @Nullable String runMode) {
        Objects.requireNonNull(getFeaturesManager()).addArtifact(runMode, mvnPackageId);
        ArtifactsDeployer deployer = Objects.requireNonNull(getArtifactsDeployer());
        deployTasks.add(() -> {
            assembler.updateDependencies(mutableContentsIds);
            try {
                File finalContentPackageArchive = assembler.createPackage();
                // deploy the new content-package to the local mvn bundles dir
                deployer.deploy(new FileArtifactWriter(finalContentPackageArchive), mvnPackageId);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    public boolean isSubContentPackageIncluded(@NotNull String path) {
        return subContentPackages.containsValue(path);
    }

    private void process(@NotNull String entryPath, @NotNull Archive archive, @Nullable Entry entry) throws IOException, ConverterException {
        if (resourceFilter != null && resourceFilter.isFilteredOut(entryPath)) {
            throw new ConverterException("Path '"
                    + entryPath
                    + "' in archive "
                    + archive.getMetaInf().getPackageProperties().getId()
                    + " not allowed by user configuration, please check configured filtering patterns");
        }

        EntryHandler entryHandler = handlersManager.getEntryHandlerByEntryPath(entryPath);
        if (entryHandler == null) {
            entryHandler = new DefaultHandler(getMainPackageAssembler(), removeInstallHooks);
        }

        if (entry == null) {
            entry = archive.getEntry(entryPath);
            if (entry == null) {
                throw new IllegalArgumentException("Archive '" + archive.getMetaInf().getPackageProperties().getId() + "' does not contain entry with path '" + entryPath + "'");
            }
        }
        entryHandler.handle(entryPath, archive, entry, this);
        if (!getMainPackageAssembler().recordEntryPath(entryPath)) {
            logger.warn("Duplicate entry path {}", entryPath);
        }
    }

    public ContentPackage2FeatureModelConverter setBundleSlingInitialContentExtractor(BundleSlingInitialContentExtractor bundleSlingInitialContentExtractor) {
        this.bundleSlingInitialContentExtractor = bundleSlingInitialContentExtractor;
        return this;
    }

    @Override
    protected void onFile(@NotNull String entryPath, @NotNull Archive archive, @NotNull Entry entry) throws IOException, ConverterException {
        try {
            process(entryPath, archive, entry);
        } catch (ConverterException ex) {
            throw new ConverterException("ConverterException occured on path " + entryPath + " with message: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new IOException("IOException occured on path " + entryPath + " with message: " + ex.getMessage(), ex);
        }
    }

    public static @NotNull ArtifactId toArtifactId(@NotNull PackageId packageId, @NotNull File file) {
        String groupId = requireNonNull(packageId.getGroup(),
                PackageProperties.NAME_GROUP
                        + " property not found in content-package "
                        + file
                        + ", please check META-INF/vault/properties.xml").replace('/', '.');
        // Replace any space with an underscore to adhere to Maven Group Id specification
        groupId = groupId.replace(" ", "_");

        String artifactId = requireNonNull(packageId.getName(),
                PackageProperties.NAME_NAME
                        + " property not found in content-package "
                        + file
                        + ", please check META-INF/vault/properties.xml");
        // Replace any space with an underscore to adhere to Maven Artifact Id specification
        artifactId = artifactId.replace(" ", "_");

        // package versions may use suffix "-cp2fm-converted" which is redundant as for artifactIds this is set as dedicated classifier
        String version = packageId.getVersionString();
        if (version.endsWith(VaultPackageAssembler.VERSION_SUFFIX)) {
            version = version.substring(0, version.length() - VaultPackageAssembler.VERSION_SUFFIX.length());
        }
        if (version.isEmpty()) {
            version = DEFAULT_VERSION;
        }

        return new ArtifactId(groupId, artifactId, version, PACKAGE_CLASSIFIER, ZIP_TYPE);
    }

    @Override
    protected void addCdnPattern(@NotNull Pattern cndPattern) {
        handlersManager.addEntryHandler(NodeTypesEntryHandler.forCndPattern(cndPattern));
    }

    @Override
    public void close() throws IOException {
        cleanup();
    }

    public List<VaultPackageAssembler> getAssemblers() {
        return new ArrayList<>(assemblers);
    }
}
