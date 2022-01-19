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

import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.SlingInitialContentPolicy;
import org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.BundleSlingInitialContentExtractor;
import org.apache.sling.feature.cpconverter.shared.ConverterConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Collections;
import java.util.Map;

public class DefaultEntryHandlersManager implements EntryHandlersManager {

    private final List<EntryHandler> entryHandlers = new LinkedList<>();

    public DefaultEntryHandlersManager() {
        this(Collections.emptyMap(), 
                false, 
                SlingInitialContentPolicy.KEEP,
                new BundleSlingInitialContentExtractor(),  
                ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT
        );
    }

    public DefaultEntryHandlersManager(@NotNull Map<String, String> configs, 
                                       boolean enforceConfigurationsAndBundlesBelowProperFolder,
                                       @NotNull SlingInitialContentPolicy slingInitialContentPolicy, 
                                       @NotNull BundleSlingInitialContentExtractor bundleSlingInitialContentExtractor,  
                                       @NotNull String systemUserRelPath) {
        ServiceLoader<EntryHandler> entryHandlersLoader = ServiceLoader.load(EntryHandler.class);
        for (EntryHandler entryHandler : entryHandlersLoader) {
            if (configs.containsKey(entryHandler.getClass().getName())) {
                entryHandler = entryHandler.withConfig(configs.get(entryHandler.getClass().getName()));
            }
            if (entryHandler instanceof AbstractConfigurationEntryHandler) {
                ((AbstractConfigurationEntryHandler) entryHandler).setEnforceConfigurationBelowConfigFolder(enforceConfigurationsAndBundlesBelowProperFolder);
            } else if (entryHandler instanceof BundleEntryHandler) {
                ((BundleEntryHandler) entryHandler).setEnforceBundlesBelowInstallFolder(enforceConfigurationsAndBundlesBelowProperFolder);
                ((BundleEntryHandler) entryHandler).setSlingInitialContentPolicy(slingInitialContentPolicy);
                ((BundleEntryHandler) entryHandler).setBundleSlingInitialContentExtractor(bundleSlingInitialContentExtractor);
            } else if (entryHandler instanceof AbstractUserEntryHandler) {
                ((AbstractUserEntryHandler) entryHandler).setSystemUserRelPath(systemUserRelPath);
            }
            addEntryHandler(entryHandler);
        }
    }

    @Override
    public void addEntryHandler(@NotNull EntryHandler handler) {
        entryHandlers.add(handler);
    }

    @Override
    public @Nullable EntryHandler getEntryHandlerByEntryPath(@NotNull String path) {
        for (EntryHandler entryHandler : entryHandlers) {
            if (entryHandler.matches(path)) {
                return entryHandler;
            }
        }
        return null;
    }

}
