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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.PackagePolicy;
import org.apache.sling.feature.cpconverter.accesscontrol.DefaultAclManager;
import org.apache.sling.feature.cpconverter.artifacts.LocalMavenRepositoryArtifactsDeployer;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.handlers.DefaultEntryHandlersManager;
import org.apache.sling.feature.cpconverter.handlers.EntryHandlersManager;
import org.apache.sling.feature.cpconverter.shared.ConverterConstants;
import org.apache.sling.feature.cpconverter.vltpkg.DefaultPackagesEventsEmitter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class ConverterUserAndPermissionTest  extends AbstractConverterTest {

    private static final Set<String> COMMON_EXPECTED_PATHS = new HashSet<>();
    static {
        COMMON_EXPECTED_PATHS.add("META-INF/vault/properties.xml");
        COMMON_EXPECTED_PATHS.add("META-INF/vault/config.xml");
        COMMON_EXPECTED_PATHS.add("META-INF/vault/filter.xml");
        COMMON_EXPECTED_PATHS.add("jcr_root/.content.xml");
        COMMON_EXPECTED_PATHS.add("jcr_root/demo-cp/.content.xml");
        COMMON_EXPECTED_PATHS.add("jcr_root/demo-cp/_rep_policy.xml");
        COMMON_EXPECTED_PATHS.add("jcr_root/home/.content.xml");
        COMMON_EXPECTED_PATHS.add("jcr_root/home/users/demo-cp/.content.xml");
        COMMON_EXPECTED_PATHS.add("jcr_root/home/users/demo-cp/XPXhA_RKMFRKNO8ViIhn/.content.xml");
        COMMON_EXPECTED_PATHS.add("jcr_root/home/users/demo-cp/XPXhA_RKMFRKNO8ViIhn/_rep_policy.xml");
        COMMON_EXPECTED_PATHS.add("jcr_root/home/groups/.content.xml");
        COMMON_EXPECTED_PATHS.add("jcr_root/home/groups/demo-cp/.content.xml");
        COMMON_EXPECTED_PATHS.add("jcr_root/home/groups/demo-cp/EsYrXeBdSRkna2kqbxjl/.content.xml");
        COMMON_EXPECTED_PATHS.add("jcr_root/home/groups/demo-cp/EsYrXeBdSRkna2kqbxjl/_rep_policy.xml");
    }

    private static final Set<String> COMMON_NOT_EXPECTED_PATHS = new HashSet<>();
    static {
        COMMON_NOT_EXPECTED_PATHS.add("jcr_root/home/users/demo-cp/_rep_policy.xml");
        COMMON_NOT_EXPECTED_PATHS.add("jcr_root/home/groups/demo-cp/_rep_policy.xml");
        COMMON_NOT_EXPECTED_PATHS.add("jcr_root/home/users/system/.content.xml");
        COMMON_NOT_EXPECTED_PATHS.add("jcr_root/home/users/system/_cq_services/.content.xml");
        COMMON_NOT_EXPECTED_PATHS.add("jcr_root/home/users/system/_cq_services/demo-cp/.content.xml");
        COMMON_NOT_EXPECTED_PATHS.add("jcr_root/home/users/system/_cq_services/demo-cp/qStDu7IQBLa95gURmer1/.content.xml");
        COMMON_NOT_EXPECTED_PATHS.add("jcr_root/home/users/system/_cq_services/demo-cp/qStDu7IQBLa95gURmer1/_rep_principalPolicy.xml");
    }

    private ContentPackage2FeatureModelConverter converter;
    private final String systemUserRelPath;

    @Parameterized.Parameters(name = "name={1}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(
                new Object[] {ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT, "Default system user rel-path"},
                new Object[] {"system/cq:services", "Modified system user rel-path"});
    }

    public ConverterUserAndPermissionTest(@NotNull String systemUserRelPath, @NotNull String name) {
        this.systemUserRelPath = systemUserRelPath;
    }

    @Before
    public void setUp() {
        EntryHandlersManager handlersManager = new DefaultEntryHandlersManager(Collections.emptyMap(), false, ContentPackage2FeatureModelConverter.SlingInitialContentPolicy.KEEP, systemUserRelPath);
        converter = new ContentPackage2FeatureModelConverter()
                .setEntryHandlersManager(handlersManager)
                .setAclManager(new DefaultAclManager());
    }

    @After
    public void tearDown() throws IOException {
        converter.close();
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
            Set<String> notExpected = new HashSet<>(COMMON_NOT_EXPECTED_PATHS);
            notExpected.add("jcr_root/apps/demo-cp/.content.xml");

            Set<String> expected = new HashSet<>(COMMON_EXPECTED_PATHS);
            expected.add("jcr_root/apps/.content.xml");

            verifyContentPackage(converted, notExpected, expected);
            assertExpectedPolicies(converted);
            assertFilterXml(converted);
        } finally {
            deleteDirTree(outputDirectory);
        }
    }

    /**
     * "demo-cp3.zip" contains the same content as "demo-cp.zip" but with altered order leading to ACE being read before 
     * the corresponding system-user, whose principal is referenced in the ACE.
     *
     * This test would fail if user/group information was not collected during the first pass (i.e. corresponding 
     * handlers listed in {@link org.apache.sling.feature.cpconverter.vltpkg.RecollectorVaultPackageScanner}.
     *
     * @throws Exception
     */
    @Test
    public void testConvertPackageWithUsersGroupsAndServiceUsersRepPolicyFirst() throws Exception {
        URL packageUrl = getClass().getResource("demo-cp3.zip");
        File packageFile = FileUtils.toFile(packageUrl);
        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        try {
            converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                    .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                    .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                    .convert(packageFile);

            File converted = new File(outputDirectory, "my_packages/demo-cp/0.0.0/demo-cp-0.0.0-cp2fm-converted.zip");

            Set<String> notExpected = new HashSet<>(COMMON_NOT_EXPECTED_PATHS);
            notExpected.add("jcr_root/apps/demo-cp/.content.xml");
            Set<String> expected = new HashSet<>(COMMON_EXPECTED_PATHS);
            expected.add("jcr_root/apps/.content.xml");

            verifyContentPackage(converted, notExpected, expected);
            assertExpectedPolicies(converted);
            assertFilterXml(converted);
        } finally {
            deleteDirTree(outputDirectory);
        }
    }

    /**
     * "demo-cp2.zip" contains the same content as "demo-cp.zip" except for the application content below /apps.
     * The content package therefore gets type CONTENT assigned (and not MIXED).
     *
     * @throws Exception
     */
    @Test
    public void testConvertCONTENTPackageWithUsersGroupsAndServiceUsers() throws Exception {
        URL packageUrl = getClass().getResource("demo-cp2.zip");
        File packageFile = FileUtils.toFile(packageUrl);
        File outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        File unrefOutputDir = new File(outputDirectory, "unref");
        try {
            converter.setFeaturesManager(new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, new DefaultAclManager()))
                    .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                    .setUnreferencedArtifactsDeployer(new LocalMavenRepositoryArtifactsDeployer(unrefOutputDir))
                    .setContentTypePackagePolicy(PackagePolicy.PUT_IN_DEDICATED_FOLDER)
                    .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory))
                    .convert(packageFile);

            File converted = new File(unrefOutputDir, "my_packages/demo-cp/0.0.0/demo-cp-0.0.0-cp2fm-converted.zip");
            verifyContentPackage(converted, COMMON_NOT_EXPECTED_PATHS, COMMON_EXPECTED_PATHS);
            assertExpectedPolicies(converted);
            assertFilterXml(converted);
        } finally {
            deleteDirTree(outputDirectory);
        }
    }

    private static void assertExpectedPolicies(@NotNull File converted ) throws IOException {
        assertPolicy(converted, "jcr_root/demo-cp/_rep_policy.xml", "cp-serviceuser-1", "cp-user1", "cp-group1");
        assertPolicy(converted, "jcr_root/home/groups/demo-cp/EsYrXeBdSRkna2kqbxjl/_rep_policy.xml", null, "cp-group1");
        assertPolicy(converted,  "jcr_root/home/users/demo-cp/XPXhA_RKMFRKNO8ViIhn/_rep_policy.xml", null, "cp-user1");
    }

    private static void assertPolicy(@NotNull File contentPackage, @NotNull String path, @Nullable String unExpectedPrincipalName, @NotNull String... expectedPrincipalNames) throws IOException {
        try (ZipFile zipFile = new ZipFile(contentPackage)) {
            ZipEntry entry = zipFile.getEntry(path);
            assertNotNull(entry);
            assertFalse(entry.isDirectory());

            try (InputStream in = zipFile.getInputStream(entry)) {
                String policy = IOUtils.toString(in, StandardCharsets.UTF_8);
                for (String principalName : expectedPrincipalNames) {
                    assertTrue(policy.contains("rep:principalName=\""+principalName+"\""));
                }
                if (unExpectedPrincipalName != null) {
                    assertFalse(policy.contains("rep:principalName=\""+unExpectedPrincipalName+"\""));
                }
            }
        }
    }

    private static void assertFilterXml(@NotNull File contentPackage) throws IOException {
        try (ZipFile zipFile = new ZipFile(contentPackage)) {
            ZipEntry entry = zipFile.getEntry("META-INF/vault/filter.xml");
            assertNotNull(entry);
            assertFalse(entry.isDirectory());

            try (InputStream in = zipFile.getInputStream(entry)) {
                String filterXml = IOUtils.toString(in, StandardCharsets.UTF_8);

                List<String> expected = new ArrayList<>();
                expected.add("/home/users/demo-cp");
                expected.add("/home/groups/demo-cp");
                expected.add("/demo-cp");

                for (String expectedPath : expected) {
                    String p = getFilterPath(expectedPath);
                    assertTrue(p, filterXml.contains(p));
                }

                List<String> notExpected = new ArrayList<>();
                notExpected.add("/apps/demo-cp");
                notExpected.add("/home/users/system/demo-cp");
                notExpected.add("/home/users/system/cq:services/demo-cp");

                for (String unexpectedPath : notExpected) {
                    String p = getFilterPath(unexpectedPath);
                    assertFalse(p, filterXml.contains(p));
                }
            }
        }
    }

    @NotNull
    private static String getFilterPath(@NotNull String path) {
        String p = (path.startsWith("jcr_root")) ? path.substring("jcr_root".length()) : path;
        return "<filter root=\""+p+"\"/>";
    }
}
