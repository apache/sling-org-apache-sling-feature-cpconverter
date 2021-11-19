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
package org.apache.sling.feature.cpconverter.vltpkg;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

import javax.jcr.NamespaceException;
import javax.jcr.RepositoryException;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.NameParser;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.impl.io.DocViewSAXFormatter;
import org.apache.jackrabbit.vault.fs.io.DocViewFormat;
import org.apache.jackrabbit.vault.util.xml.serialize.FormattingXmlStreamWriter;
import org.apache.sling.contentparser.api.ContentHandler;

/**
 * Similar to {@link DocViewSAXFormatter} but works outside a repository-based context on the input generated through {@link ContentHandler} callbacks.
 * Throws {@link DocViewSerializerContentHandlerException} in case serialization fails for some reason.
 */
public class DocViewSerializerContentHandler implements ContentHandler, AutoCloseable {

    private final XMLStreamWriter writer;
    private final JcrNamespaceRegistry nsRegistry;
    private String currentPath = "/";
    boolean isFirstElement = true;

    public DocViewSerializerContentHandler(OutputStream outputStream, JcrNamespaceRegistry nsRegistry) {
        this.nsRegistry = nsRegistry;
        try {
            writer = FormattingXmlStreamWriter.create(outputStream, new DocViewFormat().getXmlOutputFormat());
            writer.writeStartDocument();
            writer.setNamespaceContext(nsRegistry);
            
        } catch (XMLStreamException e) {
            throw new DocViewSerializerContentHandlerException("Can not start document", e);
        }
    }

    @Override
    public void resource(String path, Map<String, Object> properties) {
        // split path in parent and name
        String parent = Text.getRelativeParent(path, 1);
        String name = Text.getName(path);
        if (name.isEmpty()) {
            name = "jcr:root";
        }

        if (!parent.equals(currentPath)) {
            closeParents(parent);
        }
        currentPath = path;
        try {
            // now split by prefix and local name
            Map.Entry<String, Name> prefixAndQualifiedName = resolvePrefixedName(name);
            String key = prefixAndQualifiedName.getKey();
            String namespaceURI = prefixAndQualifiedName.getValue().getNamespaceURI();
            String localName = prefixAndQualifiedName.getValue().getLocalName();

            if(StringUtils.isNotBlank(localName)){
                if(StringUtils.isNotBlank(namespaceURI) && StringUtils.isNotBlank(nsRegistry.getPrefix(namespaceURI)) &&  StringUtils.isNotBlank(key)) {
                    //uri already registered in context, this method will do
                    writer.writeStartElement(namespaceURI, localName);
                }else if(StringUtils.isNotBlank(namespaceURI) &&  StringUtils.isNotBlank(key)) {
                    //uri not registered in context, so writing it out completely
                    writer.writeStartElement(key, namespaceURI, localName);
                }
                else{
                    writer.writeStartElement(localName);
                }
            }else{
                return;
            }
           
            if (isFirstElement) {
                for (String prefix : nsRegistry.getPrefixes()) {
                    writer.writeNamespace(prefix, nsRegistry.getURI(prefix));
                }
                isFirstElement = false;
            }
            for (Map.Entry<String, Object> property : properties.entrySet()) {
                // now split by prefix and local name
                prefixAndQualifiedName = resolvePrefixedName(property.getKey());
                writer.writeAttribute(prefixAndQualifiedName.getKey(), prefixAndQualifiedName.getValue().getNamespaceURI(), prefixAndQualifiedName.getValue().getLocalName(), ValueConverter.toString(property.getKey(), property.getValue()));
            }
        } catch (XMLStreamException e) {
            throw new DocViewSerializerContentHandlerException("Can not start element", e);
        } catch (RepositoryException e) {
            throw new DocViewSerializerContentHandlerException("Can not emit namespace declarations", e);
        }
    }

    public void closeParents(String stopAtParent) {
        try {
            while (!currentPath.equals(stopAtParent)) {
                writer.writeEndElement();
                String newCurrentPath = Text.getRelativeParent(currentPath, 1);
                if (newCurrentPath.equals(currentPath)) {
                    break;
                } else {
                    currentPath = newCurrentPath;
                }
            }
        } catch (XMLStreamException e) {
            throw new DocViewSerializerContentHandlerException("Can not end element", e);
        }
    }

    Map.Entry<String, Name> resolvePrefixedName(String name) {
        int posColon = name.indexOf(':');
        if (posColon == -1) {
            return new AbstractMap.SimpleEntry<>("", NameFactoryImpl.getInstance().create(Name.NS_DEFAULT_URI, name));
        }
        try {
            String prefix = name.substring(0, posColon);
            return new AbstractMap.SimpleEntry<>(prefix, NameParser.parse(name, nsRegistry, NameFactoryImpl.getInstance()));
        } catch (IllegalNameException|NamespaceException e) {
            throw new DocViewSerializerContentHandlerException("Could not resolve namespace URI for name " + name, e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            closeParents("");
            writer.writeEndDocument();
        } catch (XMLStreamException e) {
            throw new DocViewSerializerContentHandlerException("Can not end document", e);
        }
    }
}
