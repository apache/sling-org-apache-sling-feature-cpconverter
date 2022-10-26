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

import org.apache.sling.feature.cpconverter.ConverterException;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnforceInfoTest {

    @Test
    public void testEnforcePrincipalBasedMissingPath() {
        DefaultAclManager aclManager = new DefaultAclManager(null, "system");
        assertFalse(aclManager.enforcePrincipalBased("systemuser"));

        aclManager.recordSystemUserIds("systemuser");
        assertFalse(aclManager.enforcePrincipalBased("systemuser"));
    }
    
    @Test
    public void testEnforcePrincipalBased() {
        DefaultAclManager aclManager = new DefaultAclManager("/home/users/system/cq:services", "system");
        assertFalse(aclManager.enforcePrincipalBased("systemuser"));
        
        aclManager.recordSystemUserIds("systemuser");
        assertTrue(aclManager.enforcePrincipalBased("systemuser"));
    }

    @Test
    public void testEnforcePrincipalBasedMappedById() {
        DefaultAclManager aclManager = new DefaultAclManager("/home/users/system/cq:services", "system");
        aclManager.addMapping(new Mapping("service:subservice=systemuser", false));
        aclManager.recordSystemUserIds("systemuser");
        assertFalse(aclManager.enforcePrincipalBased("systemuser"));
    }

    @Test
    public void testEnforcePrincipalBasedMappedByIdEnforceConversion() {
        DefaultAclManager aclManager = new DefaultAclManager("/home/users/system/cq:services", "system");
        aclManager.addMapping(new Mapping("service:subservice=systemuser", true));
        aclManager.recordSystemUserIds("systemuser");
        assertTrue(aclManager.enforcePrincipalBased("systemuser"));
    }

    @Test
    public void testCalculateEnforcedIntermediateMissingPath() throws Exception {
        DefaultAclManager aclManager = new DefaultAclManager("/home/users/system/cq:services", "system");

        assertEquals("system/cq:services", aclManager.calculateEnforcedIntermediatePath(null));
        assertEquals("system/cq:services", aclManager.calculateEnforcedIntermediatePath(""));
    }
    
    @Test
    public void testCalculateEnforcedIntermediatePathSubTree() throws Exception {
        DefaultAclManager aclManager = new DefaultAclManager("/home/users/system/cq:services", "system");

        assertEquals("system/cq:services/some/path", aclManager.calculateEnforcedIntermediatePath("/home/users/system/cq:services/some/path"));
        assertEquals("system/cq:services/some/path", aclManager.calculateEnforcedIntermediatePath("/home/users/system/some/path"));
    }
    
    @Test
    public void testCalculateEnforcedIntermediatePath() throws Exception {
        DefaultAclManager aclManager = new DefaultAclManager("/home/users/system/cq:services", "system");

        assertEquals("system/cq:services", aclManager.calculateEnforcedIntermediatePath("/home/users/system/cq:services"));
        assertEquals("system/cq:services", aclManager.calculateEnforcedIntermediatePath("/home/users/system"));
    }
    
    @Test(expected = IllegalStateException.class)
    public void testCalculateEnforcedIntermediatePathMissingPath() throws Exception {
        DefaultAclManager aclManager = new DefaultAclManager(null, "system");
        aclManager.calculateEnforcedIntermediatePath("/home/users/system/some/path");
    }
    
    @Test(expected = ConverterException.class)
    public void testCalculateEnforcedIntermediatePathOutsideSupportedScope() throws Exception {
        DefaultAclManager aclManager = new DefaultAclManager("/home/users/system/cq:services", "system");
        aclManager.calculateEnforcedIntermediatePath("/home/users");
    }
}