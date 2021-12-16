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

import static java.util.Objects.requireNonNull;
import static org.osgi.framework.Version.parseVersion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.RepositoryException;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.felix.utils.manifest.Clause;
import org.apache.felix.utils.manifest.Parser;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.commons.osgi.ManifestHeader;
import org.apache.sling.contentparser.api.ContentParser;
import org.apache.sling.contentparser.api.ParserOptions;
import org.apache.sling.contentparser.json.JSONParserFeature;
import org.apache.sling.contentparser.json.JSONParserOptions;
import org.apache.sling.contentparser.json.internal.JSONContentParser;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.SlingInitialContentPolicy;
import org.apache.sling.feature.cpconverter.artifacts.InputStreamArtifactWriter;
import org.apache.sling.feature.cpconverter.vltpkg.DocViewSerializerContentHandler;
import org.apache.sling.feature.cpconverter.vltpkg.DocViewSerializerContentHandlerException;
import org.apache.sling.feature.cpconverter.vltpkg.JcrNamespaceRegistry;
import org.apache.sling.feature.cpconverter.vltpkg.SingleFileArchive;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageUtils;
import org.apache.sling.jcr.contentloader.PathEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

public class BundleEntryHandler extends AbstractRegexEntryHandler {

    private static final String NAME_GROUP_ID = "groupId";

    private static final String NAME_ARTIFACT_ID = "artifactId";

    private static final String JAR_TYPE = "jar";

    public static final String NODETYPES_BUNDLE_HEADER = "Sling-Nodetypes";

    public static final String NAMESPACES_BUNDLE_HEADER = "Sling-Namespaces";

    private static final Pattern POM_PROPERTIES_PATTERN = Pattern.compile("META-INF/maven/[^/]+/[^/]+/pom.properties");

    private static final Pattern POM_XML_PATTERN = Pattern.compile("META-INF/maven/[^/]+/[^/]+/pom.xml");

    private boolean enforceBundlesBelowInstallFolder;

    private SlingInitialContentPolicy slingInitialContentPolicy;

    public BundleEntryHandler() {
        super("/jcr_root/(?:apps|libs)/.+/(?<foldername>install|config)(?:\\.(?<runmode>[^/]+))?/(?:(?<startlevel>[0-9]+)/)?.+\\.jar");
    }

    void setEnforceBundlesBelowInstallFolder(boolean enforceBundlesBelowInstallFolder) {
        this.enforceBundlesBelowInstallFolder = enforceBundlesBelowInstallFolder;
    }

    public void setSlingInitialContentPolicy(@NotNull SlingInitialContentPolicy slingInitialContentPolicy) {
        this.slingInitialContentPolicy = slingInitialContentPolicy;
    }

    @Override
    public void handle(@NotNull String path,
           @NotNull Archive archive, 
           @NotNull Entry entry, 
           @NotNull ContentPackage2FeatureModelConverter converter) throws IOException, ConverterException {
        logger.info("Processing bundle {}...", entry.getName());

        Matcher matcher = getPattern().matcher(path);
        String runMode = null;
        Integer startLevel = null;
        // we are pretty sure it matches, here
        if (!matcher.matches()) {
            throw new IllegalStateException("Something went terribly wrong: pattern '"
                                            + getPattern().pattern()
                                            + "' should have matched already with path '"
                                            + path
                                            + "' but it does not, currently");
        }

        if (enforceBundlesBelowInstallFolder && !"install".equals(matcher.group("foldername"))) {
            throw new ConverterException("OSGi bundles are only considered if placed below a folder called 'install', but the bundle at '"+ path + "' is placed outside!");
        }

        
        runMode = matcher.group("runmode");
        if (runMode != null) {
            // there is a specified RunMode
            logger.debug("Runmode {} was extracted from path {}", runMode, path);
        }

        final String value = matcher.group("startlevel");
        if (value != null) {
            // there is a specified Start Level
            startLevel = Integer.parseInt(value); // NumberFormatException impossible due to RegEx
            logger.debug("Start level {} was extracted from path {}", startLevel, path);
        }

        String bundleName = entry.getName();
        // Remove the leading path
        int idx = bundleName.lastIndexOf('/');
        if (idx >= 0) {
            bundleName = bundleName.substring(idx + 1);
        }
        // Remove the extension
        int edx = bundleName.lastIndexOf('.');
        if (edx > 0) {
            bundleName = bundleName.substring(0, edx);
        }
        
        // create a temporary JAR file (extracted from archive)
        Path tmpBundleJar = Files.createTempFile(converter.getTempDirectory().toPath(), "extracted", bundleName + ".jar");
        try {
            try (OutputStream output = Files.newOutputStream(tmpBundleJar);
                InputStream input = Objects.requireNonNull(archive.openInputStream(entry))) {
                IOUtils.copy(input, output);
            }
            processBundleInputStream(path, tmpBundleJar, bundleName, runMode, startLevel, converter);
        } finally {
            Files.delete(tmpBundleJar);
        }
    }

    void processBundleInputStream(@NotNull String path, @NotNull Path originalBundleFile, @NotNull String bundleName, @Nullable String runMode, @Nullable Integer startLevel, @NotNull ContentPackage2FeatureModelConverter converter)
        throws ConverterException, IOException {
        try (JarFile jarFile = new JarFile(originalBundleFile.toFile())) {
            // first extract bundle metadata from JAR input stream
            Artifact artifact = extractFeatureArtifact(bundleName, jarFile);
            ArtifactId id = artifact.getId();

            try (InputStream strippedBundleInput = extractSlingInitialContent(path, originalBundleFile, artifact, jarFile, converter, runMode)) {
                if (strippedBundleInput != null && slingInitialContentPolicy == SlingInitialContentPolicy.EXTRACT_AND_REMOVE) {
                    id = id.changeVersion(id.getVersion() + "-" + ContentPackage2FeatureModelConverter.PACKAGE_CLASSIFIER);
                    Objects.requireNonNull(converter.getArtifactsDeployer()).deploy(new InputStreamArtifactWriter(strippedBundleInput), id);
                } else {
                    try (InputStream originalBundleInput = Files.newInputStream(originalBundleFile)) {
                        Objects.requireNonNull(converter.getArtifactsDeployer()).deploy(new InputStreamArtifactWriter(originalBundleInput), id);
                    }
                }
            }
            artifact = artifact.copy(id);
            Objects.requireNonNull(converter.getFeaturesManager()).addArtifact(runMode, artifact, startLevel);
            String exportHeader = Objects.requireNonNull(jarFile.getManifest()).getMainAttributes().getValue(Constants.EXPORT_PACKAGE);
            if (exportHeader != null) {
                for (Clause clause : Parser.parseHeader(exportHeader)) {
                    converter.getFeaturesManager().addAPIRegionExport(runMode, clause.getName());
                }
            }
        }
    }

    static Version getModifiedOsgiVersion(Version originalVersion) {
        return new Version(originalVersion.getMajor(), originalVersion.getMinor(), originalVersion.getMicro(), originalVersion.getQualifier() + "_" + ContentPackage2FeatureModelConverter.PACKAGE_CLASSIFIER);
    }

    @Nullable InputStream extractSlingInitialContent(@NotNull String path, @NotNull Path bundlePath, @NotNull Artifact bundleArtifact, @NotNull JarFile jarFile, @NotNull ContentPackage2FeatureModelConverter converter, @Nullable String runMode) throws IOException, ConverterException {
        if (slingInitialContentPolicy == SlingInitialContentPolicy.KEEP) {
            return null;
        }
        // parse "Sling-Initial-Content" header
        Manifest manifest = Objects.requireNonNull(jarFile.getManifest());
        Iterator<PathEntry> pathEntries = PathEntry.getContentPaths(manifest, -1);
        if (pathEntries == null) {
            return null;
        }
        logger.info("Extracting Sling-Initial-Content from '{}'", bundleArtifact.getId());
        Collection<PathEntry> pathEntryList = new ArrayList<>();
        pathEntries.forEachRemaining(pathEntryList::add);

        // remove header
        manifest.getMainAttributes().remove(new Attributes.Name(PathEntry.CONTENT_HEADER));
        // change version to have suffix
        Version originalVersion = new Version(Objects.requireNonNull(manifest.getMainAttributes().getValue(Constants.BUNDLE_VERSION)));
        manifest.getMainAttributes().putValue(Constants.BUNDLE_VERSION, getModifiedOsgiVersion(originalVersion).toString());
        Path newBundleFile = Files.createTempFile(converter.getTempDirectory().toPath(), "newBundle", ".jar");
        
        // create JAR file to prevent extracting it twice and for random access
        JcrNamespaceRegistry namespaceRegistry = createNamespaceRegistry(manifest, jarFile, converter.getFeaturesManager().getNamespaceUriByPrefix());
        
        Map<PackageType, VaultPackageAssembler> packageAssemblers = new EnumMap<>(PackageType.class);
        try (OutputStream fileOutput = Files.newOutputStream(newBundleFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            JarOutputStream bundleOutput = new JarOutputStream(fileOutput, manifest)) {
            
            for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
                JarEntry jarEntry = e.nextElement();
                if (!jarEntry.isDirectory()) {
                    try (InputStream input = jarFile.getInputStream(jarEntry)) {
                        if (!extractSlingInitialContent(jarEntry, input, bundleArtifact, pathEntryList, packageAssemblers, namespaceRegistry, converter)) {
                            // skip manifest, as already written in the constructor (as first entry)
                            if (jarEntry.getName().equals(JarFile.MANIFEST_NAME)) {
                                continue;
                            }
                            // copy entry as is to the stripped bundle
                            bundleOutput.putNextEntry(jarEntry);
                            IOUtils.copy(input, bundleOutput);
                            bundleOutput.closeEntry();
                        }
                    }
                }
            }
        }
        // add additional content packages to feature model
        finalizePackageAssembly(path, packageAssemblers, converter, runMode);
        
        // return stripped bundle's inputstream which must be deleted on close
        return Files.newInputStream(newBundleFile, StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE);
    }

    /**
     * 
     * @param jarEntry
     * @param bundleFileInputStream
     * @param packageAssemblers
     * @param converter
     * @return {@code true} in case the given entry was part of the initial content otherwise {@code false}
     * @throws Exception 
     */
    boolean extractSlingInitialContent(@NotNull JarEntry jarEntry, @NotNull InputStream bundleFileInputStream, @NotNull Artifact bundleArtifact, @NotNull Collection<PathEntry> pathEntries, @NotNull Map<PackageType, VaultPackageAssembler> packageAssemblers, @NotNull JcrNamespaceRegistry nsRegistry, @NotNull ContentPackage2FeatureModelConverter converter) throws IOException, ConverterException {
        final String entryName = jarEntry.getName();
        // check if current JAR entry is initial content
        Optional<PathEntry> pathEntry = pathEntries.stream().filter(p -> entryName.startsWith(p.getPath())).findFirst();
        if (!pathEntry.isPresent()) {
            return false;
        }
        Map.Entry<ContentParser, ParserOptions> contentParserAndOptions = getContentParserForEntry(jarEntry, pathEntry.get());

        // https://sling.apache.org/documentation/bundles/content-loading-jcr-contentloader.html#file-name-escaping
        String repositoryPath = (pathEntry.get().getTarget() != null ? pathEntry.get().getTarget() : "/") + URLDecoder.decode(entryName.substring(pathEntry.get().getPath().length()), "UTF-8");
        // all entry paths used by entry handlers start with "/"
        String contentPackageEntryPath = "/" + org.apache.jackrabbit.vault.util.Constants.ROOT_DIR + PlatformNameFormat.getPlatformPath(repositoryPath);

        Path tmpDocViewInputFile = null;
        try {
            if (contentParserAndOptions != null) {
                // convert to docview xml
                tmpDocViewInputFile = Files.createTempFile(converter.getTempDirectory().toPath(), "docview", ".xml");
                try (OutputStream docViewOutput = Files.newOutputStream(tmpDocViewInputFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                     DocViewSerializerContentHandler contentHandler = new DocViewSerializerContentHandler(docViewOutput, nsRegistry)) {
                    contentParserAndOptions.getKey().parse(contentHandler, bundleFileInputStream, contentParserAndOptions.getValue());
                    contentPackageEntryPath = FilenameUtils.removeExtension(contentPackageEntryPath) + ".xml";
                } catch (IOException e) {
                    throw new IOException("Can not parse " + jarEntry, e);
                } catch (DocViewSerializerContentHandlerException e) {
                    throw new IOException("Can not convert " + jarEntry + " to enhanced DocView format", e);
                }
            }
    
            // remap CND files to make sure they are picked up by NodeTypesEntryHandler;
            if (nsRegistry.getRegisteredCndSystemIds().contains(jarEntry.getName())) {
                contentPackageEntryPath = "/META-INF/vault/" + Text.getName(jarEntry.getName()) + ".cnd";
            }
            try (Archive virtualArchive = SingleFileArchive.fromPathOrInputStream(tmpDocViewInputFile, bundleFileInputStream, 
                    () -> Files.createTempFile(converter.getTempDirectory().toPath(), "initial-content", Text.getName(jarEntry.getName())), contentPackageEntryPath)) {
                // in which content package should this end up?
                VaultPackageAssembler packageAssembler = initPackageAssemblerForPath(bundleArtifact.getId(), repositoryPath, pathEntry.get(), packageAssemblers, converter);
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
        return true;
    }

    JcrNamespaceRegistry createNamespaceRegistry(@NotNull Manifest manifest, @NotNull JarFile jarFile, @NotNull Map<String, String> predefinedNamespaceUriByPrefix) throws IOException {
        try {
            JcrNamespaceRegistry registry = new JcrNamespaceRegistry();
            for (Map.Entry<String, String> entry : predefinedNamespaceUriByPrefix.entrySet()) {
                registry.registerNamespace(entry.getKey(), entry.getValue());
            }

            // parse Sling-Namespaces header (https://github.com/apache/sling-org-apache-sling-jcr-base/blob/66be360910c265473799635fcac0e23895898913/src/main/java/org/apache/sling/jcr/base/internal/loader/Loader.java#L192)
            final String namespacesDefinitionHeader = manifest.getMainAttributes().getValue(NAMESPACES_BUNDLE_HEADER);
            if (namespacesDefinitionHeader != null) {
                final StringTokenizer st = new StringTokenizer(namespacesDefinitionHeader, ",");

                while ( st.hasMoreTokens() ) {
                    final String token = st.nextToken().trim();
                    int pos = token.indexOf('=');
                    if ( pos == -1 ) {
                        logger.warn("createNamespaceRegistry: Bundle {} has an invalid namespace manifest header entry: {}",
                                manifest.getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME), token);
                    } else {
                        final String prefix = token.substring(0, pos).trim();
                        final String namespace = token.substring(pos+1).trim();
                        registry.registerNamespace(prefix, namespace);
                    }
                }
            }

            // parse Sling-Nodetypes header
            final String typesHeader = manifest.getMainAttributes().getValue(NODETYPES_BUNDLE_HEADER);
            if (typesHeader != null) {
                for (ManifestHeader.Entry entry : ManifestHeader.parse(typesHeader).getEntries()) {
                    JarEntry jarEntry = jarFile.getJarEntry(entry.getValue());
                    if (jarEntry == null) {
                        logger.warn("createNamespaceRegistry: Bundle {} has referenced a non existing node type definition: {}",
                                manifest.getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME), entry.getValue());
                    } else {
                        try (InputStream inputStream = jarFile.getInputStream(jarEntry);
                            Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                            registry.registerCnd(reader, entry.getValue());
                        }
                    }
                }
            }
            return registry;
        } catch ( final RepositoryException | ParseException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Lazily initializes the cache with the necessary VaultPackageAssemblers
     * @param bundleArtifactId
     * @param repositoryPath
     * @param cache
     * @param converter
     * @return the VaultPackageAssembler from the cache to use for the given repository path
     */
    public VaultPackageAssembler initPackageAssemblerForPath(@NotNull ArtifactId bundleArtifactId, @NotNull String repositoryPath, @NotNull PathEntry pathEntry, @NotNull Map<PackageType, VaultPackageAssembler> cache, @NotNull ContentPackage2FeatureModelConverter converter) 
    throws ConverterException {
        PackageType packageType = VaultPackageUtils.detectPackageType(repositoryPath);
        VaultPackageAssembler assembler = cache.get(packageType);
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
            cache.put(packageType, assembler);
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

    void finalizePackageAssembly(@NotNull String path, @NotNull Map<PackageType, VaultPackageAssembler> packageAssemblers, @NotNull ContentPackage2FeatureModelConverter converter, @Nullable String runMode) throws IOException, ConverterException {
        for (java.util.Map.Entry<PackageType, VaultPackageAssembler> entry : packageAssemblers.entrySet()) {
            File packageFile = entry.getValue().createPackage();
            converter.processSubPackage(path + "-" + entry.getKey(), runMode, converter.open(packageFile), false);
        }
    }

    Map.Entry<ContentParser, ParserOptions> getContentParserForEntry(JarEntry entry, PathEntry pathEntry) {
        if (entry.getName().endsWith(".json") && !pathEntry.isIgnoredImportProvider("json")) {
            return new AbstractMap.SimpleEntry<>(new JSONContentParser(), new JSONParserOptions().withFeatures(EnumSet.of(JSONParserFeature.COMMENTS, JSONParserFeature.QUOTE_TICK)));
        } else {
            return null;
        }
    }

    protected @NotNull Artifact extractFeatureArtifact(@NotNull String bundleName, @NotNull JarFile jarFile) throws IOException {
        String artifactId = null;
        String version = null;
        String groupId = null;
        String classifier = null;

        for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
            JarEntry jarEntry = e.nextElement();
            String nextEntryName = jarEntry.getName();

            if (POM_PROPERTIES_PATTERN.matcher(nextEntryName).matches()) {
                logger.info("Reading '{}' bundle GAV from {}...", bundleName, nextEntryName);
                Properties properties = new Properties();
                try (InputStream input = jarFile.getInputStream(jarEntry)) {
                    properties.load(input);
                }
                groupId = properties.getProperty(NAME_GROUP_ID);
                artifactId = properties.getProperty(NAME_ARTIFACT_ID);
                version = properties.getProperty(PackageProperties.NAME_VERSION);

            } else if (POM_XML_PATTERN.matcher(nextEntryName).matches()) {
                logger.info("Reading '{}' bundle GAV from {}...", bundleName, nextEntryName);
                String path = nextEntryName.substring(0, nextEntryName.length() - "/pom.xml".length());
                groupId = path.substring("META-INF/maven/".length(), path.lastIndexOf('/'));
                artifactId = path.substring(path.lastIndexOf('/') + 1);
                if (artifactId.indexOf('-') != -1) {
                    version = artifactId.substring(artifactId.indexOf('-'));
                    artifactId = artifactId.substring(0, artifactId.indexOf('-'));
                } else if (bundleName.indexOf('-') != -1){
                    try {
                        String versionString = bundleName.substring(bundleName.indexOf('-') + 1);
                        if (!parseVersion(versionString).equals(Version.emptyVersion)) {
                            version = versionString;
                        }
                    } catch (IllegalArgumentException ex) {
                        // Not a version
                    }
                }
            }

            if (groupId != null && artifactId != null && version != null) {
                // bundleName is now the bare name without extension
                String synthesized = artifactId + "-" + version;

                // it was the pom.properties  we were looking for
                if (bundleName.startsWith(synthesized) || bundleName.equals(artifactId)) {

                    // check the artifact has a classifier in the bundle file name
                    if (synthesized.length() < bundleName.length()) {
                        String suffix = bundleName.substring(synthesized.length());
                        if (suffix.length() > 1 && suffix.startsWith("-")) {
                            classifier = suffix.substring(1);
                            logger.info("Inferred classifier of '{}:{}:{}' to be '{}'", groupId, artifactId, version, classifier);
                        }
                    }
                    // no need to iterate further
                    break;
                }
            }
        }

        
        if (groupId == null) {
            // maybe the included jar is just an OSGi bundle but not a valid Maven artifact
            groupId = getCheckedProperty(jarFile.getManifest(), Constants.BUNDLE_SYMBOLICNAME);
            // Make sure there are not spaces in the name to adhere to the Maven Group Id specification
            groupId = groupId.replace(' ', '_').replace(':', '_').replace('/', '_').replace('\\', '_');
            if (groupId.indexOf('.') != -1) {
                artifactId = groupId.substring(groupId.lastIndexOf('.') + 1);
                groupId = groupId.substring(0, groupId.lastIndexOf('.'));
            }
            if (artifactId == null || artifactId.isEmpty()) {
                artifactId = groupId;
            }
            Version osgiVersion = Version.parseVersion(getCheckedProperty(jarFile.getManifest(), Constants.BUNDLE_VERSION));
            version = osgiVersion.getMajor() + "." + osgiVersion.getMinor() + "." + osgiVersion.getMicro() + (osgiVersion.getQualifier().isEmpty() ? "" : "-" + osgiVersion.getQualifier());
        }

        // create artifact and store symbolic name and version in metadata
        final Artifact result = new Artifact(new ArtifactId(groupId, artifactId, version, classifier, JAR_TYPE));
        setMetadataFromManifest(jarFile.getManifest(), Constants.BUNDLE_VERSION, result);
        setMetadataFromManifest(jarFile.getManifest(), Constants.BUNDLE_SYMBOLICNAME, result);

        return result;
    }

    private static void setMetadataFromManifest(@NotNull Manifest manifest, @NotNull String name, @NotNull Artifact artifact) {
        String value = manifest.getMainAttributes().getValue(name);
        if (value != null) {
            artifact.getMetadata().put(name, value);
        }
    }

    private static @NotNull String getCheckedProperty(@NotNull Manifest manifest, @NotNull String name) {
        String property = manifest.getMainAttributes().getValue(name);
        if (property != null) {
            property = property.trim();
        }
        return requireNonNull(property, "Jar file can not be defined as a valid OSGi bundle without specifying a valid '"
                                         + name
                                         + "' property.");
    }

}
