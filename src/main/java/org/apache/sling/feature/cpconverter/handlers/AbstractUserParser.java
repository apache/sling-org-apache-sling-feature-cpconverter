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

import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.shared.AbstractJcrNodeParser;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;

abstract class AbstractUserParser extends AbstractJcrNodeParser<Void> {

    private final static String REP_AUTHORIZABLE_ID = "rep:authorizableId";

    final ContentPackage2FeatureModelConverter converter;

    final RepoPath path;

    final RepoPath intermediatePath;

    /**
     * @param converter - the converter to use.
     * @param path - the original repository path of the user in the content-package.
     * @param intermediatePath - the intermediate path the user should have - most likely the (direct) parent of the path.
     * @param primaryTypes - the primary type of the user/group nodes to be parsed
     */
    public AbstractUserParser(@NotNull ContentPackage2FeatureModelConverter converter, @NotNull RepoPath path, @NotNull RepoPath intermediatePath, @NotNull String... primaryTypes) {
        super(primaryTypes);
        this.converter = converter;
        this.path = path;
        this.intermediatePath = intermediatePath;
    }

    @Override
    protected void onJcrRootElement(String uri, String localName, String qName, Attributes attributes) {
        String authorizableId = attributes.getValue(REP_AUTHORIZABLE_ID);
        if (authorizableId != null && !authorizableId.isEmpty()) {
            handleUser(authorizableId);
        }
    }

    @Override
    protected Void getParsingResult() {
        return null;
    }

    abstract void handleUser(@NotNull String id);

}