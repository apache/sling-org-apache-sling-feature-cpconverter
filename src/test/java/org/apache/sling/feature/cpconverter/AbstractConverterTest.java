/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.feature.cpconverter;

import org.apache.jackrabbit.vault.util.Constants;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Artifacts;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.io.json.FeatureJSONReader;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public abstract class AbstractConverterTest {

    static void deleteDirTree(File dir) throws IOException {
        Path tempDir = dir.toPath();

        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    static Feature getFeature(File outputDirectory, String name) throws Exception {
        File featureFile = new File(outputDirectory, name);
        assertTrue(featureFile + " was not correctly created", featureFile.exists());

        try (Reader reader = new FileReader(featureFile)) {
            return FeatureJSONReader.read(reader, featureFile.getAbsolutePath());
        }
    }

    static void verifyFeatureFile(File outputDirectory,
                                  String name,
                                  String expectedArtifactId,
                                  List<String> expectedArtifacts,
                                  List<String> expectedConfigurations,
                                  List<String> expectedContentPackagesExtensions) throws Exception {
        Feature feature = getFeature(outputDirectory, name);

        assertEquals(expectedArtifactId, feature.getId().toMvnId());

        for (String expectedArtifact : expectedArtifacts) {
            assertTrue(expectedArtifact + " not found in Feature " + expectedArtifactId, feature.getBundles().containsExact(ArtifactId.fromMvnId(expectedArtifact)));
            verifyInstalledArtifact(outputDirectory, expectedArtifact);
        }

        for (String expectedConfiguration : expectedConfigurations) {
            assertNotNull(expectedConfiguration + " not found in Feature " + expectedArtifactId, feature.getConfigurations().getConfiguration(expectedConfiguration));
        }

        if (expectedContentPackagesExtensions.size() > 0) {
            Artifacts contentPackages = feature.getExtensions().getByName("content-packages").getArtifacts();
            assertEquals(expectedContentPackagesExtensions.size(), contentPackages.size());

            for (String expectedContentPackagesExtension : expectedContentPackagesExtensions) {
                assertTrue(expectedContentPackagesExtension + " not found in Feature " + expectedArtifactId,
                        contentPackages.containsExact(ArtifactId.fromMvnId(expectedContentPackagesExtension)));
                verifyInstalledArtifact(outputDirectory, expectedContentPackagesExtension);
            }
        }
    }

    static void verifyContentPackageEntryNames(File contentPackage, String...expectedEntryNames) throws Exception {
        try (ZipFile zipFile = new ZipFile(contentPackage)) {
            List<String> expectedEntryNamesList = Arrays.asList(expectedEntryNames);
            for (Enumeration<? extends ZipEntry> zipEntries = zipFile.entries(); zipEntries.hasMoreElements();) {
                ZipEntry zipEntry = zipEntries.nextElement();
                String entryName = zipEntry.getName();
                if (!entryName.endsWith("/")) {
                    assertTrue("ZipEntry with name " + entryName + " not expected", expectedEntryNamesList.contains(entryName));
                }
            }
        }
    }

    static void verifyContentPackage(File contentPackage, String...expectedEntries) throws Exception {
        verifyContentPackage(contentPackage, Collections.emptySet(), Arrays.asList(expectedEntries));
    }

    static void verifyContentPackage(File contentPackage, @NotNull Iterable<String> notExpected, @NotNull Iterable<String> expectedEntries) throws Exception {
        try (ZipFile zipFile = new ZipFile(contentPackage)) {
            for (String expectedEntry : expectedEntries) {
                assertNotNull("Expected entry not found: " + expectedEntry + " in file " + contentPackage, zipFile.getEntry(expectedEntry));
            }
            for (String notExpectedEntry : notExpected) {
                assertNull("Not expected entry found: " + notExpectedEntry + " in file " + contentPackage, zipFile.getEntry(notExpectedEntry));
            }
        }
    }

    static void verifyPropertiesXmlEntry(File contentPackage, String... expectedPropertyKeys) throws IOException {
        try (ZipFile zipFile = new ZipFile(contentPackage)) {
            ZipEntry zipEntry = zipFile.getEntry(Constants.META_DIR + "/" + Constants.PROPERTIES_XML);
            assertNotNull("Package didn't contain properties.xml", zipEntry);
            try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
                Properties properties = new Properties();
                properties.loadFromXML(inputStream);
                for (String expectedPropertyKey : expectedPropertyKeys) {
                    if (expectedPropertyKey.startsWith("!")) {
                        String key = expectedPropertyKey.substring(1);
                        assertFalse("Properties.xml was not supposed to contain key " +  key + " but it does", properties.containsKey(key));
                    } else {
                        assertTrue("Properties.xml was supposed to contain key " +  expectedPropertyKey + " but it does not", properties.containsKey(expectedPropertyKey));
                    }
                }
            }
        }
    }

    static void verifyInstalledArtifact(File outputDirectory, String coordinates) {
        ArtifactId bundleId = ArtifactId.fromMvnId(coordinates);

        StringTokenizer tokenizer = new StringTokenizer(bundleId.getGroupId(), ".");
        while (tokenizer.hasMoreTokens()) {
            outputDirectory = new File(outputDirectory, tokenizer.nextToken());
        }

        outputDirectory = new File(outputDirectory, bundleId.getArtifactId());
        outputDirectory = new File(outputDirectory, bundleId.getVersion());

        StringBuilder bundleFileName = new StringBuilder()
                .append(bundleId.getArtifactId())
                .append('-')
                .append(bundleId.getVersion());
        if (bundleId.getClassifier() != null) {
            bundleFileName.append('-').append(bundleId.getClassifier());
        }
        bundleFileName.append('.').append(bundleId.getType());

        File bundleFile = new File(outputDirectory, bundleFileName.toString());
        assertTrue("Bundle " + bundleFile + " does not exist", bundleFile.exists());

        File pomFile = new File(outputDirectory, String.format("%s-%s.pom", bundleId.getArtifactId(), bundleId.getVersion()));
        assertTrue("POM file " + pomFile + " does not exist", pomFile.exists());
    }
}