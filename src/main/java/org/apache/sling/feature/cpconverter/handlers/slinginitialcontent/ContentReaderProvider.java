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

import org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.readers.XMLReader;
import org.apache.sling.jcr.contentloader.ContentReader;
import org.apache.sling.jcr.contentloader.PathEntry;
import org.apache.sling.jcr.contentloader.internal.readers.JsonReader;
import org.apache.sling.jcr.contentloader.internal.readers.ZipReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Holds The ContentReader instances and provides them for pathEntries.
 */
class ContentReaderProvider {

    static final JsonReader jsonReader = new JsonReader();
    static final XMLReader xmlReader = new XMLReader();
    static final ZipReader zipReader = new ZipReader();

    @Nullable
    ContentReader getContentReaderForEntry(@NotNull File entry, @NotNull PathEntry pathEntry) {
        String entryName = entry.getName();
        if (entryName.endsWith(".json") && !pathEntry.isIgnoredImportProvider("json")) {
            return jsonReader;
        } else if (entryName.endsWith(".xml") && !pathEntry.isIgnoredImportProvider("xml")) {
            return xmlReader;
        } else if (
                (entryName.endsWith(".zip") && !pathEntry.isIgnoredImportProvider("zip")) ||
                        (entryName.endsWith(".jar") && !pathEntry.isIgnoredImportProvider("jar"))
        ) {
            return zipReader;
        } else {
            return null;
        }
    }

}
