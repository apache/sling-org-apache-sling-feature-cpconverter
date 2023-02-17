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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Extracts the sling initial content from a bundle to an java.io.InputStream.
 */
public class BundleSlingInitialContentExtractor {

    protected final AssemblerProvider assemblerProvider = new AssemblerProvider();
    protected final ContentReaderProvider contentReaderProvider = new ContentReaderProvider();
    protected final ParentFolderRepoInitHandler parentFolderRepoInitHandler = new ParentFolderRepoInitHandler();

    /**
     * Extract the bundle sling initial content, assemble it into package assemblers into a different package,
     * And strip the bundle of all the sling initial content.
     *
     * @param context all context variables needed to perform the extraction.
     * @return stripped bundle inputstream
     * @throws IOException
     * @throws ConverterException
     */
    @Nullable
    public InputStream extract(@NotNull BundleSlingInitialContentExtractContext context) throws IOException, ConverterException {

        ContentPackage2FeatureModelConverter contentPackage2FeatureModelConverter = context.getConverter();

        if (context.getSlingInitialContentPolicy() == ContentPackage2FeatureModelConverter.SlingInitialContentPolicy.KEEP) {
            return null;
        }
        if (CollectionUtils.isEmpty(context.getPathEntryList())) {
            return null;
        }

        // create a bundle file that will contain all non-sling initial content 
        Path strippedBundleFile = getNewBundleFile(context, contentPackage2FeatureModelConverter);

        // collect the metadata into a set first, we need all the data upfront in our second loop.
        SlingInitialContentBundleEntryMetaDataCollector collector =
                new SlingInitialContentBundleEntryMetaDataCollector(context, contentPackage2FeatureModelConverter, strippedBundleFile);
        Set<SlingInitialContentBundleEntryMetaData> collectedSlingInitialContentBundleEntries = collector.collectFromContextAndWriteTmpFiles();

        // now that we got collectedSlingInitialContentBundleEntries ready, we loop it and perform an extract for each entry.
        // then add it into the appropriate vault package assemblers
        BundleSlingInitialContentJarEntryExtractor jarEntryExtractor =
                new BundleSlingInitialContentJarEntryExtractor(assemblerProvider, contentReaderProvider, parentFolderRepoInitHandler);

        for (SlingInitialContentBundleEntryMetaData slingInitialContentBundleEntryMetaData : collectedSlingInitialContentBundleEntries) {
            jarEntryExtractor.extractAndAddToAssembler(context, slingInitialContentBundleEntryMetaData, collectedSlingInitialContentBundleEntries);
        }

        // add additional content packages to feature model
        finalizePackageAssembly(context);

        // return bundle's inputstream, stripped off sling initial content, which must be deleted on close
        return Files.newInputStream(strippedBundleFile, StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE);
    }

    static Version getModifiedOsgiVersion(@NotNull Version originalVersion) {
        return new Version(originalVersion.getMajor(),
                originalVersion.getMinor(),
                originalVersion.getMicro(),
                originalVersion.getQualifier() + "_" + ContentPackage2FeatureModelConverter.PACKAGE_CLASSIFIER);
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

    private Path getNewBundleFile(@NotNull BundleSlingInitialContentExtractContext context, ContentPackage2FeatureModelConverter contentPackage2FeatureModelConverter) throws IOException {
        final Manifest manifest = context.getManifest();
        // remove header
        manifest.getMainAttributes().remove(new Attributes.Name(PathEntry.CONTENT_HEADER));
        // change version to have suffix
        Version originalVersion = new Version(Objects.requireNonNull(manifest.getMainAttributes().getValue(Constants.BUNDLE_VERSION)));
        manifest.getMainAttributes().putValue(Constants.BUNDLE_VERSION, getModifiedOsgiVersion(originalVersion).toString());
        return Files.createTempFile(contentPackage2FeatureModelConverter.getTempDirectory().toPath(), "newBundle", ".jar");
    }

}
