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
package org.apache.sling.feature.cpconverter.handlers.slinginitialcontent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.jcr.contentloader.PathEntry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SlingInitialContentBundleEntryMetaDataCollectorTest {

    @Rule
    public TemporaryFolder tmpdir = new TemporaryFolder();

    public SlingInitialContentBundleEntryMetaDataCollector collector;

    @Mock
    BundleSlingInitialContentExtractContext context;

    @Test
    public void failWithJarPathsOutsideOfTempDirectory() throws IOException {

        // setup temporary directory
        when(context.getManifest()).thenReturn(new Manifest());
        ContentPackage2FeatureModelConverter converter = Mockito.mock(ContentPackage2FeatureModelConverter.class,Mockito.RETURNS_DEEP_STUBS);
        when(converter.getTempDirectory()).thenReturn(tmpdir.newFolder("temp"));
        Path newBundleFile = new File(tmpdir.newFolder("jar").getPath(),"malicious.jar").toPath();

        // Add an Sling-Initial-Content header matching the outside-of-current-directory path "../outside.txt"
        List<PathEntry> pathEntryList = new ArrayList<>();
        PathEntry pathEntry = mock(PathEntry.class);
        when(pathEntry.getPath()).thenReturn("../outside.txt");
        pathEntryList.add(pathEntry);
        when(context.getPathEntryList()).thenReturn(pathEntryList);

        // construct a malicous jar file
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(newBundleFile.toString()), new Manifest())) {
            JarEntry entry = new JarEntry("../outside.txt");
            jos.putNextEntry(entry);
            jos.write("malicious content".getBytes());
            jos.closeEntry();
        }
        JarFile jar = new JarFile(newBundleFile.toString());
        when(context.getJarFile()).thenReturn(jar);
        collector = new SlingInitialContentBundleEntryMetaDataCollector(context, converter, newBundleFile);

        try {
            collector.collectFromContextAndWriteTmpFiles();
            fail("should have failed");
        } catch (Exception e) {
            assertEquals(IOException.class,e.getClass());
            assertTrue("unexpected log message",e.getMessage().startsWith("unpacking ../outside.txt (of"));
        }
    }

}
