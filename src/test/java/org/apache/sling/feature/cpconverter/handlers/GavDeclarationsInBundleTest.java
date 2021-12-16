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
package org.apache.sling.feature.cpconverter.handlers;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.jar.JarFile;

import org.apache.sling.feature.ArtifactId;
import org.junit.Test;

public class GavDeclarationsInBundleTest {

    @Test
    public void guessRightGavsWhenMultiplePomPropertiesDeclarationsAreIncluded() throws Exception {
        verifyBundle("core-1.0.0-SNAPSHOT.jar",
                     "core-1.0.0-SNAPSHOT",
                     "com.madplanet.sling.cp2sf",
                     "core",
                     "1.0.0-SNAPSHOT",
                     null);
        
    }

    @Test
    public void guessTheClassifierFromBundleName() throws Exception {
        verifyBundle("core-1.0.0-SNAPSHOT-classified.jar",
                     "core-1.0.0-SNAPSHOT-classified",
                     "com.madplanet.sling.cp2sf",
                     "core",
                     "1.0.0-SNAPSHOT",
                     "classified");
    }

    private void verifyBundle(String resourceName,
                              String bundleName,
                              String expectedGroupId,
                              String expectedArtifactId,
                              String expectedVersion,
                              String expectedClassifier) throws Exception {
        BundleEntryHandler bundleEntryHandler = new BundleEntryHandler();

        try (JarFile jarFile = new JarFile(new File(getClass().getResource(resourceName).toURI()))) {
            ArtifactId artifactId = bundleEntryHandler.extractFeatureArtifact(bundleName, jarFile).getId();
            assertEquals(new ArtifactId(expectedGroupId, expectedArtifactId, expectedVersion, expectedClassifier, "jar"), artifactId);
        }
    }

}
