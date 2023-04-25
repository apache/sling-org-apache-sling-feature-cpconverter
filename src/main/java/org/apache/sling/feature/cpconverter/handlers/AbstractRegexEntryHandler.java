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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.RunModePolicy;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractRegexEntryHandler implements EntryHandler {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final Pattern pattern;

    AbstractRegexEntryHandler(@NotNull String regex) {
        this(Pattern.compile(regex));
    }

    AbstractRegexEntryHandler(@NotNull Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public final boolean matches(@NotNull String path) {
        return pattern.matcher(path).matches();
    }

    protected final @NotNull Pattern getPattern() {
        return pattern;
    }

    protected String extractTargetRunmode(String path, ContentPackage2FeatureModelConverter converter,
            String runMode, String runModeMatch) {
                String targetRunmode;
                if  (RunModePolicy.PREPEND_INHERITED.equals(converter.getRunModePolicy())) {
                    final List<String> runModes = new ArrayList<>();
                    final List<String> inheritedRunModes = runMode == null ? Collections.emptyList() : Arrays.asList(StringUtils.split(runMode, '.'));
            
                    runModes.addAll(inheritedRunModes);
                    // append found run modes without duplicates (legacy behavior direct_only established by appending to empty List)
                    if (StringUtils.isNotEmpty(runModeMatch)) {
                        // there is a specified RunMode
                        logger.debug("Runmode {} was extracted from path {}", runModeMatch, path);
                        List<String> newRunModes = Arrays.asList(StringUtils.split(runModeMatch, '.'));
            
                        // add only new RunModes that are not already present
                        List<String> newRunModesList = newRunModes.stream()
                                                                  .filter(mode -> !runModes.contains(mode))
                                                                  .collect(Collectors.toList());
            
                        // identify diverging list of run modes between parent & direct definition as diverging criteria between run mode policies
                        if(!runModes.isEmpty() && !CollectionUtils.isEqualCollection(newRunModes, inheritedRunModes)) {
                            logger.info("Found diverging run modes list {} diverging from defined run modes on the parent {}", newRunModes, inheritedRunModes);
                            logger.info("Effective run modes: {}", runModes);
                        }
            
                        runModes.addAll(newRunModesList);
                    }
                    targetRunmode = String.join(".", runModes);
            
                } else {
                    //legacy behavior - direct_only - just use the directly defined run modes
                    targetRunmode = runModeMatch;
                }
                return targetRunmode;
            }

}
