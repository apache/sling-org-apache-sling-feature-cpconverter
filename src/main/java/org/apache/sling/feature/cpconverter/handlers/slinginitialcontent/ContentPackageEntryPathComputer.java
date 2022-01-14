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

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static org.apache.jackrabbit.vault.util.Constants.DOT_CONTENT_XML;

/**
 * Performs re-computation of the ContentPackagePath of the bundle entry (Sling Initial Content)
 */
public class ContentPackageEntryPathComputer {

    private final Set<SlingInitialContentBundleEntryMetaData> bundleEntries;
    private final String contentPackageEntryPath;
    private final VaultContentXMLContentCreator contentCreator;

    public ContentPackageEntryPathComputer(@NotNull Set<SlingInitialContentBundleEntryMetaData> bundleEntries, 
                                           @NotNull final String contentPackageEntryPath, 
                                           @NotNull VaultContentXMLContentCreator contentCreator){
        this.bundleEntries = bundleEntries;
        this.contentPackageEntryPath = contentPackageEntryPath;
        this.contentCreator = contentCreator;
    }

    @NotNull
    public String compute() {

        String recomputedContentPackageEntryPath = FilenameUtils.removeExtension(contentPackageEntryPath);

        // this covers the case of having a primary node name defined in the xml/json descriptor itself.
        // if this is set, we need to use it in the path.
        if(StringUtils.isNotBlank(contentCreator.getPrimaryNodeName())){
            //custom node name
            recomputedContentPackageEntryPath = StringUtils.substringBeforeLast(recomputedContentPackageEntryPath, "/") ;
            recomputedContentPackageEntryPath = recomputedContentPackageEntryPath + "/" + contentCreator.getPrimaryNodeName();
        }

        final String checkIfRecomputedPathCandidate = StringUtils.removeStart(recomputedContentPackageEntryPath, "/jcr_root");
        //  check if the resulting candidate matches one of the repositoryPaths in the bundle entries we have.
        //  for example        /apps/testJsonFile.json.xml (descriptor entry)
        //  will match         /apps/testJsonFile.json (file entry)
        if(bundleEntries.stream().anyMatch(bundleEntry -> StringUtils.equals(checkIfRecomputedPathCandidate,bundleEntry.getRepositoryPath()))){
            //we are dealing with a file descriptor here
            recomputedContentPackageEntryPath = recomputedContentPackageEntryPath + ".dir/" + DOT_CONTENT_XML;
        }else{
            //  in this case we are dealing with a folder descriptor. for example:
            //  /apps/testJsonFolder.json
            //  we want it to end up in the following format: /apps/testJsonFolder/.content.xml in our assembler.
            recomputedContentPackageEntryPath = recomputedContentPackageEntryPath + "/" + DOT_CONTENT_XML;
        }

        return recomputedContentPackageEntryPath;
    }
}
