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
import java.util.Map;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageManager;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.handlers.EntryHandler;
import org.apache.sling.feature.cpconverter.handlers.GroupEntryHandler;
import org.apache.sling.feature.cpconverter.handlers.SlingInitialContentBundleHandler;
import org.apache.sling.feature.cpconverter.handlers.UsersEntryHandler;
import org.apache.sling.feature.cpconverter.handlers.VersionResolverContentPackageEntryHandler;
import org.jetbrains.annotations.NotNull;

public final class RecollectorVaultPackageScanner extends BaseVaultPackageScanner {

    private final ContentPackage2FeatureModelConverter converter;

    private final EntryHandler[] handlers;

    public RecollectorVaultPackageScanner(@NotNull ContentPackage2FeatureModelConverter converter,
                                          @NotNull PackageManager packageManager,
                                          boolean strictValidation,
                                          @NotNull Map<PackageId, String> subContentPackages,
                                          @NotNull ContentPackage2FeatureModelConverter.SlingInitialContentPolicy slingInitialContentPolicy) {
        super(packageManager, strictValidation);
        this.converter = converter;
        VersionResolverContentPackageEntryHandler versionResolverContentPackageEntryHandler = new VersionResolverContentPackageEntryHandler(this, subContentPackages);
        handlers = new EntryHandler[] {
                new UsersEntryHandler(),
                new GroupEntryHandler(),
                versionResolverContentPackageEntryHandler,
                new SlingInitialContentBundleHandler(versionResolverContentPackageEntryHandler, slingInitialContentPolicy)
        };
    }

    @Override
    protected void onFile(@NotNull String path, @NotNull Archive archive, @NotNull Entry entry, String runMode) throws IOException, ConverterException {
        for (EntryHandler handler : handlers) {
            if (handler.matches(path)) {
                handler.handle(path, archive, entry, converter, runMode);
            }
        }
    }

}
