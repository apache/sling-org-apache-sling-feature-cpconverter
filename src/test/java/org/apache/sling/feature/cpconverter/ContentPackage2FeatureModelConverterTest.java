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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.zip.ZipFile;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.packaging.CyclicDependencyException;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Artifacts;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.acl.DefaultAclManager;
import org.apache.sling.feature.cpconverter.artifacts.DefaultArtifactsDeployer;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.filtering.RegexBasedResourceFilter;
import org.apache.sling.feature.cpconverter.handlers.DefaultEntryHandlersManager;
import org.apache.sling.feature.cpconverter.vltpkg.DefaultPackagesEventsEmitter;
import org.apache.sling.feature.io.json.FeatureJSONReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ContentPackage2FeatureModelConverterTest {

    /**
     * Test package A-1.0. Depends on B and C-1.X
     * Test package B-1.0. Depends on C
     */
    private static String[] TEST_PACKAGES_INPUT = { "test_c-1.0.zip", "test_a-1.0.zip", "test_b-1.0.zip" };

    private static String[] TEST_PACKAGES_OUTPUT = { "my_packages:test_c:1.0", "my_packages:test_b:1.0", "my_packages:test_a:1.0" };

    private static String[] TEST_PACKAGES_CYCLIC_DEPENDENCY = { "test_d-1.0.zip",
                                                                "test_c-1.0.zip",
                                                                "test_a-1.0.zip",
                                                                "test_b-1.0.zip",
                                                                "test_e-1.0.zip" };

    private ContentPackage2FeatureModelConverter converter;

    @Before
    public void setUp() {
        converter = new ContentPackage2FeatureModelConverter()
                    .setEntryHandlersManager(new DefaultEntryHandlersManager())
                    .setAclManager(new DefaultAclManager());
    }

    @After
    public void tearDowd() {
        converter = null;
    }

    @Test(expected = NullPointerException.class)
    public void convertRequiresNonNullPackage() throws Exception {
        converter.convert((File)null);
    }

    @Test(expected = NullPointerException.class)
    public void convertRequiresNonNullPackages() throws Exception {
        converter.convert((File[])null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void convertRequiresExistingFile() throws Exception {
        converter.convert(new File("this/file/does/not/exist.zip"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void convertRequiresNotDirectoryFile() throws Exception {
        File testDirectory = new File(System.getProperty("user.dir"));
        converter.convert(testDirectory);
    }

    @Test(expected = NullPointerException.class)
    public void processRequiresNotNullPackage() throws Exception {
        converter.processSubPackage("", null, null);
    }

    @Test
    public void convertContentPackage() throws Exception {
        URL packageUrl = getClass().getResource("test-content-package.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());

        converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null))
                 .setBundlesDeployer(new DefaultArtifactsDeployer(outputDirectory))
                 .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                 .convert(packageFile);

        verifyFeatureFile(outputDirectory,
                          "asd.retail.all.json",
                          "asd.sample:asd.retail.all:slingosgifeature:0.0.1",
                          Arrays.asList("org.apache.felix:org.apache.felix.framework:6.0.1"),
                          Arrays.asList("org.apache.sling.commons.log.LogManager.factory.config~asd-retail"),
                          Arrays.asList("asd.sample:asd.retail.apps:zip:cp2fm-converted:0.0.1",
                                        "asd.sample:Asd.Retail.ui.content:zip:cp2fm-converted:0.0.1",
                                        "asd:Asd.Retail.config:zip:cp2fm-converted:0.0.1",
                                        "asd.sample:asd.retail.all:zip:cp2fm-converted:0.0.1"));
        verifyFeatureFile(outputDirectory,
                          "asd.retail.all-author.json",
                          "asd.sample:asd.retail.all:slingosgifeature:author:0.0.1",
                          Arrays.asList("org.apache.sling:org.apache.sling.api:2.20.0"),
                          Collections.emptyList(),
                          Collections.emptyList());
        verifyFeatureFile(outputDirectory,
                          "asd.retail.all-publish.json",
                          "asd.sample:asd.retail.all:slingosgifeature:publish:0.0.1",
                          Arrays.asList("org.apache.sling:org.apache.sling.models.api:1.3.8"),
                          Arrays.asList("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended~asd-retail"),
                          Collections.emptyList());

        // verify the runmode.mapper integrity
        File runmodeMapperFile = new File(outputDirectory, "runmode.mapping");
        assertTrue(runmodeMapperFile.exists());
        assertTrue(runmodeMapperFile.isFile());
        Properties runModes = new Properties();
        try (FileInputStream input = new FileInputStream(runmodeMapperFile)) {
            runModes.load(input);
        }
        assertFalse(runModes.isEmpty());
        assertTrue(runModes.containsKey("(default)"));
        assertEquals("asd.retail.all.json", runModes.getProperty("(default)"));
        assertEquals("asd.retail.all-author.json", runModes.getProperty("author"));
        assertEquals("asd.retail.all-publish.json", runModes.getProperty("publish"));

        verifyContentPackage(new File(outputDirectory, "asd/Asd.Retail.config/0.0.1/Asd.Retail.config-0.0.1-cp2fm-converted.zip"),
                             "META-INF/vault/settings.xml",
                             "META-INF/vault/properties.xml",
                             "META-INF/vault/config.xml",
                             "META-INF/vault/filter.xml",
                             "jcr_root/settings.xml",
                             "jcr_root/config.xml",
                             "jcr_root/definition/.content.xml",
                             "jcr_root/apps/.content.xml");
        verifyContentPackage(new File(outputDirectory, "asd/sample/Asd.Retail.ui.content/0.0.1/Asd.Retail.ui.content-0.0.1-cp2fm-converted.zip"),
                             "META-INF/vault/settings.xml",
                             "META-INF/vault/properties.xml",
                             "META-INF/vault/config.xml",
                             "META-INF/vault/filter.xml",
                             "META-INF/vault/filter-plugin-generated.xml",
                             "jcr_root/settings.xml",
                             "jcr_root/content/asd/.content.xml",
                             "jcr_root/content/asd/resources.xml",
                             "jcr_root/config.xml",
                             "jcr_root/definition/.content.xml");
        verifyContentPackage(new File(outputDirectory, "asd/sample/asd.retail.apps/0.0.1/asd.retail.apps-0.0.1-cp2fm-converted.zip"),
                             "META-INF/vault/settings.xml",
                             "META-INF/vault/properties.xml",
                             "META-INF/vault/config.xml",
                             "META-INF/vault/filter.xml",
                             "META-INF/vault/filter-plugin-generated.xml",
                             "jcr_root/settings.xml",
                             "jcr_root/config.xml",
                             "jcr_root/definition/.content.xml");
        verifyContentPackage(new File(outputDirectory, "asd/sample/asd.retail.all/0.0.1/asd.retail.all-0.0.1-cp2fm-converted.zip"),
                             "META-INF/vault/settings.xml",
                             "META-INF/vault/properties.xml",
                             "META-INF/vault/config.xml",
                             "META-INF/vault/filter.xml",
                             "jcr_root/settings.xml",
                             "jcr_root/config.xml",
                             "jcr_root/definition/.content.xml");
    }

    @Test
    public void convertContentPackageDropContent() throws Exception {
        URL packageUrl = getClass().getResource("test-content-package.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());

        converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null))
                 .setBundlesDeployer(new DefaultArtifactsDeployer(outputDirectory))
                 .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                 .setDropContent(true)
                 .convert(packageFile);

        verifyFeatureFile(outputDirectory,
                          "asd.retail.all.json",
                          "asd.sample:asd.retail.all:slingosgifeature:0.0.1",
                          Arrays.asList("org.apache.felix:org.apache.felix.framework:6.0.1"),
                          Arrays.asList("org.apache.sling.commons.log.LogManager.factory.config~asd-retail"),
                          Arrays.asList("asd.sample:asd.retail.apps:zip:cp2fm-converted:0.0.1",
                                        "asd:Asd.Retail.config:zip:cp2fm-converted:0.0.1"));
        verifyFeatureFile(outputDirectory,
                          "asd.retail.all-author.json",
                          "asd.sample:asd.retail.all:slingosgifeature:author:0.0.1",
                          Arrays.asList("org.apache.sling:org.apache.sling.api:2.20.0"),
                          Collections.emptyList(),
                          Collections.emptyList());
        verifyFeatureFile(outputDirectory,
                          "asd.retail.all-publish.json",
                          "asd.sample:asd.retail.all:slingosgifeature:publish:0.0.1",
                          Arrays.asList("org.apache.sling:org.apache.sling.models.api:1.3.8"),
                          Arrays.asList("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended~asd-retail"),
                          Collections.emptyList());

        // verify the runmode.mapper integrity
        File runmodeMapperFile = new File(outputDirectory, "runmode.mapping");
        assertTrue(runmodeMapperFile.exists());
        assertTrue(runmodeMapperFile.isFile());
        Properties runModes = new Properties();
        try (FileInputStream input = new FileInputStream(runmodeMapperFile)) {
            runModes.load(input);
        }
        assertFalse(runModes.isEmpty());
        assertTrue(runModes.containsKey("(default)"));
        assertEquals("asd.retail.all.json", runModes.getProperty("(default)"));
        assertEquals("asd.retail.all-author.json", runModes.getProperty("author"));
        assertEquals("asd.retail.all-publish.json", runModes.getProperty("publish"));

        verifyContentPackage(new File(outputDirectory, "asd/Asd.Retail.config/0.0.1/Asd.Retail.config-0.0.1-cp2fm-converted.zip"),
                             "META-INF/vault/settings.xml",
                             "META-INF/vault/properties.xml",
                             "META-INF/vault/config.xml",
                             "META-INF/vault/filter.xml",
                             "jcr_root/settings.xml",
                             "jcr_root/config.xml",
                             "jcr_root/definition/.content.xml",
                             "jcr_root/apps/.content.xml");
        verifyContentPackage(new File(outputDirectory, "asd/sample/asd.retail.apps/0.0.1/asd.retail.apps-0.0.1-cp2fm-converted.zip"),
                             "META-INF/vault/settings.xml",
                             "META-INF/vault/properties.xml",
                             "META-INF/vault/config.xml",
                             "META-INF/vault/filter.xml",
                             "META-INF/vault/filter-plugin-generated.xml",
                             "jcr_root/settings.xml",
                             "jcr_root/config.xml",
                             "jcr_root/definition/.content.xml");
        // in contrast to previous test when dropping content packages the cases below would be filtered out and files wouldn'T be in cache
        assertFalse(new File(outputDirectory, "asd/sample/Asd.Retail.ui.content/0.0.1/Asd.Retail.ui.content-0.0.1-cp2fm-converted.zip").exists());
        assertFalse(new File(outputDirectory, "asd/sample/asd.retail.all/0.0.1/asd.retail.all-0.0.1-cp2fm-converted.zip").exists());
    }

    @Test
    public void testConvertContentPackageWithAPIRegion() throws Exception {
        URL cp = getClass().getResource("test_c-1.0.zip");
        File cpFile = new File(cp.getFile());
        File outDir = Files.createTempDirectory(getClass().getSimpleName()).toFile();

        try {
            DefaultFeaturesManager fm = new DefaultFeaturesManager(true, 5, outDir, null, null, null);
            fm.setAPIRegions(Arrays.asList("global", "foo.bar"));
            converter.setFeaturesManager(fm)
                     .setBundlesDeployer(new DefaultArtifactsDeployer(outDir))
                     .setEmitter(DefaultPackagesEventsEmitter.open(outDir))
                     .convert(cpFile);

            File featureFile = new File(outDir, "test_c.json");
            try (Reader reader = new FileReader(featureFile)) {
                Feature feature = FeatureJSONReader.read(reader, featureFile.getAbsolutePath());

                Extension apiRegions = feature.getExtensions().getByName("api-regions");
                assertEquals(ExtensionType.JSON, apiRegions.getType());
                String json = apiRegions.getJSON();
                JsonArray ja = Json.createReader(new StringReader(json)).readArray();
                assertEquals(2, ja.size());

                JsonObject globalJO = ja.getJsonObject(0);
                assertEquals("global", globalJO.getString("name"));
                JsonArray globalExports = globalJO.getJsonArray("exports");
                assertTrue(globalExports == null || globalExports.isEmpty());

                JsonObject foobarJO = ja.getJsonObject(1);
                assertEquals("foo.bar", foobarJO.getString("name"));
                JsonArray fooExports = foobarJO.getJsonArray("exports");
                assertTrue(fooExports == null || fooExports.isEmpty());
            }

        } finally {
            deleteDirTree(outDir);
        }
    }

    private void deleteDirTree(File dir) throws IOException {
        Path tempDir = dir.toPath();

        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

    private Feature getFeature(File outputDirectory, String name) throws Exception {
        File featureFile = new File(outputDirectory, name);
        assertTrue(featureFile + " was not correctly created", featureFile.exists());

        try (Reader reader = new FileReader(featureFile)) {
            return FeatureJSONReader.read(reader, featureFile.getAbsolutePath());
        }
    }

    private void verifyFeatureFile(File outputDirectory,
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

    private void verifyInstalledArtifact(File outputDirectory, String coordinates) {
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

    private void verifyContentPackage(File contentPackage, String...expectedEntries) throws Exception {
        try (ZipFile zipFile = new ZipFile(contentPackage)) {
            for (String expectedEntry : expectedEntries) {
                assertNotNull("Expected entry not found: " + expectedEntry + " in file " + contentPackage,
                              zipFile.getEntry(expectedEntry));
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void verifyFilteringOutUndesiredPackages() throws Exception {
        RegexBasedResourceFilter resourceFilter = new RegexBasedResourceFilter();
        resourceFilter.addFilteringPattern(".*\\/install(?!(\\.runMode1\\/|\\.runMode2\\/|\\/))(.*)(?=\\.zip$).*");
        converter.setResourceFilter(resourceFilter);

        URL packageUrl = getClass().getResource("test-content-package-unacceptable.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());

        converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null))
                 .setBundlesDeployer(new DefaultArtifactsDeployer(outputDirectory))
                 .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                 .convert(packageFile);
    }

    @Test(expected = IllegalStateException.class)
    public void doesNotAllowSameConfigurationPidForSameRunmode() throws Exception {
        addSamePidConfiguration(null, null);
    }

    @Test
    public void allowSameConfigurationPidForDifferentRunmode() throws Exception {
        addSamePidConfiguration(null, "test");
    }

    private void addSamePidConfiguration(String runmodeA, String runmodeB) throws Exception {
        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        URL packageUrl = getClass().getResource("test-content-package.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        converter.setBundlesDeployer(new DefaultArtifactsDeployer(outputDirectory))
                 .setFeaturesManager(new DefaultFeaturesManager(false, 5, outputDirectory, null, null, null))
                 .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                 .convert(packageFile);

        String pid = "this.is.just.a.pid";
        converter.getFeaturesManager().addConfiguration(runmodeA, pid, new Hashtable<String, Object>());
        converter.getFeaturesManager().addConfiguration(runmodeB, pid, new Hashtable<String, Object>());
    }

    @Test
    public void testAPIRegionsWithRunmodes() throws Exception {
        URL cp = getClass().getResource("test-content-package.zip");
        File cpFile = new File(cp.getFile());
        File outDir = Files.createTempDirectory(getClass().getSimpleName()).toFile();

        try {
            converter.setBundlesDeployer(new DefaultArtifactsDeployer(outDir))
            .setFeaturesManager(new DefaultFeaturesManager(false, 5, outDir, null, null, null)
                    .setAPIRegions(Arrays.asList("a.b.c")))
            .setEmitter(DefaultPackagesEventsEmitter.open(outDir))
            .convert(cpFile);

            assertAPIRegion(new File(outDir, "asd.retail.all.json"), "a.b.c");
            assertAPIRegion(new File(outDir, "asd.retail.all-author.json"), "a.b.c");
            assertAPIRegion(new File(outDir, "asd.retail.all-publish.json"), "a.b.c");
        } finally {
            deleteDirTree(outDir);
        }

    }

    private void assertAPIRegion(File featureFile, String region) throws IOException, FileNotFoundException {
        try (Reader reader = new FileReader(featureFile)) {
            Feature feature = FeatureJSONReader.read(reader, featureFile.getAbsolutePath());

            Extension apiRegions = feature.getExtensions().getByName("api-regions");
            assertEquals(ExtensionType.JSON, apiRegions.getType());
            String json = apiRegions.getJSON();
            JsonArray ja = Json.createReader(new StringReader(json)).readArray();
            assertEquals(1, ja.size());

            JsonObject regionJO = ja.getJsonObject(0);
            assertEquals(region, regionJO.getString("name"));
            JsonArray exports = regionJO.getJsonArray("exports");
            assertTrue(exports == null || exports.isEmpty());
        }
    }

    @Test
    public void overrideFeatureId() throws Exception {
        URL packageUrl = getClass().getResource("test-content-package.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());

        String overrideId = "${project.groupId}:${project.artifactId}:slingosgifeature:asd.test.all-1.0.0:${project.version}";
        converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, overrideId, null, null))
                 .setBundlesDeployer(new DefaultArtifactsDeployer(outputDirectory))
                 .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                 .convert(packageFile);

        verifyFeatureFile(outputDirectory,
                          "asd.retail.all.json",
                          "${project.groupId}:${project.artifactId}:slingosgifeature:asd.test.all-1.0.0:${project.version}",
                          Arrays.asList("org.apache.felix:org.apache.felix.framework:6.0.1"),
                          Arrays.asList("org.apache.sling.commons.log.LogManager.factory.config~asd-retail"),
                          Arrays.asList("asd.sample:asd.retail.apps:zip:cp2fm-converted:0.0.1",
                                        "asd.sample:Asd.Retail.ui.content:zip:cp2fm-converted:0.0.1",
                                        "asd:Asd.Retail.config:zip:cp2fm-converted:0.0.1",
                                        "asd.sample:asd.retail.all:zip:cp2fm-converted:0.0.1"));
        verifyFeatureFile(outputDirectory,
                          "asd.retail.all-author.json",
                          "${project.groupId}:${project.artifactId}:slingosgifeature:asd.test.all-1.0.0-author:${project.version}",
                          Arrays.asList("org.apache.sling:org.apache.sling.api:2.20.0"),
                          Collections.emptyList(),
                          Collections.emptyList());
        verifyFeatureFile(outputDirectory,
                          "asd.retail.all-publish.json",
                          "${project.groupId}:${project.artifactId}:slingosgifeature:asd.test.all-1.0.0-publish:${project.version}",
                          Arrays.asList("org.apache.sling:org.apache.sling.models.api:1.3.8"),
                          Arrays.asList("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended~asd-retail"),
                          Collections.emptyList());

        verifyContentPackage(new File(outputDirectory, "asd/sample/asd.retail.all/0.0.1/asd.retail.all-0.0.1-cp2fm-converted.zip"),
                             "META-INF/vault/properties.xml",
                             "META-INF/vault/config.xml",
                             "META-INF/vault/settings.xml",
                             "META-INF/vault/filter.xml");
    }

    @Test
    public void testPackageOrdering() throws Exception {
        File[] contentPackages = load(TEST_PACKAGES_INPUT);

        Collection<VaultPackage> ordered = converter.firstPass(contentPackages);

        Iterator<VaultPackage> fileIt = ordered.iterator();
        for (String expected : TEST_PACKAGES_OUTPUT) {
            VaultPackage next = fileIt.next();
            assertEquals(expected, next.getId().toString());
        }
    }

    @Test(expected = CyclicDependencyException.class)
    public void testDependencyCycle() throws Exception {
        File[] contentPackages = load(TEST_PACKAGES_CYCLIC_DEPENDENCY);
        converter.firstPass(contentPackages);
    }

    @Test
    public void includeLatestUpdatedContentPackagesOnly() throws Exception {
        File[] contentPackages = load("test-content-package.zip", "test-content-package-2.zip");
        converter.firstPass(contentPackages);

        assertTrue(converter.isSubContentPackageIncluded("/jcr_root/etc/packages/asd/test-content-0.2.zip"));
        assertFalse(converter.isSubContentPackageIncluded("/jcr_root/etc/packages/asd/test-content.zip"));
    }

    @Test
    public void verifyRepoinitContainsNodetypesRegistration() throws Exception {
        File[] contentPackages = load(TEST_PACKAGES_INPUT[1]);

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());

        converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null))
                 .setBundlesDeployer(new DefaultArtifactsDeployer(outputDirectory))
                 .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                 .convert(contentPackages[0]);

        File featureFile = new File(outputDirectory, "test_a.json");
        try (Reader reader = new FileReader(featureFile)) {
            Feature feature = FeatureJSONReader.read(reader, featureFile.getAbsolutePath());

            Extension repoinitExtension = feature.getExtensions().getByName("repoinit");
            assertNotNull(repoinitExtension);

            String expected = "register nodetypes" + System.lineSeparator() +
                    "<<===" + System.lineSeparator() +
                    "<< <'sling'='http://sling.apache.org/jcr/sling/1.0'>" + System.lineSeparator() +
                    "<< <'nt'='http://www.jcp.org/jcr/nt/1.0'>" + System.lineSeparator() +
                    "<< <'rep'='internal'>" + System.lineSeparator() +
                    "" + System.lineSeparator() +
                    "<< [sling:Folder] > nt:folder" + System.lineSeparator() +
                    "<<   - * (undefined) multiple" + System.lineSeparator() +
                    "<<   - * (undefined)" + System.lineSeparator() +
                    "<<   + * (nt:base) = sling:Folder version" + System.lineSeparator() +
                    System.lineSeparator() +
                    "<< [rep:RepoAccessControllable]" + System.lineSeparator() +
                    "<<   mixin" + System.lineSeparator() +
                    "<<   + rep:repoPolicy (rep:Policy) protected ignore" + System.lineSeparator() +
                    System.lineSeparator() +
                    "===>>" + System.lineSeparator();
            String actual = repoinitExtension.getText();
            assertEquals(expected, actual);
        }
    }

    // see SLING-8649
    @Test
    public void filteredOutContentPackagesAreExcludedDependencies() throws Exception {
        File[] contentPackages = load("test_dep_a-1.0.zip", "test_dep_b-1.0.zip", "test_dep_b-1.0.zip");

        // input: c <- a <- b
        // expected output: c <- a

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());

        converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null))
                 .setDropContent(true)
                 .setBundlesDeployer(new DefaultArtifactsDeployer(outputDirectory))
                 .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                 .convert(contentPackages);

        Feature a = getFeature(outputDirectory, "test_a.json");
        assertNull(a.getExtensions().getByName("content-packages"));

        Feature b = getFeature(outputDirectory, "test_b.json");
        Artifacts artifacts = b.getExtensions().getByName("content-packages").getArtifacts();
        assertFalse(artifacts.isEmpty());
        assertEquals("my_packages:test_b:zip:cp2fm-converted:1.0", artifacts.iterator().next().getId().toString());

        File contentPackage = new File(outputDirectory, "my_packages/test_b/1.0/test_b-1.0-cp2fm-converted.zip");
        VaultPackage vaultPackage = new PackageManagerImpl().open(contentPackage);
        String dependencies = vaultPackage.getProperties().getProperty(PackageProperties.NAME_DEPENDENCIES);
        assertEquals("my_packages:test_c", dependencies);
    }

    private File[] load(String...resources) {
        File[] loadedResources = new File[resources.length];

        for (int i = 0; i < resources.length; i++) {
            String resourceName = resources[i];
            URL resourceUrl = getClass().getResource(resourceName);
            loadedResources[i] = FileUtils.toFile(resourceUrl);
        }

        return loadedResources;
    }

}
