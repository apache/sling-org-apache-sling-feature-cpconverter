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

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.vltpkg.DocViewSerializerContentHandlerException;
import org.apache.sling.feature.cpconverter.vltpkg.SingleFileArchive;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.jcr.contentloader.ContentReader;
import org.apache.sling.jcr.contentloader.PathEntry;
import org.jetbrains.annotations.NotNull;

import javax.jcr.RepositoryException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/**
 * Handles the bundle sling initial content extraction on the jarEntry level.
 */
class BundleSlingInitialContentJarEntryExtractor {


    private static final String SLASH = "/";
    private final AssemblerProvider assemblerProvider;
    private final ContentReaderProvider contentReaderProvider;
    private final ParentFolderRepoInitHandler parentFolderRepoInitHandler;

    BundleSlingInitialContentJarEntryExtractor(@NotNull AssemblerProvider assemblerProvider,
                                                      @NotNull ContentReaderProvider contentReaderProvider,
                                                      @NotNull ParentFolderRepoInitHandler parentFolderRepoInitHandler) {
        this.assemblerProvider = assemblerProvider;
        this.contentReaderProvider = contentReaderProvider;
        this.parentFolderRepoInitHandler = parentFolderRepoInitHandler;
    }

    /**
     * @return {@code true} in case the given entry was part of the initial content otherwise {@code false}
     * @throws Exception
     */
    void extractSlingInitialContent(@NotNull BundleSlingInitialContentExtractContext context,
                                           @NotNull SlingInitialContentBundleEntryMetaData slingInitialContentBundleEntryMetaData,
                                           @NotNull Set<SlingInitialContentBundleEntryMetaData> collectedSlingInitialContentBundleEntries) throws IOException, ConverterException {

        String repositoryPath = slingInitialContentBundleEntryMetaData.getRepositoryPath();
        File file = slingInitialContentBundleEntryMetaData.getTargetFile();
        PathEntry pathEntryValue = slingInitialContentBundleEntryMetaData.getPathEntry();
        // all entry paths used by entry handlers start with "/"
        String contentPackageEntryPath = SLASH + org.apache.jackrabbit.vault.util.Constants.ROOT_DIR + PlatformNameFormat.getPlatformPath(repositoryPath);

        Path tmpDocViewInputFile = null;

        try (InputStream bundleFileInputStream = new FileInputStream(file)) {
            VaultPackageAssembler packageAssembler = assemblerProvider.initPackageAssemblerForPath(context, repositoryPath, pathEntryValue);

            final ContentReader contentReader = contentReaderProvider.getContentReaderForEntry(file, pathEntryValue);
            if (contentReader != null) {

                // convert to docview xml
                tmpDocViewInputFile = Files.createTempFile(context.getConverter().getTempDirectory().toPath(), "docview", ".xml");
                try (OutputStream docViewOutput = Files.newOutputStream(tmpDocViewInputFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

                    repositoryPath = FilenameUtils.removeExtension(repositoryPath);
                    boolean isFileDescriptorEntry = isFileDescriptor(collectedSlingInitialContentBundleEntries, contentPackageEntryPath);
                    VaultContentXMLContentCreator contentCreator = new VaultContentXMLContentCreator(StringUtils.substringBeforeLast(repositoryPath, "/"), docViewOutput, context.getNamespaceRegistry(), packageAssembler, isFileDescriptorEntry);


                    if (file.getName().endsWith(".xml")) {
                        contentCreator.setIsXmlProcessed();
                    }

                    contentReader.parse(file.toURI().toURL(), contentCreator);
                    contentPackageEntryPath = new ContentPackageEntryPathComputer(collectedSlingInitialContentBundleEntries, contentPackageEntryPath, contentCreator).compute();
                    contentCreator.finish();

                } catch (IOException e) {
                    throw new IOException("Can not parse " + file, e);
                } catch (DocViewSerializerContentHandlerException | RepositoryException e) {
                    throw new IOException("Can not convert " + file + " to enhanced DocView format", e);
                }

                // remap CND files to make sure they are picked up by the NodeTypesEntryHandler
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
    private boolean isFileDescriptor(@NotNull Set<SlingInitialContentBundleEntryMetaData> bundleEntries, @NotNull final String contentPackageEntryPath) {

        //sometimes we are dealing with double extensions (.json.xml)
        String recomputedContentPackageEntryPath = FilenameUtils.removeExtension(contentPackageEntryPath);

        final String checkIfRecomputedPathCandidate = StringUtils.removeStart(recomputedContentPackageEntryPath, "/jcr_root");
        return bundleEntries.stream().anyMatch(bundleEntry -> StringUtils.equals(checkIfRecomputedPathCandidate, bundleEntry.getRepositoryPath()));

    }

}
