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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;

public class NodeTypesEntryHandler extends AbstractRegexEntryHandler {

    public NodeTypesEntryHandler() {
        super("/META-INF/vault/nodetypes\\.cnd");
    }

    public NodeTypesEntryHandler(Pattern pattern) {
        super(pattern);
    }

    @Override
    public void handle(String path, Archive archive, Entry entry, ContentPackage2FeatureModelConverter converter)
            throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(archive.openInputStream(entry)))) {
            converter.getAclManager().addNodetypeRegistrationSentence("register nodetypes");
            converter.getAclManager().addNodetypeRegistrationSentence("<<===");

            String nodetypeRegistrationSentence;
            while ((nodetypeRegistrationSentence = reader.readLine()) != null) {
                if (nodetypeRegistrationSentence.isEmpty()) {
                    converter.getAclManager().addNodetypeRegistrationSentence("");
                } else {
                    converter.getAclManager().addNodetypeRegistrationSentence("<< " + nodetypeRegistrationSentence);
                }
            }

            converter.getAclManager().addNodetypeRegistrationSentence("===>>");
        }
    }

}
