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

import org.apache.sling.feature.Extension;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.DefaultAclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.Group;
import org.apache.sling.feature.cpconverter.accesscontrol.SystemUser;
import org.apache.sling.feature.cpconverter.accesscontrol.User;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.repoinit.parser.impl.RepoInitParserService;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

        // commented ACLs are due SLING-8561
        String expected =
                "create service user acs-commons-ensure-oak-index-service with path /home/users/system" + System.lineSeparator() +
                "create service user acs-commons-dispatcher-flush-service with path /home/users/system" + System.lineSeparator() +
                "create service user acs-commons-package-replication-status-event-service with path /home/users/system" + System.lineSeparator() +
                "create service user acs-commons-ensure-service-user-service with path /home/users/system" + System.lineSeparator() +
                "create service user acs-commons-automatic-package-replicator-service with path /home/users/system" + System.lineSeparator() +
                "create service user acs-commons-on-deploy-scripts-service with path /home/users/system" + System.lineSeparator() +
                "set ACL for acs-commons-automatic-package-replicator-service" + System.lineSeparator() +
                "allow jcr:read on home(acs-commons-automatic-package-replicator-service)" + System.lineSeparator() +
                "end" + System.lineSeparator() +
                "set ACL for acs-commons-package-replication-status-event-service" + System.lineSeparator() +
                "allow jcr:read,rep:write,jcr:readAccessControl,jcr:modifyAccessControl on home(acs-commons-package-replication-status-event-service)" + System.lineSeparator() +
                "deny jcr:write on home(acs-commons-package-replication-status-event-service)" + System.lineSeparator() +
                "end" + System.lineSeparator() +
                "set ACL for acs-commons-dispatcher-flush-service" + System.lineSeparator() +
                "allow jcr:read,crx:replicate,jcr:removeNode on home(acs-commons-dispatcher-flush-service)" + System.lineSeparator() +
                "deny jcr:write on home(acs-commons-dispatcher-flush-service)" + System.lineSeparator() +
                "end" + System.lineSeparator() +
                "set ACL for acs-commons-ensure-oak-index-service" + System.lineSeparator() +
                "allow jcr:read,rep:write,rep:indexDefinitionManagement on home(acs-commons-ensure-oak-index-service) restriction(rep:glob,*/oak:index/*)" + System.lineSeparator() +
                "end" + System.lineSeparator() +
                "set ACL for acs-commons-on-deploy-scripts-service" + System.lineSeparator() +
                "allow jcr:read on home(acs-commons-on-deploy-scripts-service)" + System.lineSeparator() +
                "end" + System.lineSeparator() +
                "set ACL for acs-commons-ensure-service-user-service" + System.lineSeparator() +
                "allow jcr:read,rep:write,jcr:readAccessControl,jcr:modifyAccessControl on home(acs-commons-ensure-service-user-service)" + System.lineSeparator() +
                "end" + System.lineSeparator();

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

        String expected =
                "create service user acs-commons-package-replication-status-event-service with path /home/users/system" + System.lineSeparator() +
                "create service user acs-commons-ensure-service-user-service with path /home/users/system" + System.lineSeparator() +
                "create service user acs-commons-automatic-package-replicator-service with path /home/users/system" + System.lineSeparator() +
                "create service user acs-commons-on-deploy-scripts-service with path /home/users/system" + System.lineSeparator() +
                "set ACL for acs-commons-automatic-package-replicator-service" + System.lineSeparator() +
                "allow jcr:read on home(acs-commons-automatic-package-replicator-service)" + System.lineSeparator() +
                "end" + System.lineSeparator() +
                "set ACL for acs-commons-package-replication-status-event-service" + System.lineSeparator() +
                "allow jcr:read,rep:write,jcr:readAccessControl,jcr:modifyAccessControl on home(acs-commons-package-replication-status-event-service)" + System.lineSeparator() +
                "deny jcr:write on home(acs-commons-package-replication-status-event-service)" + System.lineSeparator() +
                "end" + System.lineSeparator() +
                "set ACL for acs-commons-on-deploy-scripts-service" + System.lineSeparator() +
                "allow jcr:read on home(acs-commons-on-deploy-scripts-service)" + System.lineSeparator() +
                "end" + System.lineSeparator() +
                "set ACL for acs-commons-ensure-service-user-service" + System.lineSeparator() +
                "allow jcr:read,rep:write,jcr:readAccessControl,jcr:modifyAccessControl on home(acs-commons-ensure-service-user-service)" + System.lineSeparator() +
                "end" + System.lineSeparator();
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
                + "    <deny0 jcr:primaryType=\"rep:DenyACE\" rep:principalName=\"acs-commons-dispatcher-flush-service\" rep:privileges=\"{Name}[jcr:write]\"/>" + System.lineSeparator() +
                "</jcr:root>" + System.lineSeparator();
        actual = result.getExcludedAcls();
        assertEquals(expected, actual);
    }

    @Test
    public void systemUserAclSetNotForUserPath() throws Exception {
        ParseResult result = parseAndSetRepoinit(new SystemUser("acs-commons-package-replication-status-event-service",
                new RepoPath("/this/is/a/completely/different/path/foo"), new RepoPath("/this/is/a/completely/different/path")));
        Extension repoinitExtension = result.getRepoinitExtension();
        String expected =
                "create service user acs-commons-package-replication-status-event-service with path /this/is/a/completely/different/path" + System.lineSeparator() +
                "set ACL for acs-commons-package-replication-status-event-service" + System.lineSeparator() +
                "allow jcr:read,rep:write,jcr:readAccessControl,jcr:modifyAccessControl on /home/users/system/asd" + System.lineSeparator() +
                "deny jcr:write on /home/users/system/asd" + System.lineSeparator() +
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
                + "    <deny0 jcr:primaryType=\"rep:DenyACE\" rep:principalName=\"acs-commons-dispatcher-flush-service\" rep:privileges=\"{Name}[jcr:write]\"/>" + System.lineSeparator() +
                "</jcr:root>" + System.lineSeparator();
        actual = result.getExcludedAcls();
        assertEquals(expected, actual);
    }

    @Test
    public void parseEmptyAcl() throws Exception {
        Extension extension = TestUtils.createRepoInitExtension(handler, new DefaultAclManager(), "/jcr_root/home/users/system/asd/_rep_policy.xml", getClass().getResourceAsStream("/jcr_root/home/users/system/asd/_rep_policy.xml".substring(1)), new ByteArrayOutputStream());
        assertNull(extension);
    }

    @Test
    public void policyAtAuthorizableFolder() throws Exception {
        SystemUser s1 = new SystemUser("service1", new RepoPath("/home/users/system/services/random1"), new RepoPath("/home/users/system/services"));

        AclManager aclManager = new DefaultAclManager();
        aclManager.addSystemUser(s1);

        ParseResult result = parseAndSetRepoInit("/jcr_root/home/groups/g/_rep_policy.xml", aclManager);
        Extension repoinitExtension = result.getRepoinitExtension();

        String expected =
                "create service user service1 with path /home/users/system/services" + System.lineSeparator() +
                "set ACL for service1" + System.lineSeparator() +
                "allow jcr:read,rep:userManagement on /home/groups/g" + System.lineSeparator() +
                "end" + System.lineSeparator();
        assertEquals(expected, repoinitExtension.getText());
        assertTrue(result.getExcludedAcls().isEmpty());
    }

    @Test
    public void policyAtGroupNode() throws Exception {
        SystemUser s1 = new SystemUser("service1", new RepoPath("/home/users/system/services/random1"), new RepoPath("/home/users/system/services"));
        Group gr = new Group("testgroup", new RepoPath("/home/groups/g/HjDnfdMCjekaF4jhhUvO"), new RepoPath("/home/groups/g"));

        AclManager aclManager = new DefaultAclManager();
        aclManager.addSystemUser(s1);
        aclManager.addGroup(gr);

        ParseResult result = parseAndSetRepoInit("/jcr_root/home/groups/g/HjDnfdMCjekaF4jhhUvO/_rep_policy.xml", aclManager);
        Extension repoinitExtension = result.getRepoinitExtension();

        String expected =
                "create service user service1 with path /home/users/system/services" + System.lineSeparator() +
                "create group testgroup with path /home/groups/g" + System.lineSeparator() +
                "set ACL for service1" + System.lineSeparator() +
                "allow jcr:read on home(testgroup)" + System.lineSeparator() +
                "end" + System.lineSeparator();
        assertEquals(expected, repoinitExtension.getText());

        String expectedExclusions = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><jcr:root xmlns:jcr=\"http://www.jcp.org/jcr/1.0\" xmlns:rep=\"internal\" jcr:primaryType=\"rep:ACL\">\n" +
                "    <allow1 jcr:primaryType=\"rep:GrantACE\" rep:principalName=\"testgroup\" rep:privileges=\"{Name}[jcr:read]\"/>\n" +
                "</jcr:root>\n";
        assertEquals(expectedExclusions, result.getExcludedAcls());
    }

    @Test(expected = IllegalStateException.class)
    public void policyAtGroupSubTree() throws Exception {
        SystemUser s1 = new SystemUser("service1", new RepoPath("/home/users/system/services/random1"), new RepoPath("/home/users/system/services"));
        Group gr = new Group("testgroup3", new RepoPath("/home/groups/g/ouStmkrzT9wCEhtMD9sT"), new RepoPath("/home/groups/g"));

        AclManager aclManager = new DefaultAclManager();
        aclManager.addSystemUser(s1);
        aclManager.addGroup(gr);

        parseAndSetRepoInit("/jcr_root/home/groups/g/ouStmkrzT9wCEhtMD9sT/profile/_rep_policy.xml", aclManager);
    }

    @Test(expected = IllegalStateException.class)
    public void policyAtUserNode() throws Exception {
        SystemUser s1 = new SystemUser("service1", new RepoPath("/home/users/system/services/random1"), new RepoPath("/home/users/system/services"));
        User user = new User("author", new RepoPath("/home/users/a/author"), new RepoPath("/home/users/a"));

        AclManager aclManager = new DefaultAclManager();
        aclManager.addSystemUser(s1);
        aclManager.addUser(user);

        parseAndSetRepoInit("/jcr_root/home/users/a/author/_rep_policy.xml", aclManager);
    }

    private ParseResult parseAndSetRepoinit(String...systemUsersNames) throws Exception {
        RepoPath alwaysTheSameOrgPath = new RepoPath("/home/users/system/asd");
        RepoPath alwaysTheSameInterPath = new RepoPath("/home/users/system");

        SystemUser[] systemUsers = new SystemUser[systemUsersNames.length];
        for (int i = 0; i < systemUsersNames.length; i++) {
            systemUsers[i] = new SystemUser(systemUsersNames[i], alwaysTheSameOrgPath, alwaysTheSameInterPath);
        }

        return parseAndSetRepoinit(systemUsers);
    }

    private ParseResult parseAndSetRepoinit(@NotNull SystemUser...systemUsers) throws Exception {
        String path = "/jcr_root/home/users/system/asd/_rep_policy.xml";
        AclManager aclManager = new DefaultAclManager();
        for (SystemUser systemUser : systemUsers) {
            aclManager.addSystemUser(systemUser);
        }
        return parseAndSetRepoInit(path, aclManager);
    }

    @NotNull
    private ParseResult parseAndSetRepoInit(@NotNull String path, @NotNull AclManager aclManager) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        return new ParseResult(TestUtils.createRepoInitExtension(handler, aclManager, path, getClass().getResourceAsStream(path.substring(1)), baos), new String(baos.toByteArray()));
    }
}
