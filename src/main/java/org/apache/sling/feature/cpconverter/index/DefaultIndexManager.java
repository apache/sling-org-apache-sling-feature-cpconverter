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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;

public class DefaultIndexManager implements IndexManager {

    private IndexDefinitions indexDefinitions = new IndexDefinitions();
    private IndexDefinitionsJsonWriter writer = new IndexDefinitionsJsonWriter(indexDefinitions);

    @Override
    public void addRepoinitExtension(FeaturesManager features) throws IOException, ConverterException {

        if ( indexDefinitions.getIndexes().isEmpty() )
            return;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeAsJson(out);
        features.addOrAppendOakIndexDefinitionsExtension("content-package", out.toString(StandardCharsets.UTF_8.toString()));
    }

    @Override
    public IndexDefinitions getIndexes() {
        return indexDefinitions;
    }

    @Override
    public void reset() {
        indexDefinitions = new IndexDefinitions();
        writer = new IndexDefinitionsJsonWriter(indexDefinitions);
    }
}
