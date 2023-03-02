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
package org.apache.sling.feature.cpconverter.handlers.slinginitialcontent;

import org.apache.sling.jcr.contentloader.PathEntry;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Objects;

/**
 * Holds Initial Content meta data for a single file
 */
class SlingInitialContentBundleEntryMetaData {

    private final File sourceFile;
    private final PathEntry pathEntry;
    private final String repositoryPath;

    SlingInitialContentBundleEntryMetaData(
            @NotNull File sourceFile,
            @NotNull PathEntry pathEntry,
            @NotNull String repositoryPath) {
        this.sourceFile = sourceFile;
        this.pathEntry = pathEntry;
        this.repositoryPath = repositoryPath;
    }

    /**
     * 
     * @return the initial content file which is supposed to be installed to {@link #getRepositoryPath()}
     */
    @NotNull
    File getSourceFile() {
        return sourceFile;
    }

    /**
     * 
     * @return the path entry covering the file {@link #sourceFile} (maybe for a parent)
     */
    @NotNull
    PathEntry getPathEntry() {
        return pathEntry;
    }

    /**
     * 
     * @return the absolute target repository root path of the initial content inside {@link #sourceFile}
     */
    @NotNull
    String getRepositoryPath() {
        return repositoryPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlingInitialContentBundleEntryMetaData that = (SlingInitialContentBundleEntryMetaData) o;
        return repositoryPath.equals(that.repositoryPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFile, pathEntry, repositoryPath);
    }

    @Override
    public String toString() {
        return "SlingInitialContentBundleEntryMetaData{" +
                "repositoryPath='" + repositoryPath + '\'' +
                '}';
    }
}
