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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Stack;

import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Artifacts;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionState;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Extensions;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.Mapping;
import org.apache.sling.feature.cpconverter.interpolator.SimpleVariablesInterpolator;
import org.apache.sling.feature.cpconverter.interpolator.VariablesInterpolator;
import org.apache.sling.feature.cpconverter.repoinit.NoOpVisitor;
import org.apache.sling.feature.cpconverter.vltpkg.PackagesEventsEmitter;
import org.apache.sling.feature.extension.apiregions.api.ApiExport;
import org.apache.sling.feature.extension.apiregions.api.ApiRegion;
import org.apache.sling.feature.extension.apiregions.api.ApiRegions;
import org.apache.sling.feature.io.json.FeatureJSONWriter;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.repoinit.parser.impl.RepoInitParserService;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.apache.sling.repoinit.parser.operations.RegisterNamespace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Constants;
import org.osgi.util.converter.Converters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultFeaturesManager implements FeaturesManager, PackagesEventsEmitter {

    public enum ConfigurationHandling {
        ORDERED,
        MERGE,
        STRICT
    }

    private static final String CONTENT_PACKAGES = "content-packages";

    private static final String SLING_OSGI_FEATURE_TILE_TYPE = "slingosgifeature";

    private static final String JSON_FILE_EXTENSION = ".json";

    private static final String BUNDLE_ORIGINS = "content-package-origins";
    private static final String CONFIGURATION_ORIGINS = Configuration.CONFIGURATOR_PREFIX.concat(BUNDLE_ORIGINS);

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

    private final Map<String, String> namespaceUriByPrefix;

    private String exportsToAPIRegion;

    private Feature targetFeature;

    private AclManager aclManager;

    private final Map<String, String> pidToPathMapping = new HashMap<>();

    private final Stack<String> packageIds = new Stack<>();

    DefaultFeaturesManager() {
        this(new File(""));
    }

    public DefaultFeaturesManager(@NotNull File tempDir) {
        this(true, 20, tempDir, null, null, new HashMap<>(), null);
    }

    public DefaultFeaturesManager(boolean mergeConfigurations,
                                  int bundlesStartOrder,
                                  @NotNull File featureModelsOutputDirectory,
                                  @Nullable String artifactIdOverride,
                                  @Nullable String prefix,
                                  @NotNull Map<String, String> properties,
                                  @Nullable AclManager aclManager) {
        this(mergeConfigurations ? ConfigurationHandling.MERGE : ConfigurationHandling.ORDERED, bundlesStartOrder, featureModelsOutputDirectory,
                artifactIdOverride, prefix, properties, aclManager);
    }

    public DefaultFeaturesManager(@NotNull ConfigurationHandling configurationHandling,
                                  int bundlesStartOrder,
                                  @NotNull File featureModelsOutputDirectory,
                                  @Nullable String artifactIdOverride,
                                  @Nullable String prefix,
                                  @NotNull Map<String, String> properties,
                                  @Nullable AclManager aclManager) {
        this.configurationHandling = configurationHandling;
        this.bundlesStartOrder = bundlesStartOrder;
        this.featureModelsOutputDirectory = featureModelsOutputDirectory;
        this.artifactIdOverride = artifactIdOverride;
        this.prefix = prefix;
        this.properties = properties;
        this.aclManager = aclManager;
        this.namespaceUriByPrefix = new HashMap<>();
    }

    @Override
    public void init(@NotNull ArtifactId packageId) {
        targetFeature = new Feature(packageId.changeClassifier(null).changeType(SLING_OSGI_FEATURE_TILE_TYPE));

        runModes.clear();
        apiRegionExports.clear();
    }

    @Override
    public @Nullable Feature getTargetFeature() {
        return targetFeature;
    }

    @Override
    public @NotNull Feature getRunMode(@Nullable String runMode) {
        if (getTargetFeature() == null) {
            throw new IllegalStateException("Target feature not initialized yet, please make sure convert() method was invoked first.");
        }

        if (runMode == null) {
            return getTargetFeature();
        }

        ArtifactId newId = appendRunmode(getTargetFeature().getId(), runMode);

        return runModes.computeIfAbsent(runMode, k -> new Feature(newId));
    }

    @Override
    public void addArtifact(@Nullable String runMode, @NotNull ArtifactId id) {
        addArtifact(runMode, id, null);
    }

    @Override
    public void addArtifact(@Nullable String runMode, @NotNull ArtifactId id, @Nullable Integer startOrder) {
        requireNonNull(id, "Artifact can not be attached to a feature without specifying a valid ArtifactId.");

        Artifact artifact = new Artifact(id);

        Feature feature = getRunMode(runMode);
        Artifacts artifacts;

        if (ZIP_TYPE.equals(id.getType())) {
            Extensions extensions = feature.getExtensions();
            Extension extension = extensions.getByName(CONTENT_PACKAGES);

            if (extension == null) {
                extension = new Extension(ExtensionType.ARTIFACTS, CONTENT_PACKAGES, ExtensionState.REQUIRED);
                extensions.add(extension);
            }

            artifacts = extension.getArtifacts();
        } else {
            // set start order
            final int startOrderForBundle = startOrder != null ? startOrder : bundlesStartOrder;
            artifact.setStartOrder(startOrderForBundle);
            // set origins
            if (!this.packageIds.isEmpty()) {
                artifact.getMetadata().put(BUNDLE_ORIGINS, String.join("|", this.packageIds));
            }

            artifacts = feature.getBundles();
        }

        artifacts.add(artifact);
    }

    private static @NotNull ArtifactId appendRunmode(@NotNull ArtifactId id, @Nullable String runMode) {
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
        if (exportsToAPIRegion == null) {
            return; // Ignore if we're not exporting to an API region
        }

        getRunMode(runMode); // Trigger runmode initialization

        List<String> l = apiRegionExports.computeIfAbsent(runMode, r -> new ArrayList<>());
        l.add(exportedPackage);
    }


    private static final String REPOINIT_FACTORY_PID = "org.apache.sling.jcr.repoinit.RepositoryInitializer";

    private static final String REPOINIT_PID = "org.apache.sling.jcr.repoinit.impl.RepositoryInitializer";

    private static final String SERVICE_USER_MAPPING_PID = "org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl";

    private boolean enforceServiceMappingByPrincipal;


    public void setEnforceServiceMappingByPrincipal(boolean enforceServiceMappingByPrincipal) {
        this.enforceServiceMappingByPrincipal = enforceServiceMappingByPrincipal;
    }

    public void addSeed(@NotNull Feature seed) throws IOException, ConverterException {
        for (Configuration conf : seed.getConfigurations()) {
            handleRepoinitAndMappings("seed", conf, conf.getConfigurationProperties(), false);
        }
        if (seed.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT) != null) {
            String repoInitText = seed.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT).getText();
            getAclManager().addRepoinitExtention("seed", repoInitText, "seed", this);
            extractNamespaces(repoInitText, namespaceUriByPrefix);
        }

    }

    private static void extractNamespaces(String repoInitText, Map<String, String> namespaceUriByPrefix) {
        try {
            List<Operation> ops = new RepoInitParserService().parse(new StringReader(repoInitText));
            for (Operation op : ops) {
                op.accept(new NoOpVisitor() {
                    @Override
                    public void visitRegisterNamespace(RegisterNamespace registerNamespace) {
                        namespaceUriByPrefix.put(registerNamespace.getPrefix(), registerNamespace.getURI());
                    }
                });
            }
        } catch (RepoInitParsingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public @NotNull Map<String, String> getNamespaceUriByPrefix() {
        return namespaceUriByPrefix;
    }

    @NotNull AclManager getAclManager() {
        return Objects.requireNonNull(this.aclManager);
    }

    public void setAclManager(AclManager aclManager) {
        this.aclManager = aclManager;
    }

    private boolean handleRepoinitAndMappings(String runMode, Configuration cfg, Dictionary<String, Object> configurationProperties, boolean enforceServiceMappingByPrincipal)
            throws IOException, ConverterException {
        if (REPOINIT_FACTORY_PID.equals(cfg.getFactoryPid())) {
            final String[] scripts = Converters.standardConverter().convert(configurationProperties.get("scripts")).to(String[].class);
            if (scripts != null && scripts.length > 0) {
                getAclManager().addRepoinitExtention(cfg.getPid(), String.join(System.lineSeparator(), scripts), runMode, this);
            }
            checkReferences(configurationProperties, cfg.getPid());
            return true;
        } else if (REPOINIT_PID.equals(cfg.getPid())) {
            checkReferences(configurationProperties, cfg.getPid());
            return true;
        } else if (cfg.getPid().startsWith(SERVICE_USER_MAPPING_PID)) {
            String[] mappings = Converters.standardConverter().convert(configurationProperties.get("user.mapping")).to(String[].class);
            List<String> newMappings = convertMappings(mappings, cfg.getPid(), enforceServiceMappingByPrincipal);
            // replace 'user.mapping' property by the new mappings, which may have been refactored
            if (!newMappings.isEmpty()) {
                configurationProperties.put("user.mapping", newMappings.toArray(new String[0]));
            }
        }
        return false;
    }
    
    private List<String> convertMappings(@Nullable String[] mappings, @NotNull String pid, boolean enforceServiceMappingByPrincipal) throws ConverterException {
        if (mappings == null) {
            return Collections.emptyList();
        }
        List<String> newMappings = new ArrayList<>();
        for (String usermapping : mappings) {
            if (usermapping == null || usermapping.trim().isEmpty()) {
                logger.warn("ServiceUserMapping: Ignoring empty mapping in {}", pid);
                // invalid empty mapping => ignore
                continue;
            }
            try {
                Mapping mapping = new Mapping(usermapping, enforceServiceMappingByPrincipal);
                getAclManager().addMapping(mapping);
                newMappings.add(mapping.asString());
            } catch (IllegalArgumentException iae) {
                throw new ConverterException("ServiceUserMapping: Detected invalid mapping in " + pid);
            }
        }
        return newMappings;
    }

    @Override
    public void addConfiguration(@Nullable String runMode,
                                 @NotNull Configuration cfg,
                                 @NotNull String path,
                                 @NotNull Dictionary<String, Object> configurationProperties)
            throws IOException, ConverterException {
        if (handleRepoinitAndMappings(runMode, cfg, configurationProperties, enforceServiceMappingByPrincipal)) {
            return;
        }

        Feature feature = getRunMode(runMode);
        Configuration configuration = feature.getConfigurations().getConfiguration(cfg.getPid());

        if (configuration == null) {
            configuration = new Configuration(cfg.getPid());
            feature.getConfigurations().add(configuration);
            this.pidToPathMapping.put(cfg.getPid(), path);
        } else {
            switch (this.configurationHandling) {
                case STRICT:
                    throw new ConverterException("Configuration '"
                            + cfg.getPid()
                            + "' already defined in Feature Model '"
                            + feature.getId().toMvnId()
                            + "', set the 'mergeConfigurations' flag to 'true' if you want to merge multiple configurations with same PID");
                case ORDERED:
                    final String oldPath = this.pidToPathMapping.get(cfg.getPid());
                    if (oldPath == null || oldPath.compareTo(path) > 0) {
                        this.pidToPathMapping.put(cfg.getPid(), path);
                        feature.getConfigurations().remove(configuration);
                        configuration = new Configuration(cfg.getPid());
                        feature.getConfigurations().add(configuration);
                    } else {
                        return;
                    }
                    break;
                case MERGE: // nothing to do
            }
        }

        adjustConfigurationProperties(configuration, configurationProperties);
    }
    
    private void adjustConfigurationProperties(@NotNull Configuration configuration, 
                                               @NotNull Dictionary<String, Object> configurationProperties) {
        Enumeration<String> keys = configurationProperties.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            Object value = configurationProperties.get(key);

            if (value instanceof Collection) {
                value = ((Collection<?>) value).toArray();
            }

            configuration.getProperties().put(key, value);
        }
        // remove internal properties (these are ignored anyway)
        configuration.getProperties().remove(Constants.SERVICE_PID);
        configuration.getProperties().remove("service.bundleLocation");
        configuration.getProperties().remove("service.factoryPid");

        // set origins
        if (!this.packageIds.isEmpty()) {
            final List<String> origins = new ArrayList<>();
            final Object val = configuration.getProperties().get(CONFIGURATION_ORIGINS);
            if (val != null) {
                origins.addAll(Arrays.asList(val.toString().split(",")));
            }
            origins.add(String.join("|", this.packageIds));
            configuration.getProperties().put(CONFIGURATION_ORIGINS, String.join(",", origins));
        }
    }

    private void addAPIRegions(@NotNull Feature feature, @Nullable List<String> exportedPackages) throws IOException {
        if (exportedPackages == null)
            exportedPackages = Collections.emptyList();

        if (exportedPackages.isEmpty() && targetAPIRegions.isEmpty())
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
    public void serialize() throws IOException {
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

    private void serialize(Feature feature, String runMode, RunmodeMapper runmodeMapper) throws IOException {
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
    public void addOrAppendRepoInitExtension(@NotNull String source, @NotNull String text, @Nullable String runMode) {
        if (runMode == null) {
            logger.info("Adding global repo-init");
        } else {
            logger.info("Adding repo-init for run mode: {}", runMode);
        }

        // prepend source/origin
        text = "# origin=".concat(String.join("|", this.packageIds)).concat(" source=").concat(source).concat(System.lineSeparator()).concat(text);

        Extension repoInitExtension = getRunMode(runMode).getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        if (repoInitExtension == null) {
            repoInitExtension = new Extension(ExtensionType.TEXT, Extension.EXTENSION_NAME_REPOINIT, ExtensionState.REQUIRED);
            getRunMode(runMode).getExtensions().add(repoInitExtension);
            repoInitExtension.setText(text);
        } else {
            repoInitExtension.setText(repoInitExtension.getText().concat(System.lineSeparator()).concat(text));
        }
    }
    
    private static void checkReferences(@NotNull final Dictionary<String, Object> configurationProperties, @NotNull final String pid) throws ConverterException {
        final String[] references = Converters.standardConverter().convert(configurationProperties.get("references")).to(String[].class);
        if (references != null && references.length > 0) {
            for (final String r : references) {
                if (r != null && !r.trim().isEmpty()) {
                    throw new ConverterException("References are not supported for repoinit (configuration " + pid + ")");
                }
            }
        }
    }

    @Override
    public void start() {
        // nothing to do
    }

    @Override
    public void end() {
        // nothing to do
    }

    @Override
    public void startPackage(@NotNull VaultPackage originalPackage) {
        packageIds.push(originalPackage.getId().toString());
    }

    @Override
    public void endPackage(@NotNull PackageId originalPackageId, @NotNull VaultPackage convertedPackage) {
        packageIds.pop();
    }

    @Override
    public void startSubPackage(@NotNull String path, @NotNull VaultPackage originalPackage) {
        packageIds.push(originalPackage.getId().toString());
    }

    @Override
    public void endSubPackage(@NotNull String path, @NotNull PackageId originalPackageId, @NotNull VaultPackage convertedPackage) {
        packageIds.pop();
    }
}
