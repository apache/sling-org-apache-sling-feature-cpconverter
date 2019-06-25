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

import static java.util.Objects.requireNonNull;
import static org.apache.jackrabbit.vault.packaging.PackageProperties.NAME_CND_PATTERN;

import java.io.File;
import java.util.regex.Pattern;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.PackageManager;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseVaultPackageScanner {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final PackageManager packageManager;

    protected final boolean strictValidation;

    public BaseVaultPackageScanner(boolean strictValidation) {
        this(new PackageManagerImpl(), strictValidation);
    }

    public BaseVaultPackageScanner(PackageManager packageManager, boolean strictValidation) {
        this.packageManager = packageManager;
        this.strictValidation = strictValidation;
    }

    public VaultPackage open(File vaultPackage) throws Exception {
        requireNonNull(vaultPackage, "Impossible to process a null vault package");
        return packageManager.open(vaultPackage, strictValidation);
    }

    public final void traverse(File vaultPackageFile, boolean closeOnTraversed) throws Exception {
        VaultPackage vaultPackage = null;
        try {
            vaultPackage = open(vaultPackageFile);
            traverse(vaultPackage);
        } finally {
            if (closeOnTraversed) {
                if (vaultPackage != null) {
                    vaultPackage.close();
                }
            }
        }
    }

    public final void traverse(VaultPackage vaultPackage) throws Exception {
        requireNonNull(vaultPackage, "Impossible to process a null vault package");

        PackageProperties properties = vaultPackage.getProperties();
        ImportOptions importOptions = new ImportOptions();
        String cndPattern = properties.getProperty(NAME_CND_PATTERN);
        if (cndPattern != null && !cndPattern.isEmpty()) {
            importOptions.setCndPattern(cndPattern);
        }
        addCdnPattern(importOptions.getCndPattern());

        Archive archive = vaultPackage.getArchive();
        try {
            archive.open(strictValidation);

            Entry root = archive.getRoot();
            traverse(null, archive, root);
        } finally {
            archive.close();
        }
    }

    private void traverse(String path, Archive archive, Entry entry) throws Exception {
        String entryPath = newPath(path, entry.getName());

        if (entry.isDirectory()) {
            onDirectory(entryPath, archive, entry);

            for (Entry child : entry.getChildren()) {
                traverse(entryPath, archive, child);
            }

            return;
        }

        logger.debug("Processing entry {}...", entryPath);

        onFile(entryPath, archive, entry);

        logger.debug("Entry {} successfully processed.", entryPath);
    }

    private static String newPath(String path, String entryName) {
        if (path == null) {
            return entryName;
        }

        return path + '/' + entryName;
    }

    protected void onDirectory(String path, Archive archive, Entry entry) throws Exception {
        // do nothing by default
    }

    protected void onFile(String path, Archive archive, Entry entry) throws Exception {
        // do nothing by default
    }

    protected void addCdnPattern(Pattern cndPattern) {
        // do nothing by default
    }

}
