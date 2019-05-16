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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.Reader;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.acl.DefaultAclManager;
import org.apache.sling.feature.cpconverter.artifacts.DefaultArtifactsDeployer;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.filtering.RegexBasedResourceFilter;
import org.apache.sling.feature.cpconverter.handlers.DefaultEntryHandlersManager;
import org.apache.sling.feature.io.json.FeatureJSONReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ContentPackage2FeatureModelConverterTest {

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
        converter.convert(null);
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
        converter.processSubPackage("", null);
    }

    @Test
    public void convertContentPackage() throws Exception {
        URL packageUrl = getClass().getResource("test-content-package.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        File outputDirectory = new File(System.getProperty("testDirectory"), getClass().getName() + '_' + System.currentTimeMillis());

        converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null))
                 .setBundlesDeployer(new DefaultArtifactsDeployer(outputDirectory))
                 .convert(packageFile);

        verifyFeatureFile(outputDirectory,
                          "asd.retail.all.json",
                          "asd.sample:asd.retail.all:slingosgifeature:0.0.1",
                          Arrays.asList("org.apache.felix:org.apache.felix.framework:6.0.1"),
                          Arrays.asList("org.apache.sling.commons.log.LogManager.factory.config~asd-retail"),
                          Arrays.asList("asd.sample:asd.retail.all:zip:cp2fm-converted:0.0.1"));
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

        ZipFile zipFile = new ZipFile(new File(outputDirectory, "asd/sample/asd.retail.all/0.0.1/asd.retail.all-0.0.1-cp2fm-converted.zip"));
        for (String expectedEntry : new String[] {
                "jcr_root/content/asd/.content.xml",
                "jcr_root/content/asd/resources.xml",
                "jcr_root/apps/.content.xml",
                "META-INF/vault/properties.xml",
                "META-INF/vault/config.xml",
                "META-INF/vault/settings.xml",
                "META-INF/vault/filter.xml",
                "META-INF/vault/definition/.content.xml",
                "jcr_root/etc/packages/asd/test-bundles.zip",
                "jcr_root/etc/packages/asd/test-configurations.zip",
                "jcr_root/etc/packages/asd/test-content.zip",
                }) {
            assertNotNull(zipFile.getEntry(expectedEntry));
        }
        zipFile.close();
    }

    private void verifyFeatureFile(File outputDirectory,
                                   String name,
                                   String expectedArtifactId,
                                   List<String> expectedArtifacts,
                                   List<String> expectedConfigurations,
                                   List<String> expectedContentPackagesExtensions) throws Exception {
        File featureFile = new File(outputDirectory, name);
        assertTrue(featureFile + " was not correctly created", featureFile.exists());

        try (Reader reader = new FileReader(featureFile)) {
            Feature feature = FeatureJSONReader.read(reader, featureFile.getAbsolutePath());

            assertEquals(expectedArtifactId, feature.getId().toMvnId());

            for (String expectedArtifact : expectedArtifacts) {
                assertTrue(expectedArtifact + " not found in Feature " + expectedArtifactId, feature.getBundles().containsExact(ArtifactId.fromMvnId(expectedArtifact)));
                verifyInstalledArtifact(outputDirectory, expectedArtifact);
            }

            for (String expectedConfiguration : expectedConfigurations) {
                assertNotNull(expectedConfiguration + " not found in Feature " + expectedArtifactId, feature.getConfigurations().getConfiguration(expectedConfiguration));
            }

            for (String expectedContentPackagesExtension : expectedContentPackagesExtensions) {
                assertTrue(expectedContentPackagesExtension + " not found in Feature " + expectedArtifactId,
                           feature.getExtensions().getByName("content-packages").getArtifacts().containsExact(ArtifactId.fromMvnId(expectedContentPackagesExtension)));
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

    @Test(expected = IllegalArgumentException.class)
    public void verifyFilteringOutUndesiredPackages() throws Exception {
        RegexBasedResourceFilter resourceFilter = new RegexBasedResourceFilter();
        resourceFilter.addFilteringPattern(".*\\/install(?!(\\.runMode1\\/|\\.runMode2\\/|\\/))(.*)(?=\\.zip$).*");
        converter.setResourceFilter(resourceFilter);

        URL packageUrl = getClass().getResource("test-content-package-unacceptable.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        File outputDirectory = new File(System.getProperty("testDirectory"), getClass().getName() + '_' + System.currentTimeMillis());

        converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null))
                 .setBundlesDeployer(new DefaultArtifactsDeployer(outputDirectory))
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
            File outputDirectory = new File(System.getProperty("testDirectory"), getClass().getName() + '_' + System.currentTimeMillis());
            URL packageUrl = getClass().getResource("test-content-package.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        converter.setBundlesDeployer(new DefaultArtifactsDeployer(outputDirectory))
                 .setFeaturesManager(new DefaultFeaturesManager(false, 5, outputDirectory, null, null))
                 .convert(packageFile);

        String pid = "this.is.just.a.pid";
        converter.getFeaturesManager().addConfiguration(runmodeA, pid, new Hashtable<String, Object>());
        converter.getFeaturesManager().addConfiguration(runmodeB, pid, new Hashtable<String, Object>());
    }

    @Test
    public void overrideFeatureId() throws Exception {
        URL packageUrl = getClass().getResource("test-content-package.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        File outputDirectory = new File(System.getProperty("testDirectory"), getClass().getName() + '_' + System.currentTimeMillis());

        String overrideId = "${project.groupId}:${project.artifactId}:slingosgifeature:asd.test.all-1.0.0:${project.version}";
        converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, overrideId, null))
                 .setBundlesDeployer(new DefaultArtifactsDeployer(outputDirectory))
                 .convert(packageFile);

        verifyFeatureFile(outputDirectory,
                          "asd.retail.all.json",
                          "${project.groupId}:${project.artifactId}:slingosgifeature:asd.test.all-1.0.0:${project.version}",
                          Arrays.asList("org.apache.felix:org.apache.felix.framework:6.0.1"),
                          Arrays.asList("org.apache.sling.commons.log.LogManager.factory.config~asd-retail"),
                          Arrays.asList("asd.sample:asd.retail.all:zip:cp2fm-converted:0.0.1"));
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

        ZipFile zipFile = new ZipFile(new File(outputDirectory, "asd/sample/asd.retail.all/0.0.1/asd.retail.all-0.0.1-cp2fm-converted.zip"));
        for (String expectedEntry : new String[] {
                "jcr_root/content/asd/.content.xml",
                "jcr_root/content/asd/resources.xml",
                "jcr_root/apps/.content.xml",
                "META-INF/vault/properties.xml",
                "META-INF/vault/config.xml",
                "META-INF/vault/settings.xml",
                "META-INF/vault/filter.xml",
                "META-INF/vault/definition/.content.xml",
                "jcr_root/etc/packages/asd/test-bundles.zip",
                "jcr_root/etc/packages/asd/test-configurations.zip",
                "jcr_root/etc/packages/asd/test-content.zip",
                }) {
            assertNotNull(zipFile.getEntry(expectedEntry));
        }
        zipFile.close();
    }
}
