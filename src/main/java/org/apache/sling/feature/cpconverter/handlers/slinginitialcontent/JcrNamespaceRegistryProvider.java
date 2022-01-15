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

import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.sling.commons.osgi.ManifestHeader;
import org.apache.sling.feature.cpconverter.vltpkg.JcrNamespaceRegistry;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

class JcrNamespaceRegistryProvider {

    public static final String NODETYPES_BUNDLE_HEADER = "Sling-Nodetypes";

    public static final String NAMESPACES_BUNDLE_HEADER = "Sling-Namespaces";

    private static final Logger logger = LoggerFactory.getLogger(JcrNamespaceRegistryProvider.class);

    private final Manifest manifest;
    private final JarFile jarFile;
    private final Map<String, String> predefinedNamespaceUriByPrefix;

    JcrNamespaceRegistryProvider(@NotNull Manifest manifest,
                                 @NotNull JarFile jarFile,
                                 @NotNull Map<String, String> predefinedNamespaceUriByPrefix) {

        this.manifest = manifest;
        this.jarFile = jarFile;
        this.predefinedNamespaceUriByPrefix = predefinedNamespaceUriByPrefix;
    }

    @NotNull JcrNamespaceRegistry provideRegistryFromBundle() throws IOException {
        try {
            JcrNamespaceRegistry registry = new JcrNamespaceRegistry();
            for (Map.Entry<String, String> entry : predefinedNamespaceUriByPrefix.entrySet()) {
                registry.registerNamespace(entry.getKey(), entry.getValue());
            }

            // parse Sling-Namespaces header (https://github.com/apache/sling-org-apache-sling-jcr-base/blob/66be360910c265473799635fcac0e23895898913/src/main/java/org/apache/sling/jcr/base/internal/loader/Loader.java#L192)
            final String namespacesDefinitionHeader = manifest.getMainAttributes().getValue(NAMESPACES_BUNDLE_HEADER);
            if (namespacesDefinitionHeader != null) {
                final StringTokenizer st = new StringTokenizer(namespacesDefinitionHeader, ",");

                while (st.hasMoreTokens()) {
                    final String token = st.nextToken().trim();
                    int pos = token.indexOf('=');
                    if (pos == -1) {
                        logger.warn("createNamespaceRegistry: Bundle {} has an invalid namespace manifest header entry: {}",
                                manifest.getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME), token);
                    } else {
                        final String prefix = token.substring(0, pos).trim();
                        final String namespace = token.substring(pos + 1).trim();
                        registry.registerNamespace(prefix, namespace);
                    }
                }
            }

            // parse Sling-Nodetypes header
            final String typesHeader = manifest.getMainAttributes().getValue(NODETYPES_BUNDLE_HEADER);
            if (typesHeader != null) {
                for (ManifestHeader.Entry entry : ManifestHeader.parse(typesHeader).getEntries()) {
                    JarEntry jarEntry = jarFile.getJarEntry(entry.getValue());
                    if (jarEntry == null) {
                        logger.warn("createNamespaceRegistry: Bundle {} has referenced a non existing node type definition: {}",
                                manifest.getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME), entry.getValue());
                    } else {
                        try (InputStream inputStream = jarFile.getInputStream(jarEntry);
                             Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                            registry.registerCnd(reader, entry.getValue());
                        }
                    }
                }
            }
            return registry;
        } catch (final RepositoryException | ParseException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

}
