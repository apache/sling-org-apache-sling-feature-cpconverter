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
package org.apache.sling.feature.cpconverter.artifacts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.sling.feature.ArtifactId;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores the deployed artifacts as files in a flat folder structure.
 * All deployed artifact ids must be unique among all group ids to prevent overwriting files.
 */
public class SimpleFolderArtifactsDeployer implements ArtifactsDeployer {

    private final File artifactsDirectory;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public SimpleFolderArtifactsDeployer(@NotNull File outputDirectory) {
        artifactsDirectory = outputDirectory;
        if (!artifactsDirectory.exists()) {
            artifactsDirectory.mkdirs();
        }
    }

    @Override
    public @NotNull File getBaseDirectory() {
        return artifactsDirectory;
    }

    @Override
    public void deploy(@NotNull ArtifactWriter artifactWriter, @NotNull ArtifactId id) throws IOException {
        File targetFile = new File(artifactsDirectory, id.toMvnName());
        logger.info("Writing data to {}...", targetFile);

        try (FileOutputStream targetStream = new FileOutputStream(targetFile)) {
            artifactWriter.write(targetStream);
        }

        logger.info("Data successfully written to {}.", targetFile);
    }

}
