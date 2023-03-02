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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.NamespaceException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;

/**
 * Represents a serializable docview node inside a tree hierarchy.
 *
 */
public class DocViewTreeNode {

    private final List<DocViewTreeNode> children;
    private final Name name;
    private final String parentPath;
    private final Collection<DocViewProperty2> properties;
    
    public DocViewTreeNode(String parentPath, Name name, Collection<DocViewProperty2> properties) {
        children = new LinkedList<>();
        this.name = name;
        this.properties = properties;
        this.parentPath = parentPath;
    }

    public void addChild(DocViewTreeNode child) {
        children.add(child);
    }

    void write(XMLStreamWriter writer, NamespaceResolver nsResolver) throws NamespaceException, XMLStreamException {
        write(writer, nsResolver, Collections.emptyList());
    }

    public void write(XMLStreamWriter writer, NamespaceResolver nsResolver, Iterable<String> nsPrefixes) throws NamespaceException, XMLStreamException {
        DocViewNode2 docViewNode = new DocViewNode2(name, properties);
        docViewNode.writeStart(writer, nsResolver, nsPrefixes);
        for (DocViewTreeNode child : children) {
            child.write(writer, nsResolver);
        }
        DocViewNode2.writeEnd(writer);
    }

    public Collection<DocViewProperty2> getProperties() {
        return properties;
    }

    /**
     * 
     * @param nameResolver
     * @return the absolute JCR path (in qualified form) of the node represented by this class
     * @throws NamespaceException
     */
    public String getPath(NameResolver nameResolver) throws NamespaceException {
        if (NameConstants.JCR_ROOT.equals(name)) {
            return parentPath;
        } else {
            return parentPath + "/" + nameResolver.getJCRName(name);
        }
    }
}
