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
package org.apache.sling.feature.cpconverter.handlers;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.cm.json.Configurations;

public final class JsonConfigurationEntryHandler extends AbstractConfigurationEntryHandler {

    public JsonConfigurationEntryHandler() {
        super("cfg\\.json");
    }

    @Override
    protected Dictionary<String, Object> parseConfiguration(String name, InputStream input) throws Exception {
        final Hashtable<String, Object> props = Configurations.buildReader()
            .withIdentifier(name)
            .build(new InputStreamReader(input, StandardCharsets.UTF_8))
            .readConfiguration();

        return props;
    }

}
