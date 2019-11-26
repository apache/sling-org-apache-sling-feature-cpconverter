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
package org.apache.sling.feature.cpconverter.acl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.repoinit.parser.impl.RepoInitParserService;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.Spy;

public class AclManagerTest {

    private AclManager aclManager;

    @Before
    public void setUp() {
        aclManager = new DefaultAclManager();
    }

    @After
    public void tearDown() {
        aclManager = null;
    }

    @Test
    public void makeSureAclsAreCreatedOnlyoutsideSytemUsersPaths() throws Exception {
        aclManager.addSystemUser(new SystemUser("acs-commons-ensure-oak-index-service", Paths.get("/asd/public")));

        // emulate a second iteration of conversion
        aclManager.reset();

        aclManager.addSystemUser(new SystemUser("acs-commons-package-replication-status-event-service", Paths.get("/asd/public")));

        aclManager.addAcl("acs-commons-ensure-oak-index-service", newAcl("allow", "jcr:read,rep:write,rep:indexDefinitionManagement", "/asd/not/system/user/path"));
        aclManager.addAcl("acs-commons-package-replication-status-event-service", newAcl("allow", "jcr:read,crx:replicate,jcr:removeNode", "/asd/public"));

        // add an ACL for unknown user
        aclManager.addAcl("acs-commons-on-deploy-scripts-service", newAcl("allow", "jcr:read,crx:replicate,jcr:removeNode", "/asd/public"));

        VaultPackageAssembler assembler = mock(VaultPackageAssembler.class);
        when(assembler.getEntry(anyString())).thenReturn(new File(System.getProperty("java.io.tmpdir")));
        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));

        FeaturesManager fm = Mockito.spy(new DefaultFeaturesManager());
        when(fm.getTargetFeature()).thenReturn(feature);
        
        aclManager.addRepoinitExtension(Arrays.asList(assembler), fm);

        
        Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(repoinitExtension);

        // acs-commons-on-deploy-scripts-service will be missed
        String expected = "create path (rep:AuthorizableFolder) /asd/public\n" + // SLING-8586
                "create service user acs-commons-package-replication-status-event-service with path /asd/public\n" +
                "create path (sling:Folder) /asd\n" + 
                "create path (sling:Folder) /asd/not\n" + 
                "create path (sling:Folder) /asd/not/system\n" + 
                "create path (sling:Folder) /asd/not/system/user\n" + 
                "create path (sling:Folder) /asd/not/system/user/path\n" +
                // see SLING-8561
                // "set ACL for acs-commons-package-replication-status-event-service\n" + 
                // "allow jcr:read,crx:replicate,jcr:removeNode on /asd/public\n" + 
                // "end\n" + 
                "set ACL for acs-commons-ensure-oak-index-service\n" + 
                "allow jcr:read,rep:write,rep:indexDefinitionManagement on /asd/not/system/user/path\n" + 
                "end\n";
        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }

    @Test
    public void pathWithSpecialCharactersTest() throws RepoInitParsingException {
        aclManager.addSystemUser(new SystemUser("sys-usr", Paths.get("/home/users/system")));
        aclManager.addAcl("sys-usr", newAcl("allow", "jcr:read", "/content/_cq_tags"));
        aclManager.addAcl("sys-usr", newAcl("allow", "jcr:write", "/content/cq:tags"));
        VaultPackageAssembler assembler = mock(VaultPackageAssembler.class);
        when(assembler.getEntry(anyString())).thenReturn(new File(System.getProperty("java.io.tmpdir")));
        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));

        FeaturesManager fm = Mockito.spy(new DefaultFeaturesManager());
        when(fm.getTargetFeature()).thenReturn(feature);
        
        aclManager.addRepoinitExtension(Arrays.asList(assembler), fm);

        Extension repoinitExtension = feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
        assertNotNull(repoinitExtension);

        String expected = "create path (rep:AuthorizableFolder) /home/users/system\n" + // SLING-8586
                "create service user sys-usr with path /home/users/system\n" +
                "create path (sling:Folder) /content\n" +
                "create path (sling:Folder) /content/cq:tags\n" +
                "set ACL for sys-usr\n" +
                "allow jcr:read on /content/cq:tags\n" +
                "allow jcr:write on /content/cq:tags\n" +
                "end\n";

        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }

    private static Acl newAcl(String operation, String privileges, String path) {
        return new Acl(operation, privileges, Paths.get(path), Paths.get(PlatformNameFormat.getRepositoryPath(path)));
    }

}
