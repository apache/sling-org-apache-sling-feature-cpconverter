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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.PackageManager;
import org.apache.jackrabbit.vault.packaging.VaultPackage;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public abstract class AbstractContentPackageHandler extends AbstractRegexEntryHandler {

    private final File subContentPackagesDir;

    @Inject
    private PackageManager packageManager;

    @Inject
    @Named("packagemanager.validation.strict")
    protected boolean strictValidation;

    public AbstractContentPackageHandler(File temporaryDir) {
        super("(?:jcr_root)?/etc/packages/.+\\.zip");

        subContentPackagesDir = new File(temporaryDir, "sub-content-packages");
        if (!subContentPackagesDir.exists()) {
            subContentPackagesDir.mkdirs();
        }
    }

    @Override
    public final void handle(String path, Archive archive, Entry entry) throws Exception {
        logger.info("Processing sub-content package '{}'...", entry.getName());

        File temporaryContentPackage = new File(subContentPackagesDir, entry.getName());

        if (!temporaryContentPackage.exists()) {
            logger.debug("Extracting sub-content package '{}' to {} for future analysis...", entry.getName(), temporaryContentPackage);

            try (InputStream input = archive.openInputStream(entry);
                    OutputStream output = new FileOutputStream(temporaryContentPackage)) {
                IOUtils.copy(input, output);
            }

            logger.debug("Sub-content package '{}' successfully extracted to {} ", entry.getName(), temporaryContentPackage);
        }

        try (VaultPackage vaultPackage = packageManager.open(temporaryContentPackage, strictValidation)) {
            processSubPackage(path, vaultPackage);
        }

        logger.info("Sub-content package '{}' processing is over", entry.getName());
    }

    protected abstract void processSubPackage(String path, VaultPackage contentPackage) throws Exception;

}
