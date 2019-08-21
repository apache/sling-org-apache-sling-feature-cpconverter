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

import static org.apache.jackrabbit.vault.util.Constants.CONFIG_XML;
import static org.apache.jackrabbit.vault.util.Constants.FILTER_XML;
import static org.apache.jackrabbit.vault.util.Constants.META_DIR;
import static org.apache.jackrabbit.vault.util.Constants.PACKAGE_DEFINITION_XML;
import static org.apache.jackrabbit.vault.util.Constants.PROPERTIES_XML;
import static org.apache.jackrabbit.vault.util.Constants.ROOT_DIR;
import static org.apache.jackrabbit.vault.util.Constants.SETTINGS_XML;
import static org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.PACKAGE_CLASSIFIER;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.handlers.EntryHandler;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.codehaus.plexus.archiver.zip.ZipArchiver;

public class VaultPackageAssembler implements EntryHandler {

    private static final String DEPENDENCIES_DELIMITER = ",";

    private static final String NAME_PATH = "path";

    private static final String[] INCLUDE_RESOURCES = { PACKAGE_DEFINITION_XML, CONFIG_XML, SETTINGS_XML };

    private static final File TMP_DIR = new File(System.getProperty("java.io.tmpdir"), "syntethic-content-packages");

    private static final Pattern OSGI_BUNDLE_PATTERN = Pattern.compile("(jcr_root)?/apps/[^/]+/install(\\.([^/]+))?/.+\\.jar");

    public static VaultPackageAssembler create(VaultPackage vaultPackage) {
        return create(vaultPackage, vaultPackage.getMetaInf().getFilter());
    }

    public static File createSynthetic(VaultPackage vaultPackage) throws Exception {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        PathFilterSet filterSet = new PathFilterSet();
        SyntheticPathFilter pathFilter = new SyntheticPathFilter();
        filterSet.addExclude(pathFilter);
        filterSet.setImportMode(ImportMode.MERGE);
        filter.add(filterSet);
        return create(vaultPackage, filter).createPackage();
    }

    private static VaultPackageAssembler create(VaultPackage vaultPackage, WorkspaceFilter filter) {
        File storingDirectory = new File(TMP_DIR, vaultPackage.getFile().getName() + "-deflated");
        // avoid any possible Stream is not a content package. Missing 'jcr_root' error
        File jcrRootDirectory = new File(storingDirectory, ROOT_DIR);
        jcrRootDirectory.mkdirs();

        PackageProperties packageProperties = vaultPackage.getProperties();

        Properties properties = new Properties();
        properties.setProperty(PackageProperties.NAME_VERSION,
                               packageProperties.getProperty(PackageProperties.NAME_VERSION)
                                                             + '-'
                                                             + PACKAGE_CLASSIFIER);

        for (String key : new String[] {
                PackageProperties.NAME_GROUP,
                PackageProperties.NAME_NAME,
                PackageProperties.NAME_CREATED_BY,
                PackageProperties.NAME_CREATED,
                PackageProperties.NAME_REQUIRES_ROOT,
                PackageProperties.NAME_PACKAGE_TYPE,
                PackageProperties.NAME_AC_HANDLING,
                NAME_PATH
        }) {
            String value = packageProperties.getProperty(key);
            if (value != null && !value.isEmpty()) {
                properties.setProperty(key, value);
            }
        }

        Set<PackageId> dependencies = new HashSet<>();
        String dependenciesString = properties.getProperty(PackageProperties.NAME_DEPENDENCIES);

        if (dependenciesString != null && !dependenciesString.isEmpty()) {
         // from https://jackrabbit.apache.org/filevault/properties.html
            // Comma-separated list of dependencies
            StringTokenizer tokenizer = new StringTokenizer(dependenciesString, DEPENDENCIES_DELIMITER);
            while (tokenizer.hasMoreTokens()) {
                String dependencyString = tokenizer.nextToken();
                PackageId dependency = PackageId.fromString(dependencyString);
                dependencies.add(dependency);
            }
        }

        VaultPackageAssembler assembler = new VaultPackageAssembler(storingDirectory, properties, dependencies);
        assembler.mergeFilters(filter);
        return assembler;
    }

    private final DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();

    private final Set<PackageId> dependencies;

    private final File storingDirectory;

    private final Properties properties;

    @Override
    public boolean matches(String path) {
        return true;
    }

    @Override
    public void handle(String path, Archive archive, Entry entry, ContentPackage2FeatureModelConverter converter)
            throws Exception {
        addEntry(path, archive, entry);
    }

    /**
     * This class can not be instantiated from outside
     */
    private VaultPackageAssembler(File storingDirectory, Properties properties, Set<PackageId> dependencies) {
        this.storingDirectory = storingDirectory;
        this.properties = properties;
        this.dependencies = dependencies;
    }

    public void mergeFilters(WorkspaceFilter filter) {
        for (PathFilterSet pathFilterSet : filter.getFilterSets()) {
            if (!OSGI_BUNDLE_PATTERN.matcher(pathFilterSet.getRoot()).matches()) {
                this.filter.add(pathFilterSet);
            }
        }
    }

    public void addEntry(String path, Archive archive, Entry entry) throws IOException {
        try (InputStream input = archive.openInputStream(entry)) {
            addEntry(path, input);
        }
    }

    public void addEntry(String path, File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            addEntry(path, input);
        }
    }

    public void addEntry(String path, InputStream input) throws IOException {
        try (OutputStream output = createEntry(path)) {
            IOUtils.copy(input, output);
        }
    }

    public OutputStream createEntry(String path) throws IOException {
        File target = new File(storingDirectory, path);
        target.getParentFile().mkdirs();
        return new FileOutputStream(target);
    }

    public File getEntry(String path) {
        if (!path.startsWith(ROOT_DIR)) {
            path = ROOT_DIR + path;
        }

        return new File(storingDirectory, path);
    }

    public void removeDependencies(Set<PackageId> mutableContentsIds) {
        dependencies.removeAll(mutableContentsIds);
    }

    public File createPackage() throws IOException {
        return createPackage(TMP_DIR);
    }

    public File createPackage(File outputDirectory) throws IOException {
        // generate the Vault properties XML file

        File metaDir = new File(storingDirectory, META_DIR);
        if (!metaDir.exists()) {
            metaDir.mkdirs();
        }

        if (!dependencies.isEmpty()) {
            String dependenciesString = dependencies.stream().map(id -> id.toString()).collect(Collectors.joining(DEPENDENCIES_DELIMITER));
            properties.setProperty(PackageProperties.NAME_DEPENDENCIES, dependenciesString);
        }

        File xmlProperties = new File(metaDir, PROPERTIES_XML);

        try (FileOutputStream fos = new FileOutputStream(xmlProperties)) {
            properties.storeToXML(fos, null);
        }

        // generate the Vault filter XML file
        File xmlFilter = new File(metaDir, FILTER_XML);
        try (InputStream input = filter.getSource();
                FileOutputStream output = new FileOutputStream(xmlFilter)) {
            IOUtils.copy(input, output);
        }

        // copy the required resources

        for (String resource : INCLUDE_RESOURCES) {
            try (InputStream input = getClass().getResourceAsStream(resource)) {
                addEntry(ROOT_DIR + '/' + resource, input);
            }
        }

        // create the target archiver

        Archiver archiver = new ZipArchiver();
        archiver.setIncludeEmptyDirs(true);

        String destFileName = storingDirectory.getName().substring(0, storingDirectory.getName().lastIndexOf('-'));
        File destFile = new File(TMP_DIR, destFileName);

        archiver.setDestFile(destFile);
        archiver.addFileSet(new DefaultFileSet(storingDirectory));
        archiver.createArchive();

        return destFile;
    }

}
