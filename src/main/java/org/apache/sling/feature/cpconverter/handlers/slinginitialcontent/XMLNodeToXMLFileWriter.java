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

import com.sun.xml.txw2.output.IndentingXMLStreamWriter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.xmlbuffer.XMLNode;
import org.apache.sling.feature.cpconverter.vltpkg.JcrNamespaceRegistry;

import javax.jcr.RepositoryException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.apache.jackrabbit.vault.util.JcrConstants.JCR_MIXINTYPES;
import static org.apache.jackrabbit.vault.util.JcrConstants.JCR_PRIMARYTYPE;
import static org.apache.jackrabbit.vault.util.JcrConstants.NT_UNSTRUCTURED;

public class XMLNodeToXMLFileWriter {

    private final XMLNode parentNode;
    private final XMLStreamWriter streamWriter;
    private final JcrNamespaceRegistry namespaceRegistry;

    public XMLNodeToXMLFileWriter(XMLNode parentNode, OutputStream targetOutputStream, JcrNamespaceRegistry namespaceRegistry) throws XMLStreamException {
        this.parentNode = parentNode;

        this.streamWriter = new IndentingXMLStreamWriter(
                XMLOutputFactory.newInstance().createXMLStreamWriter(targetOutputStream, StandardCharsets.UTF_8.name())
        );
        this.namespaceRegistry = namespaceRegistry;
        this.streamWriter.setNamespaceContext(this.namespaceRegistry);
       
    }
    
    public void write() throws XMLStreamException, RepositoryException {
        streamWriter.writeStartDocument();
        
        writeNode(parentNode, true);
        
        streamWriter.writeEndDocument();
    }
    
    private void writeNode(XMLNode xmlNode, boolean isFirstElement) throws RepositoryException, XMLStreamException {
        
        streamWriter.writeStartElement(xmlNode.getXmlElementName());
        
        if (isFirstElement) {
            for (String prefix : namespaceRegistry.getPrefixes()) {
                streamWriter.writeNamespace(prefix, namespaceRegistry.getURI(prefix));
            }
        }
        
        String primaryNodeType  = xmlNode.getPrimaryNodeType();
        String[] mixinNodeTypes = xmlNode.getMixinNodeTypes();
        
        streamWriter.writeAttribute(JCR_PRIMARYTYPE, StringUtils.isNotBlank(primaryNodeType) ? primaryNodeType : NT_UNSTRUCTURED);
        if(ArrayUtils.isNotEmpty(mixinNodeTypes)){
            streamWriter.writeAttribute(JCR_MIXINTYPES, "[" + String.join(",", mixinNodeTypes) + "]");
        }
        
        for(Map.Entry<String,String> entry: xmlNode.getVltXmlParsedProperties().entrySet()){
            
            if(entry.getKey().equals(JCR_PRIMARYTYPE) || entry.getKey().equals(JCR_MIXINTYPES)){
                continue;
            }
            
            streamWriter.writeAttribute(entry.getKey(), entry.getValue());
        }
        
        for(XMLNode node: xmlNode.getChildren().values()){
            writeNode(node, false);
        }


        streamWriter.writeEndElement();
        

    }

    
    
}
