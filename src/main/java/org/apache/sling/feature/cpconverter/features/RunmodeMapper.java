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
package org.apache.sling.feature.cpconverter.features;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

final class RunmodeMapper {

    static final String FILENAME = "runmode.mapping";

    public static @NotNull RunmodeMapper open(@NotNull File featureModelsOutputDirectory) throws IOException {
        Properties properties = new Properties();

        File runmodeMappingFile = new File(featureModelsOutputDirectory, FILENAME);
        if (runmodeMappingFile.exists()) {
            try (FileInputStream input = new FileInputStream(runmodeMappingFile)) {
                properties.load(input);
            }
        }

        return new RunmodeMapper(runmodeMappingFile, properties);
    }

    private static final String DEFAULT = "(default)";

    private final File runmodeMappingFile;

    private final Map<String, Set<String>> properties = new HashMap<>();

    private RunmodeMapper(@NotNull File runmodeMappingFile, @NotNull Properties properties) {
        this.runmodeMappingFile = runmodeMappingFile;
        for(final String key : properties.stringPropertyNames()) {
            for(final String name : properties.getProperty(key).split(",")) {
                this.properties.computeIfAbsent(key, id -> new LinkedHashSet<>()).add(name);
            }
        }
    }

    public void addOrUpdate(@Nullable String runMode, @NotNull String jsonFileName) {
        if (runMode == null) {
            runMode = DEFAULT;
        }

        this.properties.computeIfAbsent(runMode, id -> new LinkedHashSet<>()).add(jsonFileName);
    }

    public void save() throws IOException {
        final Properties props = new Properties();
        for(final Map.Entry<String, Set<String>> entry : this.properties.entrySet()) {
            props.put(entry.getKey(), String.join(",", entry.getValue()));
        }
        try (FileOutputStream output = new FileOutputStream(runmodeMappingFile)) {
            props.store(output, "File edited by the Apache Sling Content Package to Sling Feature converter");
        }
    }

}
