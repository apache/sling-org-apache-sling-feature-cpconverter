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
package org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.xmlbuffer;

import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Represents an XML node in the buffer to be written in a sling initial content package
 */
public class XMLNode {

    private static final String FORMAT_SINGLE_VALUE = "%s";
    private static final String FORMAT_SINGLE_VALUE_TYPED = "{%s}%s";
    private static final String FORMAT_MULTI_VALUE = "[%s]";
    private static final String FORMAT_MULTI_VALUE_TYPED = "{%s}[%s]";

    private final String basePath;
    private final String xmlElementName;
    private final String jcrNodeName;
    private final String primaryNodeType;
    private final String[] mixinNodeTypes;

    private final VaultPackageAssembler packageAssembler;
    private final Map<String,String> vltXmlParsedProperties = new HashMap<>();
    private final Map<String,XMLNode> children = new LinkedHashMap<>();
    
    public XMLNode(VaultPackageAssembler packageAssembler, String basePath, String xmlElementName, String jcrNodeName, String primaryNodeType, String[] mixinNodeTypes){
        this.packageAssembler = packageAssembler;
        this.basePath = basePath;
        this.xmlElementName = xmlElementName;
        this.jcrNodeName = jcrNodeName;
        this.primaryNodeType = primaryNodeType;
        this.mixinNodeTypes = mixinNodeTypes;
    }
    
    public void addProperty(String name, int propertyType, String value){
        String propertyTypeName = PropertyType.nameFromValue(propertyType);
        
        if(propertyType > 0){
            vltXmlParsedProperties.put(name, String.format(FORMAT_SINGLE_VALUE_TYPED, propertyTypeName, value));
        }else{
            vltXmlParsedProperties.put(name, String.format(FORMAT_SINGLE_VALUE, value));
        }
      
    }
    
    public void addProperty(String name, int propertyType, String[] values) throws RepositoryException {
        String propertyTypeName = PropertyType.nameFromValue(propertyType);
        
        if(propertyType > 0){
            vltXmlParsedProperties.put(name, String.format(FORMAT_MULTI_VALUE_TYPED, propertyTypeName,  String.join(",", values)));
        }else{
            vltXmlParsedProperties.put(name, String.format(FORMAT_MULTI_VALUE,  String.join(",", values)));
        }
       
    }

    public void addChildNode(String name, XMLNode xmlNode){
        this.children.put(name, xmlNode);
    }
    
    public void addProperty(String name, Object value)  throws RepositoryException {
        if (value == null) {
            return;
        }
        
        if (value instanceof Long) {
            vltXmlParsedProperties.put(name, String.format(FORMAT_SINGLE_VALUE_TYPED, "Long", value.toString()));
        } else if (value instanceof Date) {
            vltXmlParsedProperties.put(name, String.format(FORMAT_SINGLE_VALUE_TYPED, "Date", ((Date) value).toGMTString()));
        } else if (value instanceof Calendar) {
            vltXmlParsedProperties.put(name, String.format(FORMAT_SINGLE_VALUE_TYPED, "Date", ((Calendar) value).getTime().toGMTString()));
        } else if (value instanceof Double) {
            vltXmlParsedProperties.put(name, String.format(FORMAT_SINGLE_VALUE_TYPED, "Double", value));
        } else if (value instanceof Boolean) {
            boolean theBoolValue = Boolean.parseBoolean(value.toString());
            vltXmlParsedProperties.put(name, String.format(FORMAT_SINGLE_VALUE_TYPED, "Boolean", theBoolValue));
        } else if (value instanceof InputStream) {
            vltXmlParsedProperties.put(name, "{Binary}");
            String path = "jcr_root/" + this.basePath;
            try {
                packageAssembler.addEntry(path, (InputStream) value);
            } catch (IOException e) {
                throw new RepositoryException(e);
            }

        } else {
            vltXmlParsedProperties.put(name, String.format(FORMAT_SINGLE_VALUE, value));
        }
        
    }

 
    public String getXmlElementName() {
        return xmlElementName;
    }

    public String getJcrNodeName() {
        return jcrNodeName;
    }

    public String getPrimaryNodeType() {
        return primaryNodeType;
    }

    public String[] getMixinNodeTypes() {
        return mixinNodeTypes;
    }

    public Map<String, XMLNode> getChildren() {
        return children;
    }

    public Map<String, String> getVltXmlParsedProperties() {
        return vltXmlParsedProperties;
    }
}
