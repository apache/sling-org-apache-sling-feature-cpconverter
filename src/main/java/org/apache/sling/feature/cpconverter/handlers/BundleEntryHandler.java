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
import static org.apache.jackrabbit.vault.packaging.PackageProperties.NAME_VERSION;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.artifacts.InputStreamArtifactWriter;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BundleEntryHandler extends AbstractRegexEntryHandler {

    private static final String NAME_GROUP_ID = "groupId";

    private static final String NAME_ARTIFACT_ID = "artifactId";

    private static final String NAME_CLASSIFIER = "classifier";

    private static final String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName";

    private static final String BUNDLE_NAME = "Bundle-Name";

    private static final String BUNDLE_VERSION = "Bundle-Version";

    private static final String JAR_TYPE = "jar";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Pattern pomPropertiesPattern = Pattern.compile("META-INF/maven/[^/]+/[^/]+/pom.properties");

    public BundleEntryHandler() {
        super("/jcr_root/(?:apps|libs)/.+/install(?:\\.([^/]+))?/(?:([0-9]+)/)?.+\\.jar");
    }

    @Override
    public void handle(String path, Archive archive, Entry entry, ContentPackage2FeatureModelConverter converter) throws Exception {
        logger.info("Processing bundle {}...", entry.getName());

        String groupId;
        String artifactId;
        String version;
        String classifier = null;

        try (JarInputStream jarInput = new JarInputStream(archive.openInputStream(entry))) {
            Properties properties = readGav(entry.getName(), jarInput);
            Manifest manifest = jarInput.getManifest();

            if (!properties.isEmpty()) {
                groupId = getCheckedProperty(properties, NAME_GROUP_ID);
                artifactId = getCheckedProperty(properties, NAME_ARTIFACT_ID);
                version = getCheckedProperty(properties, NAME_VERSION);
                classifier = properties.getProperty(NAME_CLASSIFIER);
            } else { // maybe the included jar is just an OSGi bundle but not a valid Maven artifact
                groupId = getCheckedProperty(manifest, BUNDLE_SYMBOLIC_NAME);
                // Make sure there are not spaces in the name to adhere to the Maven Group Id specification
                groupId = groupId.replaceAll(" ", "_");
                artifactId = getCheckedProperty(manifest, BUNDLE_NAME);
                // Make sure there are not spaces in the name to adhere to the Maven Artifact Id specification
                artifactId = artifactId.replaceAll(" ", "_");
                version = getCheckedProperty(manifest, BUNDLE_VERSION);
            }
        }

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

        if (StringUtils.isNotBlank(matcher.group(1))) {
            // there is a specified RunMode
            runMode = matcher.group(1);
            logger.debug("Runmode {} was extracted from path {}", runMode, path);
        }

        if (StringUtils.isNotBlank(matcher.group(2))) {
            // there is a specified Start Level
            startLevel = Integer.parseInt(matcher.group(2)); // NumberFormatException impossible due to RegEx
            logger.debug("Start level {} was extracted from path {}", startLevel, path);
        }

        try (InputStream input = archive.openInputStream(entry)) {
            ArtifactId id = new ArtifactId(groupId, artifactId, version, classifier, JAR_TYPE);

            converter.getArtifactsDeployer().deploy(new InputStreamArtifactWriter(input), id);

            converter.getFeaturesManager().addArtifact(runMode, id, startLevel);
        }
    }

    // method visibility set to 'protected' fot testing purposes
    protected Properties readGav(String entryName, JarInputStream jarInput) throws IOException {
        Properties properties = new Properties();

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
        dance : while ((jarEntry = jarInput.getNextJarEntry()) != null) {
            String nextEntryName = jarEntry.getName();

            if (pomPropertiesPattern.matcher(nextEntryName).matches()) {
                logger.info("Reading '{}' bundle GAV from {}...", bundleName, nextEntryName);

                properties.load(jarInput);

                String artifactId = properties.getProperty(NAME_ARTIFACT_ID);
                String version = properties.getProperty(NAME_VERSION);

                if (artifactId == null || version == null) {
                    continue;
                }

                // bundleName is now the bare name without extension
                String synthesized = artifactId + "-" + version;

                // it was the pom.properties  we were looking for
                if (bundleName.startsWith(synthesized)) {

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

                    break dance;
                }
            }
        }

        return properties;
    }

    private static String getCheckedProperty(Manifest manifest, String name) {
        String property = manifest.getMainAttributes().getValue(name).trim();
        return requireNonNull(property, "Jar file can not be defined as a valid OSGi bundle without specifying a valid '"
                                         + name
                                         + "' property.");
    }

    private static String getCheckedProperty(Properties properties, String name) {
        String property = properties.getProperty(name).trim();
        return requireNonNull(property, "Jar file can not be defined as a valid Maven artifact without specifying a valid '"
                                         + name
                                         + "' property.");
    }

}
