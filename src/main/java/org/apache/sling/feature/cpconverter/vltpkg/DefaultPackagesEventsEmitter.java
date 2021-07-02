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

import static java.util.stream.Collectors.joining;
import static org.apache.sling.feature.cpconverter.vltpkg.VaultPackageUtils.detectPackageType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URI;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Stack;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.CyclicDependencyException;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.DependencyUtil;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.jetbrains.annotations.NotNull;

/**
 * Writes a CSV file <code>containerFile,packageId,packageType[,parentId,pathInParent]</code>
 */
public final class DefaultPackagesEventsEmitter implements PackagesEventsEmitter {

    private static final String FILENAME = "content-packages.csv";

    private static final String PATH_SEPARATOR_CHAR = "!";
    /**
     * placeholder used in the idOutputLine for the package type until the final package is given in the end call
     */
    private static final String PACKAGE_TYPE = "PACKAGE_TYPE";

    public static @NotNull DefaultPackagesEventsEmitter open(@NotNull File featureModelsOutputDirectory) throws IOException {
        if (!featureModelsOutputDirectory.exists()) {
            featureModelsOutputDirectory.mkdirs();
        }

        File contentPackagesFiles = new File(featureModelsOutputDirectory, FILENAME);
        return new DefaultPackagesEventsEmitter(new FileWriter(contentPackagesFiles));
    }

    private final Stack<String> paths = new Stack<>();

    private final Stack<PackageId> hierarchy = new Stack<>();
    
    private final Collection<VaultPackage> dependenciesOnly = new LinkedList<>();
    
    private final Map<PackageId, String> idOutputLine = new HashMap<>();

    private final PrintWriter writer;

    private VaultPackage current;

    protected DefaultPackagesEventsEmitter(@NotNull Writer writer) {
        this.writer = new PrintWriter(writer, true);
    }

    @Override
    public void start() {
        writer.printf("# File created on %s by the Apache Sling Content Package to Sling Feature converter\n", new Date())
              .printf("# content-package path, content-package ID, content-package type, content-package parent ID, path in parent content-package, absolute path\n");
    }

    @Override
    public void end() {
        try {
            DependencyUtil.sort(dependenciesOnly);
            for (VaultPackage pkg : dependenciesOnly) {
                writer.printf(idOutputLine.get(pkg.getId()));
            }

        } catch (CyclicDependencyException e) {
            throw new ArithmeticException(
                "Cyclic dependencies between packages detected, cannot complete operation. "
                    + e);
        } finally {
            writer.close();
            paths.clear();
            hierarchy.clear();
        }
    }

    @Override
    public void startPackage(@NotNull VaultPackage originalPackage) {
        PackageId id = originalPackage.getId();
        Dependency[] dependencies = originalPackage.getDependencies();
        paths.add(originalPackage.getFile().getAbsolutePath());
        hierarchy.add(id);
        current = originalPackage;

        dependenciesOnly.add(getDepOnlyPackage(id, dependencies));
        idOutputLine.put(id, String.format("%s,%s,%s,,,\n",
            paths.peek(),
            hierarchy.peek(),
                PACKAGE_TYPE));
    }

    @Override
    public void endPackage(@NotNull PackageId originalPackageId, @NotNull VaultPackage convertedPackage) {
        idOutputLine.computeIfPresent(originalPackageId, (key, value) -> value.replace(PACKAGE_TYPE, detectPackageType(convertedPackage).toString()));
        paths.pop();
        hierarchy.pop();
    }

    @Override
    public void startSubPackage(@NotNull String path, @NotNull VaultPackage originalPackage) {
        Dependency[] dependencies = originalPackage.getDependencies();
        paths.add(path);
        String absolutePath = paths.stream().collect(joining(PATH_SEPARATOR_CHAR));

        PackageId id = originalPackage.getId();
        dependenciesOnly.add(getDepOnlyPackage(id, dependencies));
        idOutputLine.put(id, String.format("%s,%s,%s,%s,%s,%s\n",
            current.getFile().getAbsolutePath(),
            id, PACKAGE_TYPE,
            hierarchy.peek(),
            path,
            absolutePath));

        hierarchy.add(id);
    }

    @Override
    public void endSubPackage(@NotNull String path, @NotNull PackageId originalPackageId, @NotNull VaultPackage convertedPackage) {
        endPackage(originalPackageId,convertedPackage);
    }
    
    static @NotNull VaultPackage getDepOnlyPackage(@NotNull PackageId id,
            @NotNull Dependency[] dependencies) {
        return new VaultPackage() {
            
            
            @Override
            public PackageId getId() {
                return id;
            }
            
            @Override
            public Dependency[] getDependencies() {
                return dependencies;
            }
            
            /** 
             * Further methods are irrelevant for sorting
             **/
            
            @Override
            public boolean requiresRoot() {
                return false;
            }
            
            @Override
            public SubPackageHandling getSubPackageHandling() {
                return null;
            }
            
            @Override
            public String getProperty(String name) {
                return null;
            }
            
            @Override
            public PackageType getPackageType() {
                return null;
            }
            
            @Override
            public String getLastWrappedBy() {
                return null;
            }
            
            @Override
            public Calendar getLastWrapped() {
                return null;
            }
            
            @Override
            public String getLastModifiedBy() {
                return null;
            }
            
            @Override
            public Calendar getLastModified() {
                return null;
            }
            
            @Override
            public String getDescription() {
                return null;
            }

            @Override
            public Calendar getDateProperty(String name) {
                return null;
            }
            
            @Override
            public String getCreatedBy() {
                return null;
            }
            
            @Override
            public Calendar getCreated() {
                return null;
            }
            
            @Override
            public AccessControlHandling getACHandling() {
                return null;
            }
            
            @Override
            public boolean isValid() {
                return false;
            }
            
            @Override
            public boolean isClosed() {
                return false;
            }
            
            @Override
            public long getSize() {
                return 0;
            }
            
            @Override
            public PackageProperties getProperties() {
                return null;
            }
            
            @Override
            public MetaInf getMetaInf() {
                return null;
            }

            @Override
            public File getFile() {
                return null;
            }
            
            @Override
            public Archive getArchive() {
                return null;
            }
            
            @Override
            public void extract(Session session, ImportOptions opts)
                    throws RepositoryException, PackageException {
                //no invocation for dependency calculation
            }
            
            @Override
            public void close() {
                //no invocation for dependency calculation
            }

            @Override
            public boolean requiresRestart() {
                return false;
            }

            @Override
            public Map<String, String> getExternalHooks() {
                return null;
            }

            @Override
            public @NotNull Map<PackageId, URI> getDependenciesLocations() {
                return Collections.emptyMap();
            }

            @Override
            public long getBuildCount() {
                return 0;
            }
        };
    }

}
