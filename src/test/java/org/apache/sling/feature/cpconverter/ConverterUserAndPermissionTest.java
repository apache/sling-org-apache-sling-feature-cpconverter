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
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.PackagePolicy;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.DefaultAclManager;
import org.apache.sling.feature.cpconverter.artifacts.LocalMavenRepositoryArtifactsDeployer;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.handlers.DefaultEntryHandlersManager;
import org.apache.sling.feature.cpconverter.handlers.EntryHandlersManager;
import org.apache.sling.feature.cpconverter.shared.ConverterConstants;
import org.apache.sling.feature.cpconverter.vltpkg.DefaultPackagesEventsEmitter;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.repoinit.parser.impl.RepoInitParserService;
import org.apache.sling.repoinit.parser.operations.CreatePath;
import org.apache.sling.repoinit.parser.operations.CreateServiceUser;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.apache.sling.repoinit.parser.operations.RegisterNodetypes;
import org.apache.sling.repoinit.parser.operations.SetAclPaths;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipalBased;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipals;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
        COMMON_NOT_EXPECTED_PATHS.add("jcr_root/home/users/system/demo-cp/.content.xml");
        COMMON_NOT_EXPECTED_PATHS.add(" jcr_root/home/users/system/demo-cp/CsqdlsB5ppPzRE4j13cE/.content.xml");
        COMMON_NOT_EXPECTED_PATHS.add(" jcr_root/home/users/system/demo-cp/CsqdlsB5ppPzRE4j13cE/_rep_policy.xml");
        COMMON_NOT_EXPECTED_PATHS.add(" jcr_root/home/users/system/demo-cp/vy3lKscRYhnu-aui8W51/.content.xml");
        COMMON_NOT_EXPECTED_PATHS.add("jcr_root/home/users/system/_cq_services/.content.xml");
        COMMON_NOT_EXPECTED_PATHS.add("jcr_root/home/users/system/_cq_services/demo-cp/.content.xml");
        COMMON_NOT_EXPECTED_PATHS.add("jcr_root/home/users/system/_cq_services/demo-cp/qStDu7IQBLa95gURmer1/.content.xml");
        COMMON_NOT_EXPECTED_PATHS.add("jcr_root/home/users/system/_cq_services/demo-cp/qStDu7IQBLa95gURmer1/_rep_principalPolicy.xml");
    }

    private ContentPackage2FeatureModelConverter converter;
    
    private final EntryHandlersManager handlersManager;
    private final AclManager aclManager;
    private final boolean enforcePrincipalBased;
    private final String withRelPath;

    private File outputDirectory;
    private FeaturesManager featuresManager;

    @Parameterized.Parameters(name = "name={2}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(
                new Object[] {ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT, null, "Default system user rel-path"},
                new Object[] {ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT, "/home/users/system/cq:services", "Default system user rel-path with enforce-principal-based ac-setup"});
    }

    public ConverterUserAndPermissionTest(@NotNull String systemUserRelPath, @Nullable String enforcePrincipalBasedSupportedPath, @NotNull String name) {
        this.aclManager = new DefaultAclManager(enforcePrincipalBasedSupportedPath, systemUserRelPath);
        this.handlersManager = new DefaultEntryHandlersManager(Collections.emptyMap(), false, 
                ContentPackage2FeatureModelConverter.SlingInitialContentPolicy.KEEP, systemUserRelPath);
        this.enforcePrincipalBased = (enforcePrincipalBasedSupportedPath != null);
        this.withRelPath = (enforcePrincipalBased) ? enforcePrincipalBasedSupportedPath.substring(enforcePrincipalBasedSupportedPath.indexOf(systemUserRelPath)) : systemUserRelPath;
    }

    @Before
    public void setUp() throws IOException {
        converter = new ContentPackage2FeatureModelConverter()
                .setEntryHandlersManager(handlersManager)
                .setAclManager(aclManager);

        outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        featuresManager = new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, aclManager);
        
        converter.setFeaturesManager(featuresManager)
                .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory));
    }

    @After
    public void tearDown() throws IOException {
        deleteDirTree(outputDirectory);
        converter.close();
    }

    @Test
    public void testConvertPackageWithUsersGroupsAndServiceUsers() throws Exception {
        URL packageUrl = getClass().getResource("demo-cp.zip");
        File packageFile = FileUtils.toFile(packageUrl);
        converter.convert(packageFile);

        File converted = new File(outputDirectory, "my_packages/demo-cp/0.0.0/demo-cp-0.0.0-cp2fm-converted.zip");

        Set<String> notExpected = new HashSet<>(COMMON_NOT_EXPECTED_PATHS);
        notExpected.add("jcr_root/apps/demo-cp/.content.xml");

        Set<String> expected = new HashSet<>(COMMON_EXPECTED_PATHS);
        expected.add("jcr_root/apps/.content.xml");

        verifyContentPackage(converted, notExpected, expected);
        assertExpectedPolicies(converted);
        verifyWorkspaceFilter(converted, false);
        verifyRepoInit();
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

        converter.convert(packageFile);

        File converted = new File(outputDirectory, "my_packages/demo-cp/0.0.0/demo-cp-0.0.0-cp2fm-converted.zip");

        Set<String> notExpected = new HashSet<>(COMMON_NOT_EXPECTED_PATHS);
        notExpected.add("jcr_root/apps/demo-cp/.content.xml");
        Set<String> expected = new HashSet<>(COMMON_EXPECTED_PATHS);
        expected.add("jcr_root/apps/.content.xml");

        verifyContentPackage(converted, notExpected, expected);
        assertExpectedPolicies(converted);
        verifyWorkspaceFilter(converted, false);
        verifyRepoInit();
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
        File unrefOutputDir = new File(outputDirectory, "unref");

        converter.setUnreferencedArtifactsDeployer(new LocalMavenRepositoryArtifactsDeployer(unrefOutputDir))
                .setContentTypePackagePolicy(PackagePolicy.PUT_IN_DEDICATED_FOLDER)
                .convert(packageFile);

        File converted = new File(unrefOutputDir, "my_packages/demo-cp/0.0.0/demo-cp-0.0.0-cp2fm-converted.zip");
        verifyContentPackage(converted, COMMON_NOT_EXPECTED_PATHS, COMMON_EXPECTED_PATHS);
        assertExpectedPolicies(converted);
        verifyWorkspaceFilter(converted, true);
        verifyRepoInit();
        assertEquals(PackageType.CONTENT, converter.open(converted).getProperties().getPackageType());
        try (FileReader reader = new FileReader(new File(outputDirectory, "content-packages.csv"))){
            assertTrue(IOUtils.readLines(reader).get(2).contains("my_packages:demo-cp,CONTENT"));
        }
    }

    private static void assertExpectedPolicies(@NotNull File converted ) throws IOException {
        assertPolicy(converted, "jcr_root/demo-cp/_rep_policy.xml", "cp-serviceuser-1", "cp-user1", "cp-group1");
        assertPolicy(converted, "jcr_root/home/groups/demo-cp/EsYrXeBdSRkna2kqbxjl/_rep_policy.xml", null, "cp-group1");
        assertPolicy(converted,  "jcr_root/home/users/demo-cp/XPXhA_RKMFRKNO8ViIhn/_rep_policy.xml", null, "cp-user1");
        assertPolicy(converted, "jcr_root/home/groups/demo-cp/_rep_policy.xml", "cp-serviceuser-3");
        assertPolicy(converted, "jcr_root/home/users/demo-cp/_rep_policy.xml", "cp-serviceuser-3");
    }
    
    private static void assertPolicy(@NotNull File contentPackage, @NotNull String path, @Nullable String unExpectedPrincipalName, @NotNull String... expectedPrincipalNames) throws IOException {
        try (ZipFile zipFile = new ZipFile(contentPackage)) {
            ZipEntry entry = zipFile.getEntry(path);
            if (expectedPrincipalNames.length == 0) {
                assertNull(entry);
            } else {
                assertNotNull(entry);
                assertFalse(entry.isDirectory());

                try (InputStream in = zipFile.getInputStream(entry)) {
                    String policy = IOUtils.toString(in, StandardCharsets.UTF_8);
                    for (String principalName : expectedPrincipalNames) {
                        assertTrue(policy.contains("rep:principalName=\"" + principalName + "\""));
                    }
                    if (unExpectedPrincipalName != null) {
                        assertFalse(policy.contains("rep:principalName=\"" + unExpectedPrincipalName + "\""));
                    }
                }
            }
        }
    }
    
    private void verifyRepoInit() throws RepoInitParsingException {
        Feature f = featuresManager.getTargetFeature();
        Extension ext = f.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(ext);
        
        List<Operation> ops = new RepoInitParserService().parse(new StringReader(ext.getText()));
        int size = (enforcePrincipalBased) ? 7 : 8;
        assertEquals(size, ops.size());
        
        assertEquals(1, ops.stream().filter(operation -> operation instanceof RegisterNodetypes).count());
        
        List<Operation> createServiceUsers = ops.stream().filter(op -> op instanceof CreateServiceUser).collect(Collectors.toList());
        assertEquals(3, createServiceUsers.size());
        if (enforcePrincipalBased) {
            assertTrue(createServiceUsers.stream().allMatch(operation -> {
                CreateServiceUser csu = (CreateServiceUser) operation;
                return csu.isForcedPath() && csu.getPath().startsWith(withRelPath);
            }));
        } else {
            assertFalse(createServiceUsers.stream().anyMatch(operation -> ((CreateServiceUser) operation).isForcedPath()));
            assertEquals(2, createServiceUsers.stream().filter(operation -> ((CreateServiceUser) operation).getPath().startsWith(withRelPath+"/demo-cp")).count());
        }
        
        List<Operation> setAcl = ops.stream().filter(op -> op instanceof SetAclPrincipals).collect(Collectors.toList());
        List<Operation> setAclPrincipalBased = ops.stream().filter(op -> op instanceof SetAclPrincipalBased).collect(Collectors.toList());
        if (enforcePrincipalBased) {
            assertTrue(setAcl.isEmpty());
            assertEquals(3, setAclPrincipalBased.size());
        } else {
            assertEquals(2, setAcl.size());
            assertEquals(1, setAclPrincipalBased.size());
        }
        assertTrue(ops.stream().noneMatch(op -> op instanceof SetAclPaths));
        
        List<Operation> createPaths = ops.stream().filter(op -> op instanceof CreatePath).collect(Collectors.toList());
        if (enforcePrincipalBased) {
            assertTrue(createPaths.isEmpty());
        } else {
            assertEquals(1, createPaths.size());
        }
    }

    private static void verifyWorkspaceFilter(@NotNull File contentPackage, boolean isContentOnly) throws IOException {
        try (ZipFile zipFile = new ZipFile(contentPackage)) {
            ZipEntry entry = zipFile.getEntry("META-INF/vault/filter.xml");
            assertNotNull(entry);
            String filter = IOUtils.toString(new InputStreamReader(zipFile.getInputStream(entry)));

            if (!isContentOnly) {
                assertFalse(filter.contains("/apps/demo-cp"));
            }
            assertFalse(filter.contains("/home/users/system"));
            assertFalse(filter.contains("/home/users/system/demo-cp"));
            assertFalse(filter.contains("/home/users/system/cq:services/demo-cp"));
            
            assertTrue(filter.contains("<filter root=\"/demo-cp\"/>"));
            assertTrue(filter.contains("<filter root=\"/home/users/demo-cp\">"));
            assertTrue(filter.contains("<filter root=\"/home/groups/demo-cp\">"));
            
            // verify that explicit excludes have been added for filter roots that contain mixed content
            assertTrue(filter.contains("<exclude pattern=\"/home/groups/demo-cp/_rep_policy.xml\"/>"));
            assertTrue(filter.contains("<exclude pattern=\"/home/users/demo-cp/_rep_policy.xml\"/>"));
        }
    }
}
