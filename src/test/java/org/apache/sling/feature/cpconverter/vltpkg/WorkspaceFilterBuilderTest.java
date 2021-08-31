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
package org.apache.sling.feature.cpconverter.vltpkg;

import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static junit.framework.TestCase.assertNotSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class WorkspaceFilterBuilderTest {
    
    private static final String TEST_ROOT_PATH = "/some/test/path";
    private static final String PATH_NOT_COVERED = "/path/not/covered/by/filter";
    
    private final WorkspaceFilter base = mock(WorkspaceFilter.class);

    private final PathFilterSet pfs = new PathFilterSet(TEST_ROOT_PATH);
    private final List<PathFilterSet> filterSets = new ArrayList<>();
    
    @Before
    public void before() throws Exception {
        pfs.setImportMode(ImportMode.UPDATE);
        pfs.addExclude(new DefaultPathFilter("test/exclude"));
        pfs.addInclude(new DefaultPathFilter("test/include"));
        filterSets.add(pfs);
    }
    
    @After
    public void after() {
        verify(base).getPropertyFilterSets();
        verifyNoMoreInteractions(base);
        reset(base);
    }
    
    private void assertWorkspaceFilter(WorkspaceFilter newFilter, int expectedEntrySize) {
        assertTrue(newFilter.covers(TEST_ROOT_PATH));
        List<PathFilterSet> filterSets = newFilter.getFilterSets();
        assertEquals(1, filterSets.size());
        PathFilterSet copy = filterSets.get(0);
        assertNotSame(pfs, copy);
        assertEquals(pfs.getRoot(), copy.getRoot());
        assertSame(pfs.getImportMode(), copy.getImportMode());
        if (expectedEntrySize == pfs.getEntries().size()) {
            assertEquals(pfs.getEntries(), copy.getEntries());
        } else {
            assertEquals(expectedEntrySize, copy.getEntries().size());
        }

        // upon adding a node-filter-set (without corresponding property-filter-set) the property-filter-sets gets 
        // populated in the DefaultWorkspaceFilter
        assertEquals(1, newFilter.getPropertyFilterSets().size());
    }
    
    @Test
    public void testExtractPropertyFilters() {
        when(base.getPropertyFilterSets()).thenReturn(filterSets);
        
        Map<String, PathFilterSet> m = WorkspaceFilterBuilder.extractPropertyFilters(base);
        assertEquals(1, m.size());
        assertSame(m.get(TEST_ROOT_PATH), pfs);
    }

    @Test
    public void testMissingCorrespondingPropertyFilter() throws Exception {
        when(base.getPropertyFilterSets()).thenReturn(Collections.emptyList());
        when(base.getFilterSets()).thenReturn(filterSets);

        WorkspaceFilter filter = new WorkspaceFilterBuilder(base, Collections.emptySet(), 
                Collections.singleton(TEST_ROOT_PATH), 
                Collections.emptySet()).build();

        assertWorkspaceFilter(filter, 2);
        verify(base).getFilterSets();
    }

    @Test
    public void testFilteredPath() throws Exception {
        when(base.getPropertyFilterSets()).thenReturn(filterSets);
        when(base.getFilterSets()).thenReturn(filterSets);

        WorkspaceFilter filter = new WorkspaceFilterBuilder(base, Collections.singleton(TEST_ROOT_PATH), 
                Collections.emptySet(), Collections.singleton(PATH_NOT_COVERED)).build();

        assertFalse(filter.covers(TEST_ROOT_PATH));
        assertTrue(filter.getFilterSets().isEmpty());

        verify(base).getFilterSets();
    }

    @Test
    public void testFilteredPathRequiresExclude() throws Exception {
        when(base.getPropertyFilterSets()).thenReturn(filterSets);
        when(base.getFilterSets()).thenReturn(filterSets);

        Set<String> filteredPaths = new HashSet<>();
        filteredPaths.add(TEST_ROOT_PATH + "/subtree");   // to be added as additional exclude
        filteredPaths.add(PATH_NOT_COVERED); // to be ignored and not added as additional exclude
        
        WorkspaceFilter filter = new WorkspaceFilterBuilder(base,
                filteredPaths,
                Collections.singleton(TEST_ROOT_PATH), Collections.emptySet()).build();

        assertWorkspaceFilter(filter, 3);
        verify(base).getFilterSets();
    }

    @Test
    public void testExtractedPath() throws Exception {
        when(base.getPropertyFilterSets()).thenReturn(filterSets);
        when(base.getFilterSets()).thenReturn(filterSets);

        WorkspaceFilter filter = new WorkspaceFilterBuilder(base, Collections.emptySet(), 
                Collections.emptySet(), 
                Collections.singleton(TEST_ROOT_PATH)).build();

        assertWorkspaceFilter(filter, 2);
        verify(base).getFilterSets();
    }

    @Test
    public void testExtractedSiblingPath() throws Exception {
        when(base.getPropertyFilterSets()).thenReturn(filterSets);
        when(base.getFilterSets()).thenReturn(filterSets);

        WorkspaceFilter filter = new WorkspaceFilterBuilder(base, Collections.emptySet(), 
                Collections.emptySet(), 
                Collections.singleton(TEST_ROOT_PATH+"_sib")).build();

        assertWorkspaceFilter(filter, 2);
        verify(base).getFilterSets();
    }
}