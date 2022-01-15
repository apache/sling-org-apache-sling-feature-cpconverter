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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.jcr.contentloader.PathEntry;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Handles collecting the metadata for each sling initial content entry, to be used for extraction in another loop
 */
class SlingInitialContentBundleEntryMetaDataCollector {

    private static final double THRESHOLD_RATIO = 10;
    private static final int BUFFER = 512;
    private static final long TOOBIG = 0x6400000; // Max size of unzipped data, 100MB

    private final BundleSlingInitialContentExtractContext context;
    private final String basePath;
    private final ContentPackage2FeatureModelConverter contentPackage2FeatureModelConverter;
    private final Path newBundleFile;
    private final Set<SlingInitialContentBundleEntryMetaData> collectedSlingInitialContentBundleEntries = new HashSet<>();
    private final AtomicLong total = new AtomicLong(0);
    private final JarFile jarFile;

    SlingInitialContentBundleEntryMetaDataCollector(@NotNull BundleSlingInitialContentExtractContext context,
                                                    @NotNull String basePath,
                                                    @NotNull ContentPackage2FeatureModelConverter contentPackage2FeatureModelConverter,
                                                    @NotNull Path newBundleFile) {
        this.context = context;
        this.basePath = basePath;
        this.contentPackage2FeatureModelConverter = contentPackage2FeatureModelConverter;
        this.newBundleFile = newBundleFile;
        this.jarFile = context.getJarFile();
    }

    @NotNull
    Set<SlingInitialContentBundleEntryMetaData> collectFromContext() throws IOException {

        final Manifest manifest = context.getManifest();

        // create JAR file to prevent extracting it twice and for random access
        try (OutputStream fileOutput = Files.newOutputStream(newBundleFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
             JarOutputStream bundleOutput = new JarOutputStream(fileOutput, manifest)) {

            Enumeration<? extends JarEntry> entries = jarFile.entries();

            // first we collect all the entries into a set, collectedSlingInitialContentBundleEntries.
            // we need it up front to be perform various checks in another loop later.
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();

                if (jarEntry.getName().equals(JarFile.MANIFEST_NAME)) {
                    continue;
                }

                if (!jarEntry.isDirectory()) {
                    extractFile(jarEntry, bundleOutput);
                }

                if (total.get() + BUFFER > TOOBIG) {
                    throw new IllegalStateException("File being unzipped is too big.");
                }
            }
        }

        return collectedSlingInitialContentBundleEntries;
    }

    private void extractFile(JarEntry jarEntry, JarOutputStream bundleOutput) throws IOException {

        byte[] data = new byte[BUFFER];
        long compressedSize = jarEntry.getCompressedSize();

        try (InputStream input = new BufferedInputStream(jarFile.getInputStream(jarEntry))) {
            if (jarEntryContainsSlingInitialContent(context, jarEntry)) {

                File targetFile = new File(contentPackage2FeatureModelConverter.getTempDirectory(), jarEntry.getName());
                String canonicalDestinationPath = targetFile.getCanonicalPath();

                if (!canonicalDestinationPath.startsWith(contentPackage2FeatureModelConverter.getTempDirectory().getCanonicalPath())) {
                    throw new IOException("Entry is outside of the target directory");
                }

                targetFile.getParentFile().mkdirs();
                if (!targetFile.exists() && !targetFile.createNewFile()) {
                    throw new IOException("Could not create placeholder file!");
                }

                FileOutputStream fos = new FileOutputStream(targetFile);
                safelyWriteOutputStream(compressedSize, data, input, fos, true);

                SlingInitialContentBundleEntryMetaData bundleEntry = createSlingInitialContentBundleEntry(context, basePath, jarEntry, targetFile);
                collectedSlingInitialContentBundleEntries.add(bundleEntry);
            } else {
                bundleOutput.putNextEntry(jarEntry);
                safelyWriteOutputStream(compressedSize, data, input, bundleOutput, false);
                IOUtils.copy(input, bundleOutput);
                bundleOutput.closeEntry();
            }
        }
    }

    private void safelyWriteOutputStream(@NotNull long compressedSize,
                                         @NotNull byte[] data,
                                         @NotNull InputStream input,
                                         @NotNull OutputStream fos,
                                         boolean shouldClose) throws IOException {
        int count;
        BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER);
        while (total.get() + BUFFER <= TOOBIG && (count = input.read(data, 0, BUFFER)) != -1) {
            dest.write(data, 0, count);
            total.addAndGet(count);

            double compressionRatio = (double) count / compressedSize;
            if (compressionRatio > THRESHOLD_RATIO) {
                // ratio between compressed and uncompressed data is highly suspicious, looks like a Zip Bomb Attack
                break;
            }
        }
        dest.flush();

        if (shouldClose) {
            dest.close();
        }

    }

    private boolean jarEntryContainsSlingInitialContent(@NotNull BundleSlingInitialContentExtractContext context, @NotNull JarEntry jarEntry) {
        final String entryName = jarEntry.getName();
        return context.getPathEntryList().stream().anyMatch(p -> entryName.startsWith(p.getPath()));
    }

    @NotNull
    private SlingInitialContentBundleEntryMetaData createSlingInitialContentBundleEntry(@NotNull BundleSlingInitialContentExtractContext context,
                                                                                        @NotNull String basePath,
                                                                                        @NotNull JarEntry jarEntry,
                                                                                        @NotNull File targetFile) throws UnsupportedEncodingException {
        final String entryName = StringUtils.substringAfter(targetFile.getPath(), basePath + "/");
        final PathEntry pathEntryValue = context.getPathEntryList().stream().filter(p -> entryName.startsWith(p.getPath())).findFirst().orElseThrow(NullPointerException::new);
        final String target = pathEntryValue.getTarget();
        // https://sling.apache.org/documentation/bundles/content-loading-jcr-contentloader.html#file-name-escaping
        String repositoryPath = (target != null ? target : "/") + URLDecoder.decode(entryName.substring(pathEntryValue.getPath().length()), "UTF-8");
        return new SlingInitialContentBundleEntryMetaData(targetFile, pathEntryValue, repositoryPath);
    }

}
