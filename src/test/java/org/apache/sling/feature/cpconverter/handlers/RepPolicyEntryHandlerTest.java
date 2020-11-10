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
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.acl.DefaultAclManager;
import org.apache.sling.feature.cpconverter.acl.SystemUser;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.repoinit.parser.impl.RepoInitParserService;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public final class RepPolicyEntryHandlerTest {

    private RepPolicyEntryHandler handler;

    @Before
    public void setUp() {
        handler = new RepPolicyEntryHandler();
    }

    @After
    public void tearDown() {
        handler = null;
    }

    @Test
    public void doesNotMatch() {
        assertFalse(handler.matches("/this/is/a/path/not/pointing/to/a/valid/policy.xml"));
        assertFalse(handler.matches("/home/users/system/asd-share-commons/asd-index-definition-reader/_rep_policy.xml"));
    }

    @Test
    public void matches() {
        assertTrue(handler.matches("/jcr_root/home/users/system/asd-share-commons/asd-index-definition-reader/_rep_policy.xml"));
    }

    @Test
    public void matchesRootPolicies() {
        assertTrue(handler.matches("/jcr_root/_rep_policy.xml"));
    }

    @Test
    public void parseAcl() throws Exception {
        Extension repoinitExtension = parseAndSetRepoinit("acs-commons-ensure-oak-index-service",
                                                          "acs-commons-dispatcher-flush-service",
                                                          "acs-commons-package-replication-status-event-service",
                                                          "acs-commons-ensure-service-user-service",
                                                          "acs-commons-automatic-package-replicator-service",
                                                          "acs-commons-on-deploy-scripts-service").getRepoinitExtension();
        assertNotNull(repoinitExtension);
        assertEquals(ExtensionType.TEXT, repoinitExtension.getType());

        // commented ACLs are due SLING-8561
        String expected = "create path (rep:AuthorizableFolder) /asd/public" + System.lineSeparator() + // SLING-8586
                "create service user acs-commons-ensure-oak-index-service with path /asd/public" + System.lineSeparator() +
                // "create path (sling:Folder) /asd\n" +
                // "create path (sling:Folder) /asd/public\n" +
                // "set ACL for acs-commons-ensure-oak-index-service\n" +
                // "allow jcr:read,rep:write,rep:indexDefinitionManagement on /asd/public restriction(rep:glob,*/oak:index/*)\n" +
                // "end\n" +
                "create service user acs-commons-dispatcher-flush-service with path /asd/public" + System.lineSeparator() +
                // "set ACL for acs-commons-dispatcher-flush-service\n" +
                // "allow jcr:read,crx:replicate,jcr:removeNode on /asd/public\n" +
                // "end\n" +
                "create service user acs-commons-package-replication-status-event-service with path /asd/public" + System.lineSeparator() +
                // "set ACL for acs-commons-package-replication-status-event-service\n" +
                // "allow jcr:read,rep:write,jcr:readAccessControl,jcr:modifyAccessControl on /asd/public\n" +
                // "end\n" +
                "create service user acs-commons-ensure-service-user-service with path /asd/public" + System.lineSeparator() +
                // "set ACL for acs-commons-ensure-service-user-service\n" +
                // "allow jcr:read,rep:write,jcr:readAccessControl,jcr:modifyAccessControl on /asd/public\n" +
                // "end\n" +
                "create service user acs-commons-automatic-package-replicator-service with path /asd/public" + System.lineSeparator() +
                // "set ACL for acs-commons-automatic-package-replicator-service\n" +
                // "allow jcr:read on /asd/public\n" +
                // "end\n" +
                "create service user acs-commons-on-deploy-scripts-service with path /asd/public" + System.lineSeparator();
                // "set ACL for acs-commons-on-deploy-scripts-service\n" +
                // "allow jcr:read on /asd/public\n" +
                // "end\n";
        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }

    @Test
    public void notDeclaredSystemUsersWillNotHaveAclSettings() throws Exception {
        ParseResult result = parseAndSetRepoinit("acs-commons-package-replication-status-event-service",
                                                 "acs-commons-ensure-service-user-service",
                                                 "acs-commons-automatic-package-replicator-service",
                                                 "acs-commons-on-deploy-scripts-service");
        Extension repoinitExtension = result.getRepoinitExtension();

        assertNotNull(repoinitExtension);
        assertEquals(ExtensionType.TEXT, repoinitExtension.getType());

        // commented ACLs are due SLING-8561
        String expected = "create path (rep:AuthorizableFolder) /asd/public" + System.lineSeparator() + // SLING-8586
                "create service user acs-commons-package-replication-status-event-service with path /asd/public" + System.lineSeparator() +
                // "create path (sling:Folder) /asd\n" +
                // "create path (sling:Folder) /asd/public\n" +
                // "set ACL for acs-commons-package-replication-status-event-service\n" +
                // "allow jcr:read,rep:write,jcr:readAccessControl,jcr:modifyAccessControl on /asd/public\n" +
                // "end\n" +
                "create service user acs-commons-ensure-service-user-service with path /asd/public" + System.lineSeparator() +
                // "set ACL for acs-commons-ensure-service-user-service\n" +
                // "allow jcr:read,rep:write,jcr:readAccessControl,jcr:modifyAccessControl on /asd/public\n" +
                // "end\n" +
                "create service user acs-commons-automatic-package-replicator-service with path /asd/public" + System.lineSeparator() +
                // "set ACL for acs-commons-automatic-package-replicator-service\n" +
                // "allow jcr:read on /asd/public\n" +
                // "end\n" +
                "create service user acs-commons-on-deploy-scripts-service with path /asd/public" + System.lineSeparator();
                //"set ACL for acs-commons-on-deploy-scripts-service\n" +
                //"allow jcr:read on /asd/public\n" +
                //"end\n";
        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());

        // acs-commons-ensure-oak-index-service and acs-commons-dispatcher-flush-service not recognized as system users
        expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:rep=\"internal\" jcr:primaryType=\"rep:ACL\">" + System.lineSeparator()
                +
                "    <allow0 jcr:primaryType=\"rep:GrantACE\" rep:principalName=\"acs-commons-ensure-oak-index-service\" rep:privileges=\"{Name}[jcr:read,rep:write,rep:indexDefinitionManagement]\">" + System.lineSeparator()
                + "        <rep:restrictions jcr:primaryType=\"rep:Restrictions\" rep:glob=\"{Name}[*/oak:index/*]\"/>" + System.lineSeparator()
                + "    </allow0>" + System.lineSeparator()
                + "    <allow1 jcr:primaryType=\"rep:GrantACE\" rep:principalName=\"acs-commons-dispatcher-flush-service\" rep:privileges=\"{Name}[jcr:read,crx:replicate,jcr:removeNode]\"/>" + System.lineSeparator()
                +
                "</jcr:root>" + System.lineSeparator();
        actual = result.getExcludedAcls();
        assertEquals(expected, actual);
    }

    @Test
    public void systemUserAclSetNotForUserPath() throws Exception {
        ParseResult result = parseAndSetRepoinit(new SystemUser("acs-commons-package-replication-status-event-service",
                new RepoPath("/this/is/a/completely/different/path")));
        Extension repoinitExtension = result.getRepoinitExtension();
        assertNotNull(repoinitExtension);
        assertEquals(ExtensionType.TEXT, repoinitExtension.getType());

        String expected = "create path (rep:AuthorizableFolder) /this/is/a/completely/different/path" + System.lineSeparator() + // SLING-8586
                "create service user acs-commons-package-replication-status-event-service with path /this/is/a/completely/different/path" + System.lineSeparator() +
                "create path (sling:Folder) /asd" + System.lineSeparator() +
                "create path (sling:Folder) /asd/public" + System.lineSeparator() +
                "set ACL for acs-commons-package-replication-status-event-service" + System.lineSeparator() +
                "allow jcr:read,rep:write,jcr:readAccessControl,jcr:modifyAccessControl on /asd/public" + System.lineSeparator() +
                "end" + System.lineSeparator();
        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());

        // acs-commons-package-replication-status-event-service only recognised as system user - ACLs in allow2
        expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:rep=\"internal\" jcr:primaryType=\"rep:ACL\">" + System.lineSeparator()
                +
                "    <allow0 jcr:primaryType=\"rep:GrantACE\" rep:principalName=\"acs-commons-ensure-oak-index-service\" rep:privileges=\"{Name}[jcr:read,rep:write,rep:indexDefinitionManagement]\">" + System.lineSeparator()
                + "        <rep:restrictions jcr:primaryType=\"rep:Restrictions\" rep:glob=\"{Name}[*/oak:index/*]\"/>" + System.lineSeparator()
                + "    </allow0>" + System.lineSeparator()
                + "    <allow1 jcr:primaryType=\"rep:GrantACE\" rep:principalName=\"acs-commons-dispatcher-flush-service\" rep:privileges=\"{Name}[jcr:read,crx:replicate,jcr:removeNode]\"/>" + System.lineSeparator()
                + "    <allow3 jcr:primaryType=\"rep:GrantACE\" rep:principalName=\"acs-commons-ensure-service-user-service\" rep:privileges=\"{Name}[jcr:read,rep:write,jcr:readAccessControl,jcr:modifyAccessControl]\"/>" + System.lineSeparator()
                + "    <allow4 jcr:primaryType=\"rep:GrantACE\" rep:principalName=\"acs-commons-automatic-package-replicator-service\" rep:privileges=\"{Name}[jcr:read]\"/>" + System.lineSeparator()
                + "    <allow5 jcr:primaryType=\"rep:GrantACE\" rep:principalName=\"acs-commons-on-deploy-scripts-service\" rep:privileges=\"{Name}[jcr:read]\"/>" + System.lineSeparator()
                +
                "</jcr:root>" + System.lineSeparator();
        actual = result.getExcludedAcls();
        assertEquals(expected, actual);
    }

    @Test
    public void parseEmptyAcl() throws Exception {
        Extension repoinitExtension = parseAndSetRepoinit(new String[] {}).getRepoinitExtension();
        assertNull(repoinitExtension);
    }

    private ParseResult parseAndSetRepoinit(String...systemUsersNames) throws Exception {
        RepoPath alwaysTheSamePath = new RepoPath("/asd/public");

        SystemUser[] systemUsers = new SystemUser[systemUsersNames.length];
        for (int i = 0; i < systemUsersNames.length; i++) {
            systemUsers[i] = new SystemUser(systemUsersNames[i], alwaysTheSamePath);
        }

        return parseAndSetRepoinit(systemUsers);
    }

    private ParseResult parseAndSetRepoinit(SystemUser...systemUsers) throws Exception {
        String path = "/jcr_root/asd/public/_rep_policy.xml";
        Archive archive = mock(Archive.class);
        Entry entry = mock(Entry.class);
        VaultPackageAssembler packageAssembler = mock(VaultPackageAssembler.class);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(packageAssembler.createEntry(anyString())).thenReturn(baos);

        when(archive.openInputStream(entry)).thenReturn(getClass().getResourceAsStream(path.substring(1)));

        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));
        FeaturesManager featuresManager = spy(DefaultFeaturesManager.class);
        when(featuresManager.getTargetFeature()).thenReturn(feature);
        ContentPackage2FeatureModelConverter converter = spy(ContentPackage2FeatureModelConverter.class);
        when(converter.getFeaturesManager()).thenReturn(featuresManager);
        when(converter.getAclManager()).thenReturn(new DefaultAclManager());
        when(converter.getMainPackageAssembler()).thenReturn(packageAssembler);

        if (systemUsers != null) {
            for (SystemUser systemUser : systemUsers) {
                converter.getAclManager().addSystemUser(systemUser);
            }
        }

        handler.handle(path, archive, entry, converter);

        when(packageAssembler.getEntry(anyString())).thenReturn(new File("itdoesnotexist"));

        converter.getAclManager().addRepoinitExtension(Arrays.asList(packageAssembler), featuresManager);
        return new ParseResult(feature.getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT), new String(baos.toByteArray()));
    }

    private static final class ParseResult {

        private final Extension repoinitExtension;

        private final String excludedAcls;

        public ParseResult(Extension repoinitExtension, String excludedAcls) {
            this.repoinitExtension = repoinitExtension;
            this.excludedAcls = excludedAcls;
        }

        public Extension getRepoinitExtension() {
            return repoinitExtension;
        }

        public String getExcludedAcls() {
            return excludedAcls;
        }

    }

}
