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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.jcr.contentloader.PathEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Extracts the sling initial content from a bundle to an java.io.InputStream.
 */
public class BundleSlingInitialContentExtractor {

    private static final double THRESHOLD_RATIO = 10;
    private static final int BUFFER = 512;
    private static final long TOOBIG = 0x6400000; // Max size of unzipped data, 100MB

    protected final AssemblerProvider assemblerProvider = new AssemblerProvider();
    protected final ContentReaderProvider contentReaderProvider = new ContentReaderProvider();
    protected final ParentFolderRepoInitHandler parentFolderRepoInitHandler = new ParentFolderRepoInitHandler();

    @SuppressWarnings("java:S5042") // we already addressed this
    @Nullable
    public InputStream extract(@NotNull BundleSlingInitialContentExtractContext context) throws IOException, ConverterException {

        ContentPackage2FeatureModelConverter contentPackage2FeatureModelConverter = context.getConverter();

        if (context.getSlingInitialContentPolicy() == ContentPackage2FeatureModelConverter.SlingInitialContentPolicy.KEEP) {
            return null;
        }
        if (CollectionUtils.isEmpty(context.getPathEntryList())) {
            return null;
        }

        // remove header
        final Manifest manifest = context.getManifest();
        manifest.getMainAttributes().remove(new Attributes.Name(PathEntry.CONTENT_HEADER));
        // change version to have suffix
        Version originalVersion = new Version(Objects.requireNonNull(manifest.getMainAttributes().getValue(Constants.BUNDLE_VERSION)));
        manifest.getMainAttributes().putValue(Constants.BUNDLE_VERSION, getModifiedOsgiVersion(originalVersion).toString());
        Path newBundleFile = Files.createTempFile(contentPackage2FeatureModelConverter.getTempDirectory().toPath(), "newBundle", ".jar");
        String basePath = contentPackage2FeatureModelConverter.getTempDirectory().getPath();

        // create JAR file to prevent extracting it twice and for random access
        try (OutputStream fileOutput = Files.newOutputStream(newBundleFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
             JarOutputStream bundleOutput = new JarOutputStream(fileOutput, manifest)) {

            Set<SlingInitialContentBundleEntryMetaData> collectedSlingInitialContentBundleEntries = new HashSet<>();

            AtomicLong total = new AtomicLong(0);

            final JarFile jarFile = context.getJarFile();
            Enumeration<? extends JarEntry> entries = jarFile.entries();

            // first we collect all the entries into a set, collectedSlingInitialContentBundleEntries.
            // we need it up front to be perform various checks in another loop later.
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();

                if (jarEntry.getName().equals(JarFile.MANIFEST_NAME)) {
                    continue;
                }
                byte[] data = new byte[BUFFER];

                long compressedSize = jarEntry.getCompressedSize();
                if (!jarEntry.isDirectory()) {
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
                            safelyWriteOutputStream(compressedSize, total, data, input, fos, true);

                            SlingInitialContentBundleEntryMetaData bundleEntry = createSlingInitialContentBundleEntry(context, basePath, jarEntry, targetFile);
                            collectedSlingInitialContentBundleEntries.add(bundleEntry);
                        } else {
                            bundleOutput.putNextEntry(jarEntry);
                            safelyWriteOutputStream(compressedSize, total, data, input, bundleOutput, false);
                            IOUtils.copy(input, bundleOutput);
                            bundleOutput.closeEntry();
                        }
                    }
                }

                if (total.get() + BUFFER > TOOBIG) {
                    throw new IllegalStateException("File being unzipped is too big.");
                }

            }

            // now that we got collectedSlingInitialContentBundleEntries ready, we loop it and perform an extract for each entry.
            BundleSlingInitialContentJarEntryExtractor jarEntryExtractor =
                    new BundleSlingInitialContentJarEntryExtractor(assemblerProvider, contentReaderProvider, parentFolderRepoInitHandler);

            for (SlingInitialContentBundleEntryMetaData slingInitialContentBundleEntryMetaData : collectedSlingInitialContentBundleEntries) {
                jarEntryExtractor.extractSlingInitialContent(context, slingInitialContentBundleEntryMetaData, collectedSlingInitialContentBundleEntries);
            }

        }

        // add additional content packages to feature model
        finalizePackageAssembly(context);

        // return stripped bundle's inputstream which must be deleted on close
        return Files.newInputStream(newBundleFile, StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE);
    }

    static Version getModifiedOsgiVersion(@NotNull Version originalVersion) {
        return new Version(originalVersion.getMajor(),
                originalVersion.getMinor(),
                originalVersion.getMicro(),
                originalVersion.getQualifier() + "_" + ContentPackage2FeatureModelConverter.PACKAGE_CLASSIFIER);
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


    public void reset() {
        parentFolderRepoInitHandler.reset();
    }

    public void addRepoInitExtension(@NotNull List<VaultPackageAssembler> assemblers, @NotNull FeaturesManager featureManager) throws IOException, ConverterException {
        parentFolderRepoInitHandler.addRepoinitExtension(assemblers, featureManager);
    }

    protected void finalizePackageAssembly(@NotNull BundleSlingInitialContentExtractContext context) throws IOException, ConverterException {
        for (Map.Entry<PackageType, VaultPackageAssembler> entry : assemblerProvider.getPackageAssemblerEntrySet()) {
            File packageFile = entry.getValue().createPackage();
            ContentPackage2FeatureModelConverter converter = context.getConverter();
            converter.processSubPackage(context.getPath() + "-" + entry.getKey(), context.getRunMode(), converter.open(packageFile), false);
        }
        assemblerProvider.clear();
    }

    private void safelyWriteOutputStream(@NotNull long compressedSize,
                                         @NotNull AtomicLong total,
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


}
