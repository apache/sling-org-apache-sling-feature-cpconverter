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
package org.apache.sling.feature.cpconverter.handlers;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;

public class DefaultEntryHandlersManager implements EntryHandlersManager {

    private final List<EntryHandler> entryHandlers = new LinkedList<>();

    public DefaultEntryHandlersManager() {
        ServiceLoader<EntryHandler> entryHandlersLoader = ServiceLoader.load(EntryHandler.class);
        Iterator<EntryHandler> entryHandlersIterator = entryHandlersLoader.iterator();
        while (entryHandlersIterator.hasNext()) {
            EntryHandler entryHandler = entryHandlersIterator.next();

            addEntryHandler(entryHandler);
        }
    }

    @Override
    public void addEntryHandler(EntryHandler handler) {
        if (handler != null) {
            entryHandlers.add(handler);
        }
    }

    @Override
    public EntryHandler getEntryHandlerByEntryPath(String path) {
        for (EntryHandler entryHandler : entryHandlers) {
            if (entryHandler.matches(path)) {
                return entryHandler;
            }
        }

        return null;
    }

}
