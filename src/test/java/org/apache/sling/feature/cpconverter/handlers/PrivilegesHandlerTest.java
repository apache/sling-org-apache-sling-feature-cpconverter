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

import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.accesscontrol.DefaultAclManager;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

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
        assertTrue(handler.matches("/META-INF/vault/privileges.xml"));
    }

    @Test
    public void parsePrivileges() throws Exception {
        String path = "/META-INF/vault/privileges.xml";
        Archive archive = mock(Archive.class);
        Entry entry = mock(Entry.class);

        DefaultMetaInf metaInf = new DefaultMetaInf();
        metaInf.load(getClass().getResourceAsStream(path.substring(1)), "privileges.xml");

        when(archive.openInputStream(entry)).thenReturn(getClass().getResourceAsStream(path.substring(1)));
        when(archive.getMetaInf()).thenReturn(metaInf);

        VaultPackageAssembler packageAssembler = mock(VaultPackageAssembler.class);

        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));
        FeaturesManager featuresManager = spy(DefaultFeaturesManager.class);
        when(featuresManager.getTargetFeature()).thenReturn(feature);
        try(ContentPackage2FeatureModelConverter converter = new ContentPackage2FeatureModelConverter()) {
            converter.setFeaturesManager(featuresManager);

            handler.handle(path, archive, entry, converter, null);

            converter.getAclManager().addRepoinitExtension(Collections.singletonList(packageAssembler), featuresManager);

            Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
            assertNotNull(repoinitExtension);
            String str = "register privilege sling:replicate" + System.lineSeparator() +
                        "register abstract privilege sling:test with ";
            String txt = repoinitExtension.getText();
            assertTrue("Expect '"+txt+"' contains '"+str+"'", txt.contains(str));
            String aggregation1 = "with sling.replicate,jcr.read" + System.lineSeparator();
            String aggregation2 = "with jcr.read,sling.replicate" + System.lineSeparator();
            assertTrue(txt.contains(aggregation1) || txt.contains(aggregation2));
        }
    }

}
