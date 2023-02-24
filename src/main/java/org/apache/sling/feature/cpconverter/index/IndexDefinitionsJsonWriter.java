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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.jcr.PropertyType;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.util.Base64;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;

/**
 * Writes index definitions in a JSON format that can be consumed by the {@code oak-run} tool.
 *
 * @see <a href=
 *  "https://jackrabbit.apache.org/oak/docs/query/oak-run-indexing.html">Oak-Run
 *   indexing</a>
 */
public class IndexDefinitionsJsonWriter {

    private static final Function<String, JsonValue> BLOB_MAPPER =  s -> Json.createValue(":blobId:" + Base64.encode(s));

    private static final Function<String, JsonValue> SAFE_LONG_MAPPER = new Function<String, JsonValue>() {

        @Override
        public JsonValue apply(String t) {
            if ( t.endsWith(".0") )
                t = t.replace(".0", "");

            return Json.createValue(Long.parseLong(t));
        }
    };

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final IndexDefinitions indexDefinitions;

    public IndexDefinitionsJsonWriter(@NotNull IndexDefinitions indexDefinitions) {
        this.indexDefinitions = indexDefinitions;
    }

    /**
     * Writes the index definitions to the specified {@code out}
     *
     * @param out the output stream to write to
     */
    public void writeAsJson(@NotNull OutputStream out) throws IOException {
        try ( JsonGenerator root = Json.createGenerator(out) ) {
            root.writeStartObject();
            for ( Map.Entry<String, List<DocViewNode2>> indexEntry : indexDefinitions.getIndexes().entrySet() )
                for ( DocViewNode2 index : indexEntry.getValue() )
                    write(root, index, indexEntry.getKey());
            root.writeEnd(); // end object declaration
        }
    }

    private void write(JsonGenerator json, DocViewNode2 index, String parentPath) throws IOException {

        String nodeName = indexDefinitions.toShortName(index.getName());
        String objectKey = parentPath.equals(IndexDefinitions.OAK_INDEX_PATH) ?
                IndexDefinitions.OAK_INDEX_PATH + "/" + nodeName : nodeName;

        // 1. start object
        json.writeStartObject(objectKey);

        // 2. write properties
        for ( DocViewProperty2 property : index.getProperties() ) {

            String propertyName = indexDefinitions.toShortName(property.getName());

            switch ( property.getType() ) {
                case PropertyType.STRING:
                case PropertyType.UNDEFINED:
                    write(json, propertyName, property.getStringValues(), s -> Json.createValue("str:" + s ));
                    break;
                case PropertyType.LONG:
                    write(json, propertyName, property.getStringValues(), SAFE_LONG_MAPPER );
                    break;
                case PropertyType.BOOLEAN:
                    write(json, propertyName, property.getStringValues(), s -> ( Boolean.parseBoolean(s) ? JsonValue.TRUE : JsonValue.FALSE)  );
                    break;
                case PropertyType.NAME:
                    write(json, propertyName, property.getStringValues(), s -> Json.createValue("nam:" + s ));
                    break;
                case PropertyType.DOUBLE:
                    write(json, propertyName, property.getStringValues(), s -> Json.createValue(Double.parseDouble(s) ));
                    break;
                case PropertyType.DATE:
                    write(json, propertyName, property.getStringValues(), s -> Json.createValue("dat:" + s) );
                    break;
                case PropertyType.PATH:
                    write(json, propertyName, property.getStringValues(), s -> Json.createValue("pat:" + s) );
                    break;
                case PropertyType.URI:
                    write(json, propertyName, property.getStringValues(), s -> Json.createValue("uri:" + s) );
                    break;
                case PropertyType.BINARY:
                    write(json, propertyName, property.getStringValues(), BLOB_MAPPER );
                    break;
                default:
                    logger.warn("Skipping property {}, don't know how to handle type {}; values: {}", property.getName(), property.getType(), property.getStringValues());

            }
        }

        String nodePath = parentPath + "/" + nodeName;  // NOSONAR - java:S1075 does not apply as this is not a filesystem path

        // 3. write nt:data entries for nt:resource children of nt:files
        // in this case, this is the nt:resource node
        Optional<byte[]> binary = indexDefinitions.getBinary(nodePath);
        if ( binary.isPresent() ) {
            write(json, JcrConstants.JCR_PRIMARYTYPE, Collections.singletonList(JcrConstants.NT_FILE),  s -> Json.createValue("nam:" + s ));
            json.writeStartObject(JcrConstants.JCR_CONTENT);
            String blobAsString = new String(binary.get(), StandardCharsets.UTF_8);
            write(json, JcrConstants.JCR_PRIMARYTYPE, Collections.singletonList(JcrConstants.NT_RESOURCE),  s -> Json.createValue("nam:" + s ));
            write(json, JcrConstants.JCR_MIMETYPE,Collections.singletonList(Files.probeContentType(Paths.get(nodeName))), Json::createValue );
            write(json, JcrConstants.JCR_DATA, Collections.singletonList(blobAsString), BLOB_MAPPER);
            json.writeEnd();
        };

        // 4. write children
        for ( DocViewNode2 child : indexDefinitions.getChildren(nodePath)) {
            write(json, child, nodePath);
        }

        // 5. end object
        json.writeEnd();
    }

    private void write(JsonGenerator json, String propertyName, List<String> propertyValues, Function<String, JsonValue> mapper) {
        if ( propertyValues.size() == 1 ) {
            json.write(propertyName, mapper.apply(propertyValues.get(0)));
            return;
        }

        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        propertyValues.stream()
            .map( mapper )
            .forEach( arrayBuilder::add );

        json.write(propertyName, arrayBuilder.build());
    }

}
