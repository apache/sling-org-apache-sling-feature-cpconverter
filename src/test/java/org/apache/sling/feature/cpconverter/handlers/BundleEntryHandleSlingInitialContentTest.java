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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.SlingInitialContentPolicy;
import org.apache.sling.feature.cpconverter.artifacts.SimpleFolderArtifactsDeployer;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

@RunWith(MockitoJUnitRunner.class)
public class BundleEntryHandleSlingInitialContentTest extends AbstractBundleEntryHandlerTest {

    @Captor
    ArgumentCaptor<Dictionary<String, Object>> dictionaryCaptor;

    @Captor
    ArgumentCaptor<Configuration> cfgCaptor;

    @Test
    public void testSlingInitialContent() throws Exception {
        setUpArchive("/jcr_root/apps/gav/install/io.wcm.handler.media-1.11.6.jar", "io.wcm.handler.media-1.11.6.jar");
        DefaultEntryHandlersManager handlersManager = new DefaultEntryHandlersManager();
        converter.setEntryHandlersManager(handlersManager);
        Map<String, String> namespaceRegistry = Collections.singletonMap("granite", "http://www.adobe.com/jcr/granite/1.0");
        when(featuresManager.getNamespaceUriByPrefix()).thenReturn(namespaceRegistry);

        File targetFolder = tmpFolder.newFolder();
        when(converter.getArtifactsDeployer()).thenReturn(new SimpleFolderArtifactsDeployer(targetFolder));
        when(converter.isSubContentPackageIncluded("/jcr_root/apps/gav/install/io.wcm.handler.media-1.11.6.jar-APPLICATION")).thenReturn(true);

        VaultPackageAssembler assembler = Mockito.mock(VaultPackageAssembler.class);
        Properties props = new Properties();
        props.setProperty(PackageProperties.NAME_GROUP, "io.wcm");
        props.setProperty(PackageProperties.NAME_NAME, "handler.media");
        props.setProperty(PackageProperties.NAME_VERSION, "1.11.6");
        when(assembler.getPackageProperties()).thenReturn(props);
        converter.setMainPackageAssembler(assembler);

        handler.setSlingInitialContentPolicy(SlingInitialContentPolicy.EXTRACT_AND_REMOVE);
        handler.handle("/jcr_root/apps/gav/install/io.wcm.handler.media-1.11.6.jar", archive, entry, converter);

        converter.deployPackages();
        // verify generated bundle
        try (JarFile jarFile = new JarFile(new File(targetFolder, "io.wcm.handler.media-1.11.6-cp2fm-converted.jar"))) {
            String bundleVersion = jarFile.getManifest().getMainAttributes().getValue(Constants.BUNDLE_VERSION);
            assertNotNull(bundleVersion);
            assertNull(jarFile.getManifest().getMainAttributes().getValue("Sling-Initial-Content"));
            assertEquals("_cp2fm-converted", Version.parseVersion(bundleVersion).getQualifier());
            // make sure the initial content is no longer contained
            assertNull(jarFile.getEntry("SLING-INF/app-root/"));
        }
        // verify generated package
        try (VaultPackage vaultPackage = new PackageManagerImpl().open(new File(targetFolder, "io.wcm.handler.media-apps-1.11.6-cp2fm-converted.zip"));
             Archive archive = vaultPackage.getArchive()) {
            archive.open(true);
            PackageId targetId = PackageId.fromString("io.wcm:io.wcm.handler.media-apps:1.11.6-cp2fm-converted");
            assertEquals(targetId, vaultPackage.getId());
            Entry entry = archive.getEntry("jcr_root/apps/wcm-io/handler/media/components/global/include/responsiveImageSettings.xml");
            assertNotNull("Archive does not contain expected item", entry);
        }
        // verify nothing else has been deployed
        assertEquals(2, targetFolder.list().length);
        // verify changed id
        ArgumentCaptor<Artifact> captor = ArgumentCaptor.forClass(Artifact.class);
        Mockito.verify(featuresManager).addArtifact(Mockito.isNull(), captor.capture(), Mockito.isNull());
        final Artifact result = captor.getValue();
        assertNotNull(result);
        assertEquals(ArtifactId.fromMvnId("io.wcm:io.wcm.handler.media:1.11.6-cp2fm-converted"), result.getId());
        assertEquals("io.wcm.handler.media", result.getMetadata().get(Constants.BUNDLE_SYMBOLICNAME));
        assertEquals("1.11.6", result.getMetadata().get(Constants.BUNDLE_VERSION));
    }


    @Test
    public void testSlingInitialContentWithNodeType() throws Exception {
        setUpArchive("/jcr_root/apps/mysite/install/mysite-slinginitialcontent-nodetype-def.jar", "mysite-slinginitialcontent-nodetype-def.jar");
        DefaultEntryHandlersManager handlersManager = new DefaultEntryHandlersManager();
        converter.setEntryHandlersManager(handlersManager);
        Map<String, String> namespaceRegistry = Collections.singletonMap("granite", "http://www.adobe.com/jcr/granite/1.0");
        when(featuresManager.getNamespaceUriByPrefix()).thenReturn(namespaceRegistry);

        File targetFolder = tmpFolder.newFolder();
        when(converter.getArtifactsDeployer()).thenReturn(new SimpleFolderArtifactsDeployer(targetFolder));
        when(converter.isSubContentPackageIncluded("/jcr_root/apps/mysite/install/mysite-slinginitialcontent-nodetype-def.jar-APPLICATION")).thenReturn(true);

        VaultPackageAssembler assembler = Mockito.mock(VaultPackageAssembler.class);
        Properties props = new Properties();
        props.setProperty(PackageProperties.NAME_GROUP, "com.mysite");
        props.setProperty(PackageProperties.NAME_NAME, "mysite.core");
        props.setProperty(PackageProperties.NAME_VERSION, "1.0.0-SNAPSHOT");
        when(assembler.getPackageProperties()).thenReturn(props);
        converter.setMainPackageAssembler(assembler);

        handler.setSlingInitialContentPolicy(SlingInitialContentPolicy.EXTRACT_AND_REMOVE);
        handler.handle("/jcr_root/apps/mysite/install/mysite-slinginitialcontent-nodetype-def.jar", archive, entry, converter);

        converter.deployPackages();
        // verify generated bundle
        try (JarFile jarFile = new JarFile(new File(targetFolder, "mysite.core-1.0.0-SNAPSHOT-cp2fm-converted.jar"))) {
            String bundleVersion = jarFile.getManifest().getMainAttributes().getValue(Constants.BUNDLE_VERSION);
            assertNotNull(bundleVersion);
            assertNull(jarFile.getManifest().getMainAttributes().getValue("Sling-Initial-Content"));
            assertEquals("SNAPSHOT_cp2fm-converted", Version.parseVersion(bundleVersion).getQualifier());
            // make sure the initial content is no longer contained
            assertNull(jarFile.getEntry("SLING-INF/app-root/"));
        }
        // verify generated package
        try (VaultPackage vaultPackage = new PackageManagerImpl().open(new File(targetFolder, "mysite.core-apps-1.0.0-SNAPSHOT-cp2fm-converted.zip")); 
            Archive archive = vaultPackage.getArchive()) {
            archive.open(true);
            PackageId targetId = PackageId.fromString("com.mysite:mysite.core-apps:1.0.0-SNAPSHOT-cp2fm-converted");
            assertEquals(targetId, vaultPackage.getId());
            Entry entry = archive.getEntry("jcr_root/apps/myinitialcontentest/my-first-node.xml");
            assertNotNull("Archive does not contain expected item", entry);
        }
        // verify nothing else has been deployed
        assertEquals(2, targetFolder.list().length);
        // verify changed id
        ArgumentCaptor<Artifact> captor = ArgumentCaptor.forClass(Artifact.class);
        Mockito.verify(featuresManager).addArtifact(Mockito.isNull(), captor.capture(), Mockito.isNull());
        final Artifact result = captor.getValue();
        assertNotNull(result);
        assertEquals(ArtifactId.fromMvnId("com.mysite:mysite.core:1.0.0-SNAPSHOT-cp2fm-converted"), result.getId());
        assertEquals("mysite.core", result.getMetadata().get(Constants.BUNDLE_SYMBOLICNAME));
        assertEquals("1.0.0.SNAPSHOT", result.getMetadata().get(Constants.BUNDLE_VERSION));
    }

    @Test
    public void testSlingInitialContentContainingConfigurationExtractAndRemove() throws Exception {
        setUpArchive("/jcr_root/apps/gav/install/composum-nodes-config-2.5.3.jar", "composum-nodes-config-2.5.3.jar");
        DefaultEntryHandlersManager handlersManager = new DefaultEntryHandlersManager();
        converter.setEntryHandlersManager(handlersManager);
        when(converter.isSubContentPackageIncluded("/jcr_root/apps/gav/install/composum-nodes-config-2.5.3.jar-APPLICATION")).thenReturn(true);
        VaultPackageAssembler assembler = Mockito.mock(VaultPackageAssembler.class);
        Properties props = new Properties();
        props.setProperty(PackageProperties.NAME_GROUP, "io.wcm");
        props.setProperty(PackageProperties.NAME_NAME, "handler.media");
        props.setProperty(PackageProperties.NAME_VERSION, "1.11.6");
        when(assembler.getPackageProperties()).thenReturn(props);
        converter.setMainPackageAssembler(assembler);
        handler.setSlingInitialContentPolicy(SlingInitialContentPolicy.EXTRACT_AND_REMOVE);
        handler.handle("/jcr_root/apps/gav/install/composum-nodes-config-2.5.3.jar", archive, entry, converter);
        // modified bundle
        ArgumentCaptor<Artifact> captor = ArgumentCaptor.forClass(Artifact.class);
        Mockito.verify(featuresManager).addArtifact(Mockito.isNull(), captor.capture(), Mockito.isNull());
        final Artifact result = captor.getValue();
        assertNotNull(result);
        assertEquals(ArtifactId.fromMvnId("com.composum.nodes:composum-nodes-config:2.5.3-cp2fm-converted"), result.getId());
        assertEquals("com.composum.nodes.config", result.getMetadata().get(Constants.BUNDLE_SYMBOLICNAME));
        assertEquals("2.5.3", result.getMetadata().get(Constants.BUNDLE_VERSION));

        // need to use ArgumentCaptur to properly compare string arrays
        Mockito.verify(featuresManager).addConfiguration(ArgumentMatchers.isNull(), cfgCaptor.capture(), ArgumentMatchers.eq("/jcr_root/libs/composum/nodes/install/org.apache.sling.jcr.base.internal.LoginAdminWhitelist.fragment-composum_core_v2.config"), dictionaryCaptor.capture());
        assertEquals("composum_core", dictionaryCaptor.getValue().get("whitelist.name"));
        assertEquals("org.apache.sling.jcr.base.internal.LoginAdminWhitelist.fragment~composum_core_v2", cfgCaptor.getValue().getPid());
        assertArrayEquals(new String[] {
                "com.composum.nodes.commons",
                "com.composum.nodes.pckgmgr",
                "com.composum.nodes.pckginstall" }, (String[])dictionaryCaptor.getValue().get("whitelist.bundles"));
    }

    @Test
    public void testSlingInitialContentContainingConfigurationExtractAndKeep() throws Exception {
        setUpArchive("/jcr_root/apps/gav/install/composum-nodes-config-2.5.3.jar", "composum-nodes-config-2.5.3.jar");
        DefaultEntryHandlersManager handlersManager = new DefaultEntryHandlersManager();
        converter.setEntryHandlersManager(handlersManager);
        when(converter.isSubContentPackageIncluded("/jcr_root/apps/gav/install/composum-nodes-config-2.5.3.jar-APPLICATION")).thenReturn(true);
        VaultPackageAssembler assembler = Mockito.mock(VaultPackageAssembler.class);
        Properties props = new Properties();
        props.setProperty(PackageProperties.NAME_GROUP, "io.wcm");
        props.setProperty(PackageProperties.NAME_NAME, "handler.media");
        props.setProperty(PackageProperties.NAME_VERSION, "1.11.6");
        when(assembler.getPackageProperties()).thenReturn(props);
        converter.setMainPackageAssembler(assembler);
        handler.setSlingInitialContentPolicy(SlingInitialContentPolicy.EXTRACT_AND_KEEP);
        handler.handle("/jcr_root/apps/gav/install/composum-nodes-config-2.5.3.jar", archive, entry, converter);
        // original bundle
        ArgumentCaptor<Artifact> captor = ArgumentCaptor.forClass(Artifact.class);
        Mockito.verify(featuresManager).addArtifact(Mockito.isNull(), captor.capture(), Mockito.isNull());
        final Artifact result = captor.getValue();
        assertNotNull(result);
        assertEquals(ArtifactId.fromMvnId("com.composum.nodes:composum-nodes-config:2.5.3"), result.getId());
        assertEquals("com.composum.nodes.config", result.getMetadata().get(Constants.BUNDLE_SYMBOLICNAME));
        assertEquals("2.5.3", result.getMetadata().get(Constants.BUNDLE_VERSION));
        // need to use ArgumentCaptur to properly compare string arrays
        Mockito.verify(featuresManager).addConfiguration(ArgumentMatchers.isNull(),
             cfgCaptor.capture(),
             ArgumentMatchers.eq("/jcr_root/libs/composum/nodes/install/org.apache.sling.jcr.base.internal.LoginAdminWhitelist.fragment-composum_core_v2.config"), dictionaryCaptor.capture());
        assertEquals("composum_core", dictionaryCaptor.getValue().get("whitelist.name"));
        assertEquals("org.apache.sling.jcr.base.internal.LoginAdminWhitelist.fragment~composum_core_v2", cfgCaptor.getValue().getPid());
        assertArrayEquals(new String[] {
                "com.composum.nodes.commons",
                "com.composum.nodes.pckgmgr",
                "com.composum.nodes.pckginstall" }, (String[])dictionaryCaptor.getValue().get("whitelist.bundles"));
    }
}
