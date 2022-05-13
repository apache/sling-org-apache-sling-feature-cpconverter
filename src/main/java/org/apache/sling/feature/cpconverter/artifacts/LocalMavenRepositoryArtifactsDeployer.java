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

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.sling.feature.ArtifactId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores the deployed artifacts in a <a href="https://cwiki.apache.org/confluence/display/MAVENOLD/Repository+Layout+-+Final">local Maven repository layout</a>.
 */
public final class LocalMavenRepositoryArtifactsDeployer implements ArtifactsDeployer {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final File artifactsDirectory;

    public LocalMavenRepositoryArtifactsDeployer(@NotNull File outputDirectory) {
        artifactsDirectory = outputDirectory;
        if (!artifactsDirectory.exists()) {
            artifactsDirectory.mkdirs();
        }
    }

    public @NotNull File getBaseDirectory() {
        return this.artifactsDirectory;
    }

    @Override
    public @NotNull String deploy(@NotNull ArtifactWriter artifactWriter, @Nullable String runmode, @NotNull ArtifactId id) throws IOException {
        requireNonNull(artifactWriter, "Null ArtifactWriter can not install an artifact to a Maven repository.");
        requireNonNull(id, "Bundle can not be installed to a Maven repository without specifying a valid id.");

        File targetDir = artifactsDirectory;

        StringTokenizer tokenizer = new StringTokenizer(id.getGroupId(), ".");
        while (tokenizer.hasMoreTokens()) {
            String current = tokenizer.nextToken();
            targetDir = new File(targetDir, current);
        }

        targetDir = new File(targetDir, id.getArtifactId());
        targetDir = new File(targetDir, id.getVersion());
        targetDir.mkdirs();

        // deploy the main artifact


        File targetFile = new File(targetDir, id.toMvnName());

        logger.info("Writing data to {}...", targetFile);

        try (FileOutputStream targetStream = new FileOutputStream(targetFile)) {
            artifactWriter.write(targetStream);
        }

        logger.info("Data successfully written to {}.", targetFile);

        // automatically deploy the supplied POM file

        targetFile = new File(targetDir, String.format("%s-%s.pom", id.getArtifactId(), id.getVersion()));

        // If a POM already exists then there is not need to overwrite it as either the entire POM is lost
        // or if its the a file previously generated here it must be the same
        if(!targetFile.exists()) {
            try (FileWriter targetStream = new FileWriter(targetFile)) {
                new MavenPomSupplierWriter(id).write(targetStream);
            }
        }
        return targetFile.toString();
    }

}
