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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

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
        this.assembler = VaultPackageAssembler.create(testDirectory, vaultPackage, false);
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(this.testDirectory);
    }
    
    @Test
    public void matchAll() {
        assembler.matches(resourceLocation);
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

        VaultPackageAssembler assembler = VaultPackageAssembler.create(testDirectory, vaultPackage, false);
        PackageId packageId = vaultPackage.getId();
        String fileName = packageId.toString().replaceAll("/", "-").replaceAll(":", "-") + "-" + vaultPackage.getFile().getName();
        File storingDirectory = new File(assembler.getTempDir(), fileName + "-deflated");
        assertTrue("Storing Directory for Vault Package does not exist", storingDirectory.exists());
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
