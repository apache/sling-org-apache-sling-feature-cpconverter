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
import org.junit.Assert;
import org.junit.Test;

import static org.apache.jackrabbit.vault.packaging.PackageType.APPLICATION;
import static org.apache.jackrabbit.vault.packaging.PackageType.CONTENT;
import static org.apache.jackrabbit.vault.packaging.PackageType.MIXED;

public class VaultPackageAssemblerUnparameterizedTest {

    @Test
    public void testRecalculatePackageType() {
        URL resource = VaultPackageAssemblerTest.class.getResource("../immutable");
        File immutableInput = FileUtils.toFile(resource);
        resource = VaultPackageAssemblerTest.class.getResource("../mutable");
        File mutableInput = FileUtils.toFile(resource);
        resource = VaultPackageAssemblerTest.class.getResource("../mixed");
        File mixedInput = FileUtils.toFile(resource);

        Assert.assertNull(VaultPackageUtils.recalculatePackageType(APPLICATION, immutableInput, false));;
        Assert.assertNull(VaultPackageUtils.recalculatePackageType(CONTENT, immutableInput, false));;
        Assert.assertEquals(APPLICATION, VaultPackageUtils.recalculatePackageType(MIXED, immutableInput, false));;
        Assert.assertEquals(APPLICATION, VaultPackageUtils.recalculatePackageType(null, immutableInput, false));;

        Assert.assertNull(VaultPackageUtils.recalculatePackageType(APPLICATION, mutableInput, false));;
        Assert.assertNull(VaultPackageUtils.recalculatePackageType(CONTENT, mutableInput, false));;
        Assert.assertEquals(CONTENT, VaultPackageUtils.recalculatePackageType(MIXED, mutableInput, false));;
        Assert.assertEquals(CONTENT, VaultPackageUtils.recalculatePackageType(null, mutableInput, false));;

        Assert.assertNull(VaultPackageUtils.recalculatePackageType(APPLICATION, mixedInput, false));;
        Assert.assertNull(VaultPackageUtils.recalculatePackageType(CONTENT, mixedInput, false));;
        Assert.assertEquals(MIXED, VaultPackageUtils.recalculatePackageType(MIXED, mixedInput, false));;
        Assert.assertEquals(MIXED, VaultPackageUtils.recalculatePackageType(null, mixedInput, false));;
    }


    @Test
    public void testRecalculatePackageTypeWithoutParentType() {
        URL resource = VaultPackageAssemblerTest.class.getResource("../immutable");
        File immutableInput = FileUtils.toFile(resource);
        resource = VaultPackageAssemblerTest.class.getResource("../mutable");
        File mutableInput = FileUtils.toFile(resource);
        resource = VaultPackageAssemblerTest.class.getResource("../mixed");
        File mixedInput = FileUtils.toFile(resource);

        Assert.assertNull(VaultPackageUtils.recalculatePackageType(APPLICATION, immutableInput, true));;
        Assert.assertNull(VaultPackageUtils.recalculatePackageType(CONTENT, immutableInput, true));;
        Assert.assertNull(VaultPackageUtils.recalculatePackageType(MIXED, immutableInput, true));;
        Assert.assertEquals(APPLICATION, VaultPackageUtils.recalculatePackageType(null, immutableInput, true));;

        Assert.assertNull(VaultPackageUtils.recalculatePackageType(APPLICATION, mutableInput, true));;
        Assert.assertNull(VaultPackageUtils.recalculatePackageType(CONTENT, mutableInput, true));;
        Assert.assertNull(VaultPackageUtils.recalculatePackageType(MIXED, mutableInput, true));;
        Assert.assertEquals(CONTENT, VaultPackageUtils.recalculatePackageType(null, mutableInput, true));;

        Assert.assertNull(VaultPackageUtils.recalculatePackageType(APPLICATION, mixedInput, true));;
        Assert.assertNull(VaultPackageUtils.recalculatePackageType(CONTENT, mixedInput, true));;
        Assert.assertNull(VaultPackageUtils.recalculatePackageType(MIXED, mixedInput, true));;
        Assert.assertEquals(MIXED, VaultPackageUtils.recalculatePackageType(null, mixedInput, true));;
    }
}
