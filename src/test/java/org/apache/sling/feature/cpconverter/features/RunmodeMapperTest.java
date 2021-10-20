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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RunmodeMapperTest {

    private Path tempDir;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory(getClass().getSimpleName());
    }

    @After
    public void tearDown() throws IOException {
        // Delete the temp dir again
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

    @Test public void testMapper() throws IOException {
        RunmodeMapper mapper = RunmodeMapper.open(this.tempDir.toFile());
        final File runmodeMappingFile = new File(this.tempDir.toFile(), RunmodeMapper.FILENAME);

        assertFalse(runmodeMappingFile.exists());

        mapper.addOrUpdate(null, "foo.json");
        mapper.addOrUpdate("author", "my-all.json");
        mapper.addOrUpdate("author", "all.json");

        mapper.save();
        assertTrue(runmodeMappingFile.exists());

        final Properties props = new Properties();
        try (final FileInputStream input = new FileInputStream(runmodeMappingFile)) {
            props.load(input);
        }
        assertEquals(2, props.size());
        assertEquals("foo.json", props.get("(default)"));
        assertEquals("my-all.json,all.json", props.get("author"));

        mapper = RunmodeMapper.open(this.tempDir.toFile());
        mapper.addOrUpdate(null, "foo2.json");
        mapper.addOrUpdate("publish", "publish.json");
        mapper.save();
        assertTrue(runmodeMappingFile.exists());
        props.clear();
        try (final FileInputStream input = new FileInputStream(runmodeMappingFile)) {
            props.load(input);
        }
        assertEquals(3, props.size());
        assertEquals("foo.json,foo2.json", props.get("(default)"));
        assertEquals("my-all.json,all.json", props.get("author"));
        assertEquals("publish.json", props.get("publish"));
    }
}