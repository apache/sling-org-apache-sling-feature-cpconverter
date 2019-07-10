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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Date;
import java.util.Stack;

import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.packaging.VaultPackage;

/**
 * Writes a CSV file <code>containerFile,packageId,packageType[,parentId,pathInParent]</code>
 */
public final class DefaultPackagesEventsEmitter implements PackagesEventsEmitter {

    private static final String FILENAME = "content-packages.csv";

    private static final String PATH_SEPARATOR_CHAR = "!";

    public static DefaultPackagesEventsEmitter open(File featureModelsOutputDirectory) throws IOException {
        if (!featureModelsOutputDirectory.exists()) {
            featureModelsOutputDirectory.mkdirs();
        }

        File contentPackagesFiles = new File(featureModelsOutputDirectory, FILENAME);
        return new DefaultPackagesEventsEmitter(new FileWriter(contentPackagesFiles));
    }

    private final Stack<String> paths = new Stack<>();

    private final Stack<PackageId> hierarchy = new Stack<>();

    private final PrintWriter writer;

    private VaultPackage current;

    protected DefaultPackagesEventsEmitter(Writer writer) {
        this.writer = new PrintWriter(writer, true);
    }

    @Override
    public void start() {
        writer.printf("# File created on %s by the Apache Sling Content Package to Sling Feature converter%n", new Date())
              .printf("# content-package path, content-package ID, content-package type, content-package parent ID, path in parent content-package, absolute path%n");
    }

    @Override
    public void end() {
        writer.close();
        paths.clear();
        hierarchy.clear();
    }

    @Override
    public void startPackage(VaultPackage vaultPackage) {
        paths.add(vaultPackage.getFile().getAbsolutePath());
        hierarchy.add(vaultPackage.getId());
        current = vaultPackage;

        writer.printf("%s,%s,%s,,,%n",
                      paths.peek(),
                      hierarchy.peek(),
                      detectPackageType(vaultPackage));
    }

    @Override
    public void endPackage() {
        paths.pop();
        hierarchy.pop();
    }

    @Override
    public void startSubPackage(String path, VaultPackage vaultPackage) {
        paths.add(path);
        String absolutePath = paths.stream().collect(joining(PATH_SEPARATOR_CHAR));

        writer.printf("%s,%s,%s,%s,%s,%s%n",
                      current.getFile().getAbsolutePath(),
                      vaultPackage.getId(),
                      detectPackageType(vaultPackage),
                      hierarchy.peek(),
                      path,
                      absolutePath);

        hierarchy.add(vaultPackage.getId());
    }

    @Override
    public void endSubPackage() {
        endPackage();
    }

    private static PackageType detectPackageType(VaultPackage vaultPackage) {
        PackageType packageType = vaultPackage.getPackageType();
        if (packageType != null) {
            return packageType;
        }

        // borrowed from org.apache.jackrabbit.vault.fs.io.AbstractExporter
        WorkspaceFilter filter = vaultPackage.getMetaInf().getFilter();

        boolean hasApps = false;
        boolean hasOther = false;
        for (PathFilterSet p : filter.getFilterSets()) {
            if ("cleanup".equals(p.getType())) {
                continue;
            }
            String root = p.getRoot();
            if ("/apps".equals(root)
                    || root.startsWith("/apps/")
                    || "/libs".equals(root)
                    || root.startsWith("/libs/")) {
                hasApps = true;
            } else {
                hasOther = true;
            }
        }
        if (hasApps && !hasOther) {
            return PackageType.APPLICATION;
        } else if (hasOther && !hasApps) {
            return PackageType.CONTENT;
        }
        return PackageType.MIXED;
    }

}
