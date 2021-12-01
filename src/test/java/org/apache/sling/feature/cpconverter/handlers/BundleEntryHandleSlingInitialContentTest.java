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
import static org.xmlunit.assertj.XmlAssert.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarFile;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.SlingInitialContentPolicy;
import org.apache.sling.feature.cpconverter.artifacts.SimpleFolderArtifactsDeployer;
import org.apache.sling.feature.cpconverter.shared.ConverterConstants;
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
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
            Entry entry = archive.getEntry("jcr_root/apps/wcm-io/handler/media/components/global/include/responsiveImageSettings/.content.xml");
            assertNotNull("Archive does not contain expected item", entry);
        }
        // verify nothing else has been deployed
        assertEquals(2, targetFolder.list().length);
        // verify changed id
        Mockito.verify(featuresManager).addArtifact(null, ArtifactId.fromMvnId("io.wcm:io.wcm.handler.media:1.11.6-cp2fm-converted"), null);
    }

    @Test
    public void testSlingInitialContentWithNodeTypeAndPageJson() throws Exception {
        setUpArchive("/jcr_root/apps/mysite/install/mysite-slinginitialcontent-nodetype-def.jar", "mysite.core-1.0.0-SNAPSHOT-pagejson.jar");
        DefaultEntryHandlersManager handlersManager = new DefaultEntryHandlersManager(Collections.emptyMap(), false, SlingInitialContentPolicy.KEEP, ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT);
        converter.setEntryHandlersManager(handlersManager);
        Map<String, String> namespaceRegistry = new HashMap<>();

        namespaceRegistry.put("cq","http://www.day.com/jcr/cq/1.0");
        namespaceRegistry.put("granite", "http://www.adobe.com/jcr/granite/1.0");

        
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

        // verify generated package
        try (VaultPackage vaultPackage = new PackageManagerImpl().open(new File(targetFolder, "mysite.core-apps-1.0.0-SNAPSHOT-cp2fm-converted.zip"));
             Archive archive = vaultPackage.getArchive()) {
            archive.open(true);
            PackageId targetId = PackageId.fromString("com.mysite:mysite.core-apps:1.0.0-SNAPSHOT-cp2fm-converted");
            assertEquals(targetId, vaultPackage.getId());

            assertPageStructureFromEntry(archive, "jcr_root/apps/mysite/components/global", "homepage");
            assertPageStructureFromEntry(archive, "jcr_root/apps/mysite/components/global", "page", "body.html");
            assertPageStructureFromEntry(archive, "jcr_root/apps/mysite/components/global", "xfpage","body.html");
            
            InputStream inputStream = archive.getInputSource(archive.getEntry("jcr_root/apps/mysite/components/global/homepage/.content.xml")).getByteStream();

            //String expectedXML = IOUtils.toString(, StandardCharsets.UTF_8);
            //String actualXML   = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

            InputSource expectedXML = new InputSource(getClass().getResource("mysite-nodetype-and-page-json-xml-result.xml").openStream());
            InputSource actualXML = new InputSource(inputStream);

            
            assertThat(expectedXML).and(actualXML).areSimilar();
         
        }

    }


    @Test
    public void testSlingInitialContentWithSpecialCharacters() throws Exception {
        setUpArchive("/jcr_root/apps/mysite/install/mysite-slinginitialcontent-nodetype-def.jar", "mysite.core-1.0.0-SNAPSHOT-specialchars-json-inputstream.jar");
        DefaultEntryHandlersManager handlersManager = new DefaultEntryHandlersManager(Collections.emptyMap(), false, SlingInitialContentPolicy.KEEP, ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT);
        converter.setEntryHandlersManager(handlersManager);
        Map<String, String> namespaceRegistry = new HashMap<>();

        namespaceRegistry.put("cq","http://www.day.com/jcr/cq/1.0");
        namespaceRegistry.put("granite", "http://www.adobe.com/jcr/granite/1.0");


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

       
                
                
        // verify generated package
        try (VaultPackage vaultPackage = new PackageManagerImpl().open(new File(targetFolder, "mysite.core-apps-1.0.0-SNAPSHOT-cp2fm-converted.zip"));
             Archive archive = vaultPackage.getArchive()) {
            archive.open(true);
            PackageId targetId = PackageId.fromString("com.mysite:mysite.core-apps:1.0.0-SNAPSHOT-cp2fm-converted");
            assertEquals(targetId, vaultPackage.getId());

            //json containing a property: "fancyCharacters": "<&\"'>"
            assertPageStructureFromEntry(archive,"jcr_root/apps/mysite/components/global", "homepage" );
            assertResultingEntry(archive, "homepage");
            
            //xml with custom name element: nodeName and containing a textfile
            assertPageStructureFromEntry(archive,"jcr_root/apps/mysite/components/global", "nodeName", "testfile.txt" );
            assertResultingEntry(archive, "nodeName");
            
            //xml with custom "name" element "xyz"
            assertPageStructureFromEntry(archive, "jcr_root/apps/mysite/components/global", "xyz");
            assertResultingEntry(archive, "xyz");
            
            //xml with custom name element: 11&quot;&quot;&gt;&lt;mumbojumbo
            assertResultingEntry(archive, "11mumbojumbo");
            assertPageStructureFromEntry(archive,"jcr_root/apps/mysite/components/global", "11mumbojumbo" );
        }

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
            Entry entry = archive.getEntry("jcr_root/apps/myinitialcontentest/my-first-node/.content.xml");
            assertNotNull("Archive does not contain expected item", entry);
        }
        // verify nothing else has been deployed
        assertEquals(2, targetFolder.list().length);
        // verify changed id
        Mockito.verify(featuresManager).addArtifact(null, ArtifactId.fromMvnId("com.mysite:mysite.core:1.0.0-SNAPSHOT-cp2fm-converted"), null);
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
        Mockito.verify(featuresManager).addArtifact(null, ArtifactId.fromMvnId("com.composum.nodes:composum-nodes-config:2.5.3-cp2fm-converted"), null);
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
        Mockito.verify(featuresManager).addArtifact(null, ArtifactId.fromMvnId("com.composum.nodes:composum-nodes-config:2.5.3"), null);
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

    private void assertResultingEntry(Archive archive, String entryKey) throws IOException, SAXException {
        InputStream xmlFile = archive.getInputSource(archive.getEntry("jcr_root/apps/mysite/components/global/" + entryKey  +"/.content.xml")).getByteStream();
        InputSource expectedXML = new InputSource(getClass().getResourceAsStream("bundle-entry-xmls/" + entryKey + ".xml"));
        InputSource actualXML = new InputSource(xmlFile);

        assertThat(expectedXML).and(actualXML).areSimilar();
    }

    private void assertPageStructureFromEntry(Archive archive, String basePath, String pageName, String... files) throws IOException {
        Entry contentXml = archive.getEntry( basePath + "/" + pageName + "/.content.xml");
        assertNotNull(contentXml);
        Entry pageXml = archive.getEntry( basePath + "/" + pageName + ".xml");
        assertNull(pageXml);

        for(String file: files){
            Entry expectedEntry = archive.getEntry( basePath + "/" + pageName + "/" + file);
            assertNotNull(expectedEntry);
        }
    }

}
