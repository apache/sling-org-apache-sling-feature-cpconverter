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

import static org.apache.commons.io.FileUtils.toFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.junit.Before;
import org.junit.Test;

public class NodeTypesDetectionTest {

    private File packageFile;

    @Before
    public void setUp() {
        URL packageURL = getClass().getResource("../test_a-1.0.zip");
        packageFile = toFile(packageURL);
    }

    @Test
    public void detectMetaInfVaultNodetypesCdnFile() throws Exception {
        final List<String> detectedCndFiles = new LinkedList<>();

        new BaseVaultPackageScanner(true) {

            protected void addCdnPattern(Pattern cndPattern) {
                detectedCndFiles.add(cndPattern.pattern());
            }

        }.traverse(packageFile, true);

        assertFalse(detectedCndFiles.isEmpty());
        assertEquals(1, detectedCndFiles.size());
        assertTrue(detectedCndFiles.contains(new ImportOptions().getCndPattern().pattern()));
    }

}
