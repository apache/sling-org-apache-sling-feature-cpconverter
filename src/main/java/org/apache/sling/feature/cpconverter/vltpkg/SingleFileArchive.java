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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.util.FileInputSource;

/**
 * Best-effort implementation of a FileVault archive only containing a single file.
 * Lots of information is obviously not available here (i.e. metadata, ....)
 * Used for passing Sling Initial Content in bundles to the EntryHandlers
 */
public class SingleFileArchive implements Archive {

    private final File file;
    private final String relativePath;
    
    public SingleFileArchive(File file, String relativePath) {
        this.file = file;
        this.relativePath = relativePath;
    }

    @Override
    public void open(boolean strict) throws IOException {
        // noop
    }

    @Override
    public InputStream openInputStream(Entry entry) throws IOException {
        if (!(entry instanceof SingleFileEntry)) {
            throw new IllegalArgumentException("Can only open input stream for SingleFileEntry, but given entry is " + entry.getClass());
        }
        return new FileInputStream(file);
    }

    @Override
    public VaultInputSource getInputSource(Entry entry) throws IOException {
        if (!(entry instanceof SingleFileEntry)) {
            throw new IllegalArgumentException("Can only open input stream for SingleFileEntry, but given entry is " + entry.getClass());
        }
        return new FileInputSource(file);
    }

    @Override
    public Entry getJcrRoot() throws IOException {
        return null;
    }

    @Override
    public Entry getRoot() throws IOException {
        return new SingleFileEntry(this);
    }

    @Override
    public MetaInf getMetaInf() {
        return new DefaultMetaInf();
    }

    @Override
    public Entry getEntry(String path) throws IOException {
        if (path.equals(relativePath)) {
            return new SingleFileEntry(this);
        }
        return null;
    }

    @Override
    public Archive getSubArchive(String root, boolean asJcrRoot) throws IOException {
        return null;
    }

    @Override
    public void close() {
        // no resources to release here
    }

    public static class SingleFileEntry implements Entry {

        SingleFileArchive archive;
        
        SingleFileEntry(SingleFileArchive archive) {
            this.archive = archive;
        }
 
        @Override
        public String getName() {
            // use forward slashes as separators
            return archive.relativePath;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public Collection<? extends Entry> getChildren() {
            return Collections.emptyList();
        }

        @Override
        public Entry getChild(String name) {
            return null;
        }
        
    }
}
