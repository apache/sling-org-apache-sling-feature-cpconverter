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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class MappingTest {

    @Test
    public void testMapUserId() {
        String spec = "org.apache.sling.testbundle:sub-service-1=service1";
        Mapping m = new Mapping(spec);
        assertTrue(m.mapsUser("service1"));
        assertFalse(m.mapsUser("another"));
        assertFalse(m.mapsPrincipal("service1"));
        assertFalse(m.mapsPrincipal("another"));
        assertEquals(spec, m.asString());
    }

    @Test
    public void testMapUserIdEnforcePrincipal() {
        Mapping m = new Mapping("org.apache.sling.testbundle:sub-service-1=service1", true);
        assertFalse(m.mapsUser("service1"));
        assertFalse(m.mapsUser("another"));
        assertTrue(m.mapsPrincipal("service1"));
        assertFalse(m.mapsPrincipal("another"));
        assertEquals("org.apache.sling.testbundle:sub-service-1=[service1]", m.asString());
    }

    @Test
    public void testMapPrincipalNames() {
        String spec = "org.apache.sling.testbundle:sub-service-1=[service1,service2]";
        Mapping m = new Mapping(spec);
        assertFalse(m.mapsUser("service1"));
        assertFalse(m.mapsUser("another"));
        assertTrue(m.mapsPrincipal("service1"));
        assertTrue(m.mapsPrincipal("service2"));
        assertFalse(m.mapsPrincipal("another"));
        assertEquals(spec, m.asString());
    }

    @Test
    public void testMapSinglePrincipalName() {
        String spec = "org.apache.sling.testbundle:sub-service-1=[service1]";
        Mapping m = new Mapping(spec);
        assertFalse(m.mapsUser("service1"));
        assertFalse(m.mapsUser("another"));
        assertTrue(m.mapsPrincipal("service1"));
        assertFalse(m.mapsPrincipal("service2"));
        assertFalse(m.mapsPrincipal("another"));
        assertEquals(spec, m.asString());
    }

    @Test
    public void testMapEmptyPrincipalNames() {
        String spec = "org.apache.sling.testbundle:sub-service-1=[]";
        Mapping m = new Mapping(spec);
        assertFalse(m.mapsUser("service1"));
        assertFalse(m.mapsUser("another"));
        assertFalse(m.mapsPrincipal("service1"));
        assertFalse(m.mapsPrincipal("service2"));
        assertFalse(m.mapsPrincipal("another"));
        assertEquals(spec, m.asString());
    }

    @Test
    public void testMapMissingSubservice() {
        String spec = "org.apache.sling.testbundle=[service1]";
        Mapping m = new Mapping(spec);
        assertFalse(m.mapsUser("service1"));
        assertFalse(m.mapsUser("another"));
        assertTrue(m.mapsPrincipal("service1"));
        assertFalse(m.mapsPrincipal("another"));
        assertEquals(spec, m.asString());
    }

    @Test
    public void testMapIncompleteArray() {
        String spec = "org.apache.sling.testbundle:sub-service=[service1";
        Mapping m = new Mapping(spec);
        assertTrue(m.mapsUser("[service1"));
        assertFalse(m.mapsPrincipal("service1"));
        assertEquals(spec, m.asString());

        spec = "org.apache.sling.testbundle:sub-service=service1]";
        m = new Mapping(spec);
        assertTrue(m.mapsUser("service1]"));
        assertFalse(m.mapsPrincipal("service1"));
        assertEquals(spec, m.asString());
    }

    @Test
    public void testColonInUserName() {
        String spec = "org.apache.sling.testbundle=sling:service1";
        Mapping m = new Mapping(spec);
        assertTrue(m.mapsUser("sling:service1"));
        assertEquals(spec, m.asString());
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

    @Test
    public void testEquals() {
        Mapping m = new Mapping("org.apache.sling.testbundle:sub-service-1=service1");
        Mapping m2 = new Mapping("org.apache.sling.testbundle:sub-service-1=[service1,service2]");
        Mapping m3 = new Mapping("org.apache.sling.testbundle:sub-service-1=[service1]");

        assertEquals(m, new Mapping("org.apache.sling.testbundle:sub-service-1=service1"));

        assertNotEquals(m, new Mapping("org.apache.sling.testbundle=service1"));
        assertNotEquals(m, new Mapping("org.apache.sling.testbundle:other-sub-service=service1"));
        assertNotEquals(m, new Mapping("org.apache.sling.otherbundle:sub-service1=service1"));
        assertNotEquals(m, m2);
        assertNotEquals(m, m3);
        assertNotEquals(m2, m3);
        assertNotEquals(m2, new Mapping("org.apache.sling.testbundle:sub-service-1=[service3,service4]"));

        assertTrue(m.equals(m));
        assertTrue(m2.equals(m2));
        assertFalse(m.equals(null));
        assertFalse(m2.equals(m2.toString()));
    }
}