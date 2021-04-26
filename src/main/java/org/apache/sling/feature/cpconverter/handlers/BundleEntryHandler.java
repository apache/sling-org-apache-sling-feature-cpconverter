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

import org.apache.felix.utils.manifest.Clause;
import org.apache.felix.utils.manifest.Parser;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.artifacts.InputStreamArtifactWriter;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;
import static org.apache.jackrabbit.vault.packaging.PackageProperties.NAME_VERSION;
import static org.osgi.framework.Version.parseVersion;

public final class BundleEntryHandler extends AbstractRegexEntryHandler {

    private static final String NAME_GROUP_ID = "groupId";

    private static final String NAME_ARTIFACT_ID = "artifactId";

    private static final String NAME_CLASSIFIER = "classifier";

    private static final String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName";

    private static final String BUNDLE_VERSION = "Bundle-Version";

    private static final String JAR_TYPE = "jar";

    private final Pattern pomPropertiesPattern = Pattern.compile("META-INF/maven/[^/]+/[^/]+/pom.properties");

    private final Pattern pomXmlPropertiesPattern = Pattern.compile("META-INF/maven/[^/]+/[^/]+/pom.xml");

    private boolean enforceBundlesBelowInstallFolder;

    public BundleEntryHandler() {
        super("/jcr_root/(?:apps|libs)/.+/(?<foldername>install|config)(?:\\.(?<runmode>[^/]+))?/(?:(?<startlevel>[0-9]+)/)?.+\\.jar");
    }

    void setEnforceBundlesBelowInstallFolder(boolean enforceBundlesBelowInstallFolder) {
        this.enforceBundlesBelowInstallFolder = enforceBundlesBelowInstallFolder;
    }

    @Override
    public void handle(@NotNull String path, @NotNull Archive archive, @NotNull Entry entry, @NotNull ContentPackage2FeatureModelConverter converter) throws Exception {
        logger.info("Processing bundle {}...", entry.getName());

        String groupId;
        String artifactId = null;
        String version;
        String classifier = null;
        Manifest manifest;

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
            throw new IllegalStateException("OSGi bundles are only considered if placed below a folder called 'install', but the bundle at '"+ path + "' is placed outside!");
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

        try (JarInputStream jarInput = new JarInputStream(Objects.requireNonNull(archive.openInputStream(entry)))) {
            Properties properties = readGav(entry.getName(), jarInput);
            manifest = jarInput.getManifest();

            if (!properties.isEmpty()) {
                groupId = getCheckedProperty(properties, NAME_GROUP_ID);
                artifactId = getCheckedProperty(properties, NAME_ARTIFACT_ID);
                version = getCheckedProperty(properties, NAME_VERSION);
                classifier = properties.getProperty(NAME_CLASSIFIER);
            } else { // maybe the included jar is just an OSGi bundle but not a valid Maven artifact
                groupId = getCheckedProperty(manifest, BUNDLE_SYMBOLIC_NAME);
                // Make sure there are not spaces in the name to adhere to the Maven Group Id specification
                groupId = groupId.replace(' ', '_').replace(':', '_').replace('/', '_').replace('\\', '_');
                if (groupId.indexOf('.') != -1) {
                    artifactId = groupId.substring(groupId.lastIndexOf('.') + 1);
                    groupId = groupId.substring(0, groupId.lastIndexOf('.'));
                }
                if (artifactId == null || artifactId.isEmpty()) {
                    artifactId = groupId;
                }
                Version osgiVersion = Version.parseVersion(getCheckedProperty(manifest, BUNDLE_VERSION));
                version = osgiVersion.getMajor() + "." + osgiVersion.getMinor() + "." + osgiVersion.getMicro() + (osgiVersion.getQualifier().isEmpty() ? "" : "-" + osgiVersion.getQualifier());
            }
        }

        try (InputStream input = archive.openInputStream(entry)) {
            if (input != null) {
                ArtifactId id = new ArtifactId(groupId, artifactId, version, classifier, JAR_TYPE);

                Objects.requireNonNull(converter.getArtifactsDeployer()).deploy(new InputStreamArtifactWriter(input), id);

                Objects.requireNonNull(converter.getFeaturesManager()).addArtifact(runMode, id, startLevel);

                String epHeader = manifest.getMainAttributes().getValue(Constants.EXPORT_PACKAGE);
                if (epHeader != null) {
                    for (Clause clause : Parser.parseHeader(epHeader)) {
                        converter.getFeaturesManager().addAPIRegionExport(runMode, clause.getName());
                    }
                }
            }
        }
    }

    // method visibility set to 'protected' fot testing purposes
    protected @NotNull Properties readGav(@NotNull String entryName, @NotNull JarInputStream jarInput) throws IOException {
        Properties pomProperties = new Properties();
        Properties pomXmlProperties = new Properties();

        String bundleName = entryName;
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

        JarEntry jarEntry;
        while ((jarEntry = jarInput.getNextJarEntry()) != null) {
            String nextEntryName = jarEntry.getName();

            String artifactId = null;
            String version = null;
            String groupId = null;
            Properties properties = new Properties();
            boolean pomXml = false;
            if (pomPropertiesPattern.matcher(nextEntryName).matches()) {
                logger.info("Reading '{}' bundle GAV from {}...", bundleName, nextEntryName);
                properties = pomProperties;
                properties.load(jarInput);

                groupId = properties.getProperty(NAME_GROUP_ID);
                artifactId = properties.getProperty(NAME_ARTIFACT_ID);
                version = properties.getProperty(NAME_VERSION);

            } else if (pomXmlPropertiesPattern.matcher(nextEntryName).matches()) {
                pomXml = true;
                logger.info("Reading '{}' bundle GAV from {}...", bundleName, nextEntryName);
                properties = pomXmlProperties;
                String path = nextEntryName.substring(0, nextEntryName.length() - "/pom.xml".length());
                groupId = path.substring("META-INF/maven/".length(), path.lastIndexOf('/'));
                artifactId = path.substring(path.lastIndexOf('/') + 1);
                if (artifactId.indexOf('-') != -1) {
                    version = artifactId.substring(artifactId.indexOf('-'));
                    artifactId = artifactId.substring(0, artifactId.indexOf('-'));
                    properties.put(NAME_VERSION, version);
                } else if (bundleName.indexOf('-') != -1){
                    try {
                        String versionString = bundleName.substring(bundleName.indexOf('-') + 1);
                        if (!parseVersion(versionString).equals(Version.emptyVersion)) {
                            version = versionString;
                            properties.put(NAME_VERSION, version);
                        }
                    } catch (IllegalArgumentException ex) {
                        // Not a version
                    }
                }
                properties.put(NAME_GROUP_ID, groupId);
                properties.put(NAME_ARTIFACT_ID, artifactId);
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
                            String classifier = suffix.substring(1);
                            logger.info("Inferred classifier of '"
                                    + artifactId
                                    + ":"
                                    + version
                                    + "' to be '"
                                    + classifier
                                    + "'");
                            properties.setProperty(NAME_CLASSIFIER, classifier);
                        }
                    }
                    if (!pomXml) {
                        pomProperties = properties;
                        break;
                    } else {
                        pomXmlProperties = properties;
                    }
                }
            }
        }

        return pomProperties.isEmpty() ? pomXmlProperties : pomProperties;
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

    private static String getCheckedProperty(@NotNull Properties properties, @NotNull String name) {
        String property = properties.getProperty(name).trim();
        if (property != null) {
            property = property.trim();
        }
        return requireNonNull(property, "Jar file can not be defined as a valid Maven artifact without specifying a valid '"
                                         + name
                                         + "' property.");
    }

}
