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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.jackrabbit.vault.fs.api.FilterSet;
import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.handlers.EntryHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.Deflater;

import static org.apache.jackrabbit.vault.util.Constants.FILTER_XML;
import static org.apache.jackrabbit.vault.util.Constants.META_DIR;
import static org.apache.jackrabbit.vault.util.Constants.PROPERTIES_XML;
import static org.apache.jackrabbit.vault.util.Constants.ROOT_DIR;
import static org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.PACKAGE_CLASSIFIER;
import static org.apache.sling.feature.cpconverter.vltpkg.VaultPackageUtils.getDependencies;
import static org.apache.sling.feature.cpconverter.vltpkg.VaultPackageUtils.setDependencies;

public class VaultPackageAssembler implements EntryHandler {

    private static final Pattern OSGI_BUNDLE_PATTERN = Pattern.compile("(jcr_root)?/apps/[^/]+/install(\\.([^/]+))?/.+\\.jar");
    
    public static final String VERSION_SUFFIX = '-' + PACKAGE_CLASSIFIER;

    private static final Logger log = LoggerFactory.getLogger(VaultPackageAssembler.class);
    
    private final Set<String> convertedCpPaths = new HashSet<>();
    private final Set<String> allPaths = new HashSet<>();
    private final DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
    private final Set<Dependency> dependencies;
    private final File storingDirectory;
    private final Properties properties;
    private final File tmpDir;
    private final boolean removeInstallHooks;
    
    /**
     * This class can not be instantiated from outside
     */
    private VaultPackageAssembler(@NotNull File tempDir, @NotNull File storingDirectory, @NotNull Properties properties, 
                                  @NotNull Set<Dependency> dependencies, boolean removeInstallHooks) {
        this.storingDirectory = storingDirectory;
        this.properties = properties;
        this.dependencies = dependencies;
        this.tmpDir = tempDir;
        this.removeInstallHooks = removeInstallHooks;
    }
    
    /**
     * Creates a new package assembler based on an existing package.
     * Takes over properties and filter rules from existing package.
     * 
     * @param baseTempDir the temp dir
     * @param vaultPackage the package to take as blueprint
     * @param removeInstallHooks whether to remove install hooks or not
     * @return the package assembler
     */
    public static @NotNull VaultPackageAssembler create(@NotNull File baseTempDir, @NotNull VaultPackage vaultPackage, boolean removeInstallHooks) {
        final File tempDir = new File(baseTempDir, "synthetic-content-packages_" + System.currentTimeMillis());
        PackageId packageId = vaultPackage.getId();
        File storingDirectory = initStoringDirectory(packageId, tempDir);

        Properties properties = new Properties();
        Map<Object, Object> originalPackageProperties = vaultPackage.getMetaInf().getProperties();
        if (originalPackageProperties == null) {
            throw new IllegalArgumentException("No package properties found in " + vaultPackage.getId());
        }
        if (removeInstallHooks) {
            // filter install hook properties
            log.info("Removing install hooks from original package");
            originalPackageProperties = originalPackageProperties.entrySet().stream().filter(new RemoveInstallHooksPredicate()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        properties.putAll(originalPackageProperties);
        String version = vaultPackage.getId().getVersion().toString();
        if (!version.endsWith(VERSION_SUFFIX)) {
            version += VERSION_SUFFIX;
        }
        properties.setProperty(PackageProperties.NAME_VERSION, version);

        Set<Dependency> dependencies = getDependencies(vaultPackage);

        VaultPackageAssembler assembler = new VaultPackageAssembler(tempDir, storingDirectory, properties, dependencies, removeInstallHooks);
        assembler.mergeFilters(Objects.requireNonNull(vaultPackage.getMetaInf().getFilter()));
        return assembler;
    }

    /**
     * Creates a new package assembler.
     * 
     * @param baseTempDir the temp dir
     * @param packageId the package id from which to generate a minimal properties.xml
     * @param description the description which should end up in the package properties
     * @return the package assembler
     */
    public static @NotNull VaultPackageAssembler create(@NotNull File baseTempDir, @NotNull PackageId packageId, String description) {
        final File tempDir = new File(baseTempDir, "synthetic-content-packages_" + System.currentTimeMillis());
        File storingDirectory = initStoringDirectory(packageId, tempDir);
        Properties props = new Properties();
        // generate minimal properties (http://jackrabbit.apache.org/filevault/properties.html)
        props.put(PackageProperties.NAME_GROUP, packageId.getGroup());
        props.put(PackageProperties.NAME_NAME, packageId.getName());
        props.put(PackageProperties.NAME_VERSION, packageId.getVersionString() + VERSION_SUFFIX);

        props.put(PackageProperties.NAME_DESCRIPTION, description);
        return new VaultPackageAssembler(tempDir, storingDirectory, props, new HashSet<>(), false);
    }

    private static @NotNull File initStoringDirectory(PackageId packageId, @NotNull File tempDir) {
        String fileName = packageId.toString().replace('/', '-').replace(':', '-');
        File storingDirectory = new File(tempDir, fileName + "-deflated");
        if (storingDirectory.exists()) {
            try {
                FileUtils.deleteDirectory(storingDirectory);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to delete existing deflated folder: '" + storingDirectory + "'", e);
            }
        }
        // avoid any possible Stream is not a content package. Missing 'jcr_root' error
        File jcrRootDirectory = new File(storingDirectory, ROOT_DIR);
        if (!jcrRootDirectory.mkdirs() && jcrRootDirectory.isDirectory()) {
            throw new IllegalStateException("Unable to create jcr root dir: " + jcrRootDirectory);
        }
        return storingDirectory;
    }

    File getTempDir() {
        return this.tmpDir;
    }

    @Override
    public boolean matches(@NotNull String path) {
        return true;
    }

    @Override
    public void handle(@NotNull String path, @NotNull Archive archive, @NotNull Entry entry, @NotNull ContentPackage2FeatureModelConverter converter)
            throws IOException, ConverterException {
        if (removeInstallHooks && path.startsWith("/" + Constants.META_DIR + "/" + Constants.HOOKS_DIR)) {
            log.info("Skipping install hook {} from original package", path);
        } else {
            addEntry(path, archive, entry);
        }
    }

    public @NotNull Properties getPackageProperties() {
        return this.properties;
    }

    public void mergeFilters(@NotNull WorkspaceFilter filter) {
        Map<String, PathFilterSet> propFilterSets = extractPropertyFilters(filter);
        // copy over node-filters together with the corresponding property-filters
        for (PathFilterSet pathFilterSet : filter.getFilterSets()) {
            if (!OSGI_BUNDLE_PATTERN.matcher(pathFilterSet.getRoot()).matches()) {
                PathFilterSet propSet = propFilterSets.remove(pathFilterSet.getRoot());
                if (propSet != null) {
                    this.filter.add(pathFilterSet, propSet);
                } else {
                    this.filter.add(pathFilterSet);
                }
            }
        }
    }

    public DefaultWorkspaceFilter getFilter() {
        return filter;
    }

    public void addEntry(@NotNull String path, @NotNull Archive archive, @NotNull Entry entry) throws IOException {
        try (InputStream input = Objects.requireNonNull(archive.openInputStream(entry))) {
            addEntry(path, input);
        }
    }

    public void addEntry(@NotNull String path, @NotNull File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            addEntry(path, input);
        }
    }

    public void addEntry(@NotNull String path, @NotNull InputStream input) throws IOException {
        try (OutputStream output = createEntry(path)) {
            IOUtils.copy(input, output);
        }
    }

    public @NotNull OutputStream createEntry(@NotNull String path) throws IOException {
        File target = new File(storingDirectory, path);
        if (!target.getParentFile().mkdirs() && !target.getParentFile().isDirectory()) {
            throw new IOException("Could not create parent directory: " + target.getParentFile());
        }
        convertedCpPaths.add(path);
        return new FileOutputStream(target);
    }

    public @NotNull File getEntry(@NotNull String path) {
        if (!path.startsWith(ROOT_DIR)) {
            path = ROOT_DIR + path;
        }

        return new File(storingDirectory, path);
    }

    /**
     * Records an entry path as it is processed by the {@link ContentPackage2FeatureModelConverter}. The path of all 
     * original entries that got processed will later be compared to the paths of those entries written back 
     * to this assembler to build the converted content package and generate an updated {@code WorkspaceFilter} that no 
     * longer refers to paths that got moved out to the feature model (see also https://issues.apache.org/jira/browse/SLING-10467)
     *  
     * @param entryPath The path of a content package entry processed by the converter.
     * @return {@code true} if the given path was successfully added to the internal set, {@code false} otherwise.
     */
    public boolean recordEntryPath(@NotNull String entryPath) {
        return allPaths.add(entryPath);
    }

    public void updateDependencies(@NotNull Map<PackageId, Set<Dependency>> mutableContentsIds) {
        Map<Dependency, Set<Dependency>> matches = new HashMap<>();
        for (Dependency dependency : dependencies) {
            for (java.util.Map.Entry<PackageId, Set<Dependency>> mutableContentId : mutableContentsIds.entrySet()) {
                if (dependency.matches(mutableContentId.getKey())) {
                    matches.put(dependency, mutableContentId.getValue());
                }
            }
        }
        for (java.util.Map.Entry<Dependency, Set<Dependency>> match : matches.entrySet()) {
            dependencies.remove(match.getKey());
            dependencies.addAll(match.getValue());
        }
    }

    public void addDependency(@NotNull Dependency dependency) {
        dependencies.add(dependency);
    }

    public @NotNull File createPackage() throws IOException {
        return createPackage(false);
    }

    public @NotNull File createPackage(boolean generateFilters) throws IOException {
        // generate the Vault properties XML file

        File metaDir = new File(storingDirectory, META_DIR);
        if (!metaDir.exists() && !metaDir.mkdirs()) {
            throw new IOException("Could not create meta Dir: " + metaDir);
        }

        final PackageType sourcePackageType;
        final String sourcePackageTypeValue = (String) properties.get(PackageProperties.NAME_PACKAGE_TYPE);
        if (sourcePackageTypeValue != null) {
            sourcePackageType = PackageType.valueOf(sourcePackageTypeValue.toUpperCase());
        } else {
            sourcePackageType = null;
        }
        PackageType newPackageType = VaultPackageUtils.recalculatePackageType(sourcePackageType, storingDirectory);
        if (newPackageType != null) {
            properties.setProperty(PackageProperties.NAME_PACKAGE_TYPE, newPackageType.name().toLowerCase());
        }

        setDependencies(dependencies, properties);

        File xmlProperties = new File(metaDir, PROPERTIES_XML);

        try (FileOutputStream fos = new FileOutputStream(xmlProperties)) {
            properties.storeToXML(fos, null);
        }

        if (generateFilters) {
            // generate the Vault filter XML file based on new contents of the package
            computeFilters(storingDirectory);
        }
        
        Set<String> allRepoPaths = toRepositoryPaths(allPaths);
        Set<String> convertedCpRepoPaths = toRepositoryPaths(convertedCpPaths);
        Set<String> filteredPaths = new HashSet<>(allRepoPaths);
        filteredPaths.removeAll(convertedCpRepoPaths);
        WorkspaceFilter adjustedFilter = createAdjustedFilter(filter, filteredPaths, convertedCpRepoPaths);

        File xmlFilter = new File(metaDir, FILTER_XML);
        try (InputStream input = adjustedFilter.getSource();
             FileOutputStream output = new FileOutputStream(xmlFilter)) {
            IOUtils.copy(input, output);
        }

        // create the target archiver
        final String destFileName = storingDirectory.getName().substring(0, storingDirectory.getName().lastIndexOf('-'));
        final File destFile = new File(this.tmpDir, destFileName);
        final File manifestFile = new File(storingDirectory, JarFile.MANIFEST_NAME.replace('/', File.separatorChar));
        Manifest manifest = null;
        if (manifestFile.exists()) {
            try (final InputStream r = new FileInputStream(manifestFile)) {
                manifest = new Manifest(r);
            }
        }
        try (final JarOutputStream jos = manifest == null ? new JarOutputStream(new FileOutputStream(destFile)) 
                                                          : new JarOutputStream(new FileOutputStream(destFile), manifest)) {            
            jos.setLevel(Deflater.DEFAULT_COMPRESSION);
            addDirectory(jos, storingDirectory, storingDirectory.getAbsolutePath().length() + 1);
        }

        return destFile;
    }

    private static @NotNull Set<String> toRepositoryPaths(@NotNull Set<String> paths) {
        return paths.stream().map(s -> {
            if (s.startsWith("/jcr_root")) {
                String path = PlatformNameFormat.getRepositoryPath(s.substring("/jcr_root".length()));
                if (path.endsWith("/.content.xml")) {
                    return path.substring(0, path.lastIndexOf("/.content.xml"));
                } else if (path.endsWith(".xml")) {
                    // remove .xml extension from policy-nodes
                    return path.substring(0, path.lastIndexOf(".xml"));
                } else {
                    return path;
                }
            } else {
                return s;
            }
        }).collect(Collectors.toSet());
    }

    private static @NotNull WorkspaceFilter createAdjustedFilter(@NotNull WorkspaceFilter base, @NotNull Set<String> filteredPaths, @NotNull Set<String> cpPaths) throws IOException {
        try {
            DefaultWorkspaceFilter dwf = new DefaultWorkspaceFilter();
            Map<String, PathFilterSet> propFilters = extractPropertyFilters(base);
            for (PathFilterSet pfs : base.getFilterSets()) {
                processPathFilterSet(pfs, propFilters, dwf, filteredPaths, cpPaths);
            }
            return dwf;
        } catch (ConfigurationException e) {
            throw new IOException(e);
        }
    }

    private static void processPathFilterSet(@NotNull PathFilterSet pfs, @NotNull Map<String, PathFilterSet> propFilters, 
                                             @NotNull DefaultWorkspaceFilter newFilter, @NotNull Set<String> filteredPaths,
                                             @NotNull Set<String> cpPaths) throws ConfigurationException {
        if (cpPaths.stream().noneMatch(pfs::covers)) {
            // the given filterset no longer covers any of the paths included in the converted content package
            // -> ok to no longer include it in the new workspace filter.
            return;
        }
        
        // create a new node path-filter-set (and if existing the corresponding property filter)
        PathFilterSet nodeFilterSet = copyPathFilterSet(pfs, filteredPaths);
        PathFilterSet propPfs = propFilters.remove(pfs.getRoot());
        if (propPfs != null) {
            // note: no need to add additional exclude entries for property-filters
            PathFilterSet propFilterSet = copyPathFilterSet(propPfs, Collections.emptySet());
            newFilter.add(nodeFilterSet, propFilterSet);
        } else {
            newFilter.add(nodeFilterSet);
        }
    }
    
    @NotNull
    private static PathFilterSet copyPathFilterSet(@NotNull PathFilterSet pfs, @NotNull Set<String> filteredPaths) throws ConfigurationException {
        // create a new path-filter-set
        PathFilterSet filterSet = new PathFilterSet(pfs.getRoot());
        filterSet.setType(pfs.getType());
        filterSet.setImportMode(pfs.getImportMode());

        // copy all entries to the new path-filter-set
        for (FilterSet.Entry<PathFilter> entry : pfs.getEntries()) {
            if (entry.isInclude()) {
                filterSet.addInclude(entry.getFilter());
            } else {
                filterSet.addExclude(entry.getFilter());
            }
        }

        // for all paths that got filtered out and moved to repo-init make sure they get explicitly excluded
        for (String path : filteredPaths) {
            if (pfs.covers(path)) {
                filterSet.addExclude(new DefaultPathFilter(path));
            }
        }
        return filterSet;
    }

    /**
     * Extract all property-filters and remember them for later as DefaultWorkspaceFilter.addPropertyFilterSet
     * is deprecated in favor of DefaultWorkspaceFilter.add(PathFilterSet nodeFilter, PathFilterSet propFilter).
     * The map created then allows to process property-filters together with node filters.
     *
     * @param base The filter from which to extract property-filters
     * @return A map of path (the root) to the corresponding property-pathfilterset.
     */
    private static Map<String, PathFilterSet> extractPropertyFilters(@NotNull WorkspaceFilter base) {
        Map<String, PathFilterSet> propFilters = new LinkedHashMap<>();
        base.getPropertyFilterSets().forEach(pathFilterSet -> propFilters.put(pathFilterSet.getRoot(), pathFilterSet));
        return propFilters;
    }

    private static void addDirectory(@NotNull final JarOutputStream jos, @NotNull final File dir, final int prefixLength) throws IOException {
        if (dir.getAbsolutePath().length() > prefixLength && dir.listFiles().length == 0) {
            final String dirName = dir.getAbsolutePath().substring(prefixLength).replace(File.separatorChar, '/');
            final JarEntry entry = new JarEntry(dirName);
            entry.setTime(dir.lastModified());
            entry.setSize(0);
            jos.putNextEntry(entry);
            jos.closeEntry();
        }
        for (final File f : dir.listFiles()) {
            final String name = f.getAbsolutePath().substring(prefixLength).replace(File.separatorChar, '/');
            if (f.isFile() && !JarFile.MANIFEST_NAME.equals(name)) {
                final JarEntry entry = new JarEntry(name);
                entry.setTime(f.lastModified());
                jos.putNextEntry(entry);

                try (final FileInputStream in = new FileInputStream(f)) {
                    IOUtils.copy(in, jos);
                }
                jos.closeEntry();
            } else if (f.isDirectory()) {
                addDirectory(jos, f, prefixLength);
            }
        }
    }

    private void computeFilters(@NotNull File outputDirectory) {
        VaultPackageUtils.forEachDirectoryBelowJcrRoot(outputDirectory, (child, base) -> {
            TreeNode node = lowestCommonAncestor(new TreeNode(child));
            File lowestCommonAncestor = node != null ? node.val : null;
            if (lowestCommonAncestor != null) {
                String root = "/" + PlatformNameFormat.getRepositoryPath(base.toURI().relativize(lowestCommonAncestor.toURI()).getPath(), true);
                filter.add(new PathFilterSet(root));
            }
        });
    }

    private static @Nullable TreeNode lowestCommonAncestor(@NotNull TreeNode root) {
        int currMaxDepth = 0;//curr tree's deepest leaf depth
        int countMaxDepth = 0;//num of deepest leaves
        TreeNode node = null;

        for (File child : root.val.listFiles((FileFilter) DirectoryFileFilter.INSTANCE)) {
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

        TreeNode(@NotNull File x) {
            val = x;
            maxDepth = 0;
        }

    }

    private static final class RemoveInstallHooksPredicate implements Predicate<Map.Entry<Object, Object>> {
        @Override
        public boolean test(java.util.Map.Entry<Object, Object> entry) {
            String key = (String) entry.getKey();
            return !key.startsWith(PackageProperties.PREFIX_INSTALL_HOOK);
        }
    }
}
