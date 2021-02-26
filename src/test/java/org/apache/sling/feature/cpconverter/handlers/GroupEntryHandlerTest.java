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

import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.Group;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class GroupEntryHandlerTest {

    private GroupEntryHandler handler;

    @Before
    public void setUp() {
        handler = new GroupEntryHandler();
    }

    @After
    public void tearDown() {
        handler = null;
    }

    @Test
    public void doesNotMatch() {
        assertFalse(handler.matches("/this/is/a/path/not/pointing/to/a/valid/grouop.asd"));
        assertFalse(handler.matches("/home/groups/g/groupnode/.content.xml"));
    }

    @Test
    public void matches() {
        assertTrue(handler.matches("/jcr_root/home/groups/g/groupnode/.content.xml"));
    }

    @Test
    public void parseGroup() throws Exception {
        String path = "/jcr_root/home/groups/g/V084LLw1ypl2l9G0e28c/.content.xml";
        AclManager aclManager = mock(AclManager.class);
        TestUtils.createRepoInitExtension(handler, aclManager, path, getClass().getResourceAsStream(path.substring(1)));
        verify(aclManager, times(1)).addGroup(any(Group.class));
    }

    @Test
    public void parseUser() throws Exception {
        String path = "/jcr_root/home/users/a/author/.content.xml";
        AclManager aclManager = mock(AclManager.class);
        TestUtils.createRepoInitExtension(handler, aclManager, path, getClass().getResourceAsStream(path.substring(1)));
        verify(aclManager, never()).addGroup(any(Group.class));
    }

    @Test
    public void parseGroupWithConfig() throws Exception {
        String path = "/jcr_root/rep:security/rep:authorizables/rep:groups/g/V084LLw1ypl2l9G0e28c/.content.xml";
        String filePath = path.substring(1).replace("rep:", "_rep_");
        AclManager aclManager = mock(AclManager.class);

        TestUtils.createRepoInitExtension(handler, aclManager, path, getClass().getResourceAsStream(filePath));
        verify(aclManager, never()).addGroup(any(Group.class));

        TestUtils.createRepoInitExtension(handler.withConfig("/jcr_root(/rep:security/rep:authorizables/rep:groups.*/)\\.content.xml"), aclManager, path, getClass().getResourceAsStream(filePath));
        verify(aclManager, times(1)).addGroup(any(Group.class));
    }
}
