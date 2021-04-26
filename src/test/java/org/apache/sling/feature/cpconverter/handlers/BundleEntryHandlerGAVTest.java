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

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.artifacts.ArtifactsDeployer;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.codehaus.plexus.util.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BundleEntryHandlerGAVTest {

    @Mock
    private Archive.Entry entry;
    @Mock
    private Archive archive;
    @Mock
    private ContentPackage2FeatureModelConverter converter;
    @Spy
    private FeaturesManager featuresManager;
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();
    
    private BundleEntryHandler handler;

    @Before
    public void setUp() throws IOException {
        handler = new BundleEntryHandler();
        ArtifactsDeployer deployer = Mockito.spy(ArtifactsDeployer.class);
        when(converter.getArtifactsDeployer()).thenReturn(deployer);
        when(converter.getTempDirectory()).thenReturn(tmpFolder.getRoot());
        featuresManager = Mockito.spy(FeaturesManager.class);
        when(converter.getFeaturesManager()).thenReturn(featuresManager);
    }

    private void setUpArchive(String entryPath, String resourcePath) throws IOException {
        when(entry.getName()).thenReturn(entryPath);
        when(archive.openInputStream(entry)).thenReturn(BundleEntryHandlerGAVTest.class.getResourceAsStream(resourcePath));
    }

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

    @Test
    public void testSlingInitialContent() throws Exception {
        setUpArchive("/jcr_root/apps/gav/install/io.wcm.handler.media-1.11.6.jar", "io.wcm.handler.media-1.11.6.jar");
        DefaultEntryHandlersManager handlersManager = new DefaultEntryHandlersManager();
        when(converter.getHandlersManager()).thenReturn(handlersManager);
        Map<String, String> namespaceRegistry = Collections.singletonMap("granite", "http://www.adobe.com/jcr/granite/1.0");
        when(featuresManager.getNamespaceUriByPrefix()).thenReturn(namespaceRegistry);
        
        File newPackageFile = tmpFolder.newFile();
        Mockito.doAnswer(invocation -> {
            // capture package
            File sourcePackage = (File)invocation.getArgument(0);
            FileUtils.copyFile(sourcePackage, newPackageFile);
            return null;
        }).when(converter).processContentPackageArchive(Mockito.any(), Mockito.isNull());
        handler.setExtractSlingInitialContent(true);
        handler.handle("/jcr_root/apps/gav/install/io.wcm.handler.media-1.11.6.jar", archive, entry, converter);
        // verify package contents
        try (VaultPackage vaultPackage = new PackageManagerImpl().open(newPackageFile);
             Archive archive = vaultPackage.getArchive()) {
            archive.open(true);
            PackageId targetId = PackageId.fromString("io.wcm:io.wcm.handler.media-apps:1.11.6-cp2fm-converted");
            assertEquals(targetId, vaultPackage.getId());
            Entry entry = archive.getEntry("jcr_root/apps/wcm-io/handler/media/components/global/include/responsiveImageSettings.xml");
            assertNotNull("Archive does not contain expected item", entry);
        }
    }

}
