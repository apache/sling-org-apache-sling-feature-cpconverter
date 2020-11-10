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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.acl.DefaultAclManager;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PrivilegesHandlerTest {

    private PrivilegesHandler handler;

    @Before
    public void setUp() {
        handler = new PrivilegesHandler();
    }

    @After
    public void tearDown() {
        handler = null;
    }

    @Test
    public void doesNotMatch() {
        assertFalse(handler.matches("/this/is/a/path/not/pointing/to/a/valid/privileges.xml"));
    }

    @Test
    public void matches() {
        assertTrue(handler.matches("META-INF/vault/privileges.xml"));
    }

    @Test
    public void parsePrivileges() throws Exception {
        String path = "/META-INF/vault/privileges.xml";
        Archive archive = mock(Archive.class);
        Entry entry = mock(Entry.class);

        when(archive.openInputStream(entry)).thenReturn(getClass().getResourceAsStream(path.substring(1)));

        VaultPackageAssembler packageAssembler = mock(VaultPackageAssembler.class);

        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));
        FeaturesManager featuresManager = spy(DefaultFeaturesManager.class);
        when(featuresManager.getTargetFeature()).thenReturn(feature);
        ContentPackage2FeatureModelConverter converter = spy(ContentPackage2FeatureModelConverter.class);
        when(converter.getFeaturesManager()).thenReturn(featuresManager);
        when(converter.getAclManager()).thenReturn(new DefaultAclManager());

        handler.handle(path, archive, entry, converter);

        converter.getAclManager().addRepoinitExtension(Arrays.asList(packageAssembler), featuresManager);

        Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(repoinitExtension);
        assertTrue(repoinitExtension.getText().contains("register privilege rx:replicate" + System.lineSeparator()));
    }

}
