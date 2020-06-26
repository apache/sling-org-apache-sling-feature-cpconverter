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
import static org.apache.sling.feature.cpconverter.vltpkg.VaultPackageUtils.getDependencies;
import static org.apache.sling.feature.cpconverter.vltpkg.VaultPackageUtils.setDependencies;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.handlers.EntryHandler;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.codehaus.plexus.archiver.zip.ZipArchiver;

public class VaultPackageAssembler implements EntryHandler, FileFilter {

    private static final String NAME_PATH = "path";

    private static final String JCR_ROOT_DIR = "jcr_root";

    private static final String[] INCLUDE_RESOURCES = { PACKAGE_DEFINITION_XML, CONFIG_XML, SETTINGS_XML };

    private static final File TMP_DIR = new File(System.getProperty("java.io.tmpdir"), "synthetic-content-packages");

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
        PackageId packageId = vaultPackage.getId();
        String fileName = packageId.toString().replaceAll("/", "-").replaceAll(":", "-") + "-" + vaultPackage.getFile().getName();
        File storingDirectory = new File(TMP_DIR, fileName + "-deflated");
        if(storingDirectory.exists()) {
            try {
                FileUtils.deleteDirectory(storingDirectory);
            } catch(IOException e) {
                throw new FolderDeletionException("Unable to delete existing deflated folder: '" + storingDirectory + "'", e);
            }
        }
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

        Set<Dependency> dependencies = getDependencies(vaultPackage);

        VaultPackageAssembler assembler = new VaultPackageAssembler(storingDirectory, properties, dependencies);
        assembler.mergeFilters(filter);
        return assembler;
    }

    private final DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();

    private final Set<Dependency> dependencies;

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
    private VaultPackageAssembler(File storingDirectory, Properties properties, Set<Dependency> dependencies) {
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

    public void updateDependencies(Map<PackageId, Set<Dependency>> mutableContentsIds) {
        Map<Dependency, Set<Dependency>> matches = new HashMap<>();
        for (Dependency dependency : dependencies) {
            for (java.util.Map.Entry<PackageId, Set<Dependency>> mutableContentId : mutableContentsIds.entrySet()) {
                if (dependency.matches(mutableContentId.getKey())) {
                    matches.put(dependency, mutableContentId.getValue());
                }
            }
        }
        for(java.util.Map.Entry<Dependency, Set<Dependency>>  match : matches.entrySet()) {
            dependencies.remove(match.getKey());
            dependencies.addAll(match.getValue());
        }
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

        setDependencies(dependencies, properties);

        File xmlProperties = new File(metaDir, PROPERTIES_XML);

        try (FileOutputStream fos = new FileOutputStream(xmlProperties)) {
            properties.storeToXML(fos, null);
        }

        // generate the Vault filter XML file
        computeFilters(outputDirectory);
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

    private void computeFilters(File outputDirectory) {
        File jcrRootDir = new File(outputDirectory, JCR_ROOT_DIR);

        if (jcrRootDir.exists() && jcrRootDir.isDirectory()) {
            for (File child : jcrRootDir.listFiles(this)) {
                File lowestCommonAncestor = lowestCommonAncestor(new TreeNode(child)).val;
                if (lowestCommonAncestor != null) {
                    String root = outputDirectory.toURI().relativize(lowestCommonAncestor.toURI()).getPath();

                    filter.add(new PathFilterSet(root));
                }
            }
        }
    }

    @Override
    public boolean accept(File pathname) {
        return pathname.isDirectory();
    }

    private TreeNode lowestCommonAncestor(TreeNode root) {
        int currMaxDepth = 0;//curr tree's deepest leaf depth
        int countMaxDepth = 0;//num of deepest leaves
        TreeNode node = null;

        for (File child : root.val.listFiles(this)) {
            TreeNode temp = lowestCommonAncestor(new TreeNode(child));

            if (temp == null) {
                continue;
            } else if (temp.maxDepth > currMaxDepth) {//if deeper leaf found,update everything to that deeper leaf
                currMaxDepth = temp.maxDepth;
                node = temp;//update the maxDepth leaf/LCA
                countMaxDepth = 1;//reset count of maxDepth leaves
            } else if (temp.maxDepth == currMaxDepth) {
                countMaxDepth++;//more deepest leaves of curr (sub)tree found
            }
        }

        if (countMaxDepth > 1) {
            //if there're several leaves at the deepest level of curr tree,curr root is the LCA of them
            //OR if there're several LCA of several deepest leaves in curr tree,curr root is also the LCA of them
            root.maxDepth = node.maxDepth + 1;//update root's maxDepth and return it
            return root;
        } else if (countMaxDepth == 1) {
            //if there's only 1 deepest leaf or only 1 LCA of curr tree,return that leaf/LCA
            node.maxDepth++;//update node's maxDepth and return it
            return node;
        } else if (countMaxDepth == 0) {
            //if curr root's children have no children(all leaves,so all return null to temp),set root's maxDepth to 2,return
            root.maxDepth = 2;//update node's maxDepth to 2 cuz its children are leaves
            return root;
        }

        return null;
    }

    private static final class TreeNode {

        File val;

        int maxDepth;//this means the maxDepth of curr treenode-rooted (sub)tree

        TreeNode(File x) {
            val = x;
            maxDepth = 0;
        }

    }

    public static class FolderDeletionException extends RuntimeException {
        public FolderDeletionException(String message) {
            super(message);
        }

        public FolderDeletionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
