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
package org.apache.sling.feature.cpconverter.index;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.jetbrains.annotations.NotNull;

/**
 * Holds information about discovered index definitions
 *
 */
public class IndexDefinitions {

    public static final String OAK_INDEX_NAME = "oak:index";
    public static final String OAK_INDEX_PATH = "/" + OAK_INDEX_NAME; // NOSONAR - java:S1075 does not apply as this is not a filesystem path

    private final Map<String, List<DocViewNode2>> children = new HashMap<>();
    private final Map<String, byte[]> binaries = new HashMap<>();
    private Map<String, String> prefixesToUris = new HashMap<>();
    private Map<String, String> urisToPrefixes = new HashMap<>();

    public void addNode(@NotNull String parentPath, @NotNull DocViewNode2 node) {
        List<DocViewNode2> currentChildren = children.computeIfAbsent(parentPath, k -> new ArrayList<>());
        DocViewNode2 existing = null;
        for ( DocViewNode2 currentChild : currentChildren ) {

            // prevent duplicates
            if ( currentChild.getName().equals(node.getName() )) {
                // new node holds less information. There should not be a scenario where we need to
                // merge properties.
                if ( node.getProperties().size() <= currentChild.getProperties().size() ) {
                    return;
                }

                existing = currentChild;
            }
        }

        // remove node marked as placeholder
        if ( existing != null ) {
            currentChildren.remove(existing);
        }

        // add new node
        currentChildren.add(node);
    }

    public @NotNull List<DocViewNode2> getIndexes() {
        return getChildren(OAK_INDEX_PATH);
    }

    public @NotNull List<DocViewNode2> getChildren(@NotNull String parentPath) {
        return children.getOrDefault(parentPath, Collections.emptyList());
    }


    /**
     * Returns a name in compact format
     *
     * <p>Maps a fully qualified {@link Name name}, e.g. ['http://jackrabbit.apache.org/oak/ns/1.0','index'] to a compact name
     * like <tt>oak:index</tt></p>
     *
     * @param name The name to map
     * @return the compact name
     */
    public @NotNull String toShortName(@NotNull Name name) {
        if ( name.getNamespaceURI().length() == 0 )
            return name.getLocalName();
        return urisToPrefixes.get(name.getNamespaceURI()) + ":" + name.getLocalName();
    }

    /**
     * Registers a binary entry at the specified repository path
     *
     * <p>The input stream may be fully read into memory, and therefore is not expected to be unreasonably large.</p>
     *
     * <p>The input stream will be fully read, but not closed.</p>
     *
     * @param repositoryPath The JCR repository path where the binary was found
     * @param is the input stream for the binary
     * @throws IOException in case of I/O problems
     */
    public void registerBinary(@NotNull String repositoryPath, @NotNull InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IOUtils.copy(is, out);
        binaries.put(repositoryPath, out.toByteArray());
    }

    /**
     * Returns a potential binary registered for a repository path
     *
     * @param repositoryPath the path of the repository
     * @return an optional wrapping the binary data, possibly {@link Optional#empty() empty}
     */
    public @NotNull Optional<byte[]> getBinary(@NotNull String repositoryPath) {
        return Optional.ofNullable(binaries.get(repositoryPath));
    }

    /**
     * Registers a prefix mapping for a specified uri
     *
     * @param prefix the prefix
     * @param uri the uri
     */
    public void registerPrefixMapping(@NotNull String prefix, @NotNull String uri) {
        prefixesToUris.put(prefix, uri);
        urisToPrefixes.put(uri, prefix);
     }

    /**
     * Dumps a compact representation of the data
     *
     * <p>Useful for debugging purposes only</p>
     *
     * @param out the PrintStream to use
     */
    public void dump(@NotNull PrintStream out) {
        out.println("---------");
        out.println(OAK_INDEX_NAME);
        dumpChildren(out, OAK_INDEX_PATH);
        out.println("---------");
    }

    private void dumpChildren(PrintStream out, String parentPath) {

        StringBuilder padding = new StringBuilder();
        int depth = parentPath.split("/").length - 1;
        for ( int i = 0 ; i < 2 * depth; i++)
            padding.append(' ');

        for ( DocViewNode2 node : children.getOrDefault(parentPath, Collections.emptyList()) ) {
            out.println(padding.toString() + toShortName(node.getName()));
            dumpChildren(out, parentPath + '/' + node.getName().getLocalName());
        }
    }
}