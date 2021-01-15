/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.feature.cpconverter.accesscontrol;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.repoinit.parser.impl.RepoInitParserService;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EnforcePrincipalBasedTest {

    private final SystemUser systemUser = new SystemUser("user1", new RepoPath("/home/users/system/intermediate/usernode"), new RepoPath("/home/users/system/intermediate"));

    private AclManager aclManager;
    private Path tempDir;

    private VaultPackageAssembler assembler;
    private FeaturesManager fm;
    private Feature feature;

    @Before
    public void setUp() throws Exception {
        aclManager = new DefaultAclManager(true, "/home/users/system/some/subtree");
        tempDir = Files.createTempDirectory(getClass().getSimpleName());

        assembler = mock(VaultPackageAssembler.class);
        when(assembler.getEntry(anyString())).thenReturn(new File(System.getProperty("java.io.tmpdir")));
        feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));

        fm = Mockito.spy(new DefaultFeaturesManager(tempDir.toFile()));
        when(fm.getTargetFeature()).thenReturn(feature);
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
    public void testResourceBasedConversionWithoutForce() throws RepoInitParsingException {
        AclManager aclManager = new DefaultAclManager(false, "/home/users/system/cq:services"){
            @Override
            protected @Nullable String computePathWithTypes(@NotNull RepoPath path, @NotNull List<VaultPackageAssembler> packageAssemblers) {
                return "/content/feature(sling:Folder)";
            }
        };
        aclManager.addSystemUser(systemUser);

        RepoPath accessControlledPath = new RepoPath("/content/feature");
        aclManager.addAcl("user1", new AccessControlEntry(true, "jcr:read", accessControlledPath , false));

        aclManager.addRepoinitExtension(Arrays.asList(assembler), fm);

        Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(repoinitExtension);

        String expected =
                "create service user user1 with path /home/users/system/some/subtree/intermediate" + System.lineSeparator() +
                        "create path /content/feature(sling:Folder)" + System.lineSeparator() +
                        "set ACL for user1" + System.lineSeparator() +
                        "allow jcr:read on /content/feature" + System.lineSeparator() +
                        "end" + System.lineSeparator();

        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());

        aclManager = EnforcePrincipalBasedTest.this.aclManager;aclManager.addSystemUser(systemUser);
        feature.getExtensions().clear();

        accessControlledPath = new RepoPath("/content/feature");
        aclManager.addAcl("user1", new AccessControlEntry(true, "jcr:read", accessControlledPath , false));

        aclManager.addRepoinitExtension(Arrays.asList(assembler), fm);

        repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(repoinitExtension);

        expected =
                "create service user user1 with path /home/users/system/some/subtree/intermediate" + System.lineSeparator() +
                        "set principal ACL for user1" + System.lineSeparator() +
                        "allow jcr:read on /content/feature" + System.lineSeparator() +
                        "end" + System.lineSeparator();

        actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        repoInitParser = new RepoInitParserService();
        operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }

    @Test
    public void testResourceBasedConversion() throws RepoInitParsingException {
        aclManager.addSystemUser(systemUser);

        RepoPath accessControlledPath = new RepoPath("/content/feature");
        aclManager.addAcl("user1", new AccessControlEntry(true, "jcr:read", accessControlledPath , false));

        aclManager.addRepoinitExtension(Arrays.asList(assembler), fm);

        Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(repoinitExtension);

        String expected =
                "create service user user1 with path /home/users/system/some/subtree/intermediate" + System.lineSeparator() +
                "set principal ACL for user1" + System.lineSeparator() +
                "allow jcr:read on /content/feature" + System.lineSeparator() +
                "end" + System.lineSeparator();

        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }

    @Test
    public void testPrincipalBased() throws RepoInitParsingException {
        aclManager.addSystemUser(systemUser);

        RepoPath accessControlledPath = new RepoPath("/content/feature");
        aclManager.addAcl("user1", new AccessControlEntry(true, "jcr:read", accessControlledPath, true));

        aclManager.addRepoinitExtension(Arrays.asList(assembler), fm);

        Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(repoinitExtension);

        String expected =
                "create service user user1 with path /home/users/system/some/subtree/intermediate" + System.lineSeparator() +
                        "set principal ACL for user1" + System.lineSeparator() +
                        "allow jcr:read on /content/feature" + System.lineSeparator() +
                        "end" + System.lineSeparator();

        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }

    @Test
    public void testPrincipalBasedForUserHome() throws RepoInitParsingException {
        aclManager.addSystemUser(systemUser);

        RepoPath accessControlledPath = new RepoPath("/home/users/system/some/subtree/intermediate/usernode");
        AccessControlEntry acl = new AccessControlEntry(true, "jcr:read", accessControlledPath, true);
        aclManager.addAcl("user1", acl);

        aclManager.addRepoinitExtension(Arrays.asList(assembler), fm);

        Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(repoinitExtension);

        String expected =
                "create service user user1 with path /home/users/system/some/subtree/intermediate" + System.lineSeparator() +
                "set principal ACL for user1" + System.lineSeparator() +
                "allow jcr:read on home(user1)" + System.lineSeparator() +
                "end" + System.lineSeparator();

        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }
}
