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
import org.apache.sling.feature.cpconverter.accesscontrol.SystemUser;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.repoinit.parser.impl.RepoInitParserService;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.List;

import static org.apache.sling.feature.cpconverter.Util.normalize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RepPrincipalPolicyEntryHandlerTest {

    private RepPrincipalPolicyEntryHandler handler;

    @Before
    public void setUp() {
        handler = new RepPrincipalPolicyEntryHandler();
    }

    @After
    public void tearDown() {
        handler = null;
    }

    @Test
    public void doesNotMatch() {
        assertFalse(handler.matches("/this/is/a/path/not/pointing/to/a/valid/_rep_principalPolicy.xml"));
        assertFalse(handler.matches("/home/users/system/asd-share-commons/asd-index-definition-reader/_rep_principalPolicy.xml"));
        assertFalse(handler.matches("/jcr_root/home/users/system/services/feature/random1/_rep_policy.xml"));
    }

    @Test
    public void matches() {
        assertTrue(handler.matches("/jcr_root/home/users/system/services/random1/_rep_principalPolicy.xml"));
    }

    @Test
    public void parseSimplePolicy() throws Exception {
        Extension repoinitExtension = parseAndSetRepoinit("service1", "random1").getRepoinitExtension();
        String expected = normalize(
                "create service user service1 with path system/services\n" +
                "set principal ACL for service1\n" +
                "    allow jcr:read,jcr:readAccessControl on /asd/public\n" +
                "end\n");

        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }

    @Test
    public void parseMvRestrictions() throws Exception {
        Extension repoinitExtension = parseAndSetRepoinit("service2", "random2").getRepoinitExtension();
        String expected = normalize(
                "create service user service2 with path system/services\n" +
                        "set principal ACL for service2\n" +
                        "    allow jcr:read on /asd/public restriction(rep:ntNames,nt:folder,sling:Folder) restriction(sling:customRestriction,customRestrictionValue)\n" +
                        "end\n");

        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }

    @Test(expected = IllegalStateException.class)
    public void parsePolicyInSubtree() throws Exception {
        parseAndSetRepoinit("service3", "random3");
    }

    @Test
    public void parseOtherUserHomeMissing() throws Exception {
        SystemUser systemUser4 = createSystemUser("service4", "random4");

        Extension repoinitExtension = parseAndSetRepoinit(getPolicyPath(systemUser4), systemUser4).getRepoinitExtension();
        String expected = normalize(
                "create service user service4 with path system/services\n" +
                "set principal ACL for service4\n" +
                // since service3 is not known to the AclManager it treats the effective path as a regular node.
                "    allow jcr:read,rep:userManagement on /home/users/system/services/random3\n" +
                "end\n");

        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }

    @Test
    public void parseOtherUserHome() throws Exception {
        SystemUser systemUser3 = createSystemUser("service3", "random3");
        SystemUser systemUser4 = createSystemUser("service4", "random4");

        Extension repoinitExtension = parseAndSetRepoinit(getPolicyPath(systemUser4), systemUser4, systemUser3).getRepoinitExtension();
        String expected = normalize(
                "create service user service4 with path system/services\n" +
                "create service user service3 with path system/services\n" +
                "set principal ACL for service4\n" +
                "    allow jcr:read,rep:userManagement on home(service3)\n" +
                "end\n");

        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }

    @Test
    public void parsePolicyWithWrongTypeEffectivePath() throws Exception {
        Extension repoinitExtension = parseAndSetRepoinit("service4", "random5").getRepoinitExtension();
        String expected = normalize(
                "create service user service4 with path system/services\n" +
                        "set principal ACL for service4\n" +
                        "    allow jcr:read on /effective/path/of/type/string\n" +
                        "end\n");

        String actual = repoinitExtension.getText();
        assertEquals(expected, actual);

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }

    @NotNull
    private static SystemUser createSystemUser(@NotNull String systemUsersName, @NotNull String nodeName) {
        RepoPath repoPath = new RepoPath("/home/users/system/services/"+nodeName);
        return new SystemUser(systemUsersName, repoPath, new RepoPath("/home/users/system/services"));
    }

    @NotNull
    private static String getPolicyPath(@NotNull SystemUser systemUser) {
        return "/jcr_root"+systemUser.getPath().toString() + "/_rep_principalPolicy.xml";
    }

    private ParseResult parseAndSetRepoinit(@NotNull String systemUsersName, @NotNull String nodeName) throws Exception {
        SystemUser systemUser = createSystemUser(systemUsersName, nodeName);

        return parseAndSetRepoinit(getPolicyPath(systemUser), systemUser);
    }

    private ParseResult parseAndSetRepoinit(@NotNull String path, @NotNull SystemUser... systemUsers) throws Exception {
        AclManager aclManager = new DefaultAclManager();
        for (SystemUser systemUser : systemUsers) {
            aclManager.addSystemUser(systemUser);
        }

        InputStream is = getClass().getResourceAsStream(path.substring(1));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        return new ParseResult(TestUtils.createRepoInitExtension(handler, aclManager, path, is, baos), new String(baos.toByteArray()));
    }

}
