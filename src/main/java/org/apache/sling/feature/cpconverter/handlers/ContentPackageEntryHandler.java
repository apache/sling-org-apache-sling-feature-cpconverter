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

import java.io.File;

import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public final class ContentPackageEntryHandler extends AbstractContentPackageHandler {

    @Inject
    private ContentPackage2FeatureModelConverter converter;

    @Inject
    public ContentPackageEntryHandler(@Named("java.io.tmpdir") File temporaryDir) {
        super(temporaryDir);
    }

    @Override
    protected void processSubPackage(String path, VaultPackage contentPackage) throws Exception {
        converter.processSubPackage(path, contentPackage);
    }

}
