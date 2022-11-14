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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.jcr.NamespaceRegistry;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.fs.io.FileArchive;
import org.apache.jackrabbit.vault.packaging.impl.ZipVaultPackage;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.index.DefaultIndexManager;
import org.apache.sling.feature.cpconverter.index.IndexDefinitions;
import org.apache.sling.feature.cpconverter.vltpkg.BaseVaultPackageScanner;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.assertj.core.api.Condition;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mockito.Mockito;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class IndexDefinitionsEntryHandlerTest {

    @Test
    public void matches() {
        IndexDefinitionsEntryHandler handler = new IndexDefinitionsEntryHandler();
        assertThat(handler.matches("/jcr_root/_oak_index/.content.xml")).isTrue();
        assertThat(handler.matches("/jcr_root/not_oak_index/.content.xml")).isFalse();
        assertThat(handler.matches("/jcr_root/not_oak_index/stop.txt")).isFalse();
        assertThat(handler.matches("/jcr_root/_oak_index/bar/.content.xml")).isTrue();
        assertThat(handler.matches("/jcr_root/_oak_index/lucene/tika/config.xml")).isTrue();
        assertThat(handler.matches("/jcr_root/_oak_index/.vlt")).isFalse();
        assertThat(handler.matches("/jcr_root/_oak_index/stop.txt")).isTrue();
        assertThat(handler.matches("/jcr_root/not_oak_index/.vlt")).isFalse();
        assertThat(handler.matches("/jcr_root/apps/_oak_index/.content.xml")).isTrue();
        assertThat(handler.matches("/jcr_root/apps/.content.xml")).isFalse();
        assertThat(handler.matches("/jcr_root/not_oak_index/.content.xml")).isFalse();
    }

    @Test
    public void handleSingleFileIndexDefinition() throws IOException, ConverterException {

        DefaultIndexManager manager = new DefaultIndexManager();

        traverseForIndexing(manager, "index_single_file");

        IndexDefinitions defs = manager.getIndexes();
        Map<String, List<DocViewNode2>> indexes = defs.getIndexes();

        assertThat(indexes).as("index definitions")
            .hasSize(1)
            .containsKey("/oak:index");

       List<DocViewNode2> rootIndexes = indexes.get("/oak:index");
       assertThat(rootIndexes).as("root oak indexes")
            .hasSize(1)
            .element(0)
                .has( Conditions.localName("foo") )
                .has( Conditions.property("type", "property") );
    }

    @Test
    public void handleMultiFileIndexDefinition() throws IOException, ConverterException {

        DefaultIndexManager manager = new DefaultIndexManager();

        traverseForIndexing(manager, "index_multiple_files");

        IndexDefinitions defs = manager.getIndexes();
        Map<String, List<DocViewNode2>> indexes = defs.getIndexes();

        assertThat(indexes).as("index definitions")
            .hasSize(1)
            .containsKey("/oak:index");

        List<DocViewNode2> rootIndexes = indexes.get("/oak:index");
        assertThat(rootIndexes).as("root indexes")
            .hasSize(2);

        // ensure consistent order
        Collections.sort(rootIndexes, (a, b) -> defs.toShortName(a.getName()).compareTo(defs.toShortName(b.getName())));

        assertThat(rootIndexes).as("baz index")
            .element(0).has( Conditions.localName("baz") );
        assertThat(rootIndexes).as("lucene_custom index")
            .element(1)
                .has( Conditions.localName("lucene_custom") )
                .has( Conditions.property("type", "lucene") )
                .has( Conditions.childWithLocalName("/oak:index/lucene_custom", "indexRules", defs));

    }

    @Test
    public void handleIndexDefinitionWithNestedTikaXml() throws IOException, ConverterException, ParserConfigurationException, SAXException {
        DefaultIndexManager manager = new DefaultIndexManager();

        traverseForIndexing(manager, "index_nested_tika");

        IndexDefinitions defs = manager.getIndexes();
        Map<String, List<DocViewNode2>> indexes = defs.getIndexes();

        assertThat(indexes).as("index definitions")
            .hasSize(1)
            .containsKey("/oak:index");

        List<DocViewNode2> rootIndexes = indexes.get("/oak:index");
        assertThat(rootIndexes).as("root indexes")
            .hasSize(1);

        assertThat(rootIndexes).as("index definitions")
            .hasSize(1)
            .element(0)
                .has(Conditions.localName("lucene-custom"));

        DocViewNode2 luceneCustom = rootIndexes.get(0);
        assertThat(luceneCustom).as("lucene index definition")
            .has(Conditions.childWithLocalName("/oak:index/lucene-custom", "indexRules", defs))
            .has(Conditions.childWithLocalName("/oak:index/lucene-custom", "tika", defs));

        List<DocViewNode2> luceneCustomChildren = defs.getChildren("/oak:index/lucene-custom");
        assertThat(luceneCustomChildren).as("lucene index definition children")
            .hasSize(2);

        DocViewNode2 tikaConfigNode = luceneCustomChildren.stream()
            .filter( c -> c.getName().getLocalName().equals("tika") )
            .findFirst()
            .get();

        assertThat(tikaConfigNode).as("tika config node")
            .has(Conditions.childWithLocalName("/oak:index/lucene-custom/tika","config.xml", defs));

        List<DocViewNode2> children = defs.getChildren("/oak:index/lucene-custom/tika");
        assertThat(children).as("tika config child nodes")
            .hasSize(1)
            .element(0)
                .has( Conditions.localName("config.xml") )
                .has( Conditions.property(NamespaceRegistry.NAMESPACE_JCR, "primaryType", "nt:file", defs) );

        byte[] tikaConfig = defs.getBinary("/oak:index/lucene-custom/tika/config.xml").get();
        assertIsValidXml(tikaConfig);
    }


    @Test
    public void handleIndexDefinitionUnderNonRootPath() throws IOException, ConverterException {

        DefaultIndexManager manager = new DefaultIndexManager();

        traverseForIndexing(manager, "index_non_root_path");

        IndexDefinitions defs = manager.getIndexes();
        Map<String, List<DocViewNode2>> indexes = defs.getIndexes();
        assertThat(indexes).as("index definitions")
            .hasSize(1)
            .containsKey("/content/oak:index");

        List<DocViewNode2> contentIndexes = indexes.get("/content/oak:index");
        assertThat(contentIndexes).as("/content indexes")
            .hasSize(1)
            .element(0)
                .has( Conditions.localName("lucene") )
                .has( Conditions.property("type", "lucene") );

    }

    @Test
    public void handleIndexDefinitionWithMissingNamespaces() throws IOException, ConverterException {
        DefaultIndexManager manager = new DefaultIndexManager();

        traverseForIndexing(manager, "index_missing_namespaces");
    }

    private void assertIsValidXml(byte[] tikeConfig) throws ParserConfigurationException, SAXException, IOException {

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = dbFactory.newDocumentBuilder();
        documentBuilder.parse(new InputSource(new ByteArrayInputStream(tikeConfig)));
    }

    private void traverseForIndexing(DefaultIndexManager manager, String testPackageDirectory) throws IOException, ConverterException {

        try ( Archive archive = new FileArchive(TestUtils.getPackageRelativeFile(getClass(), "index", testPackageDirectory)) ) {
            archive.open(true);

            try ( ContentPackage2FeatureModelConverter converter = new ContentPackage2FeatureModelConverter() ) {

                converter.setMainPackageAssembler(Mockito.mock(VaultPackageAssembler.class))
                    .setIndexManager(manager);
                IndexDefinitionsEntryHandler handler = new IndexDefinitionsEntryHandler();

                new BaseVaultPackageScanner(true) {
                    @Override
                    protected void onFile(@NotNull String path, @NotNull Archive archive, @NotNull Entry entry)
                            throws IOException, ConverterException {
                        if ( handler.matches(path) )
                            handler.handle(path, archive, entry, converter);
                    }
                }.traverse(new ZipVaultPackage(archive, true));
            }

        }
    }

    static class Conditions {
        static final Condition<DocViewNode2> localName(String localName) {
            return new Condition<DocViewNode2>("Node with name " + localName) {
                @Override
                public boolean matches(DocViewNode2 value) {
                    return value.getName().getLocalName().equals(localName);
                }
            };
        }

        static final Condition<DocViewNode2> childWithLocalName(String path, String childName, IndexDefinitions defs) {
            return new Condition<DocViewNode2>("Node with a child with localName " + childName) {
                @Override
                public boolean matches(DocViewNode2 value) {
                    return defs.getChildren(path).stream().filter( n -> n.getName().getLocalName().equals(childName)).findAny().isPresent();
                }
            };
        }

        static final Condition<DocViewNode2> property(String localPropertyName, String propertyValue) {
            return new Condition<DocViewNode2>("Node with property '" + localPropertyName + "' equal to '" + propertyValue + "'") {
                @Override
                public boolean matches(DocViewNode2 value) {
                    return value.getProperties().stream().
                        anyMatch( p -> {
                            return p.getName().getLocalName().equals(localPropertyName)
                                    && p.getStringValue().isPresent() && Objects.equals(p.getStringValue().get(), propertyValue);
                        });
                }
            };
        }

        static final Condition<DocViewNode2> property(String uri, String localPropertyName, String propertyValue, IndexDefinitions defs) {
            return new Condition<DocViewNode2>("Node with property '{" + uri +"}" + localPropertyName + "' equal to '" + propertyValue + "'") {
                @Override
                public boolean matches(DocViewNode2 value) {
                    return value.getProperties().stream().
                            anyMatch( p -> {
                                return p.getName().getLocalName().equals(localPropertyName)
                                        && Objects.equals(p.getName().getNamespaceURI(), uri)
                                        && p.getStringValue().isPresent() && Objects.equals(p.getStringValue().get(), propertyValue);
                            });
                }
            };
        }
    }

}
