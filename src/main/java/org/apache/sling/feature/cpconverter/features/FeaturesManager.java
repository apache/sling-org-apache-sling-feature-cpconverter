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

import java.io.IOException;
import java.util.Dictionary;
import java.util.Map;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FeaturesManager {

    void init(@NotNull ArtifactId packageId);

    @Nullable Feature getTargetFeature();

    @NotNull Feature getRunMode(@Nullable String runMode);

    void addArtifact(@Nullable String runMode, @NotNull ArtifactId id);

    void addArtifact(@Nullable String runMode, @NotNull ArtifactId id, @Nullable Integer startOrder);

    void addAPIRegionExport(@Nullable String runMode, @NotNull String exportedPackage);

    /**
     * Add a configuration
     * @param runMode Optional runmode
     * @param cfg The configuration object for the pid and factory pid, name - no properties
     * @param path The path for the configuration
     * @param configurationProperties The configuration properties
     * @throws IOException if an error occurs
     * @throws ConverterException if conversion fails
     */
    void addConfiguration(@Nullable String runMode,
                          @NotNull Configuration cfg,
                          @NotNull String path,
                          @NotNull Dictionary<String, Object> configurationProperties)
            throws IOException, ConverterException;

    void serialize() throws IOException;

    /**
     * Add repoinit instructions
     * @param source An identifier for the source, for example the configuration pid
     * @param text The repoinit instructions
     * @param runMode Optional runmode
     * @throws IOException if an error occurs
     * @throws ConverterException if conversion fails
     */
    void addOrAppendRepoInitExtension(@NotNull String source, @NotNull String text, @Nullable String runMode)
            throws IOException, ConverterException;

    @NotNull
    Map<String, String> getNamespaceUriByPrefix();

}
