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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.readers.JsonReader;
import org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.readers.XMLReader;
import org.apache.sling.feature.cpconverter.shared.CheckedConsumer;
import org.apache.sling.feature.cpconverter.vltpkg.*;
import org.apache.sling.jcr.contentloader.ContentReader;
import org.apache.sling.jcr.contentloader.PathEntry;
import org.apache.sling.jcr.contentloader.internal.readers.ZipReader;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.*;

/**
 * Extracts the sling initial content from a bundle to an java.io.InputStream.
 */
public class BundleSlingInitialContentExtractor {

    private static final double THRESHOLD_RATIO = 10;
    private static final int BUFFER = 512;
    private static final long TOOBIG = 0x6400000; // Max size of unzipped data, 100MB
    private static final int TOOMANY = 1024;      // Max number of files
    private static final Logger logger = LoggerFactory.getLogger(BundleSlingInitialContentExtractor.class);
    
    private final ContentPackage2FeatureModelConverter.SlingInitialContentPolicy slingInitialContentPolicy;
    private final String path;
    private final Map<PackageType, VaultPackageAssembler> packageAssemblers = new EnumMap<>(PackageType.class);

    private final ArtifactId bundleArtifactId;
    private final JarFile jarFile;
    private final ContentPackage2FeatureModelConverter converter;
    private final String runMode;
    private final boolean isEmbeddedPackage;
    private final JcrNamespaceRegistry namespaceRegistry;
    private final Manifest manifest;
    private final CheckedConsumer<String> repoInitTextExtensionConsumer;
    private Collection<PathEntry> pathEntryList = new ArrayList<>();
    private Iterator<PathEntry> pathEntries;

    public BundleSlingInitialContentExtractor(ContentPackage2FeatureModelConverter.SlingInitialContentPolicy slingInitialContentPolicy, @NotNull String path, @NotNull ArtifactId bundleArtifactId, @NotNull JarFile jarFile, @NotNull ContentPackage2FeatureModelConverter converter, @Nullable String runMode) throws IOException {
        this(slingInitialContentPolicy, path, bundleArtifactId, jarFile, converter, runMode, false);
    }
    
    public BundleSlingInitialContentExtractor(ContentPackage2FeatureModelConverter.SlingInitialContentPolicy slingInitialContentPolicy, @NotNull String path, @NotNull ArtifactId bundleArtifactId, @NotNull JarFile jarFile, @NotNull ContentPackage2FeatureModelConverter converter, @Nullable String runMode, boolean isEmbeddedPackage) throws IOException {
        this.slingInitialContentPolicy = slingInitialContentPolicy;
        this.path = path;

        this.bundleArtifactId = bundleArtifactId;
        this.jarFile = jarFile;
        this.converter = converter;
        this.runMode = runMode;
        this.isEmbeddedPackage = isEmbeddedPackage;
        this.manifest = Objects.requireNonNull(jarFile.getManifest());
        this.namespaceRegistry = new JcrNamespaceRegistryProvider(manifest, jarFile, converter.getFeaturesManager().getNamespaceUriByPrefix()).provideRegistryFromBundle();

        pathEntries = PathEntry.getContentPaths(manifest, -1);
        
        if(pathEntries != null){
            pathEntries.forEachRemaining(pathEntryList::add);
        }

        repoInitTextExtensionConsumer = (String repoInitText) -> {
            converter.getAclManager().addRepoinitExtention("content-package", repoInitText, null, converter.getFeaturesManager());
        };
    }
    
    static Version getModifiedOsgiVersion(Version originalVersion) {
        return new Version(originalVersion.getMajor(), originalVersion.getMinor(), originalVersion.getMicro(), originalVersion.getQualifier() + "_" + ContentPackage2FeatureModelConverter.PACKAGE_CLASSIFIER);
    }

    @SuppressWarnings("S5042") // we already addressed this
    @Nullable public InputStream extract() throws IOException, ConverterException {

        if (slingInitialContentPolicy == ContentPackage2FeatureModelConverter.SlingInitialContentPolicy.KEEP) {
            return null;
        }
        if(CollectionUtils.isEmpty(pathEntryList)){
            return null;
        }
        
        // remove header
        manifest.getMainAttributes().remove(new Attributes.Name(PathEntry.CONTENT_HEADER));
        // change version to have suffix
        Version originalVersion = new Version(Objects.requireNonNull(manifest.getMainAttributes().getValue(Constants.BUNDLE_VERSION)));
        manifest.getMainAttributes().putValue(Constants.BUNDLE_VERSION, getModifiedOsgiVersion(originalVersion).toString());
        Path newBundleFile = Files.createTempFile(converter.getTempDirectory().toPath(), "newBundle", ".jar");

        // create JAR file to prevent extracting it twice and for random access
        try (OutputStream fileOutput = Files.newOutputStream(newBundleFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
             JarOutputStream bundleOutput = new JarOutputStream(fileOutput, manifest)) {

            List<File> collectedFilesWithSlingInitialContent = new ArrayList<>();

            int entryCount = 0;
            AtomicLong total = new AtomicLong(0);
          
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
                        if (containsSlingInitialContent(jarEntry)) {

                            
                            
                            File targetFile = new File(converter.getTempDirectory(), jarEntry.getName());
                            String canonicalDestinationPath = targetFile.getCanonicalPath();

                            if (!canonicalDestinationPath.startsWith(converter.getTempDirectory().getCanonicalPath())) {
                                throw new IOException("Entry is outside of the target directory");
                            }
                            
                            targetFile.getParentFile().mkdirs();
                            
                            if(targetFile.createNewFile()){
                                FileOutputStream fos = new FileOutputStream(targetFile);
                                safelyWriteOutputStream(compressedSize, total, data, input, fos, true);

                                collectedFilesWithSlingInitialContent.add(targetFile);
                            }else{
                                throw new IOException("could not create temporary file " + targetFile.getAbsolutePath());
                            }

                        } else {
                            bundleOutput.putNextEntry(jarEntry);
                            safelyWriteOutputStream(compressedSize, total, data, input, bundleOutput, false);
                            IOUtils.copy(input, bundleOutput);
                            bundleOutput.closeEntry();
                        }
                    }
                }

                if (entryCount > TOOMANY) {
                    throw new IllegalStateException("Too many files to unzip.");
                }
                if (total.get() + BUFFER > TOOBIG) {
                    throw new IllegalStateException("File being unzipped is too big.");
                }
                entryCount++;

            }

            for(File file : collectedFilesWithSlingInitialContent){
                extractSlingInitialContentForJarEntry(file,converter.getTempDirectory().getPath());
            }
            
            
      
        }
        // add additional content packages to feature model
        finalizePackageAssembly(path, runMode);

        // return stripped bundle's inputstream which must be deleted on close
        return Files.newInputStream(newBundleFile, StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE);
    }

    private void safelyWriteOutputStream(long compressedSize, AtomicLong total, byte[] data, InputStream input, OutputStream fos, boolean shouldClose) throws IOException {
        int count;
        BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER);
        while (total.get() + BUFFER <= TOOBIG && (count = input.read(data, 0, BUFFER)) != -1) {
            dest.write(data, 0, count);
            total.addAndGet(count);

            double compressionRatio = count / compressedSize;
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

    /**
     * Returns whether the jarEntry is initial content
     * @param jarEntry
     * @return
     */
    boolean containsSlingInitialContent(@NotNull JarEntry jarEntry){
        final String entryName = jarEntry.getName();
        return  pathEntryList.stream().anyMatch(p -> entryName.startsWith(p.getPath()));
    }
    
    /**
     *
     * @param file
     * @return {@code true} in case the given entry was part of the initial content otherwise {@code false}
     * @throws Exception
     */
    void extractSlingInitialContentForJarEntry(@NotNull File file, String basePath) throws IOException, ConverterException {
        final String entryName = StringUtils.substringAfter( file.getPath(),basePath + "/");
        
        final PathEntry pathEntryValue = pathEntryList.stream().filter(p -> entryName.startsWith( p.getPath())).findFirst().orElseThrow(NullPointerException::new);
        final String target = pathEntryValue.getTarget();
        // https://sling.apache.org/documentation/bundles/content-loading-jcr-contentloader.html#file-name-escaping

    
        String repositoryPath = (target != null ? target : "/") + URLDecoder.decode(entryName.substring(pathEntryValue.getPath().length()), "UTF-8");
   
        // all entry paths used by entry handlers start with "/"
        String contentPackageEntryPath = "/" + org.apache.jackrabbit.vault.util.Constants.ROOT_DIR + PlatformNameFormat.getPlatformPath(repositoryPath);

        Path tmpDocViewInputFile = null;
        
        try(InputStream bundleFileInputStream = new FileInputStream(file)) {
            VaultPackageAssembler packageAssembler = initPackageAssemblerForPath(repositoryPath, pathEntryValue);

            VaultContentXMLContentCreator contentCreator = null;
            
            final ContentReader contentReader = getContentReaderForEntry(file, pathEntryValue);
            if (contentReader != null) {
                // convert to docview xml
                tmpDocViewInputFile = Files.createTempFile(converter.getTempDirectory().toPath(), "docview", ".xml");
                try (OutputStream docViewOutput = Files.newOutputStream(tmpDocViewInputFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

                    repositoryPath = FilenameUtils.removeExtension(repositoryPath);
                    contentCreator = new VaultContentXMLContentCreator(StringUtils.substringBeforeLast(repositoryPath, "/"), docViewOutput, namespaceRegistry, packageAssembler, repoInitTextExtensionConsumer);
                  
                    if(file.getName().endsWith(".xml")){
                        contentCreator.setIsXmlProcessed();
                    }

                    contentReader.parse(file.toURI().toURL(), contentCreator);
                    contentPackageEntryPath = recomputeContentPackageEntryPath(contentPackageEntryPath, contentCreator);
                    
                    contentCreator.finish();

                } catch (IOException | XMLStreamException e) {
                    throw new IOException("Can not parse " + file, e);
                } catch (DocViewSerializerContentHandlerException | RepositoryException e) {
                    throw new IOException("Can not convert " + file + " to enhanced DocView format", e);
                }

                // remap CND files to make sure they are picked up by NodeTypesEntryHandler;
                if (namespaceRegistry.getRegisteredCndSystemIds().contains(file.getName())) {
                    contentPackageEntryPath = "/META-INF/vault/" + Text.getName(file.getName()) + ".cnd";
                }
                
            }

            try (Archive virtualArchive = SingleFileArchive.fromPathOrInputStream(tmpDocViewInputFile, bundleFileInputStream,
                    () -> Files.createTempFile(converter.getTempDirectory().toPath(), "initial-content", Text.getName(file.getName())), contentPackageEntryPath)) {
                // in which content package should this end up?

                if (tmpDocViewInputFile != null) {
                    packageAssembler.addEntry(contentPackageEntryPath, tmpDocViewInputFile.toFile());
                } else {
                    packageAssembler.addEntry(contentPackageEntryPath, bundleFileInputStream);
                }
            }
 
        } finally {
            if (tmpDocViewInputFile != null) {
                Files.delete(tmpDocViewInputFile);
            }
        }
    }

    @NotNull
    private String recomputeContentPackageEntryPath(String contentPackageEntryPath, VaultContentXMLContentCreator contentCreator) {
        contentPackageEntryPath = FilenameUtils.removeExtension(contentPackageEntryPath);

        if(StringUtils.isNotBlank(contentCreator.getPrimaryNodeName())){
            //custom node name
            contentPackageEntryPath = StringUtils.substringBeforeLast(contentPackageEntryPath, "/") ;
            contentPackageEntryPath = contentPackageEntryPath + "/" + contentCreator.getPrimaryNodeName();
            
        }
        contentPackageEntryPath = contentPackageEntryPath + "/.content.xml";
        return contentPackageEntryPath;
    }


    /**
     * Lazily initializes the cache with the necessary VaultPackageAssemblers
     * @param repositoryPath
     * @return the VaultPackageAssembler from the cache to use for the given repository path
     */
    public VaultPackageAssembler initPackageAssemblerForPath( @NotNull String repositoryPath, @NotNull PathEntry pathEntry)
            throws ConverterException {
        PackageType packageType = VaultPackageUtils.detectPackageType(repositoryPath);
        VaultPackageAssembler assembler = packageAssemblers.get(packageType);
        if (assembler == null) {
            final String packageNameSuffix;
            switch (packageType) {
                case APPLICATION:
                    packageNameSuffix = "-apps";
                    break;
                case CONTENT:
                    packageNameSuffix = "-content";
                    break;
                default:
                    throw new ConverterException("Unexpected package type " + packageType + " detected for path " + repositoryPath);
            }
            final PackageId packageId = new PackageId(bundleArtifactId.getGroupId(), bundleArtifactId.getArtifactId()+packageNameSuffix, bundleArtifactId.getVersion());
            assembler = VaultPackageAssembler.create(converter.getTempDirectory(), packageId, "Generated out of Sling Initial Content from bundle " + bundleArtifactId + " by cp2fm");
            packageAssemblers.put(packageType, assembler);
            logger.info("Created package {} out of Sling-Initial-Content from '{}'", packageId, bundleArtifactId);
        }
        DefaultWorkspaceFilter filter = assembler.getFilter();
        if (!filter.covers(repositoryPath)) {
            PathFilterSet pathFilterSet = new PathFilterSet(pathEntry.getTarget() != null ? pathEntry.getTarget() : "/");
            ImportMode importMode;
            if (pathEntry.isOverwrite()) {
                importMode = ImportMode.REPLACE;
            } else {
                importMode = ImportMode.MERGE;
            }
            // TODO: add handling for merge, mergeProperties and overwriteProperties (https://issues.apache.org/jira/browse/SLING-10318)
            pathFilterSet.setImportMode(importMode);
            filter.add(pathFilterSet);
        }
        return assembler;
    }

    void finalizePackageAssembly(@NotNull String path, @Nullable String runMode) throws IOException, ConverterException {
        for (java.util.Map.Entry<PackageType, VaultPackageAssembler> entry : packageAssemblers.entrySet()) {
            File packageFile = entry.getValue().createPackage();
            converter.processSubPackage(path + "-" + entry.getKey(), runMode, converter.open(packageFile), isEmbeddedPackage);
        }
    }
    
    static final JsonReader jsonReader = new JsonReader();
    static final XMLReader xmlReader = new XMLReader();
    static final ZipReader zipReader   = new ZipReader();
    
    
    ContentReader getContentReaderForEntry(File entry, PathEntry pathEntry){
        String entryName = entry.getName();
        if (entryName.endsWith(".json") && !pathEntry.isIgnoredImportProvider("json")) {
            return jsonReader;
        } else if(entryName.endsWith(".xml") && !pathEntry.isIgnoredImportProvider("xml")) {
            return xmlReader;
        } else if(
                (entryName.endsWith(".zip") && !pathEntry.isIgnoredImportProvider("zip")) ||
                (entryName.endsWith(".jar") && !pathEntry.isIgnoredImportProvider("jar"))
        ) {
            return zipReader;
        } else {
            return null;
        }
    }

}
