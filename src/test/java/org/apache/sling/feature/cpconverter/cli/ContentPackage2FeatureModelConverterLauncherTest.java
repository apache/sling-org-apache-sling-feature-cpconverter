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
package org.apache.sling.feature.cpconverter.cli;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.packaging.CyclicDependencyException;
import org.junit.Test;

public class ContentPackage2FeatureModelConverterLauncherTest {

    /**
     * Test package A-1.0. Depends on B and C-1.X
     * Test package B-1.0. Depends on C
     */
    private static String[] TEST_PACKAGES_INPUT = { "test_c-1.0.zip", "test_a-1.0.zip", "test_b-1.0.zip" }; 

    private static String[] TEST_PACKAGES_OUTPUT = { "test_c-1.0.zip", "test_b-1.0.zip", "test_a-1.0.zip" }; 

    private static String[] TEST_PACKAGES_CYCLIC_DEPENDENCY = { "test_d-1.0.zip",
                                                                "test_c-1.0.zip",
                                                                "test_a-1.0.zip",
                                                                "test_b-1.0.zip",
                                                                "test_e-1.0.zip" }; 

    @Test
    public void testPackageOrdering() throws Exception {
        ContentPackage2FeatureModelConverterLauncher launcher = new ContentPackage2FeatureModelConverterLauncher();
        List<File> contentPackages = new ArrayList<File>();

        for (String pkgName : TEST_PACKAGES_INPUT) {
            URL packageUrl = getClass().getResource(pkgName);
            contentPackages.add(FileUtils.toFile(packageUrl));
        }
        List<File> ordered = launcher.order(contentPackages);
        Iterator<File> fileIt = ordered.iterator();
        for (String expected : TEST_PACKAGES_OUTPUT) {
            File next = fileIt.next();
            assertEquals(expected, next.getName());
        }
    }

    @Test(expected = CyclicDependencyException.class)
    public void testDependencyCycle() throws Exception {
        ContentPackage2FeatureModelConverterLauncher launcher = new ContentPackage2FeatureModelConverterLauncher();
        List<File> contentPackages = new ArrayList<File>();

        for (String pkgName : TEST_PACKAGES_CYCLIC_DEPENDENCY) {
            URL packageUrl = getClass().getResource(pkgName);
            contentPackages.add(FileUtils.toFile(packageUrl));
        }
        launcher.order(contentPackages);
    }

}
