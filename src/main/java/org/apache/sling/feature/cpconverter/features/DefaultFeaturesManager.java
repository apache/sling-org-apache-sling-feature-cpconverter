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
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Artifacts;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Extensions;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.interpolator.SimpleVariablesInterpolator;
import org.apache.sling.feature.cpconverter.interpolator.VariablesInterpolator;
import org.apache.sling.feature.io.json.FeatureJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultFeaturesManager implements FeaturesManager {

    private static final String JAVA_IO_TMPDIR_PROPERTY = "java.io.tmpdir";

    private static final String CONTENT_PACKAGES = "content-packages";

    private static final String SLING_OSGI_FEATURE_TILE_TYPE = "slingosgifeature";

    private static final String JSON_FILE_EXTENSION = ".json";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, Feature> runModes = new HashMap<>();

    private final VariablesInterpolator interpolator = new SimpleVariablesInterpolator();

    private final boolean mergeConfigurations;

    private final int bundlesStartOrder;

    private final File featureModelsOutputDirectory;

    private final String artifactIdOverride;

    private final Map<String, String> properties;

    private Feature targetFeature = null;

    public DefaultFeaturesManager() {
        this(true, 20, new File(System.getProperty(JAVA_IO_TMPDIR_PROPERTY)), null, null);
    }

    public DefaultFeaturesManager(boolean mergeConfigurations,
                                  int bundlesStartOrder,
                                  File featureModelsOutputDirectory,
                                  String artifactIdOverride,
                                  Map<String, String> properties) {
        this.mergeConfigurations = mergeConfigurations;
        this.bundlesStartOrder = bundlesStartOrder;
        this.featureModelsOutputDirectory = featureModelsOutputDirectory;
        this.artifactIdOverride = artifactIdOverride;
        this.properties = properties;
    }

    public void init(String groupId,
                     String artifactId,
                     String version,
                     String description) {
        targetFeature = new Feature(new ArtifactId(groupId, artifactId, version, null, SLING_OSGI_FEATURE_TILE_TYPE));
        targetFeature.setDescription(description);
        runModes.clear();
    }

    public Feature getTargetFeature() {
        return targetFeature;
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

    public void addArtifact(String runMode,
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

    public void serialize() throws Exception {
        RunmodeMapper runmodeMapper = RunmodeMapper.open(featureModelsOutputDirectory);

        seralize(targetFeature, null, runmodeMapper);

        if (!runModes.isEmpty()) {
            for (Entry<String, Feature> runmodeEntry : runModes.entrySet()) {
                String runmode = runmodeEntry.getKey();
                seralize(runmodeEntry.getValue(), runmode, runmodeMapper);
            }
        }

        runmodeMapper.save();
    }

    private void seralize(Feature feature, String runMode, RunmodeMapper runmodeMapper) throws Exception {
        StringBuilder fileNameBuilder = new StringBuilder().append(feature.getId().getArtifactId());

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

        logger.info("Writing resulting Feature Model '{}' to file '{}'...", feature.getId(), targetFile);

        if (artifactIdOverride != null && !artifactIdOverride.isEmpty()) {
            String interpolatedIdOverride = interpolator.interpolate(artifactIdOverride, properties);
            ArtifactId idOverrride = appendRunmode(ArtifactId.parse(interpolatedIdOverride), runMode);
            feature = feature.copy(idOverrride);
        }

        try (FileWriter targetWriter = new FileWriter(targetFile)) {
            FeatureJSONWriter.write(targetWriter, feature);

            logger.info("'{}' Feature File successfully written!", targetFile);

            runmodeMapper.addOrUpdate(runMode, fileName);
        }
    }

}
