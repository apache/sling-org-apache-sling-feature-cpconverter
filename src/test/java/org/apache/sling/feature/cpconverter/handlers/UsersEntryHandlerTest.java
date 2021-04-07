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
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.DefaultAclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.SystemUser;
import org.apache.sling.feature.cpconverter.accesscontrol.User;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.repoinit.parser.impl.RepoInitParserService;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.StringReader;
import java.util.List;

import static org.apache.sling.feature.cpconverter.Util.normalize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class UsersEntryHandlerTest {

    private UsersEntryHandler usersEntryHandler;

    @Before
    public void setUp() {
        usersEntryHandler = new UsersEntryHandler();
    }

    @After
    public void tearDown() {
        usersEntryHandler = null;
    }

    @Test
    public void doesNotMatch() {
        assertFalse(usersEntryHandler.matches("/this/is/a/path/not/pointing/to/a/valid/configuration.asd"));
        assertFalse(usersEntryHandler.matches("/home/users/system/asd-share-commons/asd-index-definition-reader/.content.xml"));
    }

    @Test
    public void matches() {
        assertTrue(usersEntryHandler.matches("/jcr_root/home/users/system/asd-share-commons/asd-index-definition-reader/.content.xml"));
    }

    @Test
    public void parseSystemUser() throws Exception {
        String path = "/jcr_root/home/users/system/asd-share-commons/asd-index-definition-reader/.content.xml";
        Extension repoinitExtension = parseAndSetRepoinit(path);

        assertNotNull(repoinitExtension);
        assertEquals(ExtensionType.TEXT, repoinitExtension.getType());
        assertTrue(repoinitExtension.isRequired());

        String expected = normalize("create service user asd-share-commons-asd-index-definition-reader-service with path system/asd-share-commons\n");
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

    /**
     * @see <a href="https://issues.apache.org/jira/browse/SLING-9970">SLING-9970</a>
     */
    @Test
    public void testSystemUserPathIsConvertedToRepositoryPath() throws Exception {
        String path = "/jcr_root/home/users/system/_my_feature/_my_user-node/.content.xml";
        Extension repoinitExtension = parseAndSetRepoinit(path);
        assertNotNull(repoinitExtension);

        String actual = repoinitExtension.getText();
        assertFalse(actual.contains("/jcr_root/home/users/system/_my_feature"));
        assertFalse(actual.contains("/home/users/system/_my_feature"));
        assertFalse(actual.contains("/home/users/system/my:feature"));
        assertTrue(actual.contains("system/my:feature"));
    }

    @Test
    public void testUser() throws Exception {
        String path = "/jcr_root/home/users/a/author/.content.xml";
        AclManager aclManager = mock(AclManager.class);
        TestUtils.createRepoInitExtension(usersEntryHandler, aclManager, path, getClass().getResourceAsStream(path.substring(1)));
        verify(aclManager, times(1)).addUser(any(User.class));
        verify(aclManager, never()).addSystemUser(any(SystemUser.class));
    }

    @Test
    public void parseUserWithConfig() throws Exception {
        String path = "/jcr_root/rep:security/rep:authorizables/rep:users/a/author/.content.xml";
        String filePath = path.substring(1).replace("rep:", "_rep_");

        AclManager aclManager = mock(AclManager.class);

        TestUtils.createRepoInitExtension(usersEntryHandler, aclManager, path, getClass().getResourceAsStream(filePath));
        verify(aclManager, never()).addUser(any(User.class));

        TestUtils.createRepoInitExtension(usersEntryHandler.withConfig("/jcr_root(/rep:security/rep:authorizables/rep:users.*/)\\.content.xml"), aclManager, path, getClass().getResourceAsStream(filePath));
        verify(aclManager, times(1)).addUser(any(User.class));
    }

    private Extension parseAndSetRepoinit(String path) throws Exception {
        return TestUtils.createRepoInitExtension(usersEntryHandler, new DefaultAclManager(), path, getClass().getResourceAsStream(path.substring(1)));
    }
}
