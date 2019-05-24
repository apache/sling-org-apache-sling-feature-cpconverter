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

import static org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.PACKAGE_CLASSIFIER;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.handlers.EntryHandler;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.util.DefaultFileSet;

public class VaultPackageAssembler implements EntryHandler {

    private static final String JCR_ROOT_DIR_NAME = "jcr_root";

    private static final String META_INF_VAULT_DIRECTORY = "META-INF/vault/";

    private static final String VAULT_PROPERTIES_FILE = META_INF_VAULT_DIRECTORY + "properties.xml";

    private static final String VAULT_FILTER_FILE = META_INF_VAULT_DIRECTORY + "filter.xml";

    private static final String NAME_PATH = "path";

    private static final String[] INCLUDE_RESOURCES = { "definition/.content.xml", "config.xml", "settings.xml" };

    private static final File TMP_DIR = new File(System.getProperty("java.io.tmpdir"), "syntethic-content-packages");

    private static final Pattern OSGI_BUNDLE_PATTERN = Pattern.compile("(jcr_root)?/apps/[^/]+/install(\\.([^/]+))?/.+\\.jar");
    
    public static VaultPackageAssembler create(VaultPackage vaultPackage) {
        return create(vaultPackage, vaultPackage.getMetaInf().getFilter());
    }

    public static VaultPackageAssembler create(VaultPackage vaultPackage, WorkspaceFilter filter) {
        File storingDirectory = new File(TMP_DIR, vaultPackage.getFile().getName() + "-deflated");
        // avoid any possible Stream is not a content package. Missing 'jcr_root' error
        File jcrRootDirectory = new File(storingDirectory, JCR_ROOT_DIR_NAME);
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
                PackageProperties.NAME_DEPENDENCIES,
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

        VaultPackageAssembler assembler = new VaultPackageAssembler(storingDirectory, properties);
        assembler.mergeFilters(filter);
        return assembler;
    }

    private final DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();

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
    private VaultPackageAssembler(File storingDirectory, Properties properties) {
        this.storingDirectory = storingDirectory;
        this.properties = properties;
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
        File target = new File(storingDirectory, path);

        target.getParentFile().mkdirs();

        try (OutputStream output = new FileOutputStream(target)) {
            IOUtils.copy(input, output);
        }
    }

    public File getEntry(String path) {
        if (!path.startsWith(JCR_ROOT_DIR_NAME)) {
            path = JCR_ROOT_DIR_NAME + path;
        }

        return new File(storingDirectory, path);
    }

    public File createPackage() throws IOException {
        return createPackage(TMP_DIR);
    }

    public File createPackage(File outputDirectory) throws IOException {
        // generate the Vault properties XML file

        File xmlProperties = new File(storingDirectory, VAULT_PROPERTIES_FILE);
        xmlProperties.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(xmlProperties)) {
            properties.storeToXML(fos, null);
        }

        // generate the Vault filter XML file
        File xmlFilter = new File(storingDirectory, VAULT_FILTER_FILE);
        try (InputStream input = filter.getSource();
                FileOutputStream output = new FileOutputStream(xmlFilter)) {
            IOUtils.copy(input, output);
        }

        // copy the required resources

        for (String resource : INCLUDE_RESOURCES) {
            try (InputStream input = getClass().getResourceAsStream(resource)) {
                addEntry(META_INF_VAULT_DIRECTORY + resource, input);
            }
        }

        // create the target archiver

        Archiver archiver = new JarArchiver();
        archiver.setIncludeEmptyDirs(true);

        String destFileName = storingDirectory.getName().substring(0, storingDirectory.getName().lastIndexOf('-'));
        File destFile = new File(TMP_DIR, destFileName);

        archiver.setDestFile(destFile);
        archiver.addFileSet(new DefaultFileSet(storingDirectory));
        archiver.createArchive();

        return destFile;
    }

}
