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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.Mapping;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.util.converter.Converters;

abstract class AbstractConfigurationEntryHandler extends AbstractRegexEntryHandler {
    
    private static final String REPOINIT_FACTORY_PID = "org.apache.sling.jcr.repoinit.RepositoryInitializer";

    private static final String REPOINIT_PID = "org.apache.sling.jcr.repoinit.impl.RepositoryInitializer";

    private static final String SERVICE_USER_MAPPING_PID = "org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl";

    private boolean enforceServiceMappingByPrincipal;

    public AbstractConfigurationEntryHandler(@NotNull String extension) {
        super("/jcr_root/(?:apps|libs)/.+/config(\\.(?<runmode>[^/]+))?/(?<pid>.*)\\." + extension);
    }

    void setEnforceServiceMappingByPrincipal(boolean enforceServiceMappingByPrincipal) {
        this.enforceServiceMappingByPrincipal = enforceServiceMappingByPrincipal;
    }

    @Override
    public final void handle(@NotNull String path, @NotNull Archive archive, @NotNull Entry entry, @NotNull ContentPackage2FeatureModelConverter converter) throws Exception {

        Matcher matcher = getPattern().matcher(path);
        
        String runMode;
        // we are pretty sure it matches, here
        if (matcher.matches()) {
            
            String pid = matcher.group("pid");

            int idx = pid.lastIndexOf('/');
            if (idx != -1) {
                pid = pid.substring(idx + 1);
            }
            String factoryPid = null;
            String id;
            int n = pid.indexOf('~');
            if (n == -1) {
                n = pid.indexOf('-');
            }
            if (n > 0) {
                factoryPid = pid.substring(0, n);
                id = factoryPid.concat("~").concat(pid.substring(n + 1));
            } else {
                id = pid;
            }
    
            logger.info("Processing configuration '{}'.", id);
    
            Dictionary<String, Object> configurationProperties;
            try (InputStream input = Objects.requireNonNull(archive.openInputStream(entry))) {
                configurationProperties = parseConfiguration(id, input);
            }
    
            if (configurationProperties == null) {
                logger.info("{} entry does not contain a valid OSGi configuration, treating it as a regular resource", path);
                Objects.requireNonNull(converter.getMainPackageAssembler()).addEntry(path, archive, entry);
                return;
            }
            // there is a specified RunMode
            runMode = matcher.group("runmode");

            FeaturesManager featuresManager = Objects.requireNonNull(converter.getFeaturesManager());
            if (REPOINIT_FACTORY_PID.equals(factoryPid)) {
                final String[] scripts = Converters.standardConverter().convert(configurationProperties.get("scripts")).to(String[].class);
                if (scripts != null && scripts.length > 0 ) {
                    for(final String text : scripts) {
                        if ( text != null && !text.trim().isEmpty() ) {
                            featuresManager.addOrAppendRepoInitExtension(text, runMode);
                        }
                    }
                }
                checkReferences(configurationProperties, pid);
            } else if ( REPOINIT_PID.equals(pid) ) {
                checkReferences(configurationProperties, pid);
            } else if (pid.startsWith(SERVICE_USER_MAPPING_PID)) {
                String[] mappings = Converters.standardConverter().convert(configurationProperties.get("user.mapping")).to(String[].class);
                if (mappings != null) {
                    AclManager aclManager = Objects.requireNonNull(converter.getAclManager());
                    List<String> newMappings = new ArrayList<>();
                    for (String usermapping : mappings) {
                        Mapping mapping = new Mapping(usermapping, enforceServiceMappingByPrincipal);
                        aclManager.addMapping(mapping);
                        newMappings.add(mapping.asString());
                    }
                    // replace 'user.mapping' property by the new mappings, which may have been refactored
                    if (!newMappings.isEmpty()) {
                        configurationProperties.put("user.mapping", newMappings.toArray(new String[0]));
                    }
                }
                featuresManager.addConfiguration(runMode, id, path, configurationProperties);
            } else {
                featuresManager.addConfiguration(runMode, id, path, configurationProperties);
            }
        } else {
            throw new IllegalStateException("Something went terribly wrong: pattern '"
                                            + getPattern().pattern()
                                            + "' should have matched already with path '"
                                            + path
                                            + "' but it does not, currently");
        }
    }

    private void checkReferences(@NotNull final Dictionary<String, Object> configurationProperties, @NotNull final String pid) {
        final String[] references = Converters.standardConverter().convert(configurationProperties.get("references")).to(String[].class);
        if ( references != null && references.length > 0 ) {
            for(final String r  : references ) {
                if ( r != null && !r.trim().isEmpty() ) {
                    throw new IllegalArgumentException("References are not supported for repoinit (configuration " + pid + ")");
                }
            }
        }
    }

    protected abstract @Nullable Dictionary<String, Object> parseConfiguration(@NotNull String name, @NotNull InputStream input) throws Exception;

}
