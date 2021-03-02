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
package org.apache.sling.feature.cpconverter.features;

import static java.util.Objects.requireNonNull;
import static org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.ZIP_TYPE;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Artifacts;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionState;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Extensions;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.interpolator.SimpleVariablesInterpolator;
import org.apache.sling.feature.cpconverter.interpolator.VariablesInterpolator;
import org.apache.sling.feature.extension.apiregions.api.ApiExport;
import org.apache.sling.feature.extension.apiregions.api.ApiRegion;
import org.apache.sling.feature.extension.apiregions.api.ApiRegions;
import org.apache.sling.feature.io.json.FeatureJSONWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultFeaturesManager implements FeaturesManager {

    public enum ConfigurationHandling {
        ORDERED,
        MERGE,
        STRICT
    };

    private static final String CONTENT_PACKAGES = "content-packages";

    private static final String SLING_OSGI_FEATURE_TILE_TYPE = "slingosgifeature";

    private static final String JSON_FILE_EXTENSION = ".json";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, Feature> runModes = new HashMap<>();

    private final VariablesInterpolator interpolator = new SimpleVariablesInterpolator();

    private final ConfigurationHandling configurationHandling;

    private final int bundlesStartOrder;

    private final File featureModelsOutputDirectory;

    private final Map<String, List<String>> apiRegionExports = new HashMap<>();

    private final String artifactIdOverride;

    private final String prefix;

    private final Map<String, String> properties;

    private final List<String> targetAPIRegions = new ArrayList<>();

    private String exportsToAPIRegion;

    private Feature targetFeature;

    private final Map<String, String> pidToPathMapping = new HashMap<>();

    DefaultFeaturesManager() {
        this(new File(""));
    }

    public DefaultFeaturesManager(@NotNull File tempDir) {
        this(true, 20, tempDir, null, null, new HashMap<>());
    }

    public DefaultFeaturesManager(boolean mergeConfigurations,
                                  int bundlesStartOrder,
                                  @NotNull File featureModelsOutputDirectory,
                                  @Nullable String artifactIdOverride,
                                  @Nullable String prefix,
                                  @NotNull Map<String, String> properties) {
        this(mergeConfigurations ? ConfigurationHandling.MERGE : ConfigurationHandling.ORDERED, bundlesStartOrder, featureModelsOutputDirectory,
                artifactIdOverride, prefix, properties);
    }

    public DefaultFeaturesManager(@NotNull ConfigurationHandling configurationHandling,
                                    int bundlesStartOrder,
                                    @NotNull File featureModelsOutputDirectory,
                                    @Nullable String artifactIdOverride,
                                    @Nullable String prefix,
                                    @NotNull Map<String, String> properties) {
        this.configurationHandling = configurationHandling;
        this.bundlesStartOrder = bundlesStartOrder;
        this.featureModelsOutputDirectory = featureModelsOutputDirectory;
        this.artifactIdOverride = artifactIdOverride;
        this.prefix = prefix;
        this.properties = properties;
    }

    @Override
    public void init(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
        targetFeature = new Feature(new ArtifactId(groupId, artifactId, version, null, SLING_OSGI_FEATURE_TILE_TYPE));

        runModes.clear();
    }

    @Override
    public @Nullable Feature getTargetFeature() {
        return targetFeature;
    }

    @Override
    public @NotNull Feature getRunMode(@Nullable String runMode) {
        if (getTargetFeature() == null) {
            throw new IllegalStateException("Target Feature not initialized yet, please make sure convert() method was invoked first.");
        }

        if (runMode == null) {
            return getTargetFeature();
        }

        ArtifactId newId = appendRunmode(getTargetFeature().getId(), runMode);

        return runModes.computeIfAbsent(runMode, k -> {
            Feature f = new Feature(newId);
            return f;
        });
    }

    @Override
    public void addArtifact(@Nullable String runMode, @NotNull ArtifactId id) {
        addArtifact(runMode, id, null);
    }

    @Override
    public void addArtifact(@Nullable String runMode, @NotNull ArtifactId id, @Nullable Integer startOrder) {
        requireNonNull(id, "Artifact can not be attached to a feature without specifying a valid ArtifactId.");

        Artifact artifact = new Artifact(id);

        Feature targetFeature = getRunMode(runMode);
        Artifacts artifacts;

        if (ZIP_TYPE.equals(id.getType()) ) {
            Extensions extensions = targetFeature.getExtensions();
            Extension extension = extensions.getByName(CONTENT_PACKAGES);

            if (extension == null) {
                extension = new Extension(ExtensionType.ARTIFACTS, CONTENT_PACKAGES, ExtensionState.REQUIRED);
                extensions.add(extension);
            }

            artifacts = extension.getArtifacts();
        } else {
            int startOrderForBundle = startOrder != null ? startOrder.intValue() : bundlesStartOrder;
            artifact.setStartOrder(startOrderForBundle);
            artifacts = targetFeature.getBundles();
        }

        artifacts.add(artifact);
    }

    private @NotNull ArtifactId appendRunmode(@NotNull ArtifactId id, @Nullable String runMode) {
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

    @Override
    public void addAPIRegionExport(@Nullable String runMode, @NotNull String exportedPackage) {
        if (exportsToAPIRegion == null)
            return; // Ignore if we're not exporting to an API region


        getRunMode(runMode); // Trigger runmode initialization

        List<String> l = apiRegionExports.computeIfAbsent(runMode, r -> new ArrayList<>());
        l.add(exportedPackage);
    }

    @Override
    public void addConfiguration(@Nullable String runMode, 
        @NotNull String pid, 
        @NotNull String path,
        @Nullable Dictionary<String, Object> configurationProperties) {
        Feature feature = getRunMode(runMode);
        Configuration configuration = feature.getConfigurations().getConfiguration(pid);

        if (configuration == null) {
            configuration = new Configuration(pid);
            feature.getConfigurations().add(configuration);
            this.pidToPathMapping.put(pid, path);
        } else {
            switch ( this.configurationHandling ) {
                case STRICT : throw new IllegalStateException("Configuration '"
                               + pid
                               + "' already defined in Feature Model '"
                               + feature.getId().toMvnId()
                               + "', set the 'mergeConfigurations' flag to 'true' if you want to merge multiple configurations with same PID");
                case ORDERED : final String oldPath = this.pidToPathMapping.get(pid);
                               if ( oldPath == null || oldPath.compareTo(path) > 0 ) {
                                   this.pidToPathMapping.put(pid, path);
                                   feature.getConfigurations().remove(configuration);
                                   configuration = new Configuration(pid);
                                   feature.getConfigurations().add(configuration);                       
                               } else {
                                   return;
                               }
                case MERGE : // nothing to do
            }
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
        // remove internal properties (these are ignored anyway)
        configuration.getProperties().remove(Constants.SERVICE_PID);
        configuration.getProperties().remove("service.bundleLocation");
        configuration.getProperties().remove("service.factoryPid");
    }

    private void addAPIRegions(@NotNull Feature feature, @Nullable List<String> exportedPackages) throws IOException {
        if (exportedPackages == null)
            exportedPackages = Collections.emptyList();

        if (exportedPackages.size() == 0 && targetAPIRegions.size() == 0)
            return; // Nothing to do.

        ApiRegions regions = new ApiRegions();
        if (exportsToAPIRegion != null) {
            ApiRegion ar = new ApiRegion(exportsToAPIRegion);
            exportedPackages
                .stream()
                .forEach(e -> ar.add(new ApiExport(e)));
            regions.add(ar);
        }

        targetAPIRegions
            .stream()
            .forEach(r -> regions.add(new ApiRegion(r)));

        Extension apiRegions = new Extension(ExtensionType.JSON, "api-regions", ExtensionState.OPTIONAL);
        apiRegions.setJSONStructure(regions.toJSONArray());
        feature.getExtensions().add(apiRegions);
    }

    @Override
    public void serialize() throws Exception {
        RunmodeMapper runmodeMapper = RunmodeMapper.open(featureModelsOutputDirectory);

        serialize(targetFeature, null, runmodeMapper);

        if (!runModes.isEmpty()) {
            for (Entry<String, Feature> runmodeEntry : runModes.entrySet()) {
                String runmode = runmodeEntry.getKey();
                serialize(runmodeEntry.getValue(), runmode, runmodeMapper);
            }
        }

        runmodeMapper.save();
    }

    private void serialize(Feature feature, String runMode, RunmodeMapper runmodeMapper) throws Exception {
        addAPIRegions(feature, apiRegionExports.get(runMode));

        StringBuilder fileNameBuilder = new StringBuilder()
            .append((prefix != null) ? prefix : "")
            .append(feature.getId().getArtifactId());

        String classifier = feature.getId().getClassifier();
        if (classifier != null && !classifier.isEmpty()) {
            fileNameBuilder.append('-').append(classifier);
        }

        if (properties != null) {
            properties.put("filename", fileNameBuilder.toString());
        }

        fileNameBuilder.append(JSON_FILE_EXTENSION);

        String fileName = fileNameBuilder.toString();

        File targetFile = new File(featureModelsOutputDirectory, fileName);
        if (!targetFile.getParentFile().exists()) {
            targetFile.getParentFile().mkdirs();
        }

        if (artifactIdOverride != null && !artifactIdOverride.isEmpty()) {
            String interpolatedIdOverride = interpolator.interpolate(artifactIdOverride, properties);
            ArtifactId idOverrride = appendRunmode(ArtifactId.parse(interpolatedIdOverride), runMode);
            feature = feature.copy(idOverrride);
        }

        logger.info("Writing resulting Feature Model '{}' to file '{}'...", feature.getId(), targetFile);

        try (FileWriter targetWriter = new FileWriter(targetFile)) {
            FeatureJSONWriter.write(targetWriter, feature);

            logger.info("'{}' Feature File successfully written!", targetFile);

            runmodeMapper.addOrUpdate(runMode, fileName);
        }
    }

    public synchronized @NotNull DefaultFeaturesManager setAPIRegions(@NotNull List<String> regions) {
        targetAPIRegions.clear();
        targetAPIRegions.addAll(regions);
        return this;
    }

    public synchronized @NotNull DefaultFeaturesManager setExportToAPIRegion(@NotNull String region) {
        exportsToAPIRegion = region;
        return this;
    }

    @Override
    public void addOrAppendRepoInitExtension(@NotNull String text, @Nullable String runMode) {

        logger.info("Adding/Appending RepoInitExtension for runMode: {}", runMode );
        Extension repoInitExtension = getRunMode(runMode).getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);

        if (repoInitExtension == null) {
            repoInitExtension = new Extension(ExtensionType.TEXT, Extension.EXTENSION_NAME_REPOINIT, ExtensionState.REQUIRED);
            getRunMode(runMode).getExtensions().add(repoInitExtension);
            repoInitExtension.setText(text);
        } else {
            repoInitExtension.setText(repoInitExtension.getText() + "\n ".concat(text));
        }
    }
}
