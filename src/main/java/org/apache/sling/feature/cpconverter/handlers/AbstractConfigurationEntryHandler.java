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

import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;
import java.util.Objects;
import java.util.regex.Matcher;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class AbstractConfigurationEntryHandler extends AbstractRegexEntryHandler {

    private boolean enforceConfigurationBelowConfigFolder;
    
    // SLING-10469 - regexp to catch configs and potential sibling .dir folders that would carry the node properties from an export that would need to be ignored as well
    AbstractConfigurationEntryHandler(@NotNull String extension) {
        super("/jcr_root/(?:apps|libs)/.+/(?<foldername>config|install)(\\.(?<runmode>[^/]+))?(.*)/(?<pid>[^\\/]*)\\." + extension + ("(?<dir>.dir(/\\.content\\.xml)?)?$"));
    }

    void setEnforceConfigurationBelowConfigFolder(boolean enforceConfigurationBelowConfigFolder) {
        this.enforceConfigurationBelowConfigFolder = enforceConfigurationBelowConfigFolder;
    }

    @Override
    public final void handle(@NotNull String path, @NotNull Archive archive, @NotNull Entry entry, @NotNull ContentPackage2FeatureModelConverter converter) throws IOException, ConverterException {

        Matcher matcher = getPattern().matcher(path);
        
        String runMode;
        // we are pretty sure it matches, here
        if (matcher.matches()) {
            if (matcher.group("dir") != null) {
                // SLING-10469  - preventing invalid results as the corresponding configuration will be stripped from the resulting package causing the constraints of nt:file not to be satisfied (missing binary)
                logger.info("{} is only a dir folder next to config - removing.", path);
            } else {
                String id = extractId(matcher);
                logger.info("Processing configuration '{}'.", id);
        
                Dictionary<String, Object> configurationProperties;
                try (InputStream input = Objects.requireNonNull(archive.openInputStream(entry))) {
                    configurationProperties = parseConfiguration(id, input);
                }
    
                if (configurationProperties == null) {
                    logger.info("{} entry does not contain a valid OSGi configuration, treating it as a regular resource", path);
                    converter.getMainPackageAssembler().addEntry(path, archive, entry);
                    return;
                }

                if (enforceConfigurationBelowConfigFolder && !"config".equals(matcher.group("foldername"))) {
                    throw new ConverterException("OSGi configuration are only considered if placed below a folder called 'config', but the configuration at '"+ path + "' is placed outside!");
                }
                
                // there is a specified RunMode
                runMode = matcher.group("runmode");
    
                FeaturesManager featuresManager = Objects.requireNonNull(converter.getFeaturesManager());
                final Configuration cfg = new Configuration(id);
                featuresManager.addConfiguration(runMode, cfg, path, configurationProperties);
            }
        } else {
            throw new IllegalStateException("Something went terribly wrong: pattern '"
                                            + getPattern().pattern()
                                            + "' should have matched already with path '"
                                            + path
                                            + "' but it does not, currently");
        }
    }

    @NotNull
    private static String extractId(@NotNull Matcher matcher) {
        String pid = matcher.group("pid");

        int idx = pid.lastIndexOf('/');
        if (idx != -1) {
            pid = pid.substring(idx + 1);
        }
        
        String id;
        int n = pid.indexOf('~');
        if (n == -1) {
            n = pid.indexOf('-');
        }
        if (n > 0) {
            String factoryPid = pid.substring(0, n);
            id = factoryPid.concat("~").concat(pid.substring(n + 1));
        } else {
            id = pid;
        }
        return id;
    }

    protected abstract @Nullable Dictionary<String, Object> parseConfiguration(@NotNull String name, @NotNull InputStream input) throws IOException, ConverterException;

}
