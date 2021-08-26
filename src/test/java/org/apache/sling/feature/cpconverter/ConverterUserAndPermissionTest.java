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
import org.apache.jackrabbit.vault.fs.api.FilterSet;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
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
import java.io.IOException;
import java.io.InputStream;
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
import static org.junit.Assert.fail;

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

    public ConverterUserAndPermissionTest(@NotNull String systemUserRelPath, @Nullable String enforcePrincipalBasedSupportedPath, @NotNull String name) throws Exception {
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
    }

    @Test
    public void testConvertPackageWithImportModes() throws Exception {
        URL packageUrl = getClass().getResource("demo-cp-with-importmode.zip");
        File packageFile = FileUtils.toFile(packageUrl);
        converter.convert(packageFile);

        File converted = new File(outputDirectory, "my_packages/demo-cp/0.0.0/demo-cp-0.0.0-cp2fm-converted.zip");

        Set<String> notExpected = new HashSet<>(COMMON_NOT_EXPECTED_PATHS);
        notExpected.add("jcr_root/apps/demo-cp/.content.xml");

        Set<String> expected = new HashSet<>(COMMON_EXPECTED_PATHS);
        expected.add("jcr_root/apps/.content.xml");

        verifyContentPackage(converted, notExpected, expected);
        assertExpectedPolicies(converted);
        WorkspaceFilter filter = verifyWorkspaceFilter(converted, false);
        verifyRepoInit();

        assertEquals(ImportMode.MERGE, filter.getImportMode("/demo-cp"));
        assertEquals(ImportMode.UPDATE, filter.getImportMode("/home/groups/demo-cp"));
        assertEquals(ImportMode.REPLACE, filter.getImportMode("/home/users/demo-cp"));
    }

    @Test
    public void testConvertPackageWithPropertyFilterSetEntry() throws Exception {
        URL packageUrl = getClass().getResource("demo-cp-with-importmode.zip");
        File packageFile = FileUtils.toFile(packageUrl);
        converter.convert(packageFile);

        File converted = new File(outputDirectory, "my_packages/demo-cp/0.0.0/demo-cp-0.0.0-cp2fm-converted.zip");
        WorkspaceFilter filter = verifyWorkspaceFilter(converted, false);
        
        // verify that the 'matchProperties=true' exclude entry with /home/groups/demo-cp was properly converted
        // and is still present in the adjusted filter.
        List<PathFilterSet> propertyFilters = filter.getPropertyFilterSets();
        assertNotNull(propertyFilters);
        assertEquals(3, propertyFilters.size());
        // there is only a single exclude entry matching properties with /home/groups/demo-cp
        // the other filters don't have any property-entries
        for (PathFilterSet filterSet : propertyFilters) {
            List<FilterSet.Entry<PathFilter>> entries = filterSet.getEntries();
            int expectedSize = ("/home/groups/demo-cp".equals(filterSet.getRoot())) ? 1 : 0;
            assertEquals(expectedSize, entries.size());
        }
        
        // verify that the node-filtersets got updated with additional excludes for paths filtered out
        List<PathFilterSet> nodeFilters = filter.getFilterSets();
        assertEquals(3, nodeFilters.size());
        for (PathFilterSet filterSet : nodeFilters) {
            List<FilterSet.Entry<PathFilter>> entries = filterSet.getEntries();
            switch (filterSet.getRoot()) {
                case "/demo-cp":
                    assertEquals(0, entries.size());
                    break;
                case "/home/groups/demo-cp":
                    assertEquals(1, entries.size());
                    assertFalse(filterSet.contains(filterSet.getRoot() + "/rep:policy"));
                    assertEquals(0, entries.stream().filter(FilterSet.Entry::isInclude).count());
                    break;
                case "/home/users/demo-cp":
                    assertEquals(3, entries.size());
                    assertFalse(filterSet.contains(filterSet.getRoot() + "/rep:policy"));
                    assertEquals(1, entries.stream().filter(FilterSet.Entry::isInclude).count());
                    break;
                default:
                    fail("unexpected path "+ filterSet.getRoot());
            }
        }
    }

    @Test
    public void testConvertPackageWithSingleUserRoot() throws Exception {
        URL packageUrl = getClass().getResource("demo-cp-single-user-root.zip");
        File packageFile = FileUtils.toFile(packageUrl);
        converter.convert(packageFile);

        File converted = new File(outputDirectory, "my_packages/demo-cp/0.0.0/demo-cp-0.0.0-cp2fm-converted.zip");

        Set<String> notExpected = new HashSet<>(COMMON_NOT_EXPECTED_PATHS);
        notExpected.add("jcr_root/apps/demo-cp/.content.xml");

        Set<String> expected = new HashSet<>(COMMON_EXPECTED_PATHS);
        expected.add("jcr_root/apps/.content.xml");

        verifyContentPackage(converted, notExpected, expected);
        assertExpectedPolicies(converted);
        verifyRepoInit();

        WorkspaceFilter filter = getWorkspaceFilter(converted);
        assertEquals(ImportMode.MERGE, filter.getImportMode("/home"));
        
        List<PathFilterSet> filterSets = filter.getFilterSets();
        assertEquals(2, filterSets.size());
        assertNotNull(filter.getCoveringFilterSet("/demo-cp"));
        PathFilterSet homeFilter = filter.getCoveringFilterSet("/home");
        assertNotNull(homeFilter);
        // 11 excludes must have been added for paths that got moved to repo-init
        List<FilterSet.Entry<PathFilter>> entries = homeFilter.getEntries();
        assertEquals(11, entries.size());
        assertFalse(entries.stream().anyMatch(FilterSet.Entry::isInclude));
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

    private static WorkspaceFilter verifyWorkspaceFilter(@NotNull File contentPackage, boolean isContentOnly) throws Exception {
        DefaultWorkspaceFilter filter = getWorkspaceFilter(contentPackage);
        if (!isContentOnly) {
            assertFalse(filter.contains("/apps/demo-cp"));
        }
        assertFalse(filter.covers("/home/users/system"));
        assertFalse(filter.covers("/home/users/system/demo-cp"));
        assertFalse(filter.covers("/home/users/system/cq:services/demo-cp"));

        assertTrue(filter.covers("/demo-cp"));
        assertTrue(filter.covers("/home/users/demo-cp"));
        assertTrue(filter.covers("/home/groups/demo-cp"));

        // verify that explicit excludes have been added for filter roots that contain mixed content
        PathFilterSet filterSet = filter.getCoveringFilterSet("/home/groups/demo-cp");
        assertNotNull(filterSet);
        assertEquals(1, filterSet.getEntries().stream().filter(pathFilterEntry -> !pathFilterEntry.isInclude() &&
                pathFilterEntry.getFilter().matches("/home/groups/demo-cp/rep:policy")).count());

        filterSet = filter.getCoveringFilterSet("/home/users/demo-cp");
        assertNotNull(filterSet);
        assertEquals(1, filterSet.getEntries().stream().filter(pathFilterEntry -> !pathFilterEntry.isInclude() &&
                pathFilterEntry.getFilter().matches("/home/users/demo-cp/rep:policy")).count());
        return filter;
    }
    
    private static DefaultWorkspaceFilter getWorkspaceFilter(@NotNull File contentPackage) throws Exception {
        DefaultWorkspaceFilter filter;
        try (ZipFile zipFile = new ZipFile(contentPackage)) {
            filter = new DefaultWorkspaceFilter();
            ZipEntry entry = zipFile.getEntry("META-INF/vault/filter.xml");
            assertNotNull(entry);
            filter.load(zipFile.getInputStream(entry));
        }
        return filter;
    }
}
