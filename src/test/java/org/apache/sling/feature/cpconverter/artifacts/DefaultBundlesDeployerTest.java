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
package org.apache.sling.feature.cpconverter.artifacts;

import static org.junit.Assert.*;

import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DefaultBundlesDeployerTest {

    private LocalMavenRepositoryArtifactsDeployer artifactDeployer;

    private File outputDirectory;

    @Before
    public void setUp() {
        outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        artifactDeployer = new LocalMavenRepositoryArtifactsDeployer(outputDirectory);
    }

    @After
    public void tearDown() throws IOException {
        artifactDeployer = null;
        Path tempDir = outputDirectory.toPath();
    
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);

        outputDirectory = null;
    }

    @Test
    public void verifyBundlesDirectory() {
        File bundlesDirectory = artifactDeployer.getBaseDirectory();
        assertNotNull(bundlesDirectory);
        assertTrue(bundlesDirectory.exists());
        assertTrue(bundlesDirectory.isDirectory());
    }

    @Test(expected = NullPointerException.class)
    public void deployLocallyAndAttachRequiresNonNullInput() throws Exception {
        artifactDeployer.deploy(null, null, null);
    }

    @Test(expected = NullPointerException.class)
    public void deployLocallyAndAttachRequiresNonNullArtifactId() throws Exception {
        artifactDeployer.deploy(mock(ArtifactWriter.class), null, null);
    }

}
