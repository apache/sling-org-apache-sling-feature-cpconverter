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
package org.apache.sling.feature.cpconverter.index;

import java.io.IOException;

import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.jetbrains.annotations.NotNull;

/**
 * Point of entry for logic related to handling Oak indexes
 *
 * @see <a href=
 *  "https://jackrabbit.apache.org/oak/docs/query/oak-run-indexing.html">Oak-Run
 *   indexing</a>
 */
public interface IndexManager {

    public static final String EXTENSION_NAME = "oak-index-definitions";

    /**
     * Returns the index definitions managed by this instance
     *
     * <p>The returned object may be used to record data discovered about oak indexes</p>
     *
     * @return the index definitions
     */
    @NotNull IndexDefinitions getIndexes();

    /**
     * Records the Oak index data using the features manager
     *
     * <p>The index definitions will be recoreded as a JSON repoinit extension named {@value #EXTENSION_NAME} .</p>
     *
     * @param features
     * @throws IOException
     * @throws ConverterException
     */
    void addRepoinitExtension(FeaturesManager features) throws IOException, ConverterException;

    /**
     * Resets the internal state
     */
    void reset();
}
