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
package org.apache.sling.feature.cpconverter.shared;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RepoPathTest {
    @Test
    public void testCtors() {
        RepoPath r1 = new RepoPath("/a/b/c");
        RepoPath r2 = new RepoPath(Arrays.asList("a", "b", "c"));

        assertEquals(r1, r2);
        assertEquals("/a/b/c", r2.toString());
    }

    @Test
    public void testCompareTo() {
        RepoPath r1 = new RepoPath("/a/b/c");
        RepoPath r2 = new RepoPath(Arrays.asList("a", "b", "c"));

        assertEquals(0, r1.compareTo(r2));

        r2 = new RepoPath("/a/b");
        assertTrue(r1.compareTo(r2) > 0);
        assertTrue(r2.compareTo(r1) < 0);
    }

    @Test
    public void testGetParent() {
        RepoPath r1 = new RepoPath("/foo/bar");
        RepoPath r2 = new RepoPath("foo");
        RepoPath r3 = new RepoPath("/foo");

        assertEquals(r2, r1.getParent());
        assertEquals(r3, r1.getParent());
    }

    @Test
    public void testStartsWith() {
        RepoPath r1 = new RepoPath("/foo/bar");
        RepoPath r2 = new RepoPath("foo");
        RepoPath r3 = new RepoPath("/foo");

        assertTrue(r1.startsWith(r2));
        assertTrue(r1.startsWith(r3));
        assertFalse(r2.startsWith(r1));
        assertTrue(r1.startsWith(r1));
        assertTrue(r2.startsWith(r2));
        assertTrue(r2.startsWith(r3));
        assertFalse(r2.startsWith(new RepoPath(Collections.singletonList("fo"))));

        assertTrue(r1.startsWith(r1.getParent()));
        assertTrue(r2.startsWith(r2.getParent()));
        assertTrue(r3.startsWith(r3.getParent()));
    }

    @Test
    public void testTopLevelPath() {
        RepoPath path = new RepoPath("/foo");
        assertEquals("/foo", path.toString());
        assertFalse(path.isRepositoryPath());

        RepoPath parent = path.getParent();
        assertNotNull(parent);
        assertFalse(parent.isRepositoryPath());
        assertEquals(new RepoPath("/"), parent);
        
        assertTrue(path.startsWith(path));
        assertTrue(path.startsWith(new RepoPath("/")));
        assertFalse(path.startsWith(new RepoPath("")));

        assertEquals(new RepoPath("/foo"), path);
        assertEquals(new RepoPath("foo"), path);
        assertEquals(new RepoPath(Collections.singletonList("foo")), path);
        
        assertNotEquals(new RepoPath("/bar"), path);
        assertNotEquals(new RepoPath("/"), path);
        assertNotEquals(new RepoPath(""), path);
    }
    
    @Test
    public void testRootPath() {
        RepoPath path = new RepoPath("/");
        assertEquals("/", path.toString());
        assertFalse(path.isRepositoryPath());

        assertNull(path.getParent());

        assertTrue(path.startsWith(path));
        assertFalse(path.startsWith(new RepoPath("")));
        assertFalse(path.startsWith(new RepoPath("/foo")));
        
        assertEquals(new RepoPath(Collections.emptyList()), path);
        assertEquals(new RepoPath("/"), path);
        assertNotEquals(new RepoPath(""), path);
    }

    @Test
    public void testRepositoryPath() {
        RepoPath path = new RepoPath("");
        assertEquals("", path.toString());
        assertTrue(path.isRepositoryPath());

        assertNull(path.getParent());

        assertFalse(path.startsWith(path));
        assertFalse(path.startsWith(new RepoPath("/")));
        assertFalse(path.startsWith(new RepoPath("/foo")));

        assertEquals(new RepoPath(""), path);
        assertNotEquals(new RepoPath(Collections.emptyList()), path);
    }
}
