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
import org.apache.sling.feature.cpconverter.accesscontrol.Group;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.jetbrains.annotations.NotNull;

public final class GroupEntryHandler extends AbstractUserEntryHandler {

    public GroupEntryHandler() {
        // FIXME: SLING-9969
        super("/jcr_root(/home/groups.*/)\\.content.xml");
    }

    @Override
    AbstractUserParser createParser(@NotNull ContentPackage2FeatureModelConverter converter, @NotNull RepoPath originalPath, @NotNull RepoPath intermediatePath) {
        return new GroupParser(converter, originalPath, intermediatePath);
    }

    private static final class GroupParser extends AbstractUserParser {

        private final static String REP_GROUP = "rep:Group";

        /**
         * @param converter - the converter to use.
         * @param path - the original repository path of the user in the content-package.
         * @param intermediatePath - the intermediate path the user should have - most likely the (direct) parent of the path.
         */
        public GroupParser(@NotNull ContentPackage2FeatureModelConverter converter, @NotNull RepoPath path, @NotNull RepoPath intermediatePath) {
            super(converter, path, intermediatePath, REP_GROUP);
        }

        @Override
        void handleUser(@NotNull String id) {
            converter.getAclManager().addGroup(new Group(id, path, intermediatePath));
        }
    }
}
