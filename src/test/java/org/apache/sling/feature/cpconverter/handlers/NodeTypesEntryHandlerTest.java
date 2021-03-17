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

import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.junit.Assert;
import org.junit.Test;

public class NodeTypesEntryHandlerTest {

    @Test
    public void testForCndPattern() {
        // test with default pattern
        NodeTypesEntryHandler handler = NodeTypesEntryHandler.forCndPattern(new ImportOptions().getCndPattern());
        Assert.assertTrue(handler.matches("/jcr_root/apps/myapp/nodetypes/test.cnd"));
        // nodetype outside jcr_root
        Assert.assertFalse(handler.matches("/META-INF/apps/myapp/nodetypes/test.cnd"));
        // invalid nodetypes folder
        Assert.assertFalse(handler.matches("/jcr_root/apps/myapp/nodetypes2/test.cnd"));
        // invalid extension
        Assert.assertFalse(handler.matches("/jcr_root/apps/myapp/nodetypes/test.xml"));
    }
}
