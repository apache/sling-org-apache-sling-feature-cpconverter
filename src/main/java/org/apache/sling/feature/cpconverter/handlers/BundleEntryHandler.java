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
           @NotNull ContentPackage2FeatureModelConverter converter) throws IOException, ConverterException {
        logger.info("Processing bundle {}...", entry.getName());

        Matcher matcher = getPattern().matcher(path);
        String runMode = null;
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

        
        runMode = matcher.group("runmode");
        if (runMode != null) {
            // there is a specified RunMode
            logger.debug("Runmode {} was extracted from path {}", runMode, path);
        }

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
            processBundleInputStream(path, tmpBundleJar, bundleName, runMode, startLevel, converter);
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

            try (InputStream strippedBundleInput = new BundleSlingInitialContentExtractor(slingInitialContentPolicy, path, id, jarFile, converter, runMode).extract()) {
                if (strippedBundleInput != null && slingInitialContentPolicy == ContentPackage2FeatureModelConverter.SlingInitialContentPolicy.EXTRACT_AND_REMOVE) {
                    id = id.changeVersion(id.getVersion() + "-" + ContentPackage2FeatureModelConverter.PACKAGE_CLASSIFIER);
                    Objects.requireNonNull(converter.getArtifactsDeployer()).deploy(new InputStreamArtifactWriter(strippedBundleInput), id);
                } else {
                    try (InputStream originalBundleInput = Files.newInputStream(originalBundleFile)) {
                        Objects.requireNonNull(converter.getArtifactsDeployer()).deploy(new InputStreamArtifactWriter(originalBundleInput), id);
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

    protected @NotNull Artifact extractFeatureArtifact(@NotNull String bundleName, @NotNull JarFile jarFile) throws IOException {
        String artifactId = null;
        String version = null;
        String groupId = null;
        String classifier = null;

        for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
            JarEntry jarEntry = e.nextElement();
            String nextEntryName = jarEntry.getName();

            if (POM_PROPERTIES_PATTERN.matcher(nextEntryName).matches()) {
                logger.info("Reading '{}' bundle GAV from {}...", bundleName, nextEntryName);
                Properties properties = new Properties();
                try (InputStream input = jarFile.getInputStream(jarEntry)) {
                    properties.load(input);
                }
                groupId = properties.getProperty(NAME_GROUP_ID);
                artifactId = properties.getProperty(NAME_ARTIFACT_ID);
                version = properties.getProperty(PackageProperties.NAME_VERSION);

            } else if (POM_XML_PATTERN.matcher(nextEntryName).matches()) {
                logger.info("Reading '{}' bundle GAV from {}...", bundleName, nextEntryName);
                String path = nextEntryName.substring(0, nextEntryName.length() - "/pom.xml".length());
                groupId = path.substring("META-INF/maven/".length(), path.lastIndexOf('/'));
                artifactId = path.substring(path.lastIndexOf('/') + 1);
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
            }

            if (groupId != null && artifactId != null && version != null) {
                // bundleName is now the bare name without extension
                String synthesized = artifactId + "-" + version;

                // it was the pom.properties  we were looking for
                if (bundleName.startsWith(synthesized) || bundleName.equals(artifactId)) {

                    // check the artifact has a classifier in the bundle file name
                    if (synthesized.length() < bundleName.length()) {
                        String suffix = bundleName.substring(synthesized.length());
                        if (suffix.length() > 1 && suffix.startsWith("-")) {
                            classifier = suffix.substring(1);
                            logger.info("Inferred classifier of '{}:{}:{}' to be '{}'", groupId, artifactId, version, classifier);
                        }
                    }
                    // no need to iterate further
                    break;
                }
            }
        }


        if (groupId == null) {
            // maybe the included jar is just an OSGi bundle but not a valid Maven artifact
            groupId = getCheckedProperty(jarFile.getManifest(), Constants.BUNDLE_SYMBOLICNAME);
            // Make sure there are not spaces in the name to adhere to the Maven Group Id specification
            groupId = groupId.replace(' ', '_').replace(':', '_').replace('/', '_').replace('\\', '_');
            if (groupId.indexOf('.') != -1) {
                artifactId = groupId.substring(groupId.lastIndexOf('.') + 1);
                groupId = groupId.substring(0, groupId.lastIndexOf('.'));
            }
            if (artifactId == null || artifactId.isEmpty()) {
                artifactId = groupId;
            }
            Version osgiVersion = Version.parseVersion(getCheckedProperty(jarFile.getManifest(), Constants.BUNDLE_VERSION));
            version = osgiVersion.getMajor() + "." + osgiVersion.getMinor() + "." + osgiVersion.getMicro() + (osgiVersion.getQualifier().isEmpty() ? "" : "-" + osgiVersion.getQualifier());
        }

        // create artifact and store symbolic name and version in metadata
        final Artifact result = new Artifact(new ArtifactId(groupId, artifactId, version, classifier, JAR_TYPE));
        setMetadataFromManifest(jarFile.getManifest(), Constants.BUNDLE_VERSION, result);
        setMetadataFromManifest(jarFile.getManifest(), Constants.BUNDLE_SYMBOLICNAME, result);

        return result;
    }

    private static void setMetadataFromManifest(@NotNull Manifest manifest, @NotNull String name, @NotNull Artifact artifact) {
        String value = manifest.getMainAttributes().getValue(name);
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

}
