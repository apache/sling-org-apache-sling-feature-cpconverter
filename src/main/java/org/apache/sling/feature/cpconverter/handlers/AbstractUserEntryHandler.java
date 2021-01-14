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

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.regex.Matcher;

abstract class AbstractUserEntryHandler extends AbstractRegexEntryHandler {

    AbstractUserEntryHandler(@NotNull String rexex) {
        super(rexex);
    }

    @Override
    public void handle(@NotNull String path, @NotNull Archive archive, @NotNull Entry entry, @NotNull ContentPackage2FeatureModelConverter converter)
            throws Exception {
        Matcher matcher = getPattern().matcher(path);
        if (matcher.matches()) {
            path = matcher.group(1);
        }

        RepoPath originalPath = new RepoPath(PlatformNameFormat.getRepositoryPath(path));
        RepoPath intermediatePath = originalPath.getParent();

        AbstractUserParser parser = createParser(converter, originalPath, intermediatePath);
        try (InputStream input = archive.openInputStream(entry)) {
            parser.parse(input);
        }
    }

    abstract AbstractUserParser createParser(@NotNull ContentPackage2FeatureModelConverter converter, @NotNull RepoPath originalPath, @NotNull RepoPath intermediatePath);

}
