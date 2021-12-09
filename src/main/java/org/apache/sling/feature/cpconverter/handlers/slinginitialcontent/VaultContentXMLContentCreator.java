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

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.xmlbuffer.XMLNode;
import org.apache.sling.feature.cpconverter.shared.CheckedConsumer;
import org.apache.sling.feature.cpconverter.vltpkg.JcrNamespaceRegistry;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.jcr.contentloader.ContentCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ContentCreator substitute to create valid XML files to be packaged into a VaultPackage to be installed later
 */
public class VaultContentXMLContentCreator implements ContentCreator {
    
    private final static Pattern DATE_PATTERN = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}(.*)");
    private final static Logger logger = LoggerFactory.getLogger(BundleSlingInitialContentExtractor.class);

    private final String repositoryPath;
    private final OutputStream targetOutputStream;
    private final VaultPackageAssembler packageAssembler;
    private final LinkedList<XMLNode> parentNodePathStack = new LinkedList<>();
    private final JcrNamespaceRegistry namespaceRegistry;
    private final CheckedConsumer<String> repoInitTextExtensionConsumer;
    private boolean isFirstElement = true;
    private boolean finished = false;
    private boolean xmlProcessed = false;
    private String primaryNodeName;
    private XMLNode currentNode;

    public VaultContentXMLContentCreator(String repositoryPath, OutputStream targetOutputStream, JcrNamespaceRegistry namespaceRegistry, VaultPackageAssembler packageAssembler, CheckedConsumer<String> repoInitTextExtensionConsumer) throws XMLStreamException, RepositoryException {
        this.repositoryPath = repositoryPath;
        this.targetOutputStream = targetOutputStream;
        this.packageAssembler = packageAssembler;
        this.namespaceRegistry = namespaceRegistry;
        this.repoInitTextExtensionConsumer = repoInitTextExtensionConsumer;
    } 
    
    public void setIsXmlProcessed(){
        this.xmlProcessed = true;
    }
    
    @Override
    public void createNode(String name, String primaryNodeType, String[] mixinNodeTypes) throws RepositoryException {
        
        final String elementName;
        final String jcrNodeName; 
        if(xmlProcessed && isFirstElement){
            elementName = "jcr:root";
            primaryNodeName = name;
            jcrNodeName = name;
            isFirstElement = false;
        }else if(StringUtils.isNotBlank(name)){
            elementName = name;
            jcrNodeName = name;
        }else{
            elementName = "jcr:root";
            jcrNodeName = null;
        }

        
        final String basePath;
        if(parentNodePathStack.isEmpty()){
            basePath = repositoryPath;
        }else{
            StringBuilder basePathBuilder = new StringBuilder(repositoryPath);
            for(Iterator<XMLNode> xmlNodeIterator =  parentNodePathStack.descendingIterator();xmlNodeIterator.hasNext();){
                XMLNode parent = xmlNodeIterator.next();
                String parentJcrNodeName = parent.getJcrNodeName();
                if(StringUtils.isNotBlank(parentJcrNodeName)){
                    basePathBuilder.append("/");
                    basePathBuilder.append(parentJcrNodeName);
                }
            }
            basePath = basePathBuilder.toString();
        }
       
        XMLNode intermediateNode = new XMLNode(packageAssembler,basePath,elementName,jcrNodeName, primaryNodeType, mixinNodeTypes);
        //add the created node to the correct parent if present
        if(currentNode != null){
            currentNode.addChildNode(elementName, intermediateNode);
        }
        //switch the current node 
        currentNode = intermediateNode;
        
        currentNode.addProperty(JcrConstants.JCR_PRIMARYTYPE, StringUtils.isNotBlank(primaryNodeType) ? primaryNodeType : JcrConstants.NT_UNSTRUCTURED);
        
        if(ArrayUtils.isNotEmpty(mixinNodeTypes)){
            currentNode.addProperty(JcrConstants.JCR_MIXINTYPES, "[" + String.join(",", mixinNodeTypes) + "]");
        }

        parentNodePathStack.push(currentNode);
    }

    public String getPrimaryNodeName() {
        return primaryNodeName;
    }

    @Override
    public void finishNode() throws RepositoryException {
        if(parentNodePathStack.size() > 1){
            this.parentNodePathStack.pop();
        }
        this.currentNode = this.parentNodePathStack.peek();
    }

    @Override
    public void finish() throws RepositoryException {
        
        if(finished){
            return;
        }
        try {
            XMLNodeToXMLFileWriter writer = new XMLNodeToXMLFileWriter(currentNode, targetOutputStream, namespaceRegistry);
            writer.write();
            finished = true;
        } catch (XMLStreamException e) {
            throw new RepositoryException(e);
        }
    }

  

    @Override
    public void createProperty(String name, int propertyType, String value) throws RepositoryException {
        currentNode.addProperty(name,propertyType,value);
    }

   
    @Override
    public void createProperty(String name, int propertyType, String[] values) throws RepositoryException {
        currentNode.addProperty(name, propertyType, values);
    }
    
   
    @Override
    public void createProperty(String name, Object value)  throws RepositoryException {
        currentNode.addProperty(name, value);
    }
    
    
    @Override
    public void createProperty(String name, Object[] values) throws RepositoryException {
        currentNode.addProperty(name, values);
    }

    @Override
    public void createFileAndResourceNode(String name, InputStream data, String mimeType, long lastModified) throws RepositoryException {
        this.createNode(name, JcrConstants.NT_FILE, null);
        this.createNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE, null);

        // ensure sensible last modification date
        if (lastModified <= 0) {
            lastModified = System.currentTimeMillis();
        }
        this.createProperty(JcrConstants.JCR_MIMETYPE, mimeType);
        this.createProperty(JcrConstants.JCR_LASTMODIFIED, lastModified);
        this.createProperty(JcrConstants.JCR_DATA, data);
    }
    

    @Override
    public boolean switchCurrentNode(String subPath, String newNodeType) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createUser(String name, String password, Map<String, Object> extraProperties) throws RepositoryException {
        try {
            StringBuilder repoInitTextSB = new StringBuilder();
            repoInitTextSB  .append("create user " + name + " with password " + password + " \n\n")
                            .append("set properties on authorizable(" + name + ")\n");
            extraProperties.entrySet().stream().map(this::getSetPropertyString).forEach(repoInitTextSB::append);
            repoInitTextSB.append("end");
            
            repoInitTextExtensionConsumer.accept(repoInitTextSB.toString());
        } catch (IOException | ConverterException e) {
            throw new RepositoryException(e);
        }
    }

    @Override
    public void createGroup(String name, String[] members, Map<String, Object> extraProperties) throws RepositoryException {
        try {
            StringBuilder repoInitTextSB = new StringBuilder();
            repoInitTextSB  .append("create group " + name + " \n\n")
                            .append("add " + String.join(",", members) + " to group " + name)
                            .append("set properties on authorizable(" + name + ")\n");
            extraProperties.entrySet().stream().map(this::getSetPropertyString).forEach(repoInitTextSB::append);
            repoInitTextSB.append("end");
            repoInitTextExtensionConsumer.accept(repoInitTextSB.toString());
        } catch (IOException | ConverterException e) {
            throw new RepositoryException(e);
        }
    }

    @Override
    public void createAce(String principal, String[] grantedPrivileges, String[] deniedPrivileges, String order) throws RepositoryException {
        try {
            //set principal ACL for principal1,principal2
            //allow jcr:read
            StringBuilder repoInitTextSB = new StringBuilder();
            String path = this.currentNode.getPath();
            
            repoInitTextSB.append("set ACL for " + principal +"\n\n");
         
            for(String privilege: grantedPrivileges){
                repoInitTextSB.append("allow " + privilege + " on " + path + "\n");
            }
            for(String privilege: deniedPrivileges){
                repoInitTextSB.append("deny " + privilege + " on " + path + "\n");
            }
            repoInitTextSB.append("end");
            
            repoInitTextExtensionConsumer.accept(repoInitTextSB.toString());
        } catch (IOException | ConverterException e) {
            throw new RepositoryException(e);
        }
    }

    @Override
    public void createAce(String principalId, String[] grantedPrivilegeNames, String[] deniedPrivilegeNames,
                          String order, Map<String, Value> restrictions, Map<String, Value[]> mvRestrictions,
                          Set<String> removedRestrictionNames) throws RepositoryException {
        try {
            //set principal ACL for principal1,principal2
            //allow jcr:read
            StringBuilder repoInitTextSB = new StringBuilder();
            repoInitTextSB.append("set principal ACL for " + principalId +"\n\n");
            String path = this.currentNode.getPath();
            if(grantedPrivilegeNames != null){
                for(String privilege: grantedPrivilegeNames){
                    repoInitTextSB.append("allow " + privilege + " on " + path);
                }
            }else if(deniedPrivilegeNames != null){
                for(String privilege: deniedPrivilegeNames){
                    repoInitTextSB.append("deny " + privilege + " on " + path);
                }
            }
           

            
            if (((grantedPrivilegeNames != null) || (deniedPrivilegeNames != null)) && (MapUtils.isNotEmpty(restrictions) || MapUtils.isNotEmpty(mvRestrictions))) {
              
                if(MapUtils.isNotEmpty(mvRestrictions)){
                   
                    Iterator<Map.Entry<String,Value[]>> iterator = mvRestrictions.entrySet().iterator();
                    while(iterator.hasNext()){
                        Map.Entry<String,Value[]> entry = iterator.next();
                        repoInitTextSB.append(appendRestrictionString(entry.getKey(), entry.getValue()));
                    }
                    
                    
                }else{
                    Iterator<Map.Entry<String,Value>> iterator = restrictions.entrySet().iterator();
                    while(iterator.hasNext()){
                        Map.Entry<String,Value> entry = iterator.next();
                        repoInitTextSB.append(appendRestrictionString(entry.getKey(), entry.getValue()));
                    }
                }
                
            }
            repoInitTextSB.append("\nend");

            repoInitTextExtensionConsumer.accept(repoInitTextSB.toString());
        } catch (IOException | ConverterException e) {
            throw new RepositoryException(e);
        }
    }

    private String appendRestrictionString(String key, Value[] values){
        Collection<String> stringValues = new ArrayList<>();
        stringValues.add(key);
        for(Value value: values){
            try {
                String valueAsString = value.getString();
                stringValues.add(valueAsString);
            } catch (RepositoryException e) {
                logger.error("error appending restriction", e);
            }
        }

        return " restriction(" +
                stringValues.stream()
                .map( Object::toString )
                .collect( Collectors.joining( "," ) )
        + ")";
    }

    private String appendRestrictionString(String key, Value value){
        Collection<String> stringValues = new ArrayList<>();
        try {
            String valueString = value.getString();
            stringValues.add(key);
            stringValues.add(valueString);
        } catch (RepositoryException e) {
            logger.error("error appending restriction", e);
        }

        return " restriction(" +
                stringValues.stream()
                        .map( Object::toString )
                        .collect( Collectors.joining( "," ) )
                + ") ";
    }

    private String getSetPropertyString(Map.Entry<String,Object> entry) {
        String key = entry.getKey();
        Object value = entry.getValue();
        String stringValue = value.toString();

        final String setPropertyString;
        if (value instanceof String) {

            if(DATE_PATTERN.matcher(stringValue).matches()){
                setPropertyString = "set " + key + "{Date} to " + stringValue;
            }else{
                setPropertyString = "set " + key + "{String} to " + stringValue;
            }

        } else if (value instanceof Double) {
            setPropertyString = "set " + key + "{Double} to " + stringValue;
        } else if (value instanceof Long) {
            setPropertyString = "set " + key + "{Long} to " + stringValue;
        } else if (value instanceof Boolean) {
            setPropertyString = "set " + key + "{Boolean} to " + stringValue;
        } else {
            throw new RuntimeException("Unable to convert " + value + " to jcr Value");
        }
        return setPropertyString + "\n";
    }


}
