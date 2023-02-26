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

import static org.apache.sling.feature.cpconverter.shared.ConverterConstants.SLASH;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jcr.NamespaceException;
import javax.jcr.NamespaceRegistry;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.DefaultNamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.apache.jackrabbit.vault.fs.io.DocViewFormat;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.jackrabbit.vault.util.xml.serialize.FormattingXmlStreamWriter;
import org.apache.sling.feature.cpconverter.vltpkg.JcrNamespaceRegistry;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.jcr.contentloader.ContentCreator;
import org.jetbrains.annotations.NotNull;

/**
 * ContentCreator implementation to write FileVault enhanced DocView XML files (to be packaged into a VaultPackage)
 */
public class VaultContentXMLContentCreator implements ContentCreator {

    private static final String ACL_NOT_SUPPORTED_MSG = "Sling Initial Content - ACL statements are not supported yet . SLING issue: https://issues.apache.org/jira/browse/SLING-11060";

    private final VaultPackageAssembler packageAssembler;
    private final Queue<DocViewTreeNode> currentNodeStack = Collections.asLifoQueue(new ArrayDeque<>());
    private final JcrNamespaceRegistry namespaceRegistry;
    private final NamePathResolver npResolver;
    private final XMLStreamWriter writer;
    private String rootPath;
    private final boolean isFileDescriptorEntry;
    private boolean isFinished = false;

    private DocViewTreeNode rootNode = null;

    VaultContentXMLContentCreator(@NotNull String repositoryPath,
                                  @NotNull OutputStream targetOutputStream,
                                  @NotNull JcrNamespaceRegistry namespaceRegistry,
                                  @NotNull VaultPackageAssembler packageAssembler,
                                  boolean isFileDescriptorEntry) throws RepositoryException, FactoryConfigurationError {
        this.packageAssembler = packageAssembler;
        this.namespaceRegistry = namespaceRegistry;
        this.isFileDescriptorEntry = isFileDescriptorEntry;
        this.npResolver = new DefaultNamePathResolver((NamespaceRegistry)namespaceRegistry);
        try {
            writer = FormattingXmlStreamWriter.create(targetOutputStream, new DocViewFormat().getXmlOutputFormat());
        } catch (XMLStreamException e) {
            throw new RepositoryException("Cannot create XML Writer " + e, e);
        }
        rootPath = repositoryPath;
    }

    /**
     * The absolute entry path inside the content package ZIP for the generated docview.xml
     * @throws NamespaceException 
     */
    public String getContentPackageEntryPath() throws NamespaceException {
        String suffix;
        if (isFileDescriptorEntry) {
            // it is potentially https://jackrabbit.apache.org/filevault/vaultfs.html#extended-file-aggregates (and may have file name clashes otherwise with the binary file in the content package)
            suffix = ".dir" + "/" + Constants.DOT_CONTENT_XML;
        } else {
            suffix =  "/" + Constants.DOT_CONTENT_XML;
        }
        return SLASH + org.apache.jackrabbit.vault.util.Constants.ROOT_DIR + PlatformNameFormat.getPlatformPath(rootNode.getPath(npResolver)) + suffix;
    }

    @Override
    public void createNode(String name, String primaryNodeType, String[] mixinNodeTypes) throws RepositoryException {
        final Name currentNodeName;
        if (rootNode == null) {
            currentNodeName = NameConstants.JCR_ROOT;
            if (StringUtils.isNotBlank(name)) {
                // adjust root path in case the first node has an explicit name
                rootPath = Text.getRelativeParent(rootPath, 1) + "/" + name;
            }
        } else {
            currentNodeName = npResolver.getQName(name);
        }

        // if we are dealing with a descriptor file, we should use nt:file as default primaryType. 
        String defaultNtType = isFileDescriptorEntry ? JcrConstants.NT_FILE : JcrConstants.NT_UNSTRUCTURED;
        String toUsePrimaryNodeType = StringUtils.isNotBlank(primaryNodeType) ? primaryNodeType : defaultNtType;
        List<DocViewProperty2> currentProperties = new ArrayList<>();
        currentProperties.add(new DocViewProperty2(NameConstants.JCR_PRIMARYTYPE, toUsePrimaryNodeType));
        if (ArrayUtils.isNotEmpty(mixinNodeTypes)) {
            currentProperties.add(new DocViewProperty2(NameConstants.JCR_MIXINTYPES, Arrays.asList(mixinNodeTypes)));
        }
        final DocViewTreeNode newNode;
        if (rootNode == null) {
            newNode = new DocViewTreeNode(rootPath, currentNodeName, currentProperties);
            rootNode = newNode;
        } else {
            newNode = new DocViewTreeNode(rootNode.getPath(npResolver), currentNodeName, currentProperties);
            currentNodeStack.element().addChild(newNode);
        }
        currentNodeStack.add(newNode);
    }

    @Override
    public void finishNode() throws RepositoryException {
        this.currentNodeStack.remove();
    }

    @Override
    public void finish() throws RepositoryException {
        if (isFinished) {
            return;
        }
        isFinished = true;
        try {
            rootNode.write(writer, namespaceRegistry, Arrays.asList(namespaceRegistry.getPrefixes()));
            writer.close();
        } catch (XMLStreamException e) {
            throw new RepositoryException("Cannot close XML writer " + e, e);
        }
    }

    @Override
    public void createProperty(String name, int propertyType, String value) throws RepositoryException {
        currentNodeStack.peek().getProperties().add(new DocViewProperty2(npResolver.getQName(name), value, propertyType));
    }

    @Override
    public void createProperty(String name, int propertyType, String[] values) throws RepositoryException {
        currentNodeStack.peek().getProperties().add(new DocViewProperty2(npResolver.getQName(name), Arrays.asList(values), propertyType));
    }

    @Override
    public void createProperty(String name, Object value) throws RepositoryException {
        // store binaries outside of docview xml
        Value jcrValue = createValue(name, value, -1);
        DocViewProperty2 property = DocViewProperty2.fromValues(npResolver.getQName(name), new Value[] { jcrValue }, jcrValue.getType(), false, false, false);
        currentNodeStack.peek().getProperties().add(property);
    }

    @Override
    public void createProperty(String name, Object[] values) throws RepositoryException {
        try {
            AtomicInteger index = new AtomicInteger();
            Value[] jcrValues = Arrays.stream(values).map(v -> {
                try {
                    return createValue(name,v, index.getAndIncrement());
                } catch (RepositoryException e) {
                    throw new UncheckedRepositoryException(e);
                }
            }).toArray(Value[]::new);
            final int type;
            if (jcrValues.length == 0) {
                type = PropertyType.STRING;
            } else {
                type = jcrValues[0].getType();
            }
            DocViewProperty2 property = DocViewProperty2.fromValues(npResolver.getQName(name), jcrValues, type, true, false, false);
            currentNodeStack.peek().getProperties().add(property);
        } catch (UncheckedRepositoryException e) {
            throw e.getCause();
        }
    }

    static final class UncheckedRepositoryException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public UncheckedRepositoryException(RepositoryException e) {
            super(e);
        }

        public RepositoryException getCause() {
            return (RepositoryException) super.getCause();
        }
    }

    private Value createValue(String name, Object value, int index) throws RepositoryException {
        ValueFactory valueFactory = ValueFactoryImpl.getInstance();
        final Value jcrValue;
        if (value instanceof String) {
            jcrValue = valueFactory.createValue((String)value);
        } else if (value instanceof Long) {
            jcrValue = valueFactory.createValue((long)value);
        } else if (value instanceof Double) {
            jcrValue = valueFactory.createValue((Double)value);
        } else if (value instanceof BigDecimal) {
            jcrValue = valueFactory.createValue((BigDecimal)value);
        } else if (value instanceof Date) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime((Date)value);
            jcrValue = valueFactory.createValue(calendar);
        } else if (value instanceof Calendar) {
            jcrValue = valueFactory.createValue((Calendar)value);
        } else if (value instanceof Boolean) {
            jcrValue = valueFactory.createValue((Boolean)value);
        } else if (value instanceof InputStream) {
            // binaries are always stored outside the docview xml (https://jackrabbit.apache.org/filevault/vaultfs.html#Binary_Properties)
            String binaryPropertyEntryName = PlatformNameFormat.getPlatformName(name) + ((index != -1) ? index : "") + ".binary";
            createBinary((InputStream)value, SLASH + binaryPropertyEntryName);
            jcrValue = valueFactory.createValue("", PropertyType.BINARY);
        } else {
            throw new UnsupportedOperationException("Unsupported value type " + value.getClass());
        }
        return jcrValue;
    }

    private void createBinary(InputStream value, String suffix) throws RepositoryException {
        // this is called inside the node of the child
        String path = org.apache.jackrabbit.vault.util.Constants.ROOT_DIR + PlatformNameFormat.getPlatformPath(currentNodeStack.peek().getPath(npResolver)) + suffix;
        try {
            // write binary directly (not during finish)
            packageAssembler.addEntry(path, value);
        } catch (IOException e) {
            throw new RepositoryException(e);
        }
    }

    @Override
    public void createFileAndResourceNode(String name, InputStream data, String mimeType, long lastModified) throws RepositoryException {
        this.createNode(name, JcrConstants.NT_FILE, null);
        createBinary(data, ""); // binary must be created with the name of the nt:file root node
        this.createNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE, null);

        final Calendar calendar = Calendar.getInstance();
        // ensure sensible last modification date
        if (lastModified >= 0) {
            calendar.setTimeInMillis(lastModified);
        }
        this.createProperty(JcrConstants.JCR_MIMETYPE, mimeType);
        this.createProperty(JcrConstants.JCR_LASTMODIFIED, calendar);
        // the data property does not need to be added to the enhanced docview as already derived from the binary file
    }


    @Override
    public boolean switchCurrentNode(String subPath, String newNodeType) {
        throw new UnsupportedOperationException(ACL_NOT_SUPPORTED_MSG);
    }

    @Override
    public void createUser(String name, String password, Map<String, Object> extraProperties) {
        throw new UnsupportedOperationException(ACL_NOT_SUPPORTED_MSG);
    }

    @Override
    public void createGroup(String name, String[] members, Map<String, Object> extraProperties) {
        throw new UnsupportedOperationException(ACL_NOT_SUPPORTED_MSG);
    }

    @Override
    public void createAce(String principal, String[] grantedPrivileges, String[] deniedPrivileges, String order) {
        throw new UnsupportedOperationException(ACL_NOT_SUPPORTED_MSG);
    }

    @Override
    public void createAce(String principalId, String[] grantedPrivilegeNames, String[] deniedPrivilegeNames,
                          String order, Map<String, Value> restrictions, Map<String, Value[]> mvRestrictions,
                          Set<String> removedRestrictionNames) {
        throw new UnsupportedOperationException(ACL_NOT_SUPPORTED_MSG);
    }

}
