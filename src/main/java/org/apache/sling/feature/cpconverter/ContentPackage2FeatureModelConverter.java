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
import java.io.FileWriter;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.PackageManager;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Artifacts;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Extensions;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.acl.AclManager;
import org.apache.sling.feature.cpconverter.spi.BundlesDeployer;
import org.apache.sling.feature.cpconverter.spi.EntryHandler;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.feature.cpconverter.writers.FileArtifactWriter;
import org.apache.sling.feature.io.json.FeatureJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContentPackage2FeatureModelConverter {

    private static final String CONTENT_PACKAGES = "content-packages";

    public static final String ZIP_TYPE = "zip";

    public static final String PACKAGE_CLASSIFIER = "cp2fm-converted";

    private static final String SLING_OSGI_FEATURE_TILE_TYPE = "slingosgifeature";

    private static final String JSON_FILE_EXTENSION = ".json";

    private static final String DEFEAULT_VERSION = "0.0.0";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final PackageManager packageManager = new PackageManagerImpl();

    private final ServiceLoader<EntryHandler> entryHandlers = ServiceLoader.load(EntryHandler.class);

    private final Map<String, Feature> runModes = new HashMap<>();

    private final AclManager aclManager = new AclManager();

    private final RegexBasedResourceFilter filter = new RegexBasedResourceFilter();

    private BundlesDeployer artifactDeployer;

    private boolean strictValidation = false;

    private boolean mergeConfigurations = false;

    private int bundlesStartOrder = 0;

    private File artifactsOutputDirectory;

    private File featureModelsOutputDirectory;

    private Feature targetFeature = null;

    private VaultPackageAssembler mainPackageAssembler = null;

    private String id;

    private String idOverride;

    public ContentPackage2FeatureModelConverter setStrictValidation(boolean strictValidation) {
        this.strictValidation = strictValidation;
        return this;
    }

    public boolean isStrictValidation() {
        return strictValidation;
    }

    public boolean isMergeConfigurations() {
        return mergeConfigurations;
    }

    public ContentPackage2FeatureModelConverter setMergeConfigurations(boolean mergeConfigurations) {
        this.mergeConfigurations = mergeConfigurations;
        return this;
    }

    public ContentPackage2FeatureModelConverter setBundlesStartOrder(int bundlesStartOrder) {
        this.bundlesStartOrder = bundlesStartOrder;
        return this;
    }

    public ContentPackage2FeatureModelConverter setArtifactsOutputDirectory(File artifactsOutputDirectory) {
        this.artifactsOutputDirectory = artifactsOutputDirectory;
        return this;
    }

    public File getArtifactsOutputDirectory() {
        return artifactsOutputDirectory;
    }

    public ContentPackage2FeatureModelConverter setFeatureModelsOutputDirectory(File featureModelsOutputDirectory) {
        this.featureModelsOutputDirectory = featureModelsOutputDirectory;
        return this;
    }

    public Feature getTargetFeature() {
        return targetFeature;
    }

    public void addFilteringPattern(String filteringPattern) {
        requireNonNull(filteringPattern, "Null pattern to filter resources out is not a valid filtering pattern");
        if (filteringPattern.isEmpty()) {
            throw new IllegalArgumentException("Empty pattern to filter resources out is not a valid filtering pattern");
        }

        filter.addFilteringPattern(filteringPattern);
    }

    public ContentPackage2FeatureModelConverter setId(String id) {
        this.id = id;
        return this;
    }

    public ContentPackage2FeatureModelConverter setIdOverride(String id) {
        this.idOverride = id;
        return this;
    }

    public AclManager getAclManager() {
        return aclManager;
    }

    public VaultPackageAssembler getMainPackageAssembler() {
        return mainPackageAssembler;
    }

    public Feature getRunMode(String runMode) {
        if (getTargetFeature() == null) {
            throw new IllegalStateException("Target Feature not initialized yet, please make sure convert() method was invoked first.");
        }

        if (runMode == null) {
            return getTargetFeature();
        }

        ArtifactId newId = appendRunmode(getTargetFeature().getId(), runMode);

        return runModes.computeIfAbsent(runMode, k -> new Feature(newId));
    }

    private ArtifactId appendRunmode(ArtifactId id, String runMode) {
        ArtifactId newId;
        if (runMode == null) {
            newId = id;
        } else {
            final String classifier;
            if (id.getClassifier() != null && !id.getClassifier().isEmpty()) {
                classifier = id.getClassifier() + '-' + runMode;
            } else {
                classifier = runMode;
            }

            newId = new ArtifactId(id.getGroupId(), id.getArtifactId(), id.getVersion(), classifier, id.getType());
        }
        return newId;
    }

    public BundlesDeployer getArtifactDeployer() {
        return artifactDeployer;
    }

    private static void checkDirectory(File directory, String name) {
        if (directory == null) {
            throw new IllegalStateException("Null " + name + " output directory not supported, it must be set before invoking the convert(File) method.");
        }

        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("output directory "
                                            + directory
                                            + " does not exist and can not be created, please make sure current user '"
                                            + System.getProperty("user.name")
                                            + " has enough rights to write on the File System.");
        }
    }

    public void convert(File contentPackage) throws Exception {
        requireNonNull(contentPackage , "Null content-package can not be converted.");

        if (!contentPackage.exists() || !contentPackage.isFile()) {
            throw new IllegalArgumentException("Content-package "
                                            + contentPackage
                                            + " does not exist or it is not a valid file.");
        }

        checkDirectory(artifactsOutputDirectory, "artifacts");
        checkDirectory(featureModelsOutputDirectory, "models");

        Iterator<BundlesDeployer> artifactDeployerLoader = ServiceLoader.load(BundlesDeployer.class).iterator();
        if (!artifactDeployerLoader.hasNext()) {
            artifactDeployer = new DefaultBundlesDeployer(artifactsOutputDirectory);
        } else {
            artifactDeployer = artifactDeployerLoader.next();
        }

        logger.info("Reading content-package '{}'...", contentPackage);

        try (VaultPackage vaultPackage = packageManager.open(contentPackage, strictValidation)) {
            logger.info("content-package '{}' successfully read!", contentPackage);

            mainPackageAssembler = VaultPackageAssembler.create(vaultPackage);
            PackageProperties packageProperties = vaultPackage.getProperties();

            ArtifactId artifactId;
            if (id != null && !id.isEmpty()) {
                artifactId = ArtifactId.fromMvnId(id);
            } else {
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

                artifactId = new ArtifactId(group,
                                            name,
                                            version,
                                            null,
                                            SLING_OSGI_FEATURE_TILE_TYPE);
            }

            targetFeature = new Feature(artifactId);

            targetFeature.setDescription(packageProperties.getDescription());

            logger.info("Converting content-package '{}' to Feature File '{}'...", vaultPackage.getId(), targetFeature.getId());

            process(vaultPackage);

            // attach all unmatched resources as new content-package

            File contentPackageArchive = mainPackageAssembler.createPackage(artifactsOutputDirectory);

            // deploy the new zip content-package to the local mvn bundles dir

            artifactDeployer.deploy(new FileArtifactWriter(contentPackageArchive),
                                    targetFeature.getId().getGroupId(),
                                    targetFeature.getId().getArtifactId(),
                                    targetFeature.getId().getVersion(),
                                    PACKAGE_CLASSIFIER,
                                    ZIP_TYPE);

            attach(null,
                   targetFeature.getId().getGroupId(),
                   targetFeature.getId().getArtifactId(),
                   targetFeature.getId().getVersion(),
                   PACKAGE_CLASSIFIER,
                   ZIP_TYPE);

            // finally serialize the Feature Model(s) file(s)

            aclManager.addRepoinitExtension(mainPackageAssembler, getTargetFeature());

            logger.info("Conversion complete!");

            RunmodeMapper runmodeMapper = RunmodeMapper.open(featureModelsOutputDirectory);

            seralize(getTargetFeature(), null, runmodeMapper);

            if (!runModes.isEmpty()) {
                for (java.util.Map.Entry<String, Feature> runmodeEntry : runModes.entrySet()) {
                    String runmode = runmodeEntry.getKey();
                    seralize(runmodeEntry.getValue(), runmode, runmodeMapper);
                }
            }

            runmodeMapper.save();
            aclManager.reset();
        }
    }

    public void addConfiguration(String runMode, String pid, Dictionary<String, Object> configurationProperties) {
        Feature feature = getRunMode(runMode);
        Configuration configuration = feature.getConfigurations().getConfiguration(pid);

        if (configuration == null) {
            configuration = new Configuration(pid);
            feature.getConfigurations().add(configuration);
        } else if (!mergeConfigurations) {
            throw new IllegalStateException("Configuration '"
                                            + pid
                                            + "' already defined in Feature Model '"
                                            + feature.getId().toMvnId()
                                            + "', set the 'mergeConfigurations' flag to 'true' if you want to merge multiple configurations with same PID");
        }

        Enumeration<String> keys = configurationProperties.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            Object value = configurationProperties.get(key);

            if (value != null && Collection.class.isInstance(value)) {
                value = ((Collection<?>) value).toArray();
            }

            configuration.getProperties().put(key, value);
        }
    }

    private void seralize(Feature feature, String runMode, RunmodeMapper runmodeMapper) throws Exception {
        StringBuilder fileNameBuilder = new StringBuilder().append(feature.getId().getArtifactId());

        String classifier = feature.getId().getClassifier();
        if (classifier != null && !classifier.isEmpty()) {
            fileNameBuilder.append('-').append(classifier);
        }

        fileNameBuilder.append(JSON_FILE_EXTENSION);

        String fileName = fileNameBuilder.toString();

        File targetFile = new File(featureModelsOutputDirectory, fileName);

        logger.info("Writing resulting Feature Model '{}' to file '{}'...", feature.getId(), targetFile);

        if (idOverride != null && !idOverride.isEmpty()) {
            ArtifactId idOverrride = appendRunmode(ArtifactId.parse(idOverride), runMode);
            feature = feature.copy(idOverrride);
        }

        try (FileWriter targetWriter = new FileWriter(targetFile)) {
            FeatureJSONWriter.write(targetWriter, feature);

            logger.info("'{}' Feature File successfully written!", targetFile);

            runmodeMapper.addOrUpdate(runMode, fileName);
        }
    }

    public void processSubPackage(String path, File contentPackage) throws Exception {
        requireNonNull(path, "Impossible to process a null vault package");
        requireNonNull(contentPackage, "Impossible to process a null vault package");

        try (VaultPackage vaultPackage = packageManager.open(contentPackage, strictValidation)) {
            // scan the detected package, first
            process(vaultPackage);

            // merge filters to the main new package
            mainPackageAssembler.mergeFilters(vaultPackage.getMetaInf().getFilter());

            // add the metadata-only package one to the main package
            File clonedPackage = VaultPackageAssembler.create(vaultPackage).createPackage();
            mainPackageAssembler.addEntry(path, clonedPackage);
        }
    }

    private void process(VaultPackage vaultPackage) throws Exception {
        requireNonNull(vaultPackage, "Impossible to process a null vault package");

        if (getTargetFeature() == null) {
            throw new IllegalStateException("Target Feature not initialized yet, please make sure convert() method was invoked first.");
        }

        Archive archive = vaultPackage.getArchive();
        try {
            archive.open(strictValidation);

            Entry jcrRoot = archive.getJcrRoot();
            traverse(null, archive, jcrRoot);
        } finally {
            archive.close();
        }
    }

    private void traverse(String path, Archive archive, Entry entry) throws Exception {
        String entryPath = newPath(path, entry.getName());

        if (entry.isDirectory()) {
            for (Entry child : entry.getChildren()) {
                traverse(entryPath, archive, child);
            }

            return;
        }

        logger.info("Processing entry {}...", entryPath);

        if (filter.isFilteredOut(entryPath)) {
            throw new IllegalArgumentException("Path '"
                                               + entryPath
                                               + "' in archive "
                                               + archive.getMetaInf().getProperties()
                                               + " not allowed by user configuration, please check configured filtering patterns");
        }

        getEntryHandlerByEntryPath(entryPath).handle(entryPath, archive, entry, this);

        logger.info("Entry {} successfully processed.", entryPath);
    }

    private static String newPath(String path, String entryName) {
        if (path == null) {
            return entryName;
        }

        return path + '/' + entryName;
    }

    private EntryHandler getEntryHandlerByEntryPath(String path) {
        Iterator<EntryHandler> entryHandlersIterator = entryHandlers.iterator();
        while (entryHandlersIterator.hasNext()) {
            EntryHandler entryHandler = entryHandlersIterator.next();

            if (entryHandler.matches(path)) {
                return entryHandler;
            }
        }

        return mainPackageAssembler;
    }

    public void attach(String runMode,
                       String groupId,
                       String artifactId,
                       String version,
                       String classifier,
                       String type) {
        requireNonNull(groupId, "Artifact can not be attached to a feature without specifying a valid 'groupId'.");
        requireNonNull(artifactId, "Artifact can not be attached to a feature without specifying a valid 'artifactId'.");
        requireNonNull(version, "Artifact can not be attached to a feature without specifying a valid 'version'.");
        requireNonNull(type, "Artifact can not be attached to a feature without specifying a valid 'type'.");

        Artifact artifact = new Artifact(new ArtifactId(groupId, artifactId, version, classifier, type));

        Feature targetFeature = getRunMode(runMode);
        Artifacts artifacts;

        if (ZIP_TYPE.equals(type) ) {
            Extensions extensions = targetFeature.getExtensions();
            Extension extension = extensions.getByName(CONTENT_PACKAGES);

            if (extension == null) {
                extension = new Extension(ExtensionType.ARTIFACTS, CONTENT_PACKAGES, true);
                extensions.add(extension);
            }

            artifacts = extension.getArtifacts();
        } else {
            artifact.setStartOrder(bundlesStartOrder);
            artifacts = targetFeature.getBundles();
        }

        artifacts.add(artifact);
    }

}
