/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.feature.cpconverter.handlers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TestUtils {

    private static final Logger log = LoggerFactory.getLogger(TestUtils.class);

    private TestUtils() {}

    static Extension createRepoInitExtension(@NotNull EntryHandler handler, @NotNull AclManager aclManager, @NotNull String path, @NotNull InputStream is) throws Exception {
        return createRepoInitExtension(handler, aclManager, path, is, new ByteArrayOutputStream());
    }

    static Extension createRepoInitExtension(@NotNull EntryHandler handler, @NotNull AclManager aclManager, @NotNull String path, @NotNull InputStream is, @NotNull OutputStream out) throws Exception {
        Archive archive = mock(Archive.class);
        Archive.Entry entry = mock(Archive.Entry.class);
        VaultPackageAssembler packageAssembler = mock(VaultPackageAssembler.class);
        when(packageAssembler.createEntry(anyString())).thenReturn(out);
        when(archive.openInputStream(entry)).thenReturn(is);

        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));
        FeaturesManager featuresManager = spy(DefaultFeaturesManager.class);
        when(featuresManager.getTargetFeature()).thenReturn(feature);
        ContentPackage2FeatureModelConverter converter = mock(ContentPackage2FeatureModelConverter.class);
        when(converter.getFeaturesManager()).thenReturn(featuresManager);
        when(converter.getAclManager()).thenReturn(aclManager);
        when(converter.getMainPackageAssembler()).thenReturn(packageAssembler);

        handler.handle(path, archive, entry, converter);

        when(packageAssembler.getEntry(anyString())).thenReturn(new File("itdoesnotexist"));

        converter.getAclManager().addRepoinitExtension(Collections.singletonList(packageAssembler), featuresManager);
        return feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
    }

    /**
     * Returns a test file that is located in a similar directory to the specified class
     *
     * <p>This is intended to work on a similar way to <tt>getClass().getResourceAsStream()</tt>, but with
     * files instead.</p>
     *
     * @param klazz the class used to locate the file
     * @param pathElement a path element
     * @param pathElements additional, optional, elements
     * @return a file that exists
     * @throws IllegalArgumentException if the file does not exist
     */
    static File getPackageRelativeFile(Class<?> klazz, String pathElement, String... pathElements) {
        List<CharSequence> segments =  new ArrayList<>();
        segments.addAll(Arrays.asList("src", "test", "resources"));
        segments.addAll(Arrays.asList(klazz.getPackage().getName().split("\\.")));
        segments.add(pathElement);
        if ( pathElements != null )
            segments.addAll(Arrays.asList(pathElements));

        String fileName = String.join(File.separator, segments.toArray(new CharSequence[0]));
        File file = new File(fileName);
        if ( !file.exists() )
            throw new IllegalArgumentException("File " + file + " does not exist");
        return file;
    }
}