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

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.artifacts.ArtifactsDeployer;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class BundleEntryHandlerGAVTest {
    private final BundleEntryHandler handler = new BundleEntryHandler();

    @Test
    public void testGAVwithProperties() throws Exception {
        String path = "/jcr_root/apps/gav/install/core-1.0.0-SNAPSHOT.jar";
        Archive.Entry entry = Mockito.mock(Archive.Entry.class);
        when(entry.getName()).thenReturn(path);
        Archive archive = Mockito.mock(Archive.class);
        when(archive.openInputStream(entry)).thenReturn(BundleEntryHandlerGAVTest.class.getResourceAsStream("core-1.0.0-SNAPSHOT.jar"));
        ContentPackage2FeatureModelConverter converter = spy(ContentPackage2FeatureModelConverter.class);
        ArtifactsDeployer deployer = Mockito.spy(ArtifactsDeployer.class);
        when(converter.getArtifactsDeployer()).thenReturn(deployer);
        FeaturesManager manager = Mockito.spy(FeaturesManager.class);
        when(converter.getFeaturesManager()).thenReturn(manager);
        handler.handle(path, archive, entry, converter);
        Mockito.verify(manager).addArtifact(null, ArtifactId.fromMvnId("com.madplanet.sling.cp2sf:core:1.0.0-SNAPSHOT"),null);
    }

    @Test
    public void testGAVwithPom() throws Exception{
        String path = "/jcr_root/apps/gav/install/org.osgi.service.jdbc-1.0.0.jar";
        Archive.Entry entry = Mockito.mock(Archive.Entry.class);
        when(entry.getName()).thenReturn(path);
        Archive archive = Mockito.mock(Archive.class);
        when(archive.openInputStream(entry)).thenReturn(BundleEntryHandlerGAVTest.class.getResourceAsStream("org.osgi.service.jdbc-1.0.0.jar"));
        ContentPackage2FeatureModelConverter converter = spy(ContentPackage2FeatureModelConverter.class);
        ArtifactsDeployer deployer = Mockito.spy(ArtifactsDeployer.class);
        when(converter.getArtifactsDeployer()).thenReturn(deployer);
        FeaturesManager manager = Mockito.spy(FeaturesManager.class);
        when(converter.getFeaturesManager()).thenReturn(manager);
        handler.handle(path, archive, entry, converter);
        Mockito.verify(manager).addArtifact(null, ArtifactId.fromMvnId("org.osgi:org.osgi.service.jdbc:1.0.0"),null);
    }

    @Test
    public void testNoGAV() throws Exception {
        String path = "/jcr_root/apps/gav/install/org.osgi.service.jdbc-1.0.0-nogav.jar";
        Archive.Entry entry = Mockito.mock(Archive.Entry.class);
        when(entry.getName()).thenReturn(path);
        Archive archive = Mockito.mock(Archive.class);
        when(archive.openInputStream(entry)).thenReturn(BundleEntryHandlerGAVTest.class.getResourceAsStream("org.osgi.service.jdbc-1.0.0-nogav.jar"));
        ContentPackage2FeatureModelConverter converter = spy(ContentPackage2FeatureModelConverter.class);
        ArtifactsDeployer deployer = Mockito.spy(ArtifactsDeployer.class);
        when(converter.getArtifactsDeployer()).thenReturn(deployer);
        FeaturesManager manager = Mockito.spy(FeaturesManager.class);
        when(converter.getFeaturesManager()).thenReturn(manager);
        handler.handle(path, archive, entry, converter);
        Mockito.verify(manager).addArtifact(null, ArtifactId.fromMvnId("org.osgi.service:jdbc:1.0.0-201505202023"),null);
    }
}
