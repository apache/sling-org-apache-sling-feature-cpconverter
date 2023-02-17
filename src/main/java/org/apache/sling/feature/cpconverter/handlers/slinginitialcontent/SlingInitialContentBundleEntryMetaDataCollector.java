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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import static org.apache.jackrabbit.vault.util.Constants.DOT_CONTENT_XML;

/**
 * Handles collecting the metadata for each sling initial content entry, to be used for extraction in another loop
 */
class SlingInitialContentBundleEntryMetaDataCollector {

    private static final double THRESHOLD_RATIO = 10;
    private static final int BUFFER = 512;
    private static final long TOOBIG = 0x6400000; // Max size of unzipped data, 100MB
    static final ClassLoader CL = SlingInitialContentBundleEntryMetaDataCollector.class.getClassLoader();

    private final BundleSlingInitialContentExtractContext context;
    private final String basePath;
    private final ContentPackage2FeatureModelConverter contentPackage2FeatureModelConverter;
    private final Path newBundleFile;
    private final NavigableSet<SlingInitialContentBundleEntryMetaData> collectedSlingInitialContentBundleEntries = new ConcurrentSkipListSet<>();
    private final NavigableSet<SlingInitialContentBundleEntryMetaData> defaultContentXmlEntries = new ConcurrentSkipListSet<>();
    private final AtomicLong total = new AtomicLong(0);
    private final JarFile jarFile;
    
    private final File defaultContentXmlFile;
    
    SlingInitialContentBundleEntryMetaDataCollector(@NotNull BundleSlingInitialContentExtractContext context,
                                                    @NotNull ContentPackage2FeatureModelConverter contentPackage2FeatureModelConverter,
                                                    @NotNull Path newBundleFile) {
        this.context = context;
        this.basePath = contentPackage2FeatureModelConverter.getTempDirectory().getPath();
        this.contentPackage2FeatureModelConverter = contentPackage2FeatureModelConverter;
        this.newBundleFile = newBundleFile;
        this.jarFile = context.getJarFile();
        
        this.defaultContentXmlFile = new File(this.basePath, "defaultContentXml.xml");
        writeDefaultXMLFile();
    }

    private void writeDefaultXMLFile() {
        try(OutputStream outputStream = new FileOutputStream(defaultContentXmlFile)){
            IOUtils.copy(Objects.requireNonNull(CL.getResourceAsStream("default-content.xml")), outputStream);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Default Content XML file not found in classpath!", e);
        } catch (IOException e) {
            throw new RuntimeException("Could not prepare the default content XML file for sling initial content!", e);
        }
        
    }

    /**
     * Collects all the MetaData from the context into a set
     *
     * @return
     * @throws IOException
     */
    @SuppressWarnings("java:S5042") // we already addressed this
    @NotNull
    Set<SlingInitialContentBundleEntryMetaData> collectFromContextAndWriteTmpFiles() throws IOException {

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
    
    Set<SlingInitialContentBundleEntryMetaData> getDefaultContentXmlEntries() throws UnsupportedEncodingException {
        
        createDefaultContentXMLFiles();
        return defaultContentXmlEntries;
        
    }

    private void createDefaultContentXMLFiles() throws UnsupportedEncodingException {
        
        for(String repositoryPath: collectedSlingInitialContentBundleEntries.stream()
                .map(SlingInitialContentBundleEntryMetaData::getRepositoryPath)
                .collect(Collectors.toList())){
            addDefaultSlingContentXMLAndParents(repositoryPath);
        }
    }

    private void addDefaultSlingContentXMLAndParents(String repositoryPath) throws UnsupportedEncodingException {

        if(StringUtils.countMatches(repositoryPath, '/') < 2){
            //we don't add the primary type of level 1 and 2.
            return;
        }
        
        String parentPath = StringUtils.substringBeforeLast(repositoryPath, "/");
        //if we already got an entry with the path defined, we skip ahead.
        if( !alreadyFound(collectedSlingInitialContentBundleEntries, parentPath) &&
            !alreadyFound(defaultContentXmlEntries, parentPath)
           ){
            final PathEntry pathEntryValue = getPathEntryFromRepositoryPath(repositoryPath);
            if(pathEntryValue != null){
                defaultContentXmlEntries.add(new SlingInitialContentBundleEntryMetaData(defaultContentXmlFile, pathEntryValue, parentPath + "/" + DOT_CONTENT_XML));
            }
         
        }
        addDefaultSlingContentXMLAndParents(parentPath);
        
    }
    
    private boolean alreadyFound(Set<SlingInitialContentBundleEntryMetaData> set, String parentPath){
        return set.stream()
                .map(SlingInitialContentBundleEntryMetaData::getRepositoryPath)
                .anyMatch(parentPath::equals);
    }
    
    private void extractFile(JarEntry jarEntry, JarOutputStream bundleOutput) throws IOException {

        byte[] data = new byte[BUFFER];
        long compressedSize = jarEntry.getCompressedSize();

        try (InputStream input = new BufferedInputStream(jarFile.getInputStream(jarEntry))) {
            if (jarEntryIsSlingInitialContent(context, jarEntry)) {

                File targetFile = new File(contentPackage2FeatureModelConverter.getTempDirectory(), jarEntry.getName().replace('/', File.separatorChar));
                String canonicalDestinationPath = targetFile.getCanonicalPath();


                if (!checkIfPathStartsWithOrIsEqual(contentPackage2FeatureModelConverter.getTempDirectory().getCanonicalPath(), canonicalDestinationPath)) {
                    throw new IOException("Entry is outside of the target directory " + canonicalDestinationPath);
                }

                targetFile.getParentFile().mkdirs();
                if (!targetFile.exists() && !targetFile.createNewFile()) {
                    throw new IOException("Could not create placeholder file!");
                }

                FileOutputStream fos = new FileOutputStream(targetFile);
                safelyWriteOutputStream(compressedSize, data, input, fos, true);

                SlingInitialContentBundleEntryMetaData bundleEntry = createSlingInitialContentBundleEntry(context, targetFile);
                collectedSlingInitialContentBundleEntries.add(bundleEntry);
            } else {
                //write 'normal' content out to the normal bundle output
                bundleOutput.putNextEntry(jarEntry);
                safelyWriteOutputStream(compressedSize, data, input, bundleOutput, false);
                IOUtils.copy(input, bundleOutput);
                bundleOutput.closeEntry();
            }
        }
    }

    private void safelyWriteOutputStream(long compressedSize,
                                         byte[] data,
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

    private boolean jarEntryIsSlingInitialContent(@NotNull BundleSlingInitialContentExtractContext context, @NotNull JarEntry jarEntry) {
        final String entryName = jarEntry.getName();
        return context.getPathEntryList().stream().anyMatch(
                pathEntry -> checkIfPathStartsWithOrIsEqual(pathEntry.getPath(), entryName)
        );
    }

    @NotNull
    private SlingInitialContentBundleEntryMetaData createSlingInitialContentBundleEntry(@NotNull BundleSlingInitialContentExtractContext context,
                                                                                        @NotNull File targetFile) throws UnsupportedEncodingException {
        final String entryName = StringUtils.substringAfter(targetFile.getPath(), basePath + "/");
        final PathEntry pathEntryValue = context.getPathEntryList().stream().filter(
                pathEntry -> checkIfPathStartsWithOrIsEqual(pathEntry.getPath(), entryName)
        ).findFirst().orElseThrow(NullPointerException::new);
        final String target = pathEntryValue.getTarget();
        // https://sling.apache.org/documentation/bundles/content-loading-jcr-contentloader.html#file-name-escaping
        String repositoryPath = (target != null ? target : "/") + URLDecoder.decode(entryName.substring(pathEntryValue.getPath().length()), "UTF-8");
        return new SlingInitialContentBundleEntryMetaData(targetFile, pathEntryValue, repositoryPath);
    }
    

    private PathEntry getPathEntryFromRepositoryPath(@NotNull String repositoryPath) {

        if(StringUtils.countMatches(repositoryPath, '/') < 2){
            //we don't add the primary type of level 1 and 2.
            return null;
        }
        
        Optional<PathEntry> pathEntryOptional = context.getPathEntryList().stream().filter(
                pathEntry -> checkIfPathStartsWithOrIsEqual(pathEntry.getTarget(), repositoryPath)
        ).findFirst();

        return pathEntryOptional.orElse(null);
    }


    private static boolean checkIfPathStartsWithOrIsEqual(String pathA, String pathB) {
        String fixedPath = pathA;
        if (!fixedPath.endsWith(File.separator)) {
            fixedPath = pathA + File.separatorChar;
        }
        return pathB.startsWith(fixedPath) || pathB.equals(pathA);
    }
}
