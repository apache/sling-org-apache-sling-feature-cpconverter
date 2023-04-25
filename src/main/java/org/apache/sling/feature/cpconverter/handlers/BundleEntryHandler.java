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
package org.apache.sling.feature.cpconverter.handlers;

import static java.util.Objects.requireNonNull;
import static org.osgi.framework.Version.parseVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.utils.manifest.Clause;
import org.apache.felix.utils.manifest.Parser;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.SlingInitialContentPolicy;
import org.apache.sling.feature.cpconverter.artifacts.InputStreamArtifactWriter;
import org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.BundleSlingInitialContentExtractor;
import org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.BundleSlingInitialContentExtractContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

public class BundleEntryHandler extends AbstractRegexEntryHandler {

    private static final String NAME_GROUP_ID = "groupId";

    private static final String NAME_ARTIFACT_ID = "artifactId";

    private static final String JAR_TYPE = "jar";
    
    private static final Pattern POM_PROPERTIES_PATTERN = Pattern.compile("META-INF/maven/[^/]+/[^/]+/pom.properties");

    private static final Pattern POM_XML_PATTERN = Pattern.compile("META-INF/maven/[^/]+/[^/]+/pom.xml");

    private boolean enforceBundlesBelowInstallFolder;

    protected SlingInitialContentPolicy slingInitialContentPolicy;
    private BundleSlingInitialContentExtractor bundleSlingInitialContentExtractor = new BundleSlingInitialContentExtractor();

    public BundleEntryHandler() {
        super("/jcr_root/(?:apps|libs)/.+/(?<foldername>install|config)(?:\\.(?<runmode>[^/]+))?/(?:(?<startlevel>[0-9]+)/)?.+\\.jar");
    }

    void setEnforceBundlesBelowInstallFolder(boolean enforceBundlesBelowInstallFolder) {
        this.enforceBundlesBelowInstallFolder = enforceBundlesBelowInstallFolder;
    }

    public void setSlingInitialContentPolicy(@NotNull SlingInitialContentPolicy slingInitialContentPolicy) {
        this.slingInitialContentPolicy = slingInitialContentPolicy;
    }

    @Override
    public void handle(@NotNull String path,
           @NotNull Archive archive, 
           @NotNull Entry entry, 
           @NotNull ContentPackage2FeatureModelConverter converter,
           String runMode) throws IOException, ConverterException {
        logger.info("Processing bundle {}...", entry.getName());

        Matcher matcher = getPattern().matcher(path);
        
        Integer startLevel = null;
        // we are pretty sure it matches, here
        if (!matcher.matches()) {
            throw new IllegalStateException("Something went terribly wrong: pattern '"
                                            + getPattern().pattern()
                                            + "' should have matched already with path '"
                                            + path
                                            + "' but it does not, currently");
        }

        if (enforceBundlesBelowInstallFolder && !"install".equals(matcher.group("foldername"))) {
            throw new ConverterException("OSGi bundles are only considered if placed below a folder called 'install', but the bundle at '"+ path + "' is placed outside!");
        }

        // determine run mode string for current path
        String runModeMatch = matcher.group("runmode");

        String targetRunmode = extractTargetRunmode(path, converter, runMode,
            runModeMatch);

        final String value = matcher.group("startlevel");
        if (value != null) {
            // there is a specified Start Level
            startLevel = Integer.parseInt(value); // NumberFormatException impossible due to RegEx
            logger.debug("Start level {} was extracted from path {}", startLevel, path);
        }

        String bundleName = entry.getName();
        // Remove the leading path
        int idx = bundleName.lastIndexOf('/');
        if (idx >= 0) {
            bundleName = bundleName.substring(idx + 1);
        }
        // Remove the extension
        int edx = bundleName.lastIndexOf('.');
        if (edx > 0) {
            bundleName = bundleName.substring(0, edx);
        }
        
        // create a temporary JAR file (extracted from archive)
        Path tmpBundleJar = Files.createTempFile(converter.getTempDirectory().toPath(), "extracted", bundleName + ".jar");
        try {
            try (OutputStream output = Files.newOutputStream(tmpBundleJar);
                InputStream input = Objects.requireNonNull(archive.openInputStream(entry))) {
                IOUtils.copy(input, output);
            }
            processBundleInputStream(path, tmpBundleJar, bundleName, targetRunmode, startLevel, converter);
        } finally {
            Files.delete(tmpBundleJar);
        }
    }

    void processBundleInputStream(@NotNull String path, @NotNull Path originalBundleFile, @NotNull String bundleName, @Nullable String runMode, @Nullable Integer startLevel, @NotNull ContentPackage2FeatureModelConverter converter)
            throws ConverterException, IOException {
        try (JarFile jarFile = new JarFile(originalBundleFile.toFile())) {
            // first extract bundle metadata from JAR input stream
            Artifact artifact = extractFeatureArtifact(bundleName, jarFile);
            ArtifactId id = artifact.getId();

            BundleSlingInitialContentExtractContext context = new BundleSlingInitialContentExtractContext(slingInitialContentPolicy, path, id, jarFile, converter, runMode);
            try (InputStream strippedBundleInput = bundleSlingInitialContentExtractor.extract(context)) {
                if (strippedBundleInput != null && slingInitialContentPolicy == ContentPackage2FeatureModelConverter.SlingInitialContentPolicy.EXTRACT_AND_REMOVE) {
                    id = id.changeVersion(id.getVersion() + "-" + ContentPackage2FeatureModelConverter.PACKAGE_CLASSIFIER);
                    Objects.requireNonNull(converter.getArtifactsDeployer()).deploy(new InputStreamArtifactWriter(strippedBundleInput), runMode, id);
                } else {
                    try (InputStream originalBundleInput = Files.newInputStream(originalBundleFile)) {
                        Objects.requireNonNull(converter.getArtifactsDeployer()).deploy(new InputStreamArtifactWriter(originalBundleInput), runMode, id);
                    }
                }
            }
            artifact = artifact.copy(id);
            Objects.requireNonNull(converter.getFeaturesManager()).addArtifact(runMode, artifact, startLevel);
            String exportHeader = Objects.requireNonNull(jarFile.getManifest()).getMainAttributes().getValue(Constants.EXPORT_PACKAGE);
            if (exportHeader != null) {
                for (Clause clause : Parser.parseHeader(exportHeader)) {
                    converter.getFeaturesManager().addAPIRegionExport(runMode, clause.getName());
                }
            }
        }
    }

    private @Nullable ArtifactId extractArtifactIdFromPomProperties(@NotNull String bundleName, @NotNull final JarFile jarFile, @NotNull final JarEntry jarEntry) throws IOException {
        logger.info("Reading '{}' bundle GAV from {}...", bundleName, jarEntry.getName());
        final Properties properties = new Properties();
        try (final InputStream input = jarFile.getInputStream(jarEntry)) {
            properties.load(input);
        }
        final String groupId = properties.getProperty(NAME_GROUP_ID);
        final String artifactId = properties.getProperty(NAME_ARTIFACT_ID);
        final String version = properties.getProperty(PackageProperties.NAME_VERSION);
        if ( groupId != null && artifactId != null && version != null ) {
            return new ArtifactId(groupId, artifactId, version, null, null);
        }
        return null;
    }

    private @Nullable ArtifactId extractArtifactIdFromPom(@NotNull String bundleName, @NotNull final JarFile jarFile, @NotNull final JarEntry jarEntry) {
        logger.info("Reading '{}' bundle GAV from {}...", bundleName, jarEntry.getName());
        String path = jarEntry.getName().substring(0, jarEntry.getName().length() - "/pom.xml".length());
        final String groupId = path.substring("META-INF/maven/".length(), path.lastIndexOf('/'));
        String artifactId = path.substring(path.lastIndexOf('/') + 1);
        String version = null;
        if (artifactId.indexOf('-') != -1) {
            version = artifactId.substring(artifactId.indexOf('-'));
            artifactId = artifactId.substring(0, artifactId.indexOf('-'));
        } else if (bundleName.indexOf('-') != -1){
            try {
                String versionString = bundleName.substring(bundleName.indexOf('-') + 1);
                if (!parseVersion(versionString).equals(Version.emptyVersion)) {
                    version = versionString;
                }
            } catch (IllegalArgumentException ex) {
                // Not a version
            }
        }
        if ( groupId != null && artifactId != null && version != null ) {
            return new ArtifactId(groupId, artifactId, version, null, null);
        }
        return null;
    }

    private @NotNull ArtifactId extractArtifactIdFromSymbolicName(@NotNull final JarFile jarFile) throws IOException {
        // maybe the included jar is just an OSGi bundle but not a valid Maven artifact
        String groupId = StringUtils.substringBefore(getCheckedProperty(jarFile.getManifest(), Constants.BUNDLE_SYMBOLICNAME), ";");
        String artifactId = null;

        // Make sure there are not spaces in the name to adhere to the Maven Group Id specification
        groupId = groupId.replace(' ', '_').replace(':', '_').replace('/', '_').replace('\\', '_');
        if (groupId.indexOf('.') != -1) {
            artifactId = groupId.substring(groupId.lastIndexOf('.') + 1);
            groupId = groupId.substring(0, groupId.lastIndexOf('.'));
        }
        if (artifactId == null || artifactId.isEmpty()) {
            artifactId = groupId;
        }
        final Version osgiVersion = Version.parseVersion(getCheckedProperty(jarFile.getManifest(), Constants.BUNDLE_VERSION));
        final String version = osgiVersion.getMajor() + "." + osgiVersion.getMinor() + "." + osgiVersion.getMicro() + (osgiVersion.getQualifier().isEmpty() ? "" : "-" + osgiVersion.getQualifier());
        
        return new ArtifactId(groupId, artifactId, version, null, null);
    }

    protected @NotNull Artifact extractFeatureArtifact(@NotNull String bundleName, @NotNull JarFile jarFile) throws IOException {
        ArtifactId resultId = null;
        for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
            final JarEntry jarEntry = e.nextElement();

            if (POM_PROPERTIES_PATTERN.matcher(jarEntry.getName()).matches()) {
                resultId = extractArtifactIdFromPomProperties(bundleName, jarFile, jarEntry);

            } else if (POM_XML_PATTERN.matcher(jarEntry.getName()).matches()) {
                resultId = extractArtifactIdFromPom(bundleName, jarFile, jarEntry);

            }

            if (resultId != null) {
                // bundleName is now the bare name without extension
                final String synthesized = resultId.getArtifactId().concat("-").concat(resultId.getVersion());

                // it was the pom.properties  we were looking for
                if (bundleName.startsWith(synthesized) || bundleName.equals(resultId.getArtifactId())) {

                    // check the artifact has a classifier in the bundle file name
                    if (synthesized.length() < bundleName.length()) {
                        String suffix = bundleName.substring(synthesized.length());
                        if (suffix.length() > 1 && suffix.startsWith("-")) {
                            resultId = resultId.changeClassifier(suffix.substring(1));
                            logger.info("Inferred classifier of '{}'", resultId.toMvnId());
                        }
                    }
                    // no need to iterate further
                    break;
                }
            }
            // resultId should be reset here, however, this will make a lot of tests fail
            // resultId = null;
        }

        if (resultId == null) {
            resultId = extractArtifactIdFromSymbolicName(jarFile);
        }

        // create artifact and store symbolic name and version in metadata
        final Artifact result = new Artifact(resultId.changeType(JAR_TYPE));
        setMetadataFromManifest(jarFile.getManifest(), Constants.BUNDLE_VERSION, result, false);
        setMetadataFromManifest(jarFile.getManifest(), Constants.BUNDLE_SYMBOLICNAME, result, true);

        return result;
    }

    private static void setMetadataFromManifest(@NotNull Manifest manifest, @NotNull String name, @NotNull Artifact artifact, boolean strip) {
        String value = manifest.getMainAttributes().getValue(name);
        if (strip) {
            value = StringUtils.substringBefore(value, ";");
        }
        if (value != null) {
            artifact.getMetadata().put(name, value);
        }
    }

    private static @NotNull String getCheckedProperty(@NotNull Manifest manifest, @NotNull String name) {
        String property = manifest.getMainAttributes().getValue(name);
        if (property != null) {
            property = property.trim();
        }
        return requireNonNull(property, "Jar file can not be defined as a valid OSGi bundle without specifying a valid '"
                + name
                + "' property.");
    }

    public void setBundleSlingInitialContentExtractor(BundleSlingInitialContentExtractor bundleSlingInitialContentExtractor) {
        this.bundleSlingInitialContentExtractor = bundleSlingInitialContentExtractor;
    }
}
