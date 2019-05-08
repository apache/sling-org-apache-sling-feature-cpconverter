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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

final class RunmodeMapper {

    private static final String FILENAME = "runmode.mapping";

    public static RunmodeMapper open(File featureModelsOutputDirectory) throws IOException {
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

    private final Properties properties;

    private RunmodeMapper(File runmodeMappingFile, Properties properties) {
        this.runmodeMappingFile = runmodeMappingFile;
        this.properties = properties;
    }

    public void addOrUpdate(String runMode, String jsonFileName) {
        if (runMode == null) {
            runMode = DEFAULT;
        }

        String value = properties.getProperty(runMode);

        if (value != null && !value.contains(jsonFileName)) {
            value += ',' + jsonFileName;
        } else {
            value = jsonFileName;
        }

        properties.setProperty(runMode, value);
    }

    public void save() throws IOException {
        try (FileOutputStream output = new FileOutputStream(runmodeMappingFile)) {
            properties.store(output, "File edited by the Apache Sling Content Package to Sling Feature converter");
        }
    }

}
