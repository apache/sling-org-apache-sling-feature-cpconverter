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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static org.apache.sling.feature.cpconverter.shared.ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnforceInfoTest {

    private static final String SUPPORTED_PATH = "/home/users/system/cq:services";
    
    private static DefaultAclManager createEnforceInfo(@Nullable String enforcePrincipalBasedSupportedPath, @NotNull String systemRelPath) {
        return createEnforceInfo(enforcePrincipalBasedSupportedPath, systemRelPath, false);
    }
        
    private static DefaultAclManager createEnforceInfo(@Nullable String enforcePrincipalBasedSupportedPath, @NotNull String systemRelPath, boolean alwaysForceSystemUserPath) {
        return new DefaultAclManager(enforcePrincipalBasedSupportedPath, systemRelPath, alwaysForceSystemUserPath);
    }

    @Test
    public void testEnforcePrincipalBasedMissingPath() {
        DefaultAclManager aclManager = createEnforceInfo(null, "system");
        assertFalse(aclManager.enforcePrincipalBased("systemuser"));

        aclManager.recordSystemUserIds("systemuser");
        assertFalse(aclManager.enforcePrincipalBased("systemuser"));
    }
    
    @Test
    public void testEnforcePrincipalBased() {
        DefaultAclManager aclManager = createEnforceInfo(SUPPORTED_PATH, "system");
        assertFalse(aclManager.enforcePrincipalBased("systemuser"));
        
        aclManager.recordSystemUserIds("systemuser");
        assertTrue(aclManager.enforcePrincipalBased("systemuser"));
    }

    @Test
    public void testEnforcePrincipalBasedMappedById() {
        DefaultAclManager aclManager = createEnforceInfo(SUPPORTED_PATH, "system");
        aclManager.addMapping(new Mapping("service:subservice=systemuser", false));
        aclManager.recordSystemUserIds("systemuser");
        assertFalse(aclManager.enforcePrincipalBased("systemuser"));
    }

    @Test
    public void testEnforcePrincipalBasedMappedByIdEnforceConversion() {
        DefaultAclManager aclManager = createEnforceInfo(SUPPORTED_PATH, "system");
        aclManager.addMapping(new Mapping("service:subservice=systemuser", true));
        aclManager.recordSystemUserIds("systemuser");
        assertTrue(aclManager.enforcePrincipalBased("systemuser"));
    }

    @Test
    public void testCalculateEnforcedIntermediateMissingPath() throws Exception {
        DefaultAclManager aclManager = createEnforceInfo(SUPPORTED_PATH, "system");

        assertEquals("system/cq:services", aclManager.calculateEnforcedIntermediatePath(null));
        assertEquals("system/cq:services", aclManager.calculateEnforcedIntermediatePath(""));
    }
    
    @Test
    public void testCalculateEnforcedIntermediatePathSubTree() throws Exception {
        DefaultAclManager aclManager = createEnforceInfo(SUPPORTED_PATH, SYSTEM_USER_REL_PATH_DEFAULT);

        assertEquals("system/cq:services/some/path", aclManager.calculateEnforcedIntermediatePath("/home/users/system/cq:services/some/path"));
        assertEquals("system/cq:services/some/path", aclManager.calculateEnforcedIntermediatePath("/home/users/system/some/path"));
    }
    
    @Test
    public void testCalculateEnforcedIntermediatePath() throws Exception {
        DefaultAclManager aclManager = createEnforceInfo(SUPPORTED_PATH, SYSTEM_USER_REL_PATH_DEFAULT);

        assertEquals("system/cq:services", aclManager.calculateEnforcedIntermediatePath(SUPPORTED_PATH));
        assertEquals("system/cq:services", aclManager.calculateEnforcedIntermediatePath("/home/users/system"));
    }
    
    @Test(expected = IllegalStateException.class)
    public void testCalculateEnforcedIntermediatePathMissingPath() throws Exception {
        DefaultAclManager aclManager = createEnforceInfo(null, SYSTEM_USER_REL_PATH_DEFAULT);
        aclManager.calculateEnforcedIntermediatePath("/home/users/system/some/path");
    }
    
    @Test(expected = ConverterException.class)
    public void testCalculateEnforcedIntermediatePathOutsideSupportedScope() throws Exception {
        DefaultAclManager aclManager = createEnforceInfo(SUPPORTED_PATH, SYSTEM_USER_REL_PATH_DEFAULT);
        aclManager.calculateEnforcedIntermediatePath("/home/users");
    }
    
    @Test
    public void testEnforcePath() {
        DefaultAclManager aclManager = new DefaultAclManager();
        assertFalse(aclManager.enforcePath("id"));
        
        aclManager = createEnforceInfo(SUPPORTED_PATH, SYSTEM_USER_REL_PATH_DEFAULT, false);
        assertFalse(aclManager.enforcePath("id"));
        
        aclManager = createEnforceInfo(SUPPORTED_PATH, SYSTEM_USER_REL_PATH_DEFAULT, true);
        assertTrue(aclManager.enforcePath("id"));

        aclManager = createEnforceInfo(null, SYSTEM_USER_REL_PATH_DEFAULT, false);
        assertFalse(aclManager.enforcePath("id"));

        aclManager = createEnforceInfo(null, SYSTEM_USER_REL_PATH_DEFAULT, true);
        assertTrue(aclManager.enforcePath("id"));
    }
}