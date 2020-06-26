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
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VaultPackageAssemblerTest {

    private static final File TMP_DIR = new File(System.getProperty("java.io.tmpdir"), "synthetic-content-packages");

    private File testDirectory;

    private final VaultPackageAssembler assembler;

    private final String resourceLocation;

    public VaultPackageAssemblerTest(String resourceLocation, VaultPackageAssembler assembler) {
        this.resourceLocation = resourceLocation;
        this.assembler = assembler;
    }

    @Before
    public void setUp() {
        testDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
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
        File contentPackage = assembler.createPackage(testDirectory);

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

        VaultPackageAssembler assembler = VaultPackageAssembler.create(vaultPackage);
        PackageId packageId = vaultPackage.getId();
        String fileName = packageId.toString().replaceAll("/", "-").replaceAll(":", "-") + "-" + vaultPackage.getFile().getName();
        File storingDirectory = new File(TMP_DIR, fileName + "-deflated");
        assertTrue("Storing Directory for Vault Package does not exist", storingDirectory.exists());
    }

    @Parameters
    public static Collection<Object[]> data() throws Exception {
        URL resource = VaultPackageAssemblerTest.class.getResource("../test-content-package.zip");
        File file = FileUtils.toFile(resource);
        VaultPackage vaultPackage = new PackageManagerImpl().open(file);

        VaultPackageAssembler assembler = VaultPackageAssembler.create(vaultPackage);

        return Arrays.asList(new Object[][] {
            { null, assembler },
            { "/jcr_root/.content.xml", assembler },
            { "/jcr_root/asd/.content.xml", assembler },
            { "/jcr_root/asd/public/license.txt", assembler }
        });
    }

}
