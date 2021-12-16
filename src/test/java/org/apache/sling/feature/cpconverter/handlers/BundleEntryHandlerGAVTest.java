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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.Constants;

@RunWith(MockitoJUnitRunner.class)
public class BundleEntryHandlerGAVTest extends AbstractBundleEntryHandlerTest {

    @Test
    public void testGAV() throws Exception {
        setUpArchive("/jcr_root/apps/gav/install/core-1.0.0-SNAPSHOT.jar", "core-1.0.0-SNAPSHOT.jar");
        handler.handle("/jcr_root/apps/gav/install/core-1.0.0-SNAPSHOT.jar", archive, entry, converter);
        ArgumentCaptor<Artifact> captor = ArgumentCaptor.forClass(Artifact.class);
        Mockito.verify(featuresManager).addArtifact(Mockito.isNull(), captor.capture(), Mockito.isNull());
        final Artifact result = captor.getValue();
        assertNotNull(result);
        assertEquals(ArtifactId.fromMvnId("com.madplanet.sling.cp2sf:core:1.0.0-SNAPSHOT"), result.getId());
        assertEquals("com.madplanet.sling.cp2sf.core", result.getMetadata().get(Constants.BUNDLE_SYMBOLICNAME));
        assertEquals("1.0.0.SNAPSHOT", result.getMetadata().get(Constants.BUNDLE_VERSION));
    }

    @Test
    public void testGAVwithPom() throws Exception{
        setUpArchive("/jcr_root/apps/gav/install/org.osgi.service.jdbc-1.0.0.jar", "org.osgi.service.jdbc-1.0.0.jar");
        handler.handle("/jcr_root/apps/gav/install/org.osgi.service.jdbc-1.0.0.jar", archive, entry, converter);
        ArgumentCaptor<Artifact> captor = ArgumentCaptor.forClass(Artifact.class);
        Mockito.verify(featuresManager).addArtifact(Mockito.isNull(), captor.capture(), Mockito.isNull());
        final Artifact result = captor.getValue();
        assertNotNull(result);
        assertEquals(ArtifactId.fromMvnId("org.osgi:org.osgi.service.jdbc:1.0.0"), result.getId());
        assertEquals("org.osgi.service.jdbc", result.getMetadata().get(Constants.BUNDLE_SYMBOLICNAME));
        assertEquals("1.0.0.201505202023", result.getMetadata().get(Constants.BUNDLE_VERSION));
    }

    @Test
    public void testNoGAV() throws Exception {
        setUpArchive("/jcr_root/apps/gav/install/org.osgi.service.jdbc-1.0.0-nogav.jar", "org.osgi.service.jdbc-1.0.0-nogav.jar");
        handler.handle("/jcr_root/apps/gav/install/org.osgi.service.jdbc-1.0.0-nogav.jar", archive, entry, converter);
        ArgumentCaptor<Artifact> captor = ArgumentCaptor.forClass(Artifact.class);
        Mockito.verify(featuresManager).addArtifact(Mockito.isNull(), captor.capture(), Mockito.isNull());
        final Artifact result = captor.getValue();
        assertNotNull(result);
        assertEquals(ArtifactId.fromMvnId("org.osgi.service:jdbc:1.0.0-201505202023"), result.getId());
        assertEquals("org.osgi.service.jdbc", result.getMetadata().get(Constants.BUNDLE_SYMBOLICNAME));
        assertEquals("1.0.0.201505202023", result.getMetadata().get(Constants.BUNDLE_VERSION));
    }

    @Test
    public void testBundleBelowConfigFolderWithEnforcement() throws Exception {
        handler.setEnforceBundlesBelowInstallFolder(true);
        when(entry.getName()).thenReturn("mybundle.jar");
        assertThrows(ConverterException.class, () -> { handler.handle("/jcr_root/apps/myapp/config/mybundle.jar", null, entry, null); });
    }
}
