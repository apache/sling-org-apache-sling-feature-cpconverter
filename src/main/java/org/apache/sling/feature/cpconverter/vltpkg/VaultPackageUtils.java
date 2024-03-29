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

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.apache.jackrabbit.vault.util.Constants.DOT_CONTENT_XML;
import static org.apache.jackrabbit.vault.util.Constants.ROOT_DIR;

public class VaultPackageUtils {

    private static final String DEPENDENCIES_DELIMITER = ",";
    
    private static final String ENTRY_ROOT_PATH = "/" + ROOT_DIR;

    private VaultPackageUtils() {
        // this class must not be instantiated from outside
    }

    public static @NotNull PackageType detectPackageType(@NotNull VaultPackage vaultPackage) {
        PackageType packageType = vaultPackage.getPackageType();
        if (packageType != null) {
            return packageType;
        }

        // borrowed from org.apache.jackrabbit.vault.fs.io.AbstractExporter
        WorkspaceFilter filter = vaultPackage.getMetaInf().getFilter();
        if (filter != null) {
            for (PathFilterSet p : filter.getFilterSets()) {
                if ("cleanup".equals(p.getType())) {
                    continue;
                }
                String root = p.getRoot();
                @NotNull PackageType newPackageType = detectPackageType(root);
                if (packageType != null && packageType != newPackageType) {
                    // bail out once we ended up with mixed
                    return PackageType.MIXED;
                } else {
                    packageType = newPackageType;
                }
            }
        }
        return packageType != null ? packageType : PackageType.MIXED;
    }

    public static @NotNull PackageType detectPackageType(String path) {
        if ("/apps".equals(path)
                || path.startsWith("/apps/")
                || "/libs".equals(path)
                || path.startsWith("/libs/")) {
            return PackageType.APPLICATION;
        } else {
            return PackageType.CONTENT;
        }
    }

    static @Nullable PackageType recalculatePackageType(PackageType sourcePackageType, 
                                                        @NotNull File outputDirectory, 
                                                        boolean disablePackageTypeRecalculation) {
        
        if (sourcePackageType != null && (sourcePackageType != PackageType.MIXED || disablePackageTypeRecalculation)) {
            return null;
        }
        
        AtomicBoolean foundMutableFiles = new AtomicBoolean();
        AtomicBoolean foundImmutableFiles = new AtomicBoolean();
        forEachDirectoryBelowJcrRoot(outputDirectory, (child, base) -> {
            if (child.getName().equals("apps") || child.getName().equals("libs")) {
                foundImmutableFiles.weakCompareAndSet(false, true);
            } else {
                foundMutableFiles.weakCompareAndSet(false, true);
            }
        });
        if (foundImmutableFiles.get() && !foundMutableFiles.get()) {
            return PackageType.APPLICATION;
        } else if (!foundImmutableFiles.get() && foundMutableFiles.get()) {
            return PackageType.CONTENT;
        } else {
            return PackageType.MIXED;
        }
    }

    static void forEachDirectoryBelowJcrRoot(File outputDirectory, BiConsumer<File, File> consumer) {
        File jcrRootDir = new File(outputDirectory, ROOT_DIR);
        if (jcrRootDir.exists() && jcrRootDir.isDirectory()) {
            for (File child : jcrRootDir.listFiles((FileFilter) DirectoryFileFilter.INSTANCE)) {
                // calls consumer with absolute files
                consumer.accept(child, jcrRootDir);
            }
        }
    }

    public static @NotNull Set<Dependency> getDependencies(@NotNull VaultPackage vaultPackage) {
        Dependency[] originalDepenencies = vaultPackage.getDependencies();

        Set<Dependency> dependencies = new HashSet<>();

        if (originalDepenencies != null && originalDepenencies.length > 0) {
            dependencies.addAll(Arrays.asList(originalDepenencies));
        }

        return dependencies;
    }

    public static void setDependencies(@Nullable Set<Dependency> dependencies, @NotNull Properties properties) {
        if (dependencies == null || dependencies.isEmpty()) {
            properties.remove(PackageProperties.NAME_DEPENDENCIES);
            return;
        }

        String dependenciesString = dependencies.stream().map(d -> d.toString()).collect(Collectors.joining(DEPENDENCIES_DELIMITER));
        properties.setProperty(PackageProperties.NAME_DEPENDENCIES, dependenciesString);
    }

    public static @NotNull Set<String> toRepositoryPaths(@NotNull Set<String> paths) {
        return paths.stream().map(VaultPackageUtils::toRepositoryPath).collect(Collectors.toSet());
    }

    public static @NotNull String toRepositoryPath(@NotNull String s) {
        if (s.startsWith(ENTRY_ROOT_PATH)) {
            String path = PlatformNameFormat.getRepositoryPath(s.substring(ENTRY_ROOT_PATH.length()));
            if (path.endsWith(Constants.DOT_CONTENT_XML)) {
                path = path.substring(0, path.lastIndexOf(Constants.DOT_CONTENT_XML)-1);
            } else if (path.endsWith(".xml")) {
                // remove .xml extension from policy-nodes
                path = path.substring(0, path.lastIndexOf(".xml"));
            }
            return (path.isEmpty()) ? "/" : path;
        } else {
            return s;
        }
    }
    
    public static boolean isContentEntry(@NotNull String entryPath) {
        return entryPath.startsWith(ENTRY_ROOT_PATH) && entryPath.endsWith(DOT_CONTENT_XML);
    }
}
