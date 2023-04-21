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

import static org.apache.sling.feature.cpconverter.index.SimpleNamespaceResolver.OAK_NAMESPACE;
import static org.apache.sling.feature.cpconverter.index.SimpleNamespaceResolver.OAK_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.jcr.NamespaceRegistry;
import javax.jcr.PropertyType;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.spi.NameFactory;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.util.Base64;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.Test;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;

public class IndexDefinitionsJsonWriterTest {

    private NameFactory nameFactory;
    private IndexDefinitions definitions;

    @Before
    public void setUp() {
        nameFactory = NameFactoryImpl.getInstance();
        definitions = new IndexDefinitions();
        definitions.registerPrefixMapping(NamespaceRegistry.PREFIX_NT, NamespaceRegistry.NAMESPACE_NT);
        definitions.registerPrefixMapping(NamespaceRegistry.PREFIX_JCR, NamespaceRegistry.NAMESPACE_JCR);
        definitions.registerPrefixMapping(OAK_PREFIX, OAK_NAMESPACE);

    }

    @Test
    public void emptyInput() throws IOException {
        JsonObject root = generateAndParse(definitions);
        assertThat(root).as("index definitions").isEmpty();
    }

    @Test
    public void propertyIndexDefinition() throws IOException {

        Collection<DocViewProperty2> fooProps = new ArrayList<>();
        fooProps.add(new DocViewProperty2(nameFactory.create("{}type"), "property"));
        fooProps.add(new DocViewProperty2(nameFactory.create("{}comment"), "foo:bar"));
        fooProps.add(new DocViewProperty2(nameFactory.create(NamespaceRegistry.NAMESPACE_JCR, "primaryType"), OAK_PREFIX+":QueryIndexDefinition"));
        fooProps.add(new DocViewProperty2(nameFactory.create("{}reindex"), Boolean.FALSE.toString(), PropertyType.BOOLEAN));
        fooProps.add(new DocViewProperty2(nameFactory.create("{}reindexCount"), "1", PropertyType.LONG));

        definitions.addNode("/oak:index", new DocViewNode2(nameFactory.create("{}foo"), fooProps));

        Collection<DocViewProperty2> barProps = new ArrayList<>();
        barProps.add(new DocViewProperty2(nameFactory.create("{}type"), "property"));
        barProps.add(new DocViewProperty2(nameFactory.create(NamespaceRegistry.NAMESPACE_JCR, "primaryType"), OAK_PREFIX+":QueryIndexDefinition"));
        barProps.add(new DocViewProperty2(nameFactory.create("{}reindex"), Boolean.TRUE.toString(), PropertyType.BOOLEAN));
        barProps.add(new DocViewProperty2(nameFactory.create("{}reindexCount"), "25", PropertyType.LONG));

        definitions.addNode("/oak:index", new DocViewNode2(nameFactory.create("{}bar"), barProps));

        JsonObject root = generateAndParse(definitions);
        assertThat(root).as("indexDefinitions")
            .hasSize(2)
            .hasEntrySatisfying("/oak:index/foo", Conditions.isJsonObject())
            .hasEntrySatisfying("/oak:index/bar", Conditions.isJsonObject());

        JsonObject fooIndex = root.getJsonObject("/oak:index/foo");
        assertThat(fooIndex).as("foo index")
            .hasSize(5)
            .contains(entry("type", Json.createValue("property")))
            .contains(entry("comment", Json.createValue("str:foo:bar")))
            .contains(entry("jcr:primaryType", Json.createValue("nam:oak:QueryIndexDefinition")))
            .contains(entry("reindex", JsonObject.FALSE))
            .contains(entry("reindexCount", Json.createValue(1)));
    }

    @Test
    public void invalidLongPropertyIsAccepted() throws IOException {

        Collection<DocViewProperty2> fooProps = new ArrayList<>();
        fooProps.add(new DocViewProperty2(nameFactory.create("{}type"), "property"));
        fooProps.add(new DocViewProperty2(nameFactory.create(NamespaceRegistry.NAMESPACE_JCR, "primaryType"), OAK_PREFIX+":QueryIndexDefinition"));
        fooProps.add(new DocViewProperty2(nameFactory.create("{}reindex"), Boolean.FALSE.toString(), PropertyType.BOOLEAN));
        fooProps.add(new DocViewProperty2(nameFactory.create("{}reindexCount"), "1.0", PropertyType.LONG));

        definitions.addNode("/oak:index", new DocViewNode2(nameFactory.create("{}foo"), fooProps));

        JsonObject root = generateAndParse(definitions);
        assertThat(root).as("indexDefinitions")
            .hasSize(1)
            .hasEntrySatisfying("/oak:index/foo", Conditions.isJsonObject());

        JsonObject fooIndex = root.getJsonObject("/oak:index/foo");
        assertThat(fooIndex).as("foo index")
            .contains(entry("reindexCount", Json.createValue(1)));
    }

    @Test
    public void luceneIndexDefinitionWithTikaConfig() throws IOException {

        String configXmlFileContents = "<properties/>";

        // lucene index
        Collection<DocViewProperty2> luceneProps = new ArrayList<>();
        luceneProps.add(new DocViewProperty2(nameFactory.create("{}type"), "lucene"));
        luceneProps.add(new DocViewProperty2(nameFactory.create(NamespaceRegistry.NAMESPACE_JCR, "primaryType"), OAK_PREFIX+":QueryIndexDefinition"));
        luceneProps.add(new DocViewProperty2(nameFactory.create("{}reindex"), Boolean.FALSE.toString(), PropertyType.BOOLEAN));
        luceneProps.add(new DocViewProperty2(nameFactory.create("{}reindexCount"), "1", PropertyType.LONG));
        luceneProps.add(new DocViewProperty2(nameFactory.create("{}includePropertyTypes"), Arrays.asList("String", "Binary"), PropertyType.STRING));

        definitions.addNode("/oak:index", new DocViewNode2(nameFactory.create("{}lucene"), luceneProps));

        // index rules node
        List<DocViewProperty2> indexRulesProps = Collections.singletonList(new DocViewProperty2(nameFactory.create(NamespaceRegistry.NAMESPACE_JCR, "primaryType"), "nt:unstructured"));

        definitions.addNode("/oak:index/lucene", new DocViewNode2(nameFactory.create("{}indexRules"), indexRulesProps));

        // tika node
        List<DocViewProperty2> tikaProps = Collections.singletonList(new DocViewProperty2(nameFactory.create(NamespaceRegistry.NAMESPACE_JCR, "primaryType"), "nt:unstructured"));

        definitions.addNode("/oak:index/lucene", new DocViewNode2(nameFactory.create("{}tika"), tikaProps));

        // tika config.xml node
        List<DocViewProperty2> configXmlProps = Collections.singletonList(new DocViewProperty2(nameFactory.create(NamespaceRegistry.NAMESPACE_JCR, "primaryType"), "nt:file"));

        definitions.addNode("/oak:index/lucene/tika", new DocViewNode2(nameFactory.create("{}config.xml"), configXmlProps));
        definitions.registerBinary("/oak:index/lucene/tika/config.xml", new ByteArrayInputStream(configXmlFileContents.getBytes(StandardCharsets.UTF_8)));

        // tika config.xml jcr:content node
        List<DocViewProperty2> jcrContentProps = Collections.singletonList(new DocViewProperty2(nameFactory.create(NamespaceRegistry.NAMESPACE_JCR, "primaryType"), "nt:resource"));
        definitions.addNode("/oak:index/lucene/tika/config.xml", new DocViewNode2(nameFactory.create(NamespaceRegistry.NAMESPACE_JCR, "resource"), jcrContentProps));

        JsonObject root = generateAndParse(definitions);
        System.out.println(root);

        assertThat(root).as("root index")
            .hasEntrySatisfying("/oak:index/lucene", Conditions.isJsonObject());

        JsonObject lucene = root.getJsonObject("/oak:index/lucene");
        assertThat(lucene).as("lucene index")
            .hasEntrySatisfying("tika", Conditions.isJsonObject());

        JsonObject tika = lucene.getJsonObject("tika");
        assertThat(tika).as("tika node index")
            .hasEntrySatisfying("config.xml", Conditions.isJsonObject());

        JsonObject configNode = tika.getJsonObject("config.xml");
        assertThat(configNode).as("config node")
            .hasEntrySatisfying("jcr:resource", Conditions.isJsonObject());

        assertThat(configNode).as("config node has " + JcrConstants.JCR_PRIMARYTYPE)
                .containsKey(JcrConstants.JCR_PRIMARYTYPE);

        JsonString jcrPrimaryType = configNode.getJsonString(JcrConstants.JCR_PRIMARYTYPE);
        assertThat(jcrPrimaryType.toString()).as("jcrPrimaryType property contains " + JcrConstants.NT_FILE)
                .contains(JcrConstants.NT_FILE);

        JsonObject configContentNode = configNode.getJsonObject(JcrConstants.JCR_CONTENT);
        JsonString binaryEntry = configContentNode.getJsonString(JcrConstants.JCR_DATA);
        assertThat(binaryEntry).as("config.xml blob")
            .hasFieldOrPropertyWithValue("string", ":blobId:" + Base64.encode(configXmlFileContents));
    }


    private JsonObject generateAndParse(IndexDefinitions definitions) throws IOException {

        IndexDefinitionsJsonWriter writer = new IndexDefinitionsJsonWriter(definitions);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeAsJson(out);

        JsonParser parser = Json.createParser(new ByteArrayInputStream(out.toByteArray()));
        JsonObject root = parser.getObject();
        return root;
    }

    static class Conditions {
        public static Condition<JsonValue> isJsonObject() {
            return new Condition<JsonValue>("Is a " + JsonObject.class.getSimpleName()) {
                @Override
                public boolean matches(JsonValue value) {
                    return value instanceof JsonObject;
                }
            };
        }
    }

}
