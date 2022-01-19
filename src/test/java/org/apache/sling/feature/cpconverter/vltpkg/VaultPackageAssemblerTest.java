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
package org.apache.sling.feature.cpconverter.vltpkg;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class VaultPackageAssemblerTest {

    private File testDirectory;

    private VaultPackageAssembler assembler;

    private final String resourceLocation;

    public VaultPackageAssemblerTest(String resourceLocation) {
        this.resourceLocation = resourceLocation;
    }

    @Before
    public void setUp() throws IOException {
        URL resource = VaultPackageAssemblerTest.class.getResource("../test-content-package.zip");
        File file = FileUtils.toFile(resource);
 
        VaultPackage vaultPackage = new PackageManagerImpl().open(file);

        this.testDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        this.assembler = VaultPackageAssembler.create(testDirectory, vaultPackage, false, false);
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(this.testDirectory);
    }
    
    @Test
    public void packageResource() throws Exception {
        if (resourceLocation != null) {
            assembler.addEntry(resourceLocation, getClass().getResourceAsStream("../handlers" + resourceLocation));
        }
        File contentPackage = assembler.createPackage();

        ZipFile zipFile = new ZipFile(contentPackage);
        ZipEntry resourceEntry;
        if (resourceLocation != null) {
            resourceEntry = zipFile.getEntry(resourceLocation.substring(1));
        } else {
            resourceEntry = zipFile.getEntry("jcr_root");
        }
        assertNotNull(resourceEntry);

        zipFile.close();
    }

    @Test
    public void testCreate() throws Exception {
        // This is just here to force the deletion
        URL resource = VaultPackageAssemblerTest.class.getResource("../test-content-package.zip");
        File file = FileUtils.toFile(resource);
        VaultPackage vaultPackage = new PackageManagerImpl().open(file);

        VaultPackageAssembler assembler = VaultPackageAssembler.create(testDirectory, vaultPackage, false, false);
        PackageId packageId = vaultPackage.getId();
        String fileName = packageId.toString().replaceAll("/", "-").replaceAll(":", "-");
        File storingDirectory = new File(assembler.getTempDir(), fileName + "-deflated");
        assertTrue("Storing Directory for Vault Package does not exist", storingDirectory.exists());
    }

    @Test
    public void testMergeFiltersWithPropertyFilterSet() throws Exception {
        PathFilterSet nodeFilter = new PathFilterSet();
        nodeFilter.setRoot("/test");
        nodeFilter.setImportMode(ImportMode.MERGE);
        nodeFilter.addExclude(new DefaultPathFilter(".*/pattern"));
        nodeFilter.addInclude(new DefaultPathFilter("/test/content/subtree"));

        PathFilterSet propFilter = new PathFilterSet();
        propFilter.setRoot("/test");
        propFilter.addExclude(new DefaultPathFilter(".*/excludedProperty"));

        DefaultWorkspaceFilter toMerge = new DefaultWorkspaceFilter();
        toMerge.add(nodeFilter, propFilter);
        
        List<PathFilterSet> before = new ArrayList<>(assembler.getFilter().getFilterSets());
        List<PathFilterSet> beforeProperties = new ArrayList<>(assembler.getFilter().getPropertyFilterSets());
        
        assembler.mergeFilters(toMerge);
        
        List<PathFilterSet> after = assembler.getFilter().getFilterSets();
        assertEquals(before.size()+1, after.size());
        assertTrue(after.contains(nodeFilter));

        List<PathFilterSet> afterProperties = assembler.getFilter().getPropertyFilterSets();
        assertEquals(beforeProperties.size()+1, afterProperties.size());
        assertTrue(afterProperties.contains(propFilter));
    }
    
    @Parameters
    public static Collection<Object[]> data() throws Exception {
        return Arrays.asList(new Object[][] {
            { null },
            { "/jcr_root/.content.xml" },
            { "/jcr_root/asd/.content.xml" },
            { "/jcr_root/asd/public/license.txt" }
        });
    }

}
