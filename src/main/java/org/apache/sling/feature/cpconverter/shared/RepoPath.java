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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 
 * A Repo Path represents a path in the repository, for example when used in
 * a repoinit section.
 *
 * @see
 * <a href="https://github.com/apache/sling-org-apache-sling-repoinit-parser/blob/master/src/main/javacc/RepoInitGrammar.jjt">Repoinit Grammar</a>
 *
 */
public class RepoPath implements Comparable<RepoPath>{
    private final List<String> path;
    private final boolean isRepositoryPath;

    /**
     * Construct a Repo Path from a string. The string should separate the path
     * segments with forward slashes, e.g. {@code /my/repo/path}.
     *
     * @param path The string representation of the path. If the initial leading forward
     * slash is missing it will be assumed to be there.
     */
    public RepoPath(@NotNull String path) {
        path = path.trim();
        isRepositoryPath = path.isEmpty();

        if (path.startsWith("/"))
            path = path.substring(1);

        this.path = (path.isEmpty()) ? Collections.emptyList() : Arrays.asList(path.split("/"));
    }

    /**
     * Construct a Repo Path from a List.
     *
     * @param list The list to create the repo path from. The list should not have
     * any separators.
     */
    public RepoPath(@NotNull List<String> list) {
        this.path = new ArrayList<>(list);
        this.isRepositoryPath = false;
    }

    @Override
    public int compareTo(@NotNull RepoPath o) {
        String me = toString();
        String them = o.toString();
        return me.compareTo(them);
    }

    /**
     * Get the parent path of the current path.
     *
     * @return The parent path, or {@code null} if we are at the root and there is no
     * further parent.
     */
    public @Nullable RepoPath getParent() {
        // root path or repository path
        if (path.isEmpty())
            return null;

        ArrayList<String> parentPath = new ArrayList<>(path.subList(0, path.size() - 1));
        return new RepoPath(parentPath);
    }
    
    public @NotNull List<String> getSegments() {
        return path;
    }

    /**
     * Check is this path starts with the other path.
     *
     * @param otherPath The other path to check against.
     * @return If it starts with the other path or not.
     */
    public boolean startsWith(@Nullable RepoPath otherPath) {
        if (otherPath == null || isRepositoryPath || otherPath.isRepositoryPath) {
            return false;
        }

        if (path.size() < otherPath.path.size()) {
            return false;
        }

        List<String> l = new ArrayList<>(path.subList(0, otherPath.path.size()));
        return l.equals(otherPath.path);
    }

    public boolean isRepositoryPath() {
        return isRepositoryPath;
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, isRepositoryPath);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RepoPath other = (RepoPath) obj;
        return Objects.equals(path, other.path) && isRepositoryPath == other.isRepositoryPath;
    }

    @Override
    public String toString() {
        return isRepositoryPath ? "" : "/" + path.stream().collect(Collectors.joining("/"));
    }
}
