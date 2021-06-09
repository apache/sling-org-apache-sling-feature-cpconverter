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

import static org.apache.sling.feature.cpconverter.Util.normalize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.spi.PrivilegeDefinitions;
import org.apache.jackrabbit.vault.packaging.CyclicDependencyException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Artifacts;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.PackagePolicy;
import org.apache.sling.feature.cpconverter.accesscontrol.DefaultAclManager;
import org.apache.sling.feature.cpconverter.artifacts.LocalMavenRepositoryArtifactsDeployer;
import org.apache.sling.feature.cpconverter.artifacts.SimpleFolderArtifactsDeployer;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.filtering.RegexBasedResourceFilter;
import org.apache.sling.feature.cpconverter.handlers.DefaultEntryHandlersManager;
import org.apache.sling.feature.cpconverter.handlers.EntryHandlersManager;
import org.apache.sling.feature.cpconverter.shared.ConverterConstants;
import org.apache.sling.feature.cpconverter.vltpkg.DefaultPackagesEventsEmitter;
import org.apache.sling.feature.io.json.FeatureJSONReader;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ContentPackage2FeatureModelConverterTest {

    /**
     * Test package A-1.0. Depends on B and C-1.X
     * Test package B-1.0. Depends on C
     */
    private static final String[] TEST_PACKAGES_INPUT = { "test_c-1.0.zip", "test_a-1.0.zip", "test_b-1.0.zip" };

    private static final String[] TEST_PACKAGES_OUTPUT = { "my_packages:test_c:1.0", "my_packages:test_b:1.0", "my_packages:test_a:1.0" };

    private static final String[] TEST_PACKAGES_CYCLIC_DEPENDENCY = { "test_d-1.0.zip",
                                                                "test_c-1.0.zip",
                                                                "test_a-1.0.zip",
                                                                "test_b-1.0.zip",
                                                                "test_e-1.0.zip" };

    private ContentPackage2FeatureModelConverter converter;
    private EntryHandlersManager handlersManager;
    
    private final String systemUserRelPath;
    
    @Parameterized.Parameters(name = "name={1}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(
                new Object[] {ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT, "Default system user rel-path"},
                new Object[] { "system/cq:services", "Modified system user rel-path"});
    }
    
    public ContentPackage2FeatureModelConverterTest(@NotNull String systemUserRelPath, @NotNull String name) {
        this.systemUserRelPath = systemUserRelPath;
    }
        
    @Before
    public void setUp() {
        handlersManager = new DefaultEntryHandlersManager(Collections.emptyMap(), false, ContentPackage2FeatureModelConverter.SlingInitialContentPolicy.KEEP, systemUserRelPath);
        converter = new ContentPackage2FeatureModelConverter()
                    .setEntryHandlersManager(handlersManager)
                    .setAclManager(new DefaultAclManager());
    }

    @After
    public void tearDown() throws IOException {
        converter.close();
    }

    @Test
    public void testHandlersPresent() {
        assertNotNull(handlersManager.getEntryHandlerByEntryPath("/jcr_root/_rep_policy.xml"));
        assertNotNull(handlersManager.getEntryHandlerByEntryPath("/jcr_root/_rep_repoPolicy.xml"));
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
        converter.processSubPackage("", null, null, false);
    }

    @Test
    public void convertContentPackage() throws Exception {
        URL packageUrl = getClass().getResource("test-content-package.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());

        try {

            converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                    .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                    .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                    .convert(packageFile);

            verifyFeatureFile(outputDirectory,
                            "asd.retail.all.json",
                            "asd.sample:asd.retail.all:slingosgifeature:0.0.1",
                            Collections.singletonList("org.apache.felix:org.apache.felix.framework:6.0.1"),
                            Collections.singletonList("org.apache.sling.commons.log.LogManager.factory.config~asd-retail"),
                            Arrays.asList("asd.sample:asd.retail.apps:zip:cp2fm-converted:0.0.1",
                                            "asd.sample:Asd.Retail.ui.content:zip:cp2fm-converted:0.0.1",
                                            "asd:Asd.Retail.config:zip:cp2fm-converted:0.0.1",
                                            "asd.sample:asd.retail.all:zip:cp2fm-converted:0.0.1"));
            verifyFeatureFile(outputDirectory,
                            "asd.retail.all-author.json",
                            "asd.sample:asd.retail.all:slingosgifeature:author:0.0.1",
                            Collections.singletonList("org.apache.sling:org.apache.sling.api:2.20.0"),
                            Collections.emptyList(),
                            Collections.emptyList());
            verifyFeatureFile(outputDirectory,
                            "asd.retail.all-publish.json",
                            "asd.sample:asd.retail.all:slingosgifeature:publish:0.0.1",
                            Collections.singletonList("org.apache.sling:org.apache.sling.models.api:1.3.8"),
                            Collections.singletonList("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended~asd-retail"),
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
                                "jcr_root/apps/.content.xml");
            verifyContentPackage(new File(outputDirectory, "asd/sample/Asd.Retail.ui.content/0.0.1/Asd.Retail.ui.content-0.0.1-cp2fm-converted.zip"),
                                "META-INF/vault/settings.xml",
                                "META-INF/vault/properties.xml",
                                "META-INF/vault/config.xml",
                                "META-INF/vault/filter.xml",
                                "META-INF/vault/filter-plugin-generated.xml",
                                "jcr_root/content/asd/.content.xml",
                                "jcr_root/content/asd/resources.xml");
            verifyContentPackage(new File(outputDirectory, "asd/sample/asd.retail.apps/0.0.1/asd.retail.apps-0.0.1-cp2fm-converted.zip"),
                                "META-INF/vault/settings.xml",
                                "META-INF/vault/properties.xml",
                                "META-INF/vault/config.xml",
                                "META-INF/vault/filter.xml",
                                "META-INF/vault/filter-plugin-generated.xml");
            verifyContentPackage(new File(outputDirectory, "asd/sample/asd.retail.all/0.0.1/asd.retail.all-0.0.1-cp2fm-converted.zip"),
                                "META-INF/vault/settings.xml",
                                "META-INF/vault/properties.xml",
                                "META-INF/vault/config.xml",
                                "META-INF/vault/filter.xml");
        } finally {
            deleteDirTree(outputDirectory);
        }
    }

    @Test
    public void convertContentPackageDropContentTypePackagePolicy() throws Exception {
        URL packageUrl = getClass().getResource("test-content-package.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());

        try {

            converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                    .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                    .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                    .setContentTypePackagePolicy(PackagePolicy.DROP)
                    .convert(packageFile);

            verifyFeatureFile(outputDirectory,
                            "asd.retail.all.json",
                            "asd.sample:asd.retail.all:slingosgifeature:0.0.1",
                            Collections.singletonList("org.apache.felix:org.apache.felix.framework:6.0.1"),
                            Collections.singletonList("org.apache.sling.commons.log.LogManager.factory.config~asd-retail"),
                            Arrays.asList("asd.sample:asd.retail.apps:zip:cp2fm-converted:0.0.1", "asd:Asd.Retail.config:zip:cp2fm-converted:0.0.1"));
            verifyFeatureFile(outputDirectory,
                            "asd.retail.all-author.json",
                            "asd.sample:asd.retail.all:slingosgifeature:author:0.0.1",
                            Collections.singletonList("org.apache.sling:org.apache.sling.api:2.20.0"),
                            Collections.emptyList(),
                            Collections.emptyList());
            verifyFeatureFile(outputDirectory,
                            "asd.retail.all-publish.json",
                            "asd.sample:asd.retail.all:slingosgifeature:publish:0.0.1",
                            Collections.singletonList("org.apache.sling:org.apache.sling.models.api:1.3.8"),
                            Collections.singletonList("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended~asd-retail"),
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
                                "jcr_root/apps/.content.xml");
            verifyContentPackage(new File(outputDirectory, "asd/sample/asd.retail.apps/0.0.1/asd.retail.apps-0.0.1-cp2fm-converted.zip"),
                                "META-INF/vault/settings.xml",
                                "META-INF/vault/properties.xml",
                                "META-INF/vault/config.xml",
                                "META-INF/vault/filter.xml",
                                "META-INF/vault/filter-plugin-generated.xml");
            // in contrast to previous test when dropping content packages the cases below would be filtered out and files wouldn'T be in cache
            assertFalse(new File(outputDirectory, "asd/sample/Asd.Retail.ui.content/0.0.1/Asd.Retail.ui.content-0.0.1-cp2fm-converted.zip").exists());
            assertFalse(new File(outputDirectory, "asd/sample/asd.retail.all/0.0.1/asd.retail.all-0.0.1-cp2fm-converted.zip").exists());

        } finally {
            deleteDirTree(outputDirectory);
        }
    }

    @Test
    public void convertContentPackagePutInDedicatedFolderContentTypePackagePolicy() throws Exception {
        URL packageUrl = getClass().getResource("test-content-package.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        File outputDirectoryUnreferencedArtifacts = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + "_unreferenced_" + System.currentTimeMillis());

        try {

            converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                    .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                    .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                    .setContentTypePackagePolicy(PackagePolicy.PUT_IN_DEDICATED_FOLDER)
                    .setUnreferencedArtifactsDeployer(new SimpleFolderArtifactsDeployer(outputDirectoryUnreferencedArtifacts))
                    .convert(packageFile);

            verifyFeatureFile(outputDirectory,
                            "asd.retail.all.json",
                            "asd.sample:asd.retail.all:slingosgifeature:0.0.1",
                            Collections.singletonList("org.apache.felix:org.apache.felix.framework:6.0.1"),
                            Collections.singletonList("org.apache.sling.commons.log.LogManager.factory.config~asd-retail"),
                            Arrays.asList("asd.sample:asd.retail.apps:zip:cp2fm-converted:0.0.1",
                                    "asd:Asd.Retail.config:zip:cp2fm-converted:0.0.1"));
            verifyFeatureFile(outputDirectory,
                            "asd.retail.all-author.json",
                            "asd.sample:asd.retail.all:slingosgifeature:author:0.0.1",
                            Collections.singletonList("org.apache.sling:org.apache.sling.api:2.20.0"),
                            Collections.emptyList(),
                            Collections.emptyList());
            verifyFeatureFile(outputDirectory,
                            "asd.retail.all-publish.json",
                            "asd.sample:asd.retail.all:slingosgifeature:publish:0.0.1",
                            Collections.singletonList("org.apache.sling:org.apache.sling.models.api:1.3.8"),
                            Collections.singletonList("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended~asd-retail"),
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
                    "jcr_root/apps/.content.xml");
            verifyContentPackage(new File(outputDirectoryUnreferencedArtifacts, "Asd.Retail.ui.content-0.0.1-cp2fm-converted.zip"),
                    "META-INF/vault/settings.xml",
                    "META-INF/vault/properties.xml",
                    "META-INF/vault/config.xml",
                    "META-INF/vault/filter.xml",
                    "META-INF/vault/filter-plugin-generated.xml",
                    "jcr_root/content/asd/.content.xml",
                    "jcr_root/content/asd/resources.xml");
            verifyContentPackage(new File(outputDirectory, "asd/sample/asd.retail.apps/0.0.1/asd.retail.apps-0.0.1-cp2fm-converted.zip"),
                    "META-INF/vault/settings.xml",
                    "META-INF/vault/properties.xml",
                    "META-INF/vault/config.xml",
                    "META-INF/vault/filter.xml",
                    "META-INF/vault/filter-plugin-generated.xml");
            verifyContentPackage(new File(outputDirectoryUnreferencedArtifacts, "asd.retail.all-0.0.1-cp2fm-converted.zip"),
                    "META-INF/vault/settings.xml",
                    "META-INF/vault/properties.xml",
                    "META-INF/vault/config.xml",
                    "META-INF/vault/filter.xml");

        } finally {
            deleteDirTree(outputDirectory);
            deleteDirTree(outputDirectoryUnreferencedArtifacts);
        }
    }

    @Test
    public void convertContentPackageRemoveInstallHooks() throws Exception {
        URL packageUrl = getClass().getResource("test-with-install-hooks.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());

        try {
            converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                    .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                    .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                    .setRemoveInstallHooks(true)
                    .convert(packageFile);

            File expectedPackage = new File(outputDirectory, "my_packages/tmp/0.0.0/tmp-0.0.0-cp2fm-converted.zip");
            verifyContentPackageEntryNames(expectedPackage,
                                "META-INF/vault/config.xml",
                                "META-INF/vault/definition/.content.xml",
                                "META-INF/vault/filter.xml",
                                "META-INF/vault/properties.xml",
                                "jcr_root/.content.xml",
                                "jcr_root/testroot/.content.xml");
            verifyPropertiesXmlEntry(expectedPackage, "!installhook.test1.class", "!installhook.test2.class");
        } finally {
            deleteDirTree(outputDirectory);
        }
    }

    @Test
    public void convertContentPackageKeepInstallHooks() throws Exception {
        URL packageUrl = getClass().getResource("test-with-install-hooks.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());

        try {
            converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                    .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                    .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                    .convert(packageFile);

            File expectedPackage = new File(outputDirectory, "my_packages/tmp/0.0.0/tmp-0.0.0-cp2fm-converted.zip");
            verifyContentPackageEntryNames(expectedPackage,
                    "META-INF/vault/config.xml",
                    "META-INF/vault/definition/.content.xml",
                    "META-INF/vault/filter.xml",
                    "META-INF/vault/hooks/vault-hook-example-3.0.0.jar",
                    "META-INF/vault/properties.xml",
                    "jcr_root/.content.xml",
                    "jcr_root/testroot/.content.xml");
            verifyPropertiesXmlEntry(expectedPackage, "installhook.test1.class", "installhook.test2.class");
        } finally {
            deleteDirTree(outputDirectory);
        }
    }

    @Test
    public void testConvertContentPackageWithAPIRegion() throws Exception {
        URL cp = getClass().getResource("test_c-1.0.zip");
        File cpFile = new File(cp.getFile());
        File outDir = Files.createTempDirectory(getClass().getSimpleName()).toFile();

        try {
            DefaultFeaturesManager fm = new DefaultFeaturesManager(true, 5, outDir, null, null, null, new DefaultAclManager());
            fm.setAPIRegions(Arrays.asList("global", "foo.bar"));
            converter.setFeaturesManager(fm)
                     .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outDir))
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

    @Test
    public void testContentPackageWithPrivileges() throws Exception {
        URL packageUrl = getClass().getResource("test-with-privilege.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        AclManager aclManager = spy(new DefaultAclManager());

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        try {
            DefaultEntryHandlersManager handlersManager = new DefaultEntryHandlersManager();
            ContentPackage2FeatureModelConverter converter = new ContentPackage2FeatureModelConverter()
                    .setEntryHandlersManager(handlersManager)
                    .setAclManager(aclManager);

            converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, aclManager))
                    .setBundlesDeployer(new SimpleFolderArtifactsDeployer(outputDirectory))
                    .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                    .convert(packageFile);
        } finally {
            verify(aclManager, times(1)).addPrivilegeDefinitions(any(PrivilegeDefinitions.class));
            deleteDirTree(outputDirectory);
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

    private void verifyContentPackageEntryNames(File contentPackage, String...expectedEntryNames) throws Exception {
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

    private void verifyContentPackage(File contentPackage, String...expectedEntries) throws Exception {
        verifyContentPackage(contentPackage, Collections.emptySet(), expectedEntries);
    }
    
    private void verifyContentPackage(File contentPackage, @NotNull Set<String> notExpected, String...expectedEntries) throws Exception {
        try (ZipFile zipFile = new ZipFile(contentPackage)) {
            for (String expectedEntry : expectedEntries) {
                assertNotNull("Expected entry not found: " + expectedEntry + " in file " + contentPackage, zipFile.getEntry(expectedEntry));
            }
            for (String notExpectedEntry : notExpected) {
                assertNull("Not expected entry found: " + notExpectedEntry + " in file " + contentPackage, zipFile.getEntry(notExpectedEntry));
            }
        }
    }

    private void verifyPropertiesXmlEntry(File contentPackage, String... expectedPropertyKeys) throws InvalidPropertiesFormatException, IOException {
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

    @Test(expected = IllegalArgumentException.class)
    public void verifyFilteringOutUndesiredPackages() throws Exception {
        RegexBasedResourceFilter resourceFilter = new RegexBasedResourceFilter();
        resourceFilter.addFilteringPattern(".*\\/install(?!(\\.runMode1\\/|\\.runMode2\\/|\\/))(.*)(?=\\.zip$).*");
        converter.setResourceFilter(resourceFilter);

        URL packageUrl = getClass().getResource("test-content-package-unacceptable.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());

        try {

            converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                    .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                    .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                    .convert(packageFile);
        } finally {
            deleteDirTree(outputDirectory);
        }
    }
    
    /** app package containing another app package must lead to an explicit dependency from  
     *  embedded to embedding package to reflect the implicit installation order via osgi Installer
    **/
    
    @Test
    public void verifyEmbeddedDependency() throws Exception {
        URL packageUrl = getClass().getResource("embedded.package.test-0.0.1.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());

        try {

            converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                    .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                    .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                    .convert(packageFile);
            
            File contentPackage = new File(outputDirectory, "asd/sample/embedded.test.app/0.0.0/embedded.test.app-0.0.0-cp2fm-converted.zip");
            VaultPackage vaultPackage = new PackageManagerImpl().open(contentPackage);
            String dependencies = vaultPackage.getProperties().getProperty(PackageProperties.NAME_DEPENDENCIES);
            org.apache.jackrabbit.vault.packaging.Dependency dep = org.apache.jackrabbit.vault.packaging.Dependency.fromString(dependencies);
            PackageId targetId = PackageId.fromString("asd/sample:embedded.package.test:0.0.1-cp2fm-converted");
            assertTrue(dep.matches(targetId));
        } finally {
            deleteDirTree(outputDirectory);
        }
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
        try {
            URL packageUrl = getClass().getResource("test-content-package.zip");
            File packageFile = FileUtils.toFile(packageUrl);
    
            converter.setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                     .setFeaturesManager(new DefaultFeaturesManager(DefaultFeaturesManager.ConfigurationHandling.STRICT, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                     .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                     .convert(packageFile);
    
            String pid = "this.is.just.a.pid";
            converter.getFeaturesManager().addConfiguration(runmodeA, pid, "/a", new Hashtable<String, Object>());
            converter.getFeaturesManager().addConfiguration(runmodeB, pid, "/b", new Hashtable<String, Object>());
    
        } finally {
            deleteDirTree(outputDirectory);
        }
    }

    @Test
    public void testSameConfigurationPidTwoDifferentPaths() throws Exception {
        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        try {
            URL packageUrl = getClass().getResource("test-content-package.zip");
            File packageFile = FileUtils.toFile(packageUrl);
    
            converter.setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                     .setFeaturesManager(new DefaultFeaturesManager(false, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                     .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                     .convert(packageFile);
    
            String pid = "this.is.just.a.pid";
            converter.getFeaturesManager().addConfiguration(null, pid, "/apps/a/config/pid.json", new Hashtable<String, Object>(){{put("foo", "a");}});
            converter.getFeaturesManager().addConfiguration(null, pid, "/apps/b/config/pid.json", new Hashtable<String, Object>(){{put("foo", "b");}});
    
            Configuration c = converter.getFeaturesManager().getTargetFeature().getConfigurations().getConfiguration(pid);
            assertNotNull(c);
            assertEquals("a", c.getConfigurationProperties().get("foo"));
        } finally {
            deleteDirTree(outputDirectory);
        }
    }

    @Test
    public void testSameConfigurationPidThreeDifferentPaths() throws Exception {
        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        try {
            URL packageUrl = getClass().getResource("test-content-package.zip");
            File packageFile = FileUtils.toFile(packageUrl);
    
            converter.setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                     .setFeaturesManager(new DefaultFeaturesManager(false, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                     .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                     .convert(packageFile);
    
            String pid = "this.is.just.a.pid";
            converter.getFeaturesManager().addConfiguration(null, pid, "/apps/b/config/pid.json", new Hashtable<String, Object>(){{put("foo", "b");}});
            converter.getFeaturesManager().addConfiguration(null, pid, "/apps/a/config/pid.json", new Hashtable<String, Object>(){{put("foo", "a");}});
            converter.getFeaturesManager().addConfiguration(null, pid, "/apps/c/config/pid.json", new Hashtable<String, Object>(){{put("foo", "c");}});
    
            Configuration c = converter.getFeaturesManager().getTargetFeature().getConfigurations().getConfiguration(pid);
            assertNotNull(c);
            assertEquals("a", c.getConfigurationProperties().get("foo"));
        } finally {
            deleteDirTree(outputDirectory);
        }
    }

    @Test
    public void testAPIRegionsWithRunmodes() throws Exception {
        URL cp = getClass().getResource("test-content-package.zip");
        File cpFile = new File(cp.getFile());
        File outDir = Files.createTempDirectory(getClass().getSimpleName()).toFile();

        try {
            converter.setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outDir))
            .setFeaturesManager(new DefaultFeaturesManager(false, 5, outDir, null, null, null, new DefaultAclManager())
                    .setAPIRegions(Collections.singletonList("a.b.c")))
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

        try {

            String overrideId = "${project.groupId}:${project.artifactId}:slingosgifeature:asd.test.all-1.0.0:${project.version}";
            converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, overrideId, null, null, new DefaultAclManager()))
                    .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                    .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                    .convert(packageFile);

            verifyFeatureFile(outputDirectory,
                            "asd.retail.all.json",
                            "${project.groupId}:${project.artifactId}:slingosgifeature:asd.test.all-1.0.0:${project.version}",
                            Collections.singletonList("org.apache.felix:org.apache.felix.framework:6.0.1"),
                            Collections.singletonList("org.apache.sling.commons.log.LogManager.factory.config~asd-retail"),
                            Arrays.asList("asd.sample:asd.retail.apps:zip:cp2fm-converted:0.0.1",
                                            "asd.sample:Asd.Retail.ui.content:zip:cp2fm-converted:0.0.1",
                                            "asd:Asd.Retail.config:zip:cp2fm-converted:0.0.1",
                                            "asd.sample:asd.retail.all:zip:cp2fm-converted:0.0.1"));
            verifyFeatureFile(outputDirectory,
                            "asd.retail.all-author.json",
                            "${project.groupId}:${project.artifactId}:slingosgifeature:asd.test.all-1.0.0-author:${project.version}",
                            Collections.singletonList("org.apache.sling:org.apache.sling.api:2.20.0"),
                            Collections.emptyList(),
                            Collections.emptyList());
            verifyFeatureFile(outputDirectory,
                            "asd.retail.all-publish.json",
                            "${project.groupId}:${project.artifactId}:slingosgifeature:asd.test.all-1.0.0-publish:${project.version}",
                            Collections.singletonList("org.apache.sling:org.apache.sling.models.api:1.3.8"),
                            Collections.singletonList("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended~asd-retail"),
                            Collections.emptyList());

            verifyContentPackage(new File(outputDirectory, "asd/sample/asd.retail.all/0.0.1/asd.retail.all-0.0.1-cp2fm-converted.zip"),
                                "META-INF/vault/properties.xml",
                                "META-INF/vault/config.xml",
                                "META-INF/vault/settings.xml",
                                "META-INF/vault/filter.xml");
        } finally {
            deleteDirTree(outputDirectory);
        }
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

        try {

            converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                    .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                    .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                    .convert(contentPackages[0]);

            File featureFile = new File(outputDirectory, "test_a.json");
            try (Reader reader = new FileReader(featureFile)) {
                Feature feature = FeatureJSONReader.read(reader, featureFile.getAbsolutePath());

                Extension repoinitExtension = feature.getExtensions().getByName("repoinit");
                assertNotNull(repoinitExtension);

                String expected = normalize("register nodetypes\n" +
                        "<<===\n" +
                        "<< <'sling'='http://sling.apache.org/jcr/sling/1.0'>\n" +
                        "<< <'nt'='http://www.jcp.org/jcr/nt/1.0'>\n" + 
                        "<< <'rep'='internal'>\n" + 
                        "\n" + 
                        "<< [sling:Folder] > nt:folder\n" +
                        "<<   - * (undefined) multiple\n" +
                        "<<   - * (undefined)\n" +
                        "<<   + * (nt:base) = sling:Folder version\n" + 
                        "\n" +
                        "<< [rep:RepoAccessControllable]\n" +
                        "<<   mixin\n"  +
                        "<<   + rep:repoPolicy (rep:Policy) protected ignore\n" +
                        "===>>\n");
                String actual = repoinitExtension.getText();
                assertEquals(expected, actual);
            }
        } finally {
            deleteDirTree(outputDirectory);
        }
    }

    // see SLING-10433
    @Test
    public void filteredOutContentPackagesAreExcludedDependencies() throws Exception {
        File[] contentPackages = load("test_dep_a-1.0.zip", "test_dep_b-1.0.zip");

        // input: c <- a <- b
        // expected output: c <- a

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        try {
            converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                    .setContentTypePackagePolicy(PackagePolicy.DROP)
                    .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                    .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                    .convert(contentPackages);

            Feature a = getFeature(outputDirectory, "test_a.json");
            assertNull(a.getExtensions().getByName("content-packages"));

            Feature b = getFeature(outputDirectory, "test_b.json");
            assertNotNull(b.getExtensions().getByName("content-packages"));
            Artifacts artifacts = b.getExtensions().getByName("content-packages").getArtifacts();
            assertFalse(artifacts.isEmpty());
            assertEquals("my_packages:test_b:zip:cp2fm-converted:1.0", artifacts.iterator().next().getId().toString());

            File contentPackage = new File(outputDirectory, "my_packages/test_b/1.0/test_b-1.0-cp2fm-converted.zip");
            VaultPackage vaultPackage = new PackageManagerImpl().open(contentPackage);
            String dependencies = vaultPackage.getProperties().getProperty(PackageProperties.NAME_DEPENDENCIES);
            assertEquals("my_packages:test_c", dependencies);
        }
        finally {
            deleteDirTree(outputDirectory);
        }
    }

    // see SLING-8649
    @Test
    public void filteredOutSubContentPackagesAreExcludedDependencies() throws Exception {
        File[] contentPackages = load("test_dep_sub.zip");

        // input: c <- a <- b
        // expected output: c <- a

        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        try {
            converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                    .setContentTypePackagePolicy(PackagePolicy.DROP)
                    .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                    .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                    .convert(contentPackages);

            Feature sub = getFeature(outputDirectory, "test_sub.json");

            assertNotNull(sub.getExtensions().getByName("content-packages"));
            Artifacts artifacts = sub.getExtensions().getByName("content-packages").getArtifacts();
            assertEquals(2, artifacts.size());
            assertTrue(artifacts.containsExact(ArtifactId.fromMvnId("my_packages:test_b:zip:cp2fm-converted:1.0")));

            File contentPackage = new File(outputDirectory, "my_packages/test_b/1.0/test_b-1.0-cp2fm-converted.zip");
            VaultPackage vaultPackage = new PackageManagerImpl().open(contentPackage);
            String dependencies = vaultPackage.getProperties().getProperty(PackageProperties.NAME_DEPENDENCIES);
            assertEquals("my_packages:test_sub:[1.0-cp2fm-converted,1.0-cp2fm-converted]", dependencies);
        }
        finally {
            deleteDirTree(outputDirectory);
        }
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

    @Test
    public void testConvertPackageWithUsersGroupsAndServiceUsers() throws Exception {
        URL packageUrl = getClass().getResource("demo-cp.zip");
        File packageFile = FileUtils.toFile(packageUrl);
        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        try {
            converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                    .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                    .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                    .convert(packageFile);

            File converted = new File(outputDirectory, "my_packages/demo-cp/0.0.0/demo-cp-0.0.0-cp2fm-converted.zip");
            Set<String> notExpected = new HashSet<>();
            notExpected.add("jcr_root/apps/demo-cp/.content.xml");
            notExpected.add("jcr_root/home/users/demo-cp/_rep_policy.xml");
            notExpected.add("jcr_root/home/groups/demo-cp/_rep_policy.xml");
            notExpected.add("jcr_root/home/users/system/.content.xml");
            notExpected.add("jcr_root/home/users/system/_cq_services/.content.xml");
            notExpected.add("jcr_root/home/users/system/_cq_services/demo-cp/.content.xml");
            notExpected.add("jcr_root/home/users/system/_cq_services/demo-cp/qStDu7IQBLa95gURmer1/.content.xml");
            notExpected.add("jcr_root/home/users/system/_cq_services/demo-cp/qStDu7IQBLa95gURmer1/_rep_principalPolicy.xml");
            verifyContentPackage(converted, 
                    notExpected,
                    "META-INF/vault/properties.xml",
                    "META-INF/vault/config.xml",
                    "META-INF/vault/filter.xml",
                    "jcr_root/.content.xml",
                    "jcr_root/demo-cp/.content.xml",
                    "jcr_root/demo-cp/_rep_policy.xml",
                    "jcr_root/apps/.content.xml",
                    "jcr_root/home/.content.xml",
                    "jcr_root/home/users/demo-cp/.content.xml",
                    "jcr_root/home/users/demo-cp/XPXhA_RKMFRKNO8ViIhn/.content.xml",
                    "jcr_root/home/users/demo-cp/XPXhA_RKMFRKNO8ViIhn/_rep_policy.xml",
                    "jcr_root/home/groups/.content.xml",
                    "jcr_root/home/groups/demo-cp/.content.xml",
                    "jcr_root/home/groups/demo-cp/EsYrXeBdSRkna2kqbxjl/.content.xml",
                    "jcr_root/home/groups/demo-cp/EsYrXeBdSRkna2kqbxjl/_rep_policy.xml"
                    );

        } finally {
            deleteDirTree(outputDirectory);
        }
    }
    
}
