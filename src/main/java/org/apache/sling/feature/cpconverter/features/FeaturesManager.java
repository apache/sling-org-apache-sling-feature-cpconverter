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

import java.util.Dictionary;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FeaturesManager {

    void init(@NotNull String groupId, @NotNull String artifactId, @NotNull String version);

    @Nullable Feature getTargetFeature();

    @NotNull Feature getRunMode(@Nullable String runMode);

    void addArtifact(@Nullable String runMode, @NotNull ArtifactId id);

    void addArtifact(@Nullable String runMode, @NotNull ArtifactId id, @Nullable Integer startOrder);

    void addAPIRegionExport(@Nullable String runMode, @NotNull String exportedPackage);

    void addConfiguration(@Nullable String runMode, @NotNull String pid, @Nullable Dictionary<String, Object> configurationProperties);

    void serialize() throws Exception;

    void addOrAppendRepoInitExtension(@NotNull String text, @Nullable String runMode);

}
