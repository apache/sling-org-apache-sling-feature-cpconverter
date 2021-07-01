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

import java.io.File;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.junit.Assert;
import org.junit.Test;

public class VaultPackageAssemblerUnparameterizedTest {

    @Test
    public void testRecalculatePackageType() {
        URL resource = VaultPackageAssemblerTest.class.getResource("../immutable");
        File immutableInput = FileUtils.toFile(resource);
        resource = VaultPackageAssemblerTest.class.getResource("../mutable");
        File mutableInput = FileUtils.toFile(resource);
        resource = VaultPackageAssemblerTest.class.getResource("../mixed");
        File mixedInput = FileUtils.toFile(resource);

        Assert.assertNull(VaultPackageUtils.recalculatePackageType(PackageType.APPLICATION, immutableInput));
        Assert.assertNull(VaultPackageUtils.recalculatePackageType(PackageType.CONTENT, immutableInput));
        Assert.assertEquals(PackageType.APPLICATION, VaultPackageUtils.recalculatePackageType(PackageType.MIXED, immutableInput));
        Assert.assertEquals(PackageType.APPLICATION, VaultPackageUtils.recalculatePackageType(null, immutableInput));

        Assert.assertNull(VaultPackageUtils.recalculatePackageType(PackageType.APPLICATION, mutableInput));
        Assert.assertNull(VaultPackageUtils.recalculatePackageType(PackageType.CONTENT, mutableInput));
        Assert.assertEquals(PackageType.CONTENT, VaultPackageUtils.recalculatePackageType(PackageType.MIXED, mutableInput));
        Assert.assertEquals(PackageType.CONTENT, VaultPackageUtils.recalculatePackageType(null, mutableInput));

        Assert.assertNull(VaultPackageUtils.recalculatePackageType(PackageType.APPLICATION, mixedInput));
        Assert.assertNull(VaultPackageUtils.recalculatePackageType(PackageType.CONTENT, mixedInput));
        Assert.assertEquals(PackageType.MIXED, VaultPackageUtils.recalculatePackageType(PackageType.MIXED, mixedInput));
        Assert.assertEquals(PackageType.MIXED, VaultPackageUtils.recalculatePackageType(null, mixedInput));
    }
}
