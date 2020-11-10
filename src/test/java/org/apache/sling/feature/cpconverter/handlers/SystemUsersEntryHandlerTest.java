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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.Arrays;
import java.io.StringReader;
import java.util.List;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.acl.DefaultAclManager;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.repoinit.parser.impl.RepoInitParserService;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SystemUsersEntryHandlerTest {

    private SystemUsersEntryHandler systemUsersEntryHandler;

    @Before
    public void setUp() {
        systemUsersEntryHandler = new SystemUsersEntryHandler();
    }

    @After
    public void tearDown() {
        systemUsersEntryHandler = null;
    }

    @Test
    public void doesNotMatch() {
        assertFalse(systemUsersEntryHandler.matches("/this/is/a/path/not/pointing/to/a/valid/configuration.asd"));
        assertFalse(systemUsersEntryHandler.matches("/home/users/system/asd-share-commons/asd-index-definition-reader/.content.xml"));
    }

    @Test
    public void matches() {
        assertTrue(systemUsersEntryHandler.matches("/jcr_root/home/users/system/asd-share-commons/asd-index-definition-reader/.content.xml"));
    }

    @Test
    public void parseSystemUser() throws Exception {
        String path = "/jcr_root/home/users/system/asd-share-commons/asd-index-definition-reader/.content.xml";
        Extension repoinitExtension = parseAndSetRepoinit(path);

        assertNotNull(repoinitExtension);
        assertEquals(ExtensionType.TEXT, repoinitExtension.getType());
        assertTrue(repoinitExtension.isRequired());

        String expected = "create path (rep:AuthorizableFolder) /home/users/system/asd-share-commons" + System.lineSeparator() + // SLING-8586
                "create service user asd-share-commons-asd-index-definition-reader-service with path /home/users/system/asd-share-commons" + System.lineSeparator();
        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }

    @Test
    public void unrecognisedSystemUserJcrNode() throws Exception {
        String path = "/jcr_root/home/users/system/asd-share-commons/asd-index-definition-invalid/.content.xml";
        Extension repoinitExtension = parseAndSetRepoinit(path);
        assertNull(repoinitExtension);
    }

    private Extension parseAndSetRepoinit(String path) throws Exception {
        Archive archive = mock(Archive.class);
        Entry entry = mock(Entry.class);
        VaultPackageAssembler packageAssembler = mock(VaultPackageAssembler.class);

        when(archive.openInputStream(entry)).thenReturn(getClass().getResourceAsStream(path.substring(1)));

        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));
        FeaturesManager featuresManager = spy(DefaultFeaturesManager.class);
        when(featuresManager.getTargetFeature()).thenReturn(feature);
        ContentPackage2FeatureModelConverter converter = spy(ContentPackage2FeatureModelConverter.class);
        when(converter.getFeaturesManager()).thenReturn(featuresManager);
        when(converter.getAclManager()).thenReturn(new DefaultAclManager());

        systemUsersEntryHandler.handle(path, archive, entry, converter);

        when(packageAssembler.getEntry(anyString())).thenReturn(new File("itdoesnotexist"));

        converter.getAclManager().addRepoinitExtension(Arrays.asList(packageAssembler), featuresManager);
        return feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
    }

}
