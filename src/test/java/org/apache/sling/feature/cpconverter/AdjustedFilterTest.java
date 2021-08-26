/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.feature.cpconverter;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.DefaultAclManager;
import org.apache.sling.feature.cpconverter.artifacts.LocalMavenRepositoryArtifactsDeployer;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.handlers.DefaultEntryHandlersManager;
import org.apache.sling.feature.cpconverter.handlers.EntryHandlersManager;
import org.apache.sling.feature.cpconverter.shared.ConverterConstants;
import org.apache.sling.feature.cpconverter.vltpkg.DefaultPackagesEventsEmitter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdjustedFilterTest extends AbstractConverterTest {

    private ContentPackage2FeatureModelConverter converter;

    private File outputDirectory;
    private FeaturesManager featuresManager;

    @Before
    public void setUp() throws Exception {
        AclManager aclManager = new DefaultAclManager();
        EntryHandlersManager handlersManager = new DefaultEntryHandlersManager(Collections.emptyMap(), false, ContentPackage2FeatureModelConverter.SlingInitialContentPolicy.KEEP, ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT);

        converter = new ContentPackage2FeatureModelConverter()
                .setEntryHandlersManager(handlersManager)
                .setAclManager(aclManager);

        outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        featuresManager = new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, aclManager);

        converter.setFeaturesManager(featuresManager)
                .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory));
    }

    @After
    public void tearDown() throws IOException {
        deleteDirTree(outputDirectory);
        converter.close();
    }
    
    @Test
    public void testSubTreeInContentXml() throws Exception {
        URL packageUrl = getClass().getResource("subtree_in_contentxml.zip");
        File packageFile = FileUtils.toFile(packageUrl);
        converter.convert(packageFile);

        File converted = new File(outputDirectory, "subtree_in_contentxml/0.0.0/subtree_in_contentxml-0.0.0-cp2fm-converted.zip");
        WorkspaceFilter filter = getWorkspaceFilter(converted);
        
        assertFalse(filter.covers("/"));
        assertFalse(filter.covers("/oak:index"));
        assertFalse(filter.covers("/oak:index/uuid"));
        
        assertTrue(filter.contains("/oak:index/custom"));
        assertTrue(filter.covers("/oak:index/custom/indexRules"));
        assertTrue(filter.covers("/oak:index/custom/indexRules/nt:unstructured"));
        assertTrue(filter.covers("/oak:index/custom/indexRules/nt:unstructured/properties"));
    }
}