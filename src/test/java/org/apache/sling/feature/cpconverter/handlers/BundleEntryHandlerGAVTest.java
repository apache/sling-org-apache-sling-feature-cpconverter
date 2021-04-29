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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import org.apache.sling.feature.ArtifactId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BundleEntryHandlerGAVTest extends AbstractBundleEntryHandlerTest {

    @Test
    public void testGAV() throws Exception {
        setUpArchive("/jcr_root/apps/gav/install/core-1.0.0-SNAPSHOT.jar", "core-1.0.0-SNAPSHOT.jar");
        handler.handle("/jcr_root/apps/gav/install/core-1.0.0-SNAPSHOT.jar", archive, entry, converter);
        Mockito.verify(featuresManager).addArtifact(null, ArtifactId.fromMvnId("com.madplanet.sling.cp2sf:core:1.0.0-SNAPSHOT"), null);
    }

    @Test
    public void testGAVwithPom() throws Exception{
        setUpArchive("/jcr_root/apps/gav/install/org.osgi.service.jdbc-1.0.0.jar", "org.osgi.service.jdbc-1.0.0.jar");
        handler.handle("/jcr_root/apps/gav/install/org.osgi.service.jdbc-1.0.0.jar", archive, entry, converter);
        Mockito.verify(featuresManager).addArtifact(null, ArtifactId.fromMvnId("org.osgi:org.osgi.service.jdbc:1.0.0"), null);
    }

    @Test
    public void testNoGAV() throws Exception {
        setUpArchive("/jcr_root/apps/gav/install/org.osgi.service.jdbc-1.0.0-nogav.jar", "org.osgi.service.jdbc-1.0.0-nogav.jar");
        handler.handle("/jcr_root/apps/gav/install/org.osgi.service.jdbc-1.0.0-nogav.jar", archive, entry, converter);
        Mockito.verify(featuresManager).addArtifact(null, ArtifactId.fromMvnId("org.osgi.service:jdbc:1.0.0-201505202023"), null);
    }

    @Test
    public void testBundleBelowConfigFolderWithEnforcement() throws Exception {
        handler.setEnforceBundlesBelowInstallFolder(true);
        when(entry.getName()).thenReturn("mybundle.jar");
        assertThrows(IllegalStateException.class, () -> { handler.handle("/jcr_root/apps/myapp/config/mybundle.jar", null, entry, null); });
    }
}
