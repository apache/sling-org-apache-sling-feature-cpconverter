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
package org.apache.sling.feature.cpconverter.accesscontrol;

import static org.apache.sling.feature.cpconverter.Util.createServiceUserStatement;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.Util;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.shared.ConverterConstants;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.repoinit.parser.impl.RepoInitParserService;
import org.apache.sling.repoinit.parser.operations.CreatePath;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

@RunWith(Parameterized.class)
public class AclManagerTest {

    private final boolean enforcePath;

    private AclManager aclManager;
    private Path tempDir;
    
    @Parameterized.Parameters(name = "name={1}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(
                new Object[] {true, "Always enforce system user path = true"},
                new Object[] {false, "Always enforce system user path = false"});
    }

    public AclManagerTest(boolean enforcePath, @NotNull String name) {
        this.enforcePath = enforcePath;
    }
    
    @Before
    public void setUp() throws Exception {
        aclManager = new DefaultAclManager(null, ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT, enforcePath);
        tempDir = Files.createTempDirectory(getClass().getSimpleName());
    }

    @After
    public void tearDown() throws Exception {
        aclManager = null;

        // Delete the temp dir again
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

    @Test
    public void makeSureAclsAreCreatedOnlyoutsideSytemUsersPaths() throws Exception {
        aclManager.addSystemUser(new SystemUser("acs-commons-package-replication-status-event-service", new RepoPath("/home/users/system/foo"), new RepoPath("/home/users/system")));

        aclManager.addAccessControlEntry("acs-commons-package-replication-status-event-service", newAccessControlEntry(true, "jcr:read,rep:write,rep:indexDefinitionManagement", "/_sling_tests/not/system/user/path"));
        aclManager.addAccessControlEntry("acs-commons-package-replication-status-event-service", newAccessControlEntry(true, "jcr:read,crx:replicate,jcr:removeNode", "/home/users/system"));

        VaultPackageAssembler assembler = mock(VaultPackageAssembler.class);
        when(assembler.getFileEntry(anyString())).thenReturn(tempDir.toFile());
        when(assembler.getFileEntry("/_sling_tests/not/.content.xml")).thenReturn(new File(getClass().getResource("_sling_tests/not/.content.xml").getFile()));


        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));

        FeaturesManager fm = Mockito.spy(new DefaultFeaturesManager(tempDir.toFile()));
        when(fm.getTargetFeature()).thenReturn(feature);

        aclManager.addRepoinitExtension(Collections.singletonList(assembler), fm);


        Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(repoinitExtension);

        // acs-commons-on-deploy-scripts-service will be missed
        String expected = "# origin= source=content-package" + System.lineSeparator() + Util.normalize(
                createServiceUserStatement(enforcePath, "acs-commons-package-replication-status-event-service", "system") +
                        "create path /sling:tests/not(nt:unstructured mixin rep:AccessControllable,mix:created)/system/user/path\n" +
                        "set ACL for acs-commons-package-replication-status-event-service\n" + 
                        "    allow jcr:read,rep:write,rep:indexDefinitionManagement on /sling:tests/not/system/user/path\n" +
                        "    allow jcr:read,crx:replicate,jcr:removeNode on /home/users/system\n" +
                        "end\n");
        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }

    @Test
    public void testReset() throws Exception {
        // We assume this user will not be in the result because of the reset in the next line
        aclManager.addSystemUser(new SystemUser("acs-commons-ensure-oak-index-service", new RepoPath("/home/users/system/foo"), new RepoPath("/home/users/system")));

        // emulate a second iteration of conversion
        aclManager.reset();

        aclManager.addSystemUser(new SystemUser("acs-commons-package-replication-status-event-service", new RepoPath("/home/users/system/foo"), new RepoPath("/home/users/system")));
        aclManager.addAccessControlEntry("acs-commons-package-replication-status-event-service", newAccessControlEntry(true, "jcr:read,rep:write,rep:indexDefinitionManagement", "/_sling_tests/not/system/user/path"));

        VaultPackageAssembler assembler = mock(VaultPackageAssembler.class);
        when(assembler.getFileEntry(anyString())).thenReturn(tempDir.toFile());
        when(assembler.getFileEntry("/_sling_tests/not/.content.xml")).thenReturn(new File(getClass().getResource("_sling_tests/not/.content.xml").getFile()));

        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));

        FeaturesManager fm = Mockito.spy(new DefaultFeaturesManager(tempDir.toFile()));
        when(fm.getTargetFeature()).thenReturn(feature);

        aclManager.addRepoinitExtension(Collections.singletonList(assembler), fm);


        Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(repoinitExtension);

        // aacs-commons-ensure-oak-index-service will be missed
        String expected = "# origin= source=content-package" + System.lineSeparator() + Util.normalize(
                createServiceUserStatement(enforcePath, "acs-commons-package-replication-status-event-service","system") +
                "create path /sling:tests/not(nt:unstructured mixin rep:AccessControllable,mix:created)/system/user/path\n" +
                "set ACL for acs-commons-package-replication-status-event-service\n" +
                "    allow jcr:read,rep:write,rep:indexDefinitionManagement on /sling:tests/not/system/user/path\n" +
                "end\n");
        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }

    @Test
    public void testAddACLforUnknownUser() throws Exception {
        // we expect this acl to not show up because the user is unknown
        aclManager.addAccessControlEntry("acs-commons-on-deploy-scripts-service", newAccessControlEntry(true, "jcr:read,crx:replicate,jcr:removeNode", "/home/users/system"));

        VaultPackageAssembler assembler = mock(VaultPackageAssembler.class);
        when(assembler.getFileEntry(anyString())).thenReturn(new File(System.getProperty("java.io.tmpdir")));
        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));

        FeaturesManager fm = Mockito.spy(new DefaultFeaturesManager(tempDir.toFile()));
        when(fm.getTargetFeature()).thenReturn(feature);

        aclManager.addRepoinitExtension(Collections.singletonList(assembler), fm);


        Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNull(repoinitExtension);
    }

    @Test
    public void pathWithSpecialCharactersTest() throws Exception {
        aclManager.addSystemUser(new SystemUser("sys-usr", new RepoPath("/home/users/system/foo"), new RepoPath("/home/users/system")));
        aclManager.addAccessControlEntry("sys-usr", newAccessControlEntry(true, "jcr:read", "/content/_cq_tags"));
        aclManager.addAccessControlEntry("sys-usr", newAccessControlEntry(true, "jcr:write", "/content/cq:tags"));
        VaultPackageAssembler assembler = mock(VaultPackageAssembler.class);
        when(assembler.getFileEntry(anyString())).thenReturn(new File(System.getProperty("java.io.tmpdir")));
        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));

        FeaturesManager fm = Mockito.spy(new DefaultFeaturesManager(tempDir.toFile()));
        when(fm.getTargetFeature()).thenReturn(feature);

        aclManager.addRepoinitExtension(Collections.singletonList(assembler), fm);

        Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(repoinitExtension);

        String expected = "# origin= source=content-package" + System.lineSeparator() + Util.normalize(
                createServiceUserStatement(enforcePath, "sys-usr", "system") +
                "create path /content/cq:tags\n"+
                "set ACL for sys-usr\n" +
                "    allow jcr:read on /content/cq:tags\n" +
                "    allow jcr:write on /content/cq:tags\n" +
                "end\n");

        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }

    @Test(expected = ConverterException.class)
    public void testGroupHandlingWithGroupUsed() throws Exception {
        aclManager.addSystemUser(new SystemUser("sys-usr", new RepoPath("/home/users/system/foo"), new RepoPath("/home/users/system")));

        aclManager.addGroup(new Group("test", new RepoPath("/home/groups/test"),  new RepoPath("/home/groups/test")));
        aclManager.addAccessControlEntry("sys-usr", newAccessControlEntry(true, "jcr:read", "/home/groups/test"));
        VaultPackageAssembler assembler = mock(VaultPackageAssembler.class);
        when(assembler.getFileEntry(anyString())).thenReturn(new File(System.getProperty("java.io.tmpdir")));
        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));

        FeaturesManager fm = Mockito.spy(new DefaultFeaturesManager(tempDir.toFile()));
        when(fm.getTargetFeature()).thenReturn(feature);

        aclManager.addRepoinitExtension(Collections.singletonList(assembler), fm);

        Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
    }

    @Test
    public void testGroupHandlingWithGroupNotUsed() throws Exception {
        aclManager.addSystemUser(new SystemUser("sys-usr", new RepoPath("/home/users/system/foo"), new RepoPath("/home/users/system")));

        aclManager.addGroup(new Group("test", new RepoPath("/home/groups/test"),  new RepoPath("/home/groups/test")));
        aclManager.addAccessControlEntry("sys-usr", newAccessControlEntry(true, "jcr:read", "/content/test"));
        VaultPackageAssembler assembler = mock(VaultPackageAssembler.class);
        when(assembler.getFileEntry(anyString())).thenReturn(new File(System.getProperty("java.io.tmpdir")));
        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));

        FeaturesManager fm = Mockito.spy(new DefaultFeaturesManager(tempDir.toFile()));
        when(fm.getTargetFeature()).thenReturn(feature);

        aclManager.addRepoinitExtension(Collections.singletonList(assembler), fm);

        Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(repoinitExtension);

        String expected = "# origin= source=content-package" + System.lineSeparator() + Util.normalize(
                createServiceUserStatement(enforcePath, "sys-usr", "system") +
                        "create path /content/test\n" +
                        "set ACL for sys-usr\n" +
                        "    allow jcr:read on /content/test\n" +
                        "end\n");

        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

    }

    @Test(expected = ConverterException.class)
    public void testGroupHandlingWithGroupMatchingSubPath() throws Exception {
        aclManager.addSystemUser(new SystemUser("sys-usr", new RepoPath("/home/users/system/foo"), new RepoPath("/home/users/system")));

        aclManager.addGroup(new Group("test", new RepoPath("/home/groups/test"),  new RepoPath("/home/groups/test")));
        aclManager.addAccessControlEntry("sys-usr", newAccessControlEntry(true, "jcr:read", "/home/groups/test/foo"));
        VaultPackageAssembler assembler = mock(VaultPackageAssembler.class);
        when(assembler.getFileEntry(anyString())).thenReturn(new File(System.getProperty("java.io.tmpdir")));
        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));

        FeaturesManager fm = Mockito.spy(new DefaultFeaturesManager(tempDir.toFile()));
        when(fm.getTargetFeature()).thenReturn(feature);
        aclManager.addRepoinitExtension(Collections.singletonList(assembler), fm);
    }

    @Test(expected = ConverterException.class)
    public void testUserHandlingWithMatchingUser() throws Exception {
        aclManager.addSystemUser(new SystemUser("sys-usr", new RepoPath("/home/users/system/foo"), new RepoPath("/home/users/system")));

        aclManager.addUser(new User("test", new RepoPath("/home/users/test"),  new RepoPath("/home/users/test")));
        aclManager.addAccessControlEntry("sys-usr", newAccessControlEntry(true, "jcr:read", "/home/users/test/foo"));
        VaultPackageAssembler assembler = mock(VaultPackageAssembler.class);
        when(assembler.getFileEntry(anyString())).thenReturn(new File(System.getProperty("java.io.tmpdir")));
        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));

        FeaturesManager fm = Mockito.spy(new DefaultFeaturesManager(tempDir.toFile()));
        when(fm.getTargetFeature()).thenReturn(feature);
        aclManager.addRepoinitExtension(Collections.singletonList(assembler), fm);
    }

    @Test
    public void testUserHandlingWithNonMatchingUser() throws Exception {
        aclManager.addSystemUser(new SystemUser("sys-usr", new RepoPath("/home/users/system/foo"), new RepoPath("/home/users/system")));

        aclManager.addUser(new User("test", new RepoPath("/home/users/test"),  new RepoPath("/home/users/test")));
        aclManager.addAccessControlEntry("sys-usr", newAccessControlEntry(true, "jcr:read", "/content/test"));
        VaultPackageAssembler assembler = mock(VaultPackageAssembler.class);
        when(assembler.getFileEntry(anyString())).thenReturn(new File(System.getProperty("java.io.tmpdir")));
        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));

        FeaturesManager fm = Mockito.spy(new DefaultFeaturesManager(tempDir.toFile()));
        when(fm.getTargetFeature()).thenReturn(feature);

        aclManager.addRepoinitExtension(Collections.singletonList(assembler), fm);

        Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(repoinitExtension);

        String expected = "# origin= source=content-package" + System.lineSeparator() + Util.normalize(
                createServiceUserStatement(enforcePath, "sys-usr", "system") +
                        "create path /content/test\n" +
                        "set ACL for sys-usr\n" +
                        "    allow jcr:read on /content/test\n" +
                        "end\n");

        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);
    }

    @Test
    public void testPathHandlingWithUser() throws Exception {
        aclManager.addSystemUser(new SystemUser("sys-usr", new RepoPath("/home/users/system/foo"), new RepoPath("/home/users/system")));

        aclManager.addUser(new User("test", new RepoPath("/home/users/test"),  new RepoPath("/home/users/test")));
        aclManager.addAccessControlEntry("sys-usr", newAccessControlEntry(true, "jcr:read", "/home/users/notMatching"));
        VaultPackageAssembler assembler = mock(VaultPackageAssembler.class);
        when(assembler.getFileEntry(anyString())).thenReturn(new File(System.getProperty("java.io.tmpdir")));
        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));

        FeaturesManager fm = Mockito.spy(new DefaultFeaturesManager(tempDir.toFile()));
        when(fm.getTargetFeature()).thenReturn(feature);

        aclManager.addRepoinitExtension(Collections.singletonList(assembler), fm);

        Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(repoinitExtension);

        // in contrast to testUserHandlingWithMatchingUser in this test /home/users/test2 is not detected 
        // as user-home-path and thus is processed like a regular path (no 'home(uid)' repo-init statement and no exception).\
        // however, no attempt is made to create the path without any available node type information.
        String expected = "# origin= source=content-package" + System.lineSeparator() + Util.normalize(
                createServiceUserStatement(enforcePath, "sys-usr", "system") +
                        "set ACL for sys-usr\n" +
                        "    allow jcr:read on /home/users/notMatching\n" +
                        "end\n");

        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);
    }

    @Test(expected = ConverterException.class)
    public void testAddRepoinitExtentionInvalidTxt() throws Exception {
        DefaultAclManager aclManager = new DefaultAclManager("/home/users/system/cq:services", "system", false);
        aclManager.addRepoinitExtention("test", "some invalid txt", null, mock(FeaturesManager.class));
    }

    @Test
    public void testAddRepoinitExtentionEmptyTxt()throws Exception {
        FeaturesManager fm = mock(FeaturesManager.class);
        DefaultAclManager aclManager = new DefaultAclManager("/home/users/system/cq:services", "system", false);
        aclManager.addRepoinitExtention("test", "", null, fm);

        verifyNoInteractions(fm);
    }

    @Test
    public void testAddRepoinitExtentionNullTxt() throws Exception {
        FeaturesManager fm = mock(FeaturesManager.class);
        DefaultAclManager aclManager = new DefaultAclManager("/home/users/system/cq:services", "system", false);
        aclManager.addRepoinitExtention("test", null, null, fm);

        verifyNoInteractions(fm);
    }

    private static AccessControlEntry newAccessControlEntry(boolean isAllow, String privileges, String path) {
        return new AccessControlEntry(isAllow, Arrays.asList(privileges.split(",")), new RepoPath(PlatformNameFormat.getRepositoryPath(path)));
    }

    @Test
    public void testGetCreatePathForRootNode() throws Exception {
        RepoPath rootPath = new RepoPath("/");
        DefaultAclManager aclManager = new DefaultAclManager();
        CreatePath cp = aclManager.getCreatePath(rootPath, Collections.emptyList());
        assertNull(cp);
    }

    @Test
    public void testGetCreatePathForRepositoryPath() throws Exception {
        RepoPath repoPath = new RepoPath("");
        assertTrue(repoPath.isRepositoryPath());
        
        DefaultAclManager aclManager = new DefaultAclManager();
        CreatePath cp = aclManager.getCreatePath(repoPath, Collections.emptyList());
        assertNull(cp);
    }

    @Test
    public void testGetCreatePathForToplevel() throws Exception {
        RepoPath toplevel = new RepoPath("/apps");
        DefaultAclManager aclManager = new DefaultAclManager();
        CreatePath cp = aclManager.getCreatePath(toplevel, Collections.emptyList());
        assertNotNull(cp);
    }

    @Test
    public void testGetCreatePathForPathBelowUserRoot() throws Exception {
        RepoPath userPath = new RepoPath("/home/user/system/feature/usernode");
        DefaultAclManager aclManager = new DefaultAclManager();
        aclManager.addSystemUser(new SystemUser("systemUser", userPath, userPath.getParent()));
        
        CreatePath cp = aclManager.getCreatePath(new RepoPath("/home/somenode"), Collections.emptyList());
        assertNull(cp);
        cp = aclManager.getCreatePath(new RepoPath("/home"), Collections.emptyList());
        assertNull(cp);
    }
}
