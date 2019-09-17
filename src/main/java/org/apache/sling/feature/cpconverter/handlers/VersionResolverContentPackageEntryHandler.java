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

import java.util.Map;

import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.vltpkg.RecollectorVaultPackageScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VersionResolverContentPackageEntryHandler extends AbstractContentPackageHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final RecollectorVaultPackageScanner scanner;

    private final Map<PackageId, String> subContentPackages;

    public VersionResolverContentPackageEntryHandler(RecollectorVaultPackageScanner scanner, Map<PackageId, String> subContentPackages) {
        this.scanner = scanner;
        this.subContentPackages = subContentPackages;
    }

    @Override
    protected void processSubPackage(String path, String runMode, VaultPackage contentPackage, ContentPackage2FeatureModelConverter converter)
            throws Exception {

        boolean addPackage = false;
        PackageId currentId = contentPackage.getId();

        logger.info("Checking if other {}:{} content-package versions were handled already", currentId.getGroup(), currentId.getName());

        PackageId olderId = getPackage(currentId);

        if (olderId != null) {
            logger.info("Comparing {}:{} package versions: current one is {}, previous one is {} ",
                        currentId.getGroup(), currentId.getName(), currentId.getVersionString(), olderId.getVersionString());

            addPackage = currentId.compareTo(olderId) > 0;

            if (addPackage) {
                logger.info("Replacing version {} of content-package {}:{} with version {}",
                            olderId.getVersionString(), currentId.getGroup(), currentId.getName(), currentId.getVersionString());

                subContentPackages.remove(olderId);
            }
        } else {
            logger.info("There were not other version of {}:{} content-package", currentId.getGroup(), currentId.getName());

            addPackage = true;
        }

        if (addPackage) {
            subContentPackages.put(currentId, path);

            // iteratively traverse the sub(-sub)*content-packages
            scanner.traverse(contentPackage);
        }
    }

    private PackageId getPackage(PackageId expectedId) {
        for (PackageId currentId : subContentPackages.keySet()) {
            if (expectedId.getGroup().equals(currentId.getGroup())
                    && expectedId.getName().equals(currentId.getName())) {
                return currentId;
            }
        }

        return null;
    }

}
