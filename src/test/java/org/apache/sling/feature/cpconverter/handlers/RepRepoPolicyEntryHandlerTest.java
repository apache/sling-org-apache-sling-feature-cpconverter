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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RepRepoPolicyEntryHandlerTest {

    private RepPolicyEntryHandler handler;

    @Before
    public void setUp() {
        handler = new RepRepoPolicyEntryHandler();
    }

    @After
    public void tearDown() {
        handler = null;
    }

    @Test
    public void doesNotMatch() {
        assertFalse(handler.matches("/this/is/a/path/not/pointing/to/a/valid/repoPolicy.xml"));
        assertFalse(handler.matches("/this/is/a/path/not/pointing/to/a/valid/_rep_repoPolicy.xml"));
    }

    @Test
    public void matches() {
        assertTrue(handler.matches("/jcr_root/_rep_repoPolicy.xml"));
    }

    @Test
    public void parsePolicy() throws Exception {
        String path = "/jcr_root/_rep_repoPolicy.xml";
        AclManager aclManager = new DefaultAclManager();
        aclManager.addSystemUser(new SystemUser("repolevel-service", new RepoPath("/home/users/system/test"), new RepoPath("/home/users/system")));
        OutputStream out = new ByteArrayOutputStream();
        Extension repoinitExtension = new ParseResult(TestUtils.createRepoInitExtension(handler, aclManager, path, getClass().getResourceAsStream(path.substring(1)), out), out.toString()).getRepoinitExtension();

        String expectedEnd =
                "set ACL for repolevel-service\n" +
                "    allow jcr:namespaceManagement on :repository\n" +
                "end\n";
        String actual = repoinitExtension.getText();
        assertTrue(actual.endsWith(expectedEnd));
        // no path must be create for repository level entries
        assertFalse(actual, actual.contains("create path (sling:Folder) /"));

        RepoInitParser repoInitParser = new RepoInitParserService();
        List<Operation> operations = repoInitParser.parse(new StringReader(actual));
        assertFalse(operations.isEmpty());
    }
}
