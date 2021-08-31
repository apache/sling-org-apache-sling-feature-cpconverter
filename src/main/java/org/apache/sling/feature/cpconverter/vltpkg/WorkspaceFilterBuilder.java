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

import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.FilterSet;
import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility class to build a new {@link WorkspaceFilter} from a given base filter and a new set of paths corresponding 
 * to the content of the new converted content packages, which no longer contains entries that were moved to repo-init.
 */
class WorkspaceFilterBuilder {
    
    private final WorkspaceFilter baseFilter;
    private final Set<String> filteredPaths;
    private final Set<String> cpPaths;
    private final Set<String> extractedPaths;

    /**
     * Create a new {@link WorkspaceFilterBuilder}
     * 
     * @param baseFilter The base {@link WorkspaceFilter} as computed from the original content package.
     * @param filteredRepositoryPaths A set of repository paths that got moved from the original content package to repo 
     *                                init and which there should no longer be referenced in the new {@link WorkspaceFilter}.
     * @param convertedRepositoryPaths A set of repository paths representing entries in the converted content packages.
     * @param extractedRepositoryPaths A set of repository paths extracted from those {@link org.apache.jackrabbit.vault.util.Constants#DOT_CONTENT_XML .content.xml} 
     *                                 files of the original content package that contain paths listed in the original {@link WorkspaceFilter}. 
     */
    WorkspaceFilterBuilder(@NotNull WorkspaceFilter baseFilter, 
                           @NotNull Set<String> filteredRepositoryPaths,
                           @NotNull Set<String> convertedRepositoryPaths,
                           @NotNull Set<String> extractedRepositoryPaths) {
        this.baseFilter = baseFilter;
        this.filteredPaths = filteredRepositoryPaths;
        this.cpPaths = convertedRepositoryPaths;
        this.extractedPaths = extractedRepositoryPaths;
        
    }

    /**
     * Build a new {@link WorkspaceFilter}
     * 
     * @return a new {@link WorkspaceFilter}
     * @throws IOException If an error occurs.
     */
    @NotNull WorkspaceFilter build() throws IOException {
        try {
            DefaultWorkspaceFilter dwf = new DefaultWorkspaceFilter();
            Map<String, PathFilterSet> propFilters = extractPropertyFilters(baseFilter);
            for (PathFilterSet pfs : baseFilter.getFilterSets()) {
                // add the filter to the new workspace filter if it either covers content from the converted package
                // or doesn't match any of the content that has been removed to repo-init. the latter condition 
                // make sure filter sets without any corresponding content (i.e. removal) are not dropped.
                if (coversConvertedPath(pfs) || !coversFilteredPath(pfs)) {
                    processPathFilterSet(dwf, pfs, propFilters);
                }
            }
            return dwf;
        } catch (ConfigurationException e) {
            throw new IOException(e);
        }
    }

    /**
     * Extract all property-filters and remember them for later as DefaultWorkspaceFilter.addPropertyFilterSet
     * is deprecated in favor of DefaultWorkspaceFilter.add(PathFilterSet nodeFilter, PathFilterSet propFilter).
     * The map created then allows to process property-filters together with node filters.
     *
     * @param base A {@link WorkspaceFilter} from which to extract the property filters.
     * @return A map of path (the root) to the corresponding property @{@link PathFilterSet}.
     * @see WorkspaceFilter#getPropertyFilterSets() 
     */
    static @NotNull Map<String, PathFilterSet> extractPropertyFilters(@NotNull WorkspaceFilter base) {
        Map<String, PathFilterSet> propFilters = new LinkedHashMap<>();
        base.getPropertyFilterSets().forEach(pathFilterSet -> propFilters.put(pathFilterSet.getRoot(), pathFilterSet));
        return propFilters;
    }
    
    private boolean coversFilteredPath(@NotNull PathFilterSet pfs) {
        return filteredPaths.stream().anyMatch(pfs::covers);
    }

    /**
     * Test if the given {@link PathFilterSet} is still required in the converted content package.
     * The following two conditions are taking into account:
     * - the given filter-set covers any of the paths included in the converted content package
     * - the given filter-set covers any of the extracted paths hidden in a .content.xml
     * - it's root path is a sibling of a manually extracted paths hidden in a .content.xml that get 
     *   installed despite not being covered by the filter.
     * 
     * @param pfs A {@link PathFilterSet} of the original base filter.
     * @return {@code true} if the given {@code PathFilterSet} is still relevant for the new {@code WorkspaceFilter} given
     * the new content of the converted package as reflected by the recorded paths.
     */
    private boolean coversConvertedPath(@NotNull PathFilterSet pfs) {
        return cpPaths.stream().anyMatch(pfs::covers) ||
                // test if a extracted path matches or is a sibling of the filter root (see discussion in SLING-10760)
                extractedPaths.stream().anyMatch(path -> path.equals(pfs.getRoot()) || Text.isSibling(path, pfs.getRoot()));
    }

    /**
     * Populate the new {@link WorkspaceFilter} with an adjusted copy of the given {@link PathFilterSet}.
     * 
     * @param newFilter The new {@link WorkspaceFilter} which gets populated with ethe copied (and adjusted) path filter sets.
     * @param pfs A {@link PathFilterSet path filter set} extracted from the original base filter.
     * @param propFilters The lookup map for {@link PathFilterSet property filters} as extracted before using {@link #extractPropertyFilters(WorkspaceFilter)}.
     * @throws ConfigurationException If an error occurs while building the adjusted path filter sets.
     */
    private void processPathFilterSet(@NotNull DefaultWorkspaceFilter newFilter, @NotNull PathFilterSet pfs, 
                                      @NotNull Map<String, PathFilterSet> propFilters) throws ConfigurationException {
        // create a new node path-filter-set (and if existing the corresponding property filter)
        PathFilterSet nodeFilterSet = copyPathFilterSet(pfs, filteredPaths);
        PathFilterSet propPfs = propFilters.remove(pfs.getRoot());
        if (propPfs != null) {
            // note: no need to add additional exclude entries for property-filters
            PathFilterSet propFilterSet = copyPathFilterSet(propPfs, Collections.emptySet());
            newFilter.add(nodeFilterSet, propFilterSet);
        } else {
            newFilter.add(nodeFilterSet);
        }
    }

    /**
     * Create a copy of the original {@link PathFilterSet} adding additional exclude rules for all filter paths that are 
     * covered by the given {@link PathFilterSet}.
     * 
     * @param pfs The original {@link PathFilterSet} as present on the original base {@link WorkspaceFilter}.
     * @param filteredPaths A set of paths that have been moved from the original content package to repo-init.
     * @return A copy of the given {@link PathFilterSet} plus additional exclude rules for covered filtered paths.
     * @throws ConfigurationException If an error occurs while constructing the new {@link PathFilterSet}
     */
    @NotNull
    private static PathFilterSet copyPathFilterSet(@NotNull PathFilterSet pfs, @NotNull Set<String> filteredPaths) throws ConfigurationException {
        // create a new path-filter-set
        PathFilterSet filterSet = new PathFilterSet(pfs.getRoot());
        filterSet.setType(pfs.getType());
        filterSet.setImportMode(pfs.getImportMode());

        // copy all entries to the new path-filter-set
        for (FilterSet.Entry<PathFilter> entry : pfs.getEntries()) {
            if (entry.isInclude()) {
                filterSet.addInclude(entry.getFilter());
            } else {
                filterSet.addExclude(entry.getFilter());
            }
        }

        // for all paths that got filtered out and moved to repo-init make sure they get explicitly excluded
        for (String path : filteredPaths) {
            if (pfs.covers(path)) {
                filterSet.addExclude(new DefaultPathFilter(path));
            }
        }
        return filterSet;
    }
}