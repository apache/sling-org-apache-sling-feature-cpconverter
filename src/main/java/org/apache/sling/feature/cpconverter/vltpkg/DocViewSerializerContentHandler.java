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
import java.util.Map;

import javax.jcr.NamespaceException;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.NameParser;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.vault.fs.impl.io.DocViewSAXFormatter;
import org.apache.jackrabbit.vault.fs.io.DocViewFormat;
import org.apache.jackrabbit.vault.util.Text;
import org.apache.jackrabbit.vault.util.xml.serialize.XMLSerializer;
import org.apache.sling.contentparser.api.ContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Similar to {@link DocViewSAXFormatter} but works outside a repository-based context on the input generated through {@link ContentHandler} callbacks.
 * Throws {@link DocViewSerializerContentHandlerException} in case serialization fails for some reason.
 */
public class DocViewSerializerContentHandler implements ContentHandler, AutoCloseable {

    private final XMLSerializer serializer;
    private final JcrNamespaceRegistry nsRegistry;
    private String currentPath = "/";

    public DocViewSerializerContentHandler(OutputStream outputStream, JcrNamespaceRegistry nsRegistry) {
        serializer = new XMLSerializer(outputStream, new DocViewFormat().getXmlOutputFormat());
        this.nsRegistry = nsRegistry;
        try {
            serializer.startDocument();
            // TODO: this can be optimized by only emitting the used prefixes
            for (String prefix : nsRegistry.getPrefixes()) {
                serializer.startPrefixMapping(prefix, nsRegistry.getURI(prefix));
            }
        } catch (SAXException e) {
            throw new DocViewSerializerContentHandlerException("Can not start document", e);
        } catch (RepositoryException e) {
            throw new DocViewSerializerContentHandlerException("Can not emit namespace declarations", e);
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
            Name qualifiedName = resolvePrefixedName(name);
            // defer writing until all namespaces have been collected
            serializer.startElement(qualifiedName.getNamespaceURI(), qualifiedName.getLocalName(), name, toAttributes(properties));
        } catch (SAXException e) {
            throw new DocViewSerializerContentHandlerException("Can not start element", e);
        }
    }

    public void closeParents(String stopAtParent) {
        try {
            while (!currentPath.equals(stopAtParent)) {
                serializer.endElement(Text.getName(currentPath));
                String newCurrentPath = Text.getRelativeParent(currentPath, 1);
                if (newCurrentPath.equals(currentPath)) {
                    break;
                } else {
                    currentPath = newCurrentPath;
                }
            }
        } catch (SAXException e) {
            throw new DocViewSerializerContentHandlerException("Can not end element", e);
        }
    }

    Attributes toAttributes(Map<String, Object> properties) {
        AttributesImpl attributes = new AttributesImpl();
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            // now split by prefix and local name
            Name qualifiedName = resolvePrefixedName(property.getKey());
            attributes.addAttribute(qualifiedName.getNamespaceURI(), qualifiedName.getLocalName(), property.getKey(), "CDATA", ValueConverter.toString(property.getKey(), property.getValue()));
        }
        return attributes;
    }

    Name resolvePrefixedName(String name) {
        if (name.indexOf(':') == -1) {
            return NameFactoryImpl.getInstance().create(Name.NS_DEFAULT_URI, name);
        }
        try {
            return NameParser.parse(name, nsRegistry, NameFactoryImpl.getInstance());
        } catch (IllegalNameException|NamespaceException e) {
            throw new DocViewSerializerContentHandlerException("Could not resolve namespace URI for name " + name, e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            closeParents("");
            serializer.endDocument();
        } catch (SAXException e) {
            throw new DocViewSerializerContentHandlerException("Can not end document", e);
        }
    }
}
