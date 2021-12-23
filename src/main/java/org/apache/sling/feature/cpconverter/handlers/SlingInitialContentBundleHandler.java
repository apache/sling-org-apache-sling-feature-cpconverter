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
package org.apache.sling.feature.cpconverter.handlers;


import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.BundleSlingInitialContentExtractor;
import org.apache.sling.feature.cpconverter.shared.CheckedConsumer;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarFile;

public class SlingInitialContentBundleHandler extends BundleEntryHandler {
    private final @NotNull AbstractContentPackageHandler handler;

    public SlingInitialContentBundleHandler(@NotNull AbstractContentPackageHandler handler, @NotNull ContentPackage2FeatureModelConverter.SlingInitialContentPolicy slingInitialContentPolicy) {
        this.handler = handler;
        setSlingInitialContentPolicy(slingInitialContentPolicy);
    }
    
    @Override
    void processBundleInputStream(@NotNull String path, @NotNull Path originalBundleFile, @NotNull String bundleName, @Nullable String runMode, @Nullable Integer startLevel, @NotNull ContentPackage2FeatureModelConverter converter) throws IOException, ConverterException {
        try (JarFile jarFile = new JarFile(originalBundleFile.toFile())) {
            // first extract bundle metadata from JAR input stream
            Artifact artifact = extractFeatureArtifact(bundleName, jarFile);
            ArtifactId id = artifact.getId();
            
            try (InputStream ignored = new BundleSlingInitialContentExtractorOverride(slingInitialContentPolicy, path, id, jarFile, converter, runMode).extract()) {
                logger.info("Ignoring inputstream {} with id {}", path, id);
            }
        }
    }
    
    class BundleSlingInitialContentExtractorOverride extends BundleSlingInitialContentExtractor{

        public BundleSlingInitialContentExtractorOverride(ContentPackage2FeatureModelConverter.SlingInitialContentPolicy slingInitialContentPolicy, @NotNull String path, @NotNull ArtifactId bundleArtifactId, @NotNull JarFile jarFile, @NotNull ContentPackage2FeatureModelConverter converter, @Nullable String runMode) throws IOException {
            super(slingInitialContentPolicy, path, bundleArtifactId, jarFile, converter, runMode);
        }

        @Override
        protected void finalizePackageAssembly(@NotNull String path, @Nullable String runMode) throws IOException, ConverterException {
            for (java.util.Map.Entry<PackageType, VaultPackageAssembler> entry : packageAssemblers.entrySet()) {
                File packageFile = entry.getValue().createPackage();
                handler.processSubPackage(path + "-" + entry.getKey(), runMode, converter.open(packageFile), converter, true);
            }
        }
    }

   
}
