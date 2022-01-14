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

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.vltpkg.JcrNamespaceRegistry;
import org.apache.sling.jcr.contentloader.PathEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Holds various context variables for the BundleSlingInitialContentExtractor
 */
public class BundleSlingInitialContentExtractorContext {

    private final ContentPackage2FeatureModelConverter.SlingInitialContentPolicy slingInitialContentPolicy;
    private final String path;
    private final ArtifactId bundleArtifactId;
    private final JarFile jarFile;
    private final ContentPackage2FeatureModelConverter converter;
    private final String runMode;
    private final Manifest manifest;
    private final JcrNamespaceRegistry namespaceRegistry;
    private final Iterator<PathEntry> pathEntries;
    private final List<PathEntry> pathEntryList = new ArrayList<>();

    public BundleSlingInitialContentExtractorContext(ContentPackage2FeatureModelConverter.SlingInitialContentPolicy slingInitialContentPolicy, @NotNull String path, @NotNull ArtifactId bundleArtifactId, @NotNull JarFile jarFile, @NotNull ContentPackage2FeatureModelConverter converter, @Nullable String runMode) throws IOException {
        this.slingInitialContentPolicy = slingInitialContentPolicy;
        this.path = path;
        this.bundleArtifactId = bundleArtifactId;
        this.jarFile = jarFile;
        this.converter = converter;
        this.runMode = runMode;

        this.manifest = Objects.requireNonNull(jarFile.getManifest());
        this.namespaceRegistry = new JcrNamespaceRegistryProvider(manifest, jarFile, converter.getFeaturesManager().getNamespaceUriByPrefix()).provideRegistryFromBundle();

        pathEntries = PathEntry.getContentPaths(manifest, -1);

        if(pathEntries != null){
            pathEntries.forEachRemaining(pathEntryList::add);
        }
    }

    public ContentPackage2FeatureModelConverter.SlingInitialContentPolicy getSlingInitialContentPolicy() {
        return slingInitialContentPolicy;
    }

    public String getPath() {
        return path;
    }

    public ArtifactId getBundleArtifactId() {
        return bundleArtifactId;
    }

    public ContentPackage2FeatureModelConverter getConverter() {
        return converter;
    }

    public String getRunMode() {
        return runMode;
    }

    public JarFile getJarFile() {
        return jarFile;
    }

    public Manifest getManifest() {
        return manifest;
    }

    public JcrNamespaceRegistry getNamespaceRegistry() {
        return namespaceRegistry;
    }

    public List<PathEntry> getPathEntryList() {
        return new ArrayList<>(pathEntryList);
    }
}
