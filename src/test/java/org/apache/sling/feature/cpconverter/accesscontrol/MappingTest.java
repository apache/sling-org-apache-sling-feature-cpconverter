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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MappingTest {

    @Test
    public void testMapUserId() {
        Mapping m = new Mapping("org.apache.sling.testbundle:sub-service-1=service1");
        assertTrue(m.mapsUser("service1"));
        assertFalse(m.mapsUser("another"));
        assertFalse(m.mapsPrincipal("service1"));
        assertFalse(m.mapsPrincipal("another"));
    }

    @Test
    public void testMapPrincipalNames() {
        Mapping m = new Mapping("org.apache.sling.testbundle:sub-service-1=[service1,service2]");
        assertFalse(m.mapsUser("service1"));
        assertFalse(m.mapsUser("another"));
        assertTrue(m.mapsPrincipal("service1"));
        assertTrue(m.mapsPrincipal("service2"));
        assertFalse(m.mapsPrincipal("another"));
    }

    @Test
    public void testMapSinglePrincipalName() {
        Mapping m = new Mapping("org.apache.sling.testbundle:sub-service-1=[service1]");
        assertFalse(m.mapsUser("service1"));
        assertFalse(m.mapsUser("another"));
        assertTrue(m.mapsPrincipal("service1"));
        assertFalse(m.mapsPrincipal("service2"));
        assertFalse(m.mapsPrincipal("another"));
    }

    @Test
    public void testMapEmptyPrincipalNames() {
        Mapping m = new Mapping("org.apache.sling.testbundle:sub-service-1=[]");
        assertFalse(m.mapsUser("service1"));
        assertFalse(m.mapsUser("another"));
        assertFalse(m.mapsPrincipal("service1"));
        assertFalse(m.mapsPrincipal("service2"));
        assertFalse(m.mapsPrincipal("another"));
    }

    @Test
    public void testMapMissingSubservice() {
        Mapping m = new Mapping("org.apache.sling.testbundle=[service1]");
        assertFalse(m.mapsUser("service1"));
        assertFalse(m.mapsUser("another"));
        assertTrue(m.mapsPrincipal("service1"));
        assertFalse(m.mapsPrincipal("another"));
    }

    @Test
    public void testMapIncompleteArray() {
        Mapping m = new Mapping("org.apache.sling.testbundle:sub-service=[service1");
        assertTrue(m.mapsUser("[service1"));
        assertFalse(m.mapsPrincipal("service1"));

        m = new Mapping("org.apache.sling.testbundle:sub-service=service1]");
        assertTrue(m.mapsUser("service1]"));
        assertFalse(m.mapsPrincipal("service1"));
    }

    @Test
    public void testColonInUserName() {
        Mapping m = new Mapping("org.apache.sling.testbundle=sling:service1");
        assertTrue(m.mapsUser("sling:service1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingUser() {
        new Mapping("org.apache.sling.testbundle:subservice");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingUser2() {
        new Mapping("org.apache.sling.testbundle:sub-service=");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingBundle() {
        new Mapping(":sub-service=[service1]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingSubservice() {
        new Mapping("org.apache.sling.testbundle:=service1");
    }
}