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

import static java.lang.String.format;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.NameFactory;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.fs.io.DocViewParser;
import org.apache.jackrabbit.vault.fs.io.DocViewParser.XmlParseException;
import org.apache.jackrabbit.vault.fs.io.DocViewParserHandler;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.index.IndexDefinitions;
import org.apache.sling.feature.cpconverter.index.IndexManager;
import org.apache.sling.feature.cpconverter.index.SimpleNamespaceResolver;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.InputSource;

/**
 * Handler for Jackrabbit Oak index definitions
 *
 * <p>This implementation scans content packages for entries stored under {@code /oak:index}
 * and exposes them to the {@link IndexManager} for further processing.
 *
 */
public class IndexDefinitionsEntryHandler extends AbstractRegexEntryHandler {

    private static final String[] EXCLUDED_EXTENSIONS = new String[]{
            "vlt",
            "gitignore"
    };
    private static final String PATH_PATTERN = "" +
            "/jcr_root" + // jcr_root dir
            "(.*/?)/" + // optional path segment
            PlatformNameFormat.getPlatformName(IndexDefinitions.OAK_INDEX_NAME) +
            "(.*/?)" + // additional path segments
            "/(.*?)\\.(?!(" + //excluding extensions
            String.join("|", EXCLUDED_EXTENSIONS) +
            ")$)[^.]+$"; // match everything else

    private static final String EXTENSION_XML = ".xml";
    
    // we hardcode the node type name to avoid a dependency on oak-core
    private static final String NODETYPE_OAK_QUERY_INDEX_DEFINITION = "oak:QueryIndexDefinition";
    
    private static final String removeXmlExtension(String input) {
        if ( !input.endsWith(EXTENSION_XML) )
            throw new IllegalArgumentException(format("Input string '%s' does not end in '%s'", input, EXTENSION_XML));
        
        return input.substring(0, input.length() - EXTENSION_XML.length());
        
    }
    
    private final class IndexDefinitionsParserHandler implements DocViewParserHandler {
        
        private final WorkspaceFilter filter;
        private IndexDefinitions definitions;

        public IndexDefinitionsParserHandler(WorkspaceFilter filter, IndexDefinitions definitions) {
            this.filter = filter;
            this.definitions = definitions;
        }

        @Override
        public void startDocViewNode(@NotNull final String nodePath, @NotNull final DocViewNode2 docViewNode,
                @NotNull final Optional<DocViewNode2> parentDocViewNode, final int line, final int column)
                throws IOException, RepositoryException {
            
            DocViewNode2 effectiveNode = docViewNode;
            String effectivePath = nodePath;
            String primaryType = docViewNode.getPrimaryType().orElse(null);
            
            // SLING-12469 - support index definitions serialized as full coverage aggregates
            if ( NODETYPE_OAK_QUERY_INDEX_DEFINITION.equals(primaryType)
                    && nodePath.endsWith(EXTENSION_XML)) {
                effectivePath = removeXmlExtension(nodePath);
                
                NameFactory nameFactory = NameFactoryImpl.getInstance();
                
                final Name oldName = docViewNode.getName();
                Name newName = nameFactory.create(oldName.getNamespaceURI(), removeXmlExtension(oldName.getLocalName()));
                effectiveNode = new DocViewNode2(newName, docViewNode.getIndex(), docViewNode.getProperties());
            }

            if ( effectivePath.contains(IndexDefinitions.OAK_INDEX_PATH) && filter.contains(effectivePath) ) {
                definitions.addNode(Text.getRelativeParent(effectivePath, 1), effectiveNode);
            }
        }

        @Override
        public void endDocViewNode(@NotNull String nodePath, @NotNull DocViewNode2 docViewNode,
                @NotNull Optional<DocViewNode2> parentDocViewNode, int line, int column)
                throws IOException, RepositoryException {
            // nothing to do
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) {
            definitions.registerPrefixMapping(prefix, uri);
        }
    }

    public IndexDefinitionsEntryHandler() {
        super(PATH_PATTERN);
    }

    @Override
    public void handle(@NotNull String path, @NotNull Archive archive, @NotNull Entry entry,
            @NotNull ContentPackage2FeatureModelConverter converter, String runMode) throws IOException, ConverterException {

        IndexManager indexManager = converter.getIndexManager();
        if ( indexManager == null ) {
            logger.info("{} not present, will skip index definition extraction", IndexManager.class.getName());
        } else {
            try (InputStream is = archive.openInputStream(entry)) {

                String platformPath = path.replaceAll("^/jcr_root", "")
                        .replaceAll("/\\.content\\.xml$", "")
                        .replace(".dir", "");
                String repositoryPath = PlatformNameFormat.getRepositoryPath(platformPath);
                InputSource inputSource = new InputSource(is);

                boolean isDocView = false;
                // DocViewParser.isDocView closes the input stream it is passed
                try ( InputStream isCheck = archive.openInputStream(entry) ) {
                    isDocView =  DocViewParser.isDocView(new InputSource(isCheck));
                }
                if ( isDocView ) {
                    DocViewParser parser = new DocViewParser(new SimpleNamespaceResolver());
                    IndexDefinitionsParserHandler handler = new IndexDefinitionsParserHandler(archive.getMetaInf().getFilter(), indexManager.getIndexes());

                    parser.parse(repositoryPath, inputSource, handler);

                } else {
                    // binary file, should we attach?
                    if ( archive.getMetaInf().getFilter().contains(repositoryPath)) {
                        indexManager.getIndexes().registerBinary(repositoryPath, is);
                    }
                }


            } catch (XmlParseException e) {
                throw new ConverterException("Failed parsing the index definitions", e);
            }
        }

        converter.getMainPackageAssembler().addEntry(path, archive, entry);
    }
}
