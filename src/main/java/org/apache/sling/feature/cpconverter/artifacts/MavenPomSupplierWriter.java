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

import java.io.FileWriter;
import java.io.IOException;

import org.apache.sling.feature.ArtifactId;
import org.jetbrains.annotations.NotNull;

public final class MavenPomSupplierWriter {

    private final ArtifactId id;

    public MavenPomSupplierWriter(@NotNull ArtifactId id) {
        this.id = id;
    }

    public void write(@NotNull FileWriter writer) throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<project xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\" xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        writer.write("          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");
        writer.write("  <modelVersion>4.0.0</modelVersion>\n");
        writer.write("    <groupId>");
        writer.write(id.getGroupId());
        writer.write("</groupId>\n");
        writer.write("    <artifactId>");
        writer.write(id.getArtifactId());
        writer.write("</artifactId>\n");
        writer.write("    <version>");
        writer.write(id.getVersion());
        writer.write("</version>\n");
        writer.write("</project>\n");
    }
}
