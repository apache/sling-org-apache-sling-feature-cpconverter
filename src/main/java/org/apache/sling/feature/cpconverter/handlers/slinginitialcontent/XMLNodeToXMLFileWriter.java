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
package org.apache.sling.feature.cpconverter.handlers.slinginitialcontent;


import javanet.staxutils.IndentingXMLEventWriter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.xmlbuffer.XMLNode;
import org.apache.sling.feature.cpconverter.vltpkg.JcrNamespaceRegistry;
import org.jetbrains.annotations.NotNull;

import javax.jcr.RepositoryException;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.apache.jackrabbit.vault.util.JcrConstants.JCR_MIXINTYPES;
import static org.apache.jackrabbit.vault.util.JcrConstants.JCR_PRIMARYTYPE;
import static org.apache.jackrabbit.vault.util.JcrConstants.NT_UNSTRUCTURED;

class XMLNodeToXMLFileWriter {

    private final XMLNode parentNode;
    private final XMLEventWriter eventWriter;
    private final JcrNamespaceRegistry namespaceRegistry;
    private final XMLEventFactory eventFactory = XMLEventFactory.newInstance();
    
    XMLNodeToXMLFileWriter(@NotNull XMLNode parentNode,
                                  @NotNull OutputStream targetOutputStream,
                                  @NotNull JcrNamespaceRegistry namespaceRegistry) throws XMLStreamException {
        this.parentNode = parentNode;
        XMLEventWriter writer = XMLOutputFactory.newInstance().createXMLEventWriter(targetOutputStream, StandardCharsets.UTF_8.name());
        this.eventWriter = new IndentingXMLEventWriter(
                writer
        );
        this.namespaceRegistry = namespaceRegistry;
        this.eventWriter.setNamespaceContext(this.namespaceRegistry);

    }

    void write() throws XMLStreamException, RepositoryException {

        eventWriter.add(eventFactory.createStartDocument());
        writeNode(parentNode, true);
        eventWriter.add(eventFactory.createEndDocument());
        
    }

    void writeNode(@NotNull XMLNode xmlNode, boolean isFirstElement) throws RepositoryException, XMLStreamException {

        eventWriter.add(eventFactory.createStartElement(StringUtils.EMPTY, StringUtils.EMPTY, xmlNode.getXmlElementName()));
       
        if (isFirstElement) {
            for (String prefix : namespaceRegistry.getPrefixes()) {
                eventWriter.add(eventFactory.createNamespace(prefix, namespaceRegistry.getURI(prefix)));
            }
        }

        String primaryNodeType = xmlNode.getPrimaryNodeType();
        String[] mixinNodeTypes = xmlNode.getMixinNodeTypes();


        eventWriter.add(eventFactory.createAttribute(JCR_PRIMARYTYPE,  StringUtils.isNotBlank(primaryNodeType) ? primaryNodeType : NT_UNSTRUCTURED));
       
        if (ArrayUtils.isNotEmpty(mixinNodeTypes)) {
            eventWriter.add(eventFactory.createAttribute(JCR_MIXINTYPES, "[" + String.join(",", mixinNodeTypes) + "]"));
        }

        for (Map.Entry<String, String> entry : xmlNode.getVltXmlParsedProperties().entrySet()) {

            if (entry.getKey().equals(JCR_PRIMARYTYPE) || entry.getKey().equals(JCR_MIXINTYPES)) {
                continue;
            }

            eventWriter.add(eventFactory.createAttribute(entry.getKey(), entry.getValue()));
        }

        for (XMLNode node : xmlNode.getChildren().values()) {
            writeNode(node, false);
        }

        eventWriter.add(eventFactory.createEndElement(StringUtils.EMPTY, StringUtils.EMPTY, xmlNode.getXmlElementName()));


    }


}
