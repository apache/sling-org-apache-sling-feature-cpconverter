package org.apache.sling.feature.cpconverter.handlers.slinginitialcontent;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.feature.cpconverter.vltpkg.JcrNamespaceRegistry;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.jcr.contentloader.ContentCreator;
 
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * ContentCreator substitute to create valid XML files to be packaged into a VaultPackage to be installed later
 */
public class VaultContentXMLContentCreator implements ContentCreator {
    
    String FORMAT_SINGLE_VALUE = "%s";
    String FORMAT_SINGLE_VALUE_TYPED = "{%s}%s";
    String FORMAT_MULTI_VALUE = "[%s]";
    String FORMAT_MULTI_VALUE_TYPED = "{%s}[%s]";
    
    private final String repositoryPath;
    private final XMLStreamWriter writer;
    private final VaultPackageAssembler packageAssembler;
    private final LinkedList<String> parentNodePathStack = new LinkedList<>();
    private final JcrNamespaceRegistry namespaceRegistry;
    private boolean isFirstElement = true;
    private boolean finished = false;
    private boolean xmlProcessed = false;
    private String primaryNodeName;

    public VaultContentXMLContentCreator(String repositoryPath, OutputStream targetOutputStream, JcrNamespaceRegistry namespaceRegistry, VaultPackageAssembler packageAssembler) throws XMLStreamException {
        this.repositoryPath = repositoryPath;
        this.writer = XMLOutputFactory.newInstance().createXMLStreamWriter(targetOutputStream, StandardCharsets.UTF_8.name());
        this.packageAssembler = packageAssembler;
        this.writer.writeStartDocument();
        this.namespaceRegistry = namespaceRegistry;
        this.writer.setNamespaceContext(this.namespaceRegistry);
    } 
    
    public void setIsXmlProcessed(){
        this.xmlProcessed = true;
    }
    
    @Override
    public void createNode(String name, String primaryNodeType, String[] mixinNodeTypes) throws RepositoryException {

        final String elementName;
        
        if(xmlProcessed && isFirstElement){
            elementName = "jcr:root";
            primaryNodeName = name;
        }else if(StringUtils.isNotBlank(name)){
            elementName = name;
        }else{
            elementName = "jcr:root";
        }
        
        
        try {
          
            parentNodePathStack.push(elementName);
            writer.writeStartElement(elementName);
            
            if (isFirstElement) {
                for (String prefix : namespaceRegistry.getPrefixes()) {
                    writer.writeNamespace(prefix, namespaceRegistry.getURI(prefix));
                }
                isFirstElement = false;
            }
            
            writer.writeAttribute(JcrConstants.JCR_PRIMARYTYPE, StringUtils.isNotBlank(primaryNodeType) ? primaryNodeType : JcrConstants.NT_UNSTRUCTURED);
            if(ArrayUtils.isNotEmpty(mixinNodeTypes)){
                writer.writeAttribute(JcrConstants.JCR_MIXINTYPES, "[" + String.join(",", mixinNodeTypes) + "]");
            }
            
        } catch (XMLStreamException e) {
            throw new RepositoryException(e);
        }
    }

    public String getPrimaryNodeName() {
        return primaryNodeName;
    }

    @Override
    public void finishNode() throws RepositoryException {
        try {
            this.parentNodePathStack.pop();
            writer.writeEndElement();
        } catch (XMLStreamException e) {
            throw new RepositoryException(e);
        }
    }

    @Override
    public void finish() throws RepositoryException {
        
        if(finished){
            return;
        }
        try {
            writer.writeEndDocument();
            writer.flush();
            writer.close();
            this.finished = true;
        } catch (XMLStreamException e) {
            throw new RepositoryException(e);
        }
       
    }

  

    @Override
    public void createProperty(String name, int propertyType, String value) throws RepositoryException {
        
        String propertyTypeName = PropertyType.nameFromValue(propertyType);

        try {
            if(propertyType > 0){
                writer.writeAttribute(name, String.format(FORMAT_SINGLE_VALUE_TYPED, propertyTypeName, value));
            }else{
                writer.writeAttribute(name, String.format(FORMAT_SINGLE_VALUE, value));
            }
           
        } catch (XMLStreamException e) {
            throw new RepositoryException(e);
        }
    }

   
    @Override
    public void createProperty(String name, int propertyType, String[] values) throws RepositoryException {
        String propertyTypeName = PropertyType.nameFromValue(propertyType);

        try {
            if(propertyType > 0){
                writer.writeAttribute(name, String.format(FORMAT_MULTI_VALUE_TYPED, propertyTypeName,  String.join(",", values)));
            }else{
                writer.writeAttribute(name, String.format(FORMAT_MULTI_VALUE,  String.join(",", values)));
            }
        } catch (XMLStreamException e) {
            throw new RepositoryException(e);
        }
    }
    
   
    @Override
    public void createProperty(String name, Object value)  throws RepositoryException {
        if (value == null) {
            return;
        }
        
        try{

            if (value instanceof Long) {
                writer.writeAttribute(name, String.format(FORMAT_SINGLE_VALUE_TYPED, "Long", value.toString()));
            } else if (value instanceof Date) {
                writer.writeAttribute(name, String.format(FORMAT_SINGLE_VALUE_TYPED, "Date", ((Date) value).toGMTString()));
            } else if (value instanceof Calendar) {
                writer.writeAttribute(name, String.format(FORMAT_SINGLE_VALUE_TYPED, "Date", ((Calendar) value).getTime().toGMTString()));
            } else if (value instanceof Double) {
                writer.writeAttribute(name, String.format(FORMAT_SINGLE_VALUE_TYPED, "Double", value));
            } else if (value instanceof Boolean) {
                boolean theBoolValue = Boolean.parseBoolean(value.toString());
                writer.writeAttribute(name, String.format(FORMAT_SINGLE_VALUE_TYPED, "Boolean", theBoolValue));
            } else if (value instanceof InputStream) {
                writer.writeAttribute(name, "{Binary}");
                String path = "/jcr_root" + this.repositoryPath + "/" + this.primaryNodeName + "/" + parentNodePathStack.get(parentNodePathStack.size() - 2);
                packageAssembler.addEntry(path, (InputStream) value);
                
            } else {
                writer.writeAttribute(name, String.format(FORMAT_SINGLE_VALUE, value));
            }

        } catch (XMLStreamException | IOException e) {
            throw new RepositoryException(e);
        }
    }
    
    
    @Override
    public void createProperty(String name, Object[] values) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createFileAndResourceNode(String name, InputStream data, String mimeType, long lastModified) throws RepositoryException {
        this.createNode(name, "nt:file", null);
        this.createNode("jcr:content", "nt:resource", null);

        // ensure sensible last modification date
        if (lastModified <= 0) {
            lastModified = System.currentTimeMillis();
        }
        this.createProperty("jcr:mimeType", mimeType);
        this.createProperty("jcr:lastModified", lastModified);
        this.createProperty("jcr:data", data);
    }
    

    @Override
    public boolean switchCurrentNode(String subPath, String newNodeType) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createUser(String name, String password, Map<String, Object> extraProperties) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createGroup(String name, String[] members, Map<String, Object> extraProperties) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createAce(String principal, String[] grantedPrivileges, String[] deniedPrivileges, String order) throws RepositoryException {
        //we need to use repoinit 
        throw new UnsupportedOperationException();
    }

   
}
