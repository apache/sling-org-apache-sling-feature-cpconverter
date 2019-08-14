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
import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.sling.feature.ArtifactId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultArtifactsDeployer implements ArtifactsDeployer {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final File artifactsDirectory;

    public DefaultArtifactsDeployer(File outputDirectory) {
        artifactsDirectory = outputDirectory;
        if (!artifactsDirectory.exists()) {
            artifactsDirectory.mkdirs();
        }
    }

    @Override
    public File getBundlesDirectory() {
        return artifactsDirectory;
    }

    @Override
    public void deploy(ArtifactWriter artifactWriter, ArtifactId id) throws IOException {
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

        StringBuilder nameBuilder = new StringBuilder()
                                    .append(id.getArtifactId())
                                    .append('-')
                                    .append(id.getVersion());

        if (id.getClassifier() != null) {
            nameBuilder.append('-').append(id.getClassifier());
        }

        nameBuilder.append('.').append(id.getType());

        File targetFile = new File(targetDir, nameBuilder.toString());

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
            try (FileOutputStream targetStream = new FileOutputStream(targetFile)) {
                new MavenPomSupplierWriter(id).write(targetStream);
            }
        }
    }

}
