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

import org.apache.jackrabbit.commons.SimpleValueFactory;
import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.vault.validation.spi.impl.nodetype.NodeTypeManagerProvider;
import org.jetbrains.annotations.NotNull;

import javax.jcr.NamespaceException;
import javax.jcr.NamespaceRegistry;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;
import javax.jcr.nodetype.NodeTypeManager;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;

/** Simple namespace registry backed by a map */
public class JcrNamespaceRegistry implements NamespaceRegistry, NamespaceResolver {
    
    private final Collection<String> registeredCndSystemIds = new ArrayList<>();
    private final NodeTypeManagerProvider ntManagerProvider = new NodeTypeManagerProvider();
    private final NodeTypeManager ntManager = ntManagerProvider.getNodeTypeManager();

    public JcrNamespaceRegistry() throws RepositoryException, ParseException, IOException {
        ntManagerProvider.registerNamespace(PREFIX_XML, NAMESPACE_XML);
        ntManagerProvider.registerNamespace("sling", "http://sling.apache.org/jcr/sling/1.0");
    }

    public void registerCnd(Reader reader, String systemId) throws ParseException, RepositoryException, IOException {
        ValueFactory valueFactory = new SimpleValueFactory();
        CndImporter.registerNodeTypes(reader, systemId, ntManager, this, valueFactory, false);
        registeredCndSystemIds.add(systemId);
    }

    @Override
    public void registerNamespace(String prefix, String uri)
            throws RepositoryException {
        ntManagerProvider.registerNamespace(prefix, uri);
    }

    @Override
    public void unregisterNamespace(String prefix)
            throws RepositoryException {
        ntManagerProvider.unregisterNamespace(prefix);
    }

    @Override
    public String[] getPrefixes() throws RepositoryException {
        return ntManagerProvider.getRegisteredNamespaces().keySet().toArray(new String[0]);
    }

    @Override
    public String[] getURIs() throws RepositoryException {
        return ntManagerProvider.getRegisteredNamespaces().values().toArray(new String[0]);
    }

    @Override
    public String getURI(String prefix) throws NamespaceException {
        try {
            return ntManagerProvider.getURI(prefix);
        } catch (RepositoryException e) {
            throw new NamespaceException(e);
        }
    }

    @Override
    public String getPrefix(String uri) throws NamespaceException {
        try {
            return ntManagerProvider.getPrefix(uri);
        } catch (RepositoryException e) {
            throw new NamespaceException(e);
        }
    }

    public @NotNull Collection<String> getRegisteredCndSystemIds() {
        return registeredCndSystemIds;
    }


}
