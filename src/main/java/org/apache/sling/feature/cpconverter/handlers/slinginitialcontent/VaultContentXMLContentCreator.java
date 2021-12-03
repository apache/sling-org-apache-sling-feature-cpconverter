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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.Group;
import org.apache.sling.feature.cpconverter.accesscontrol.User;
import org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.xmlbuffer.XMLNode;
import org.apache.sling.feature.cpconverter.repoinit.Util;
import org.apache.sling.feature.cpconverter.shared.CheckedConsumer;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.apache.sling.feature.cpconverter.vltpkg.JcrNamespaceRegistry;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.jcr.contentloader.ContentCreator;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.testing.mock.jcr.MockJcr;
import org.jetbrains.annotations.NotNull;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import org.apache.sling.testing.mock.jcr.MockQueryResult;
/**
 * ContentCreator substitute to create valid XML files to be packaged into a VaultPackage to be installed later
 */
public class VaultContentXMLContentCreator implements ContentCreator {

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

    //private final Session session = MockJcr.newSession();
    //private final Node rootNode = session.getNode("/");
    private Node currentJcrNode;
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

        this.currentJcrNode = currentJcrNode.addNode(jcrNodeName, primaryNodeName);
        
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
            repoInitTextSB  .append("create user " + name + " with password " + password + " \n")
                            .append("create path /content/cq:tags\n")
                            .append("set properties on authorizable(" + name + ")\n");
            for(Map.Entry<String,Object> propSet: extraProperties.entrySet()){
                String type = "{String}";
                String value = propSet.getValue().toString();
                repoInitTextSB.append("    " + propSet.getKey() + type + " to " +  value);
            }
            repoInitTextSB.append("end\n");
            String repoInitText = "# origin= source=content-package" + System.lineSeparator() + Util.normalize(repoInitTextSB.toString());
            
            repoInitTextExtensionConsumer.accept(repoInitText);
        } catch (IOException | ConverterException | RepoInitParsingException e) {
            throw new RepositoryException(e);
        }
    }

    @Override
    public void createGroup(String name, String[] members, Map<String, Object> extraProperties) throws RepositoryException {
        try {
            repoInitTextExtensionConsumer.accept("");
        } catch (IOException | ConverterException e) {
            throw new RepositoryException(e);
        }
    }

    @Override
    public void createAce(String principal, String[] grantedPrivileges, String[] deniedPrivileges, String order) throws RepositoryException {
        try {
            repoInitTextExtensionConsumer.accept("");
        } catch (IOException | ConverterException e) {
            throw new RepositoryException(e);
        }
    }

    @Override
    public void createAce(String principal, String[] grantedPrivileges, String[] deniedPrivileges, String order, Map<String, Value> restrictions, Map<String, Value[]> mvRestrictions, Set<String> removedRestrictionNames) throws RepositoryException {
        try {
            repoInitTextExtensionConsumer.accept("");
        } catch (IOException | ConverterException e) {
            throw new RepositoryException(e);
        }
    }

   
}
