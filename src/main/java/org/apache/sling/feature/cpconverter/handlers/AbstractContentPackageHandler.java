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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractContentPackageHandler extends AbstractRegexEntryHandler {

    private static final String SNAPSHOT_POSTFIX = "-SNAPSHOT";

    private final Pattern EMBEDDED_PACKAGE_PATTERN = Pattern.compile("/jcr_root/apps/.+/install(?:\\\\.([^/]+))?/.+.zip");
    
    public AbstractContentPackageHandler() {
        super("/jcr_root/(?:etc/packages|apps/.+/install(?:\\.([^/]+))?)/.+.zip");
    }

    @Override
    public final void handle(@NotNull String path, @NotNull Archive archive, @NotNull Entry entry, @NotNull ContentPackage2FeatureModelConverter converter, String runMode)
            throws IOException, ConverterException {
        logger.info("Processing sub-content package '{}'...", entry.getName());

        final File temporaryDir = new File(converter.getTempDirectory(), "sub-content-packages");
        temporaryDir.mkdirs();
        File temporaryContentPackage = new File(temporaryDir, entry.getName());

        if (entry.getName().contains(SNAPSHOT_POSTFIX) && temporaryContentPackage.exists()) {
            logger.debug("SNAPSHOT content-package detected, deleting previous version on {}...", temporaryContentPackage);
            if (temporaryContentPackage.delete()) {
                logger.debug("Previous SNAPSHOT content-package version on {} deleted", temporaryContentPackage);
            } else {
                logger.warn("Impossible to delete previous SNAPSHOT content-package version on {}, please check current user permissions",
                            temporaryContentPackage);
            }
        }

        if (!temporaryContentPackage.exists()) {
            logger.debug("Extracting sub-content package '{}' to {} for future analysis...", entry.getName(), temporaryContentPackage);

            try (InputStream input = archive.openInputStream(entry);
                    OutputStream output = new FileOutputStream(temporaryContentPackage)) {
                IOUtils.copy(input, output);
            }

            logger.debug("Sub-content package '{}' successfully extracted to {} ", entry.getName(), temporaryContentPackage);
        }

        Matcher matcher = getPattern().matcher(path);

        // we are pretty sure it matches, here
        if (!matcher.matches()) {
            throw new IllegalStateException("Something went terribly wrong: pattern '"
                                            + getPattern().pattern()
                                            + "' should have matched already with path '"
                                            + path
                                            + "' but it does not, currently");
        }


        String targetRunmode;
        // determine runmodestring for current path
        String runModeMatch = matcher.group(1);
        targetRunmode = extractTargetRunmode(path, converter, runMode,
            runModeMatch);
        
        boolean isEmbeddedPackage = EMBEDDED_PACKAGE_PATTERN.matcher(path).matches();
        try (VaultPackage vaultPackage = converter.open(temporaryContentPackage)) {
            processSubPackage(path, targetRunmode, vaultPackage, converter, isEmbeddedPackage);
        }

        logger.info("Sub-content package '{}' processing is over", entry.getName());
    }

    protected abstract void processSubPackage(@NotNull String path, @Nullable String runMode, @NotNull VaultPackage contentPackage, @NotNull ContentPackage2FeatureModelConverter converter, boolean isEmbeddedPackage) throws IOException, ConverterException;

}
