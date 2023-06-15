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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.jetbrains.annotations.NotNull;

/**
 * Holds information about discovered index definitions
 *
 * <p>According to the Oak documentation, indexes are located under a root {@code /oak:index}, or (lucene indexes only)
 * under arbitrary repository locations, as long as they have an {@code oak:index} parent node.</p>
 *
 * <p>This class supports non-root indexes but does not attempt to enforce Oak-level invariants, such as which index
 * types support non-root locations.</p>
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
        // if node properties are null and there is no binaries for node exists.
        if ( CollectionUtils.isEmpty(node.getProperties())
                && ( binaries.get(parentPath + "/" + node.getName().getLocalName()) == null
                    || node.getName().getLocalName().contains(".xml"))){
            return;
        }
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

    /**
     * Returns the discovered index definitions by location
     *
     * <p>The returned map has the index parent location as keys and the index definitions as values, for instance:</p>
     *
     * <ul>
     *  <li>oak:index -&gt; [counter, uuid]
     *  <li>content/oak:index -&gt; [lucene-2]
     * </ul>
     *
     *
     * @return a map of discovered index locations, possibly empty
     */
    public @NotNull Map<String, List<DocViewNode2>> getIndexes() {
        return children.entrySet().stream()
            .filter( e -> e.getKey().endsWith(OAK_INDEX_PATH) )
            .collect( Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue) );
    }

    public @NotNull List<DocViewNode2> getChildren(@NotNull String parentPath) {
        return children.getOrDefault(parentPath, Collections.emptyList());
    }

    /**
     * Returns a name in compact format
     *
     * <p>Maps a fully qualified {@link Name name}, e.g. ['http://jackrabbit.apache.org/oak/ns/1.0','index'] to a compact name
     * like {@code oak:index}</p>
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
