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
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.NamespaceException;
import javax.jcr.NamespaceRegistry;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.jetbrains.annotations.NotNull;

/** Simple namespace registry backed by a map */
public class JcrNamespaceRegistry implements NamespaceRegistry, NamespaceResolver {

    private final Map<String, String> prefixUriMapping;
    private final Collection<String> registeredCndSystemIds;
    
    public JcrNamespaceRegistry() {
        prefixUriMapping = new HashMap<>();
        prefixUriMapping.put(PREFIX_JCR, NAMESPACE_JCR);
        prefixUriMapping.put(PREFIX_MIX, NAMESPACE_MIX);
        prefixUriMapping.put(PREFIX_NT, NAMESPACE_NT);
        prefixUriMapping.put(PREFIX_XML, NAMESPACE_XML);
        // referencing from org.apache.sling.jcr.resource.api.JcrResourceConstants.SLING_NAMESPACE_URI would require an additional dependency
        prefixUriMapping.put("sling", "http://sling.apache.org/jcr/sling/1.0");
        registeredCndSystemIds = new ArrayList<>();
    }

    public void registerCnd(Reader reader, String systemId) throws ParseException, RepositoryException, IOException {
        throw new IllegalStateException("Not implemented");
        /*
        TODO: SLING-10770, implement CND support
            NodeTypeManager ntManager = null;
            ValueFactory valueFactory = null;
            CndImporter.registerNodeTypes(reader, systemId, ntManager, this, valueFactory, false);
            registeredCndSystemIds.add(systemId);
         */
    }

    @Override
    public void registerNamespace(String prefix, String uri)
            throws RepositoryException {
        String oldUri = prefixUriMapping.putIfAbsent(prefix, uri);
        if (oldUri != null && !oldUri.equals(uri)) {
            throw new RepositoryException("Prefix " + prefix + " already used for another uri!");
        }
    }

    @Override
    public void unregisterNamespace(String prefix)
            throws RepositoryException {
        throw new UnsupportedOperationException("Unregistering namespaces is unsupported");
    }

    @Override
    public String[] getPrefixes() throws RepositoryException {
        return prefixUriMapping.keySet().toArray(new String[0]);
    }

    @Override
    public String[] getURIs() throws RepositoryException {
        return prefixUriMapping.values().toArray(new String[0]);
    }

    @Override
    public String getURI(String prefix) throws NamespaceException {
        String uri = prefixUriMapping.get(prefix);
        if (uri == null) {
            throw new NamespaceException("No registered URI found for prefix " + prefix);
        }
        return uri;
    }

    @Override
    public String getPrefix(String uri) throws NamespaceException {
        throw new UnsupportedOperationException("This lookup direction is unsupported");
    }

    public @NotNull Collection<String> getRegisteredCndSystemIds() {
        return registeredCndSystemIds;
    }
}
