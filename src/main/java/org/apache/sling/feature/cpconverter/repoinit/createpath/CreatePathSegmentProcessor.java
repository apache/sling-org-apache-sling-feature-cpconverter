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
package org.apache.sling.feature.cpconverter.repoinit.createpath;

import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.cpconverter.shared.ConverterConstants;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.repoinit.parser.operations.CreatePath;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.jackrabbit.vault.util.Constants.DOT_CONTENT_XML;

public class CreatePathSegmentProcessor {

    private final RepoPath path;
    private final Collection<VaultPackageAssembler> packageAssemblers;
    private final CreatePath cp;
    private boolean foundType = false;
    private String repositoryPath = "";

    private Map<String,String> primaryTypeMap = new LinkedHashMap<>();
    private Map<String,List<String>> mixinTypeMap = new LinkedHashMap<>();
    
    public CreatePathSegmentProcessor(@NotNull RepoPath path, 
                                      @NotNull Collection<VaultPackageAssembler> packageAssemblers, 
                                      @NotNull CreatePath cp) {
        this.path = path;
        this.packageAssemblers = packageAssemblers;
        this.cp = cp;
    }

    /**
     * Process segments of a repopath to createpath, checking packageassemblers for existing primaryType definitions.
     * @return
     */
    public boolean processSegments() {
        for (final String part : path.getSegments()) {
            repositoryPath = processSegment(part);
        }
        return foundType;
    }

    @NotNull
    private String processSegment(String part) {
        final String platformName = PlatformNameFormat.getPlatformName(part);
        repositoryPath = repositoryPath.concat(ConverterConstants.SLASH).concat(platformName);
        
        //loop all package assemblers and check if .content.xml is defined
        collectTypeDataForSegment();
        addSegment(part);
        
        return repositoryPath;
    }
    
    private void addSegment(String part){
        //add segment if jcr:primaryType is defined.
        if(primaryTypeMap.containsKey(repositoryPath)){
            cp.addSegment(
                    part, 
                    primaryTypeMap.get(repositoryPath), 
                    mixinTypeMap.get(repositoryPath)
            );
        }else{
            cp.addSegment(part, null);
        }
    }

    private void collectTypeDataForSegment() {
        for (VaultPackageAssembler packageAssembler : packageAssemblers) {
            
            if(primaryTypeMap.containsKey(repositoryPath)){
                boolean merge = true;
                for(PathFilterSet set: packageAssembler.getFilter().getFilterSets()){
                    if(set.covers(repositoryPath) && (set.getImportMode() != ImportMode.MERGE && set.getImportMode() != ImportMode.MERGE_PROPERTIES)){
                        //found a path with a mode other than merge, proceed to replace the type definitions
                        merge = false;
                    }else if(set.covers(repositoryPath)){
                        // merge is flipped to true again by another filter defined after the previous ones
                        merge = true;
                    }
                }
                if(merge){
                    // we already got the path defined by an earlier package. only proceed to replace if the filter is not merge.
                    break;
                }
            }
            
            File currentContent = packageAssembler.getFileEntry(repositoryPath.concat(ConverterConstants.SLASH).concat(DOT_CONTENT_XML));
            if (currentContent.exists() && currentContent.isFile()) {
                //collect the primary Data up ahead
                collectTypeData(repositoryPath, currentContent);
            }
        }
    }

    
    private boolean collectTypeData(String repositoryPath, @NotNull File currentContent) {
        try (FileInputStream input = new FileInputStream(currentContent);
             FileInputStream input2 = new FileInputStream(currentContent)) {
            String primary = new PrimaryTypeParser().parse(input);
            if (primary != null) {
                List<String> mixins = new ArrayList<>();
                String mixin = new MixinParser().parse(input2);
                if (mixin != null) {
                    mixin = mixin.trim();
                    if (mixin.startsWith("[")) {
                        mixin = mixin.substring(1, mixin.length() - 1);
                    }
                    for (String m : mixin.split(",")) {
                        String mixinName = m.trim();
                        if (!mixinName.isEmpty()) {
                            mixins.add(mixinName);
                        }
                    }
                }

                primaryTypeMap.put(repositoryPath, primary);
                mixinTypeMap.put(repositoryPath, mixins);
                foundType = true;
                
                return true;
            }
        } catch (Exception e) {
            throw new RuntimeException("A fatal error occurred while parsing the '"
                    + currentContent
                    + "' file, see nested exceptions: "
                    + e);
        }
        return false;
    }

}
