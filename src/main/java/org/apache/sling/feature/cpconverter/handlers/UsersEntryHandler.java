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
import org.apache.sling.feature.cpconverter.accesscontrol.SystemUser;
import org.apache.sling.feature.cpconverter.accesscontrol.User;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.jetbrains.annotations.NotNull;

public final class UsersEntryHandler extends AbstractUserEntryHandler {

    public UsersEntryHandler() {
        // FIXME: SLING-9969
        super("/jcr_root(/home/users/.*/)\\.content.xml");
    }

    @Override
    AbstractUserParser createParser(@NotNull ContentPackage2FeatureModelConverter converter, @NotNull RepoPath originalPath, @NotNull RepoPath intermediatePath) {
        return new SystemUserParser(converter, originalPath, intermediatePath);
    }

    private static final class SystemUserParser extends AbstractUserParser {

        private static final String REP_SYSTEM_USER = "rep:SystemUser";
        private static final String REP_USER = "rep:User";

        /**
         * @param converter - the converter to use.
         * @param path - the original repository path of the user in the content-package.
         * @param intermediatePath - the intermediate path the user should have - most likely the (direct) parent of the path.
         */
        public SystemUserParser(@NotNull ContentPackage2FeatureModelConverter converter, @NotNull RepoPath path, @NotNull RepoPath intermediatePath) {
            super(converter, path, intermediatePath, REP_SYSTEM_USER, REP_USER);
        }

        @Override
        void handleUser(@NotNull String id) {
            if (REP_USER.equals(detectedPrimaryType)) {
                converter.getAclManager().addUser(new User(id, path, intermediatePath));
            } else{
                converter.getAclManager().addSystemUser(new SystemUser(id, path, intermediatePath));
            }
        }
    }

}
