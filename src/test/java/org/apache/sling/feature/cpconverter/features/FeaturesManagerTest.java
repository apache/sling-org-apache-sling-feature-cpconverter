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
package org.apache.sling.feature.cpconverter.features;

import org.apache.sling.feature.ArtifactId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.stream.JsonParser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FeaturesManagerTest {
    private FeaturesManager featuresManager;
    private Path tempDir;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory(getClass().getSimpleName());
        featuresManager = new DefaultFeaturesManager(tempDir.toFile());
    }

    @After
    public void tearDown() throws IOException {
        featuresManager = null;

        // Delete the temp dir again
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

    @Test(expected = IllegalStateException.class)
    public void getRunModeRequiresConvertInvoked() {
        featuresManager.getRunMode(null);
    }

    @Test(expected = NullPointerException.class)
    public void deployLocallyAndaddArtifactRequiresNonNullId() throws Exception {
        featuresManager.addArtifact(null, null);
    }

    @Test
    public void testExportsToAPIRegion() throws Exception {
        ((DefaultFeaturesManager) featuresManager).setExportToAPIRegion("global");
        ((DefaultFeaturesManager) featuresManager).setAPIRegions(Collections.singletonList("deprecated"));
        featuresManager.init(ArtifactId.parse("g:a:1"));
        featuresManager.addAPIRegionExport(null, "org.foo.a");
        featuresManager.addAPIRegionExport(null, "org.foo.b");
        featuresManager.addAPIRegionExport("rm1", "x.y");

        featuresManager.serialize();

        try (InputStream in = new FileInputStream(new File(tempDir.toFile(), "a.json"))) {
            JsonParser p = Json.createParser(in);
            JsonObject jo = p.getObject();

            assertEquals("g:a:slingosgifeature:1", jo.getString("id"));

            JsonArray ja = jo.getJsonArray("api-regions:JSON|false");
            assertEquals(2, ja.size());

            JsonObject ar1 = ja.getJsonObject(0);
            assertEquals("global", ar1.getString("name"));
            JsonArray are1 = ar1.getJsonArray("exports");

            assertEquals(Arrays.asList("org.foo.a", "org.foo.b"),
                    are1.getValuesAs(JsonString::getString));

            JsonObject ar2 = ja.getJsonObject(1);
            assertEquals("deprecated", ar2.getString("name"));
            JsonArray are2 = ar2.getJsonArray("exports");
            assertTrue(are2 == null || are2.isEmpty());
        }

        // Runmode file:
        try (InputStream in = new FileInputStream(new File(tempDir.toFile(), "a-rm1.json"))) {
            JsonParser p = Json.createParser(in);
            JsonObject jo = p.getObject();

            assertEquals("g:a:slingosgifeature:rm1:1", jo.getString("id"));

            JsonArray ja = jo.getJsonArray("api-regions:JSON|false");
            assertEquals(2, ja.size());

            JsonObject ar1 = ja.getJsonObject(0);
            assertEquals("global", ar1.getString("name"));
            JsonArray are1 = ar1.getJsonArray("exports");

            assertEquals(Arrays.asList("x.y"),
                    are1.getValuesAs(JsonString::getString));

            JsonObject ar2 = ja.getJsonObject(1);
            assertEquals("deprecated", ar2.getString("name"));
            JsonArray are2 = ar2.getJsonArray("exports");
            assertTrue(are2 == null || are2.isEmpty());
        }
    }
}
