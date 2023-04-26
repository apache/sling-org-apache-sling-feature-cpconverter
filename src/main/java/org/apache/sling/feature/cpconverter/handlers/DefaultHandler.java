/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.feature.cpconverter.handlers;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class DefaultHandler implements EntryHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultHandler.class);
    
    private static final String INSTALL_HOOK_ROOT = "/" + Constants.META_DIR + "/" + Constants.HOOKS_DIR;

    private final VaultPackageAssembler mainPackageAssembler;
    private final boolean removeInstallHooks;
    
    public DefaultHandler(@NotNull VaultPackageAssembler mainPackageAssembler, boolean removeInstallHooks) {
        this.mainPackageAssembler = mainPackageAssembler;
        this.removeInstallHooks = removeInstallHooks;
    }
    
    @Override
    public boolean matches(@NotNull String path) {
        return true;
    }

    @Override
    public void handle(@NotNull String path, @NotNull Archive archive, @NotNull Archive.Entry entry, @NotNull ContentPackage2FeatureModelConverter converter, String runMode)
            throws IOException {
        if (removeInstallHooks && path.startsWith(INSTALL_HOOK_ROOT)) {
            log.info("Skipping install hook {} from original package", path);
        } else {
            mainPackageAssembler.addEntry(path, archive, entry);
        }
    }
}