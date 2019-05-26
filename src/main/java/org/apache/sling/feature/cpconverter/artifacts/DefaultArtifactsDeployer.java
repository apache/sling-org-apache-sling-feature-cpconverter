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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public final class DefaultArtifactsDeployer implements ArtifactsDeployer {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final File artifactsDirectory;

    @Inject
    public DefaultArtifactsDeployer(@Named("features.artifacts.outdir") File outputDirectory) {
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
    public void deploy(ArtifactWriter artifactWriter,
                       String groupId,
                       String artifactId,
                       String version,
                       String classifier,
                       String type) throws IOException {
        requireNonNull(artifactWriter, "Null ArtifactWriter can not install an artifact to a Maven repository.");
        requireNonNull(groupId, "Bundle can not be installed to a Maven repository without specifying a valid 'groupId'.");
        requireNonNull(artifactId, "Bundle can not be installed to a Maven repository without specifying a valid 'artifactId'.");
        requireNonNull(version, "Bundle can not be installed to a Maven repository without specifying a valid 'version'.");
        requireNonNull(type, "Bundle can not be installed to a Maven repository without specifying a valid 'type'.");

        File targetDir = artifactsDirectory;

        StringTokenizer tokenizer = new StringTokenizer(groupId, ".");
        while (tokenizer.hasMoreTokens()) {
            String current = tokenizer.nextToken();
            targetDir = new File(targetDir, current);
        }

        targetDir = new File(targetDir, artifactId);
        targetDir = new File(targetDir, version);
        targetDir.mkdirs();

        // deploy the main artifact

        StringBuilder nameBuilder = new StringBuilder()
                                    .append(artifactId)
                                    .append('-')
                                    .append(version);

        if (classifier != null) {
            nameBuilder.append('-').append(classifier);
        }

        nameBuilder.append('.').append(type);

        File targetFile = new File(targetDir, nameBuilder.toString());

        logger.info("Writing data to {}...", targetFile);

        try (FileOutputStream targetStream = new FileOutputStream(targetFile)) {
            artifactWriter.write(targetStream);
        }

        logger.info("Data successfully written to {}.", targetFile);

        // automatically deploy the supplied POM file

        targetFile = new File(targetDir, String.format("%s-%s.pom", artifactId, version));

        try (FileOutputStream targetStream = new FileOutputStream(targetFile)) {
            new MavenPomSupplierWriter(groupId, artifactId, version, type).write(targetStream);
        }
    }

}
