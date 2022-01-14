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
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.shared.CheckedConsumer;
import org.apache.sling.feature.cpconverter.vltpkg.*;
import org.apache.sling.jcr.contentloader.ContentReader;
import org.apache.sling.jcr.contentloader.PathEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.*;

/**
 * Extracts the sling initial content from a bundle to an java.io.InputStream.
 */
public class BundleSlingInitialContentExtractor {

    private static final double THRESHOLD_RATIO = 10;
    private static final int BUFFER = 512;
    private static final long TOOBIG = 0x6400000; // Max size of unzipped data, 100MB
    private static final Logger logger = LoggerFactory.getLogger(BundleSlingInitialContentExtractor.class);


    protected final AssemblerProvider assemblerProvider = new AssemblerProvider();
    protected final ContentReaderProvider contentReaderProvider = new ContentReaderProvider();
    protected final ParentFolderRepoInitHandler parentFolderRepoInitHandler = new ParentFolderRepoInitHandler();
    
    private CheckedConsumer<String> repoInitTextExtensionConsumer;
 
    
    static Version getModifiedOsgiVersion(Version originalVersion) {
        return new Version(originalVersion.getMajor(), originalVersion.getMinor(), originalVersion.getMicro(), originalVersion.getQualifier() + "_" + ContentPackage2FeatureModelConverter.PACKAGE_CLASSIFIER);
    }

    @SuppressWarnings("java:S5042") // we already addressed this
    @Nullable public InputStream extract(BundleSlingInitialContentExtractorContext context) throws IOException, ConverterException {

        ContentPackage2FeatureModelConverter contentPackage2FeatureModelConverter = context.getConverter();
        repoInitTextExtensionConsumer = (String repoInitText) -> {
            contentPackage2FeatureModelConverter.getAclManager().addRepoinitExtention("content-package", repoInitText, null, contentPackage2FeatureModelConverter.getFeaturesManager());
        };
        
        if (context.getSlingInitialContentPolicy() == ContentPackage2FeatureModelConverter.SlingInitialContentPolicy.KEEP) {
            return null;
        }
        if(CollectionUtils.isEmpty(context.getPathEntryList())){
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

            Set<SlingInitialContentBundleEntry> collectedSlingInitialContentBundleEntries = new HashSet<>();
            
            AtomicLong total = new AtomicLong(0);

            final JarFile jarFile = context.getJarFile();
            Enumeration<? extends JarEntry> entries = jarFile.entries();
            
            while(entries.hasMoreElements()){
                JarEntry jarEntry = entries.nextElement();

                if (jarEntry.getName().equals(JarFile.MANIFEST_NAME)) {
                    continue;
                }
                byte[] data = new byte[BUFFER];
                
                long compressedSize = jarEntry.getCompressedSize();
                if (!jarEntry.isDirectory()) {
                    try (InputStream input = new BufferedInputStream(jarFile.getInputStream(jarEntry))) {
                        if (containsSlingInitialContent(context, jarEntry)) {

                            
                            File targetFile = new File(contentPackage2FeatureModelConverter.getTempDirectory(), jarEntry.getName());
                            String canonicalDestinationPath = targetFile.getCanonicalPath();

                            if (!canonicalDestinationPath.startsWith(contentPackage2FeatureModelConverter.getTempDirectory().getCanonicalPath())) {
                                throw new IOException("Entry is outside of the target directory");
                            }

                            targetFile.getParentFile().mkdirs();
                            targetFile.createNewFile();
                           
                            FileOutputStream fos = new FileOutputStream(targetFile);
                            safelyWriteOutputStream(compressedSize, total, data, input, fos, true);
                            
                            final String entryName = StringUtils.substringAfter( targetFile.getPath(),basePath + "/");
                            final PathEntry pathEntryValue = context.getPathEntryList().stream().filter(p -> entryName.startsWith( p.getPath())).findFirst().orElseThrow(NullPointerException::new);
                            final String target = pathEntryValue.getTarget();
                            // https://sling.apache.org/documentation/bundles/content-loading-jcr-contentloader.html#file-name-escaping
                            String repositoryPath = (target != null ? target : "/") + URLDecoder.decode(entryName.substring(pathEntryValue.getPath().length()), "UTF-8");
                            SlingInitialContentBundleEntry bundleEntry = new SlingInitialContentBundleEntry(jarEntry, targetFile, pathEntryValue, repositoryPath);
                            
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
           
     
  
            for(SlingInitialContentBundleEntry slingInitialContentBundleEntry : collectedSlingInitialContentBundleEntries){
                extractSlingInitialContentForJarEntry(context, slingInitialContentBundleEntry, collectedSlingInitialContentBundleEntries, basePath);
            }
      
        }
        
        // add additional content packages to feature model
        finalizePackageAssembly(context);

        // return stripped bundle's inputstream which must be deleted on close
        return Files.newInputStream(newBundleFile, StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE);
    }

    

    private void safelyWriteOutputStream(long compressedSize, AtomicLong total, byte[] data, InputStream input, OutputStream fos, boolean shouldClose) throws IOException {
        int count;
        BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER);
        while (total.get() + BUFFER <= TOOBIG && (count = input.read(data, 0, BUFFER)) != -1) {
            dest.write(data, 0, count);
            total.addAndGet(count);

            double compressionRatio = (double) count / compressedSize;
            if(compressionRatio > THRESHOLD_RATIO) {
                // ratio between compressed and uncompressed data is highly suspicious, looks like a Zip Bomb Attack
                break;
            }
        }
        dest.flush();
        
        if(shouldClose){
            dest.close();
        }
        
    }

    
    public void reset() {
        parentFolderRepoInitHandler.reset();
    }

    public void addRepoinitExtension(List<VaultPackageAssembler> assemblers, FeaturesManager featureManager) throws IOException, ConverterException {
        parentFolderRepoInitHandler.addRepoinitExtension(assemblers, featureManager);
    }

    protected void finalizePackageAssembly(BundleSlingInitialContentExtractorContext context) throws IOException, ConverterException {
        for (Map.Entry<PackageType, VaultPackageAssembler> entry : assemblerProvider.getPackageAssemblerEntrySet()) {
            File packageFile = entry.getValue().createPackage();
            ContentPackage2FeatureModelConverter converter = context.getConverter();
            converter.processSubPackage(context.getPath() + "-" + entry.getKey(), context.getRunMode(), converter.open(packageFile), false);
        }
        assemblerProvider.clear();
    }

    /**
     * Returns whether the jarEntry is initial content
     * @param jarEntry
     * @return
     */
    boolean containsSlingInitialContent( @NotNull BundleSlingInitialContentExtractorContext context, @NotNull JarEntry jarEntry){
        final String entryName = jarEntry.getName();
        return  context.getPathEntryList().stream().anyMatch(p -> entryName.startsWith(p.getPath()));
    }

    /**
     *
     * @return {@code true} in case the given entry was part of the initial content otherwise {@code false}
     * @throws Exception
     */
    void extractSlingInitialContentForJarEntry(@NotNull BundleSlingInitialContentExtractorContext context, @NotNull SlingInitialContentBundleEntry slingInitialContentBundleEntry, @NotNull  Set<SlingInitialContentBundleEntry> collectedSlingInitialContentBundleEntries, @NotNull String basePath) throws IOException, ConverterException {

        String repositoryPath = slingInitialContentBundleEntry.getRepositoryPath();
        File file = slingInitialContentBundleEntry.getTargetFile();
        PathEntry pathEntryValue = slingInitialContentBundleEntry.getPathEntry();
        // all entry paths used by entry handlers start with "/"
        String contentPackageEntryPath = "/" + org.apache.jackrabbit.vault.util.Constants.ROOT_DIR + PlatformNameFormat.getPlatformPath(repositoryPath);

        Path tmpDocViewInputFile = null;

        try(InputStream bundleFileInputStream = new FileInputStream(file)) {
            VaultPackageAssembler packageAssembler = assemblerProvider.initPackageAssemblerForPath(context, repositoryPath, pathEntryValue);

            final ContentReader contentReader = contentReaderProvider.getContentReaderForEntry(file, pathEntryValue);
            if (contentReader != null) {

                // convert to docview xml
                tmpDocViewInputFile = Files.createTempFile(context.getConverter().getTempDirectory().toPath(), "docview", ".xml");
                try (OutputStream docViewOutput = Files.newOutputStream(tmpDocViewInputFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

                    repositoryPath = FilenameUtils.removeExtension(repositoryPath);
                    boolean isFileDescriptor = isFileDescriptor(collectedSlingInitialContentBundleEntries, contentPackageEntryPath);
                    VaultContentXMLContentCreator contentCreator = new VaultContentXMLContentCreator(StringUtils.substringBeforeLast(repositoryPath, "/"), docViewOutput, context.getNamespaceRegistry(), packageAssembler, repoInitTextExtensionConsumer, isFileDescriptor);


                    if(file.getName().endsWith(".xml")){
                        contentCreator.setIsXmlProcessed();
                    }

                    contentReader.parse(file.toURI().toURL(), contentCreator);
                    contentPackageEntryPath =  new ContentPackageEntryPathComputer(collectedSlingInitialContentBundleEntries, contentPackageEntryPath, contentCreator).compute();
                    contentCreator.finish();

                } catch (IOException | XMLStreamException e) {
                    throw new IOException("Can not parse " + file, e);
                } catch (DocViewSerializerContentHandlerException | RepositoryException e) {
                    throw new IOException("Can not convert " + file + " to enhanced DocView format", e);
                }

                // remap CND files to make sure they are picked up by NodeTypesEntryHandler;
                if (context.getNamespaceRegistry().getRegisteredCndSystemIds().contains(file.getName())) {
                    contentPackageEntryPath = "/META-INF/vault/" + Text.getName(file.getName()) + ".cnd";
                }


            }

            try (Archive virtualArchive = SingleFileArchive.fromPathOrInputStream(tmpDocViewInputFile, bundleFileInputStream,
                    () -> Files.createTempFile(context.getConverter().getTempDirectory().toPath(), "initial-content", Text.getName(file.getName())), contentPackageEntryPath)) {
                // in which content package should this end up?

                if (tmpDocViewInputFile != null) {
                    packageAssembler.addEntry(contentPackageEntryPath, tmpDocViewInputFile.toFile());
                } else {
                    packageAssembler.addEntry(contentPackageEntryPath, bundleFileInputStream);
                }
                parentFolderRepoInitHandler.addParentsForPath(contentPackageEntryPath);
            }

        } finally {
            if (tmpDocViewInputFile != null) {
                Files.delete(tmpDocViewInputFile);
            }
        }
    }

    @NotNull
    private boolean isFileDescriptor(@NotNull Set<SlingInitialContentBundleEntry> bundleEntries, final String contentPackageEntryPath) {

        //sometimes we are dealing with double extensions (.json.xml)
        String recomputedContentPackageEntryPath = FilenameUtils.removeExtension(contentPackageEntryPath);

        final String checkIfRecomputedPathCandidate = StringUtils.removeStart(recomputedContentPackageEntryPath, "/jcr_root");
        return bundleEntries.stream().anyMatch(bundleEntry -> StringUtils.equals(checkIfRecomputedPathCandidate,bundleEntry.getRepositoryPath()));

    }

    
}
