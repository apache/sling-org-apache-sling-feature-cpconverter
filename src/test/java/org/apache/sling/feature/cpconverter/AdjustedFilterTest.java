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
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;

public class AdjustedFilterTest extends AbstractConverterTest {

    private ContentPackage2FeatureModelConverter converter;
    private File outputDirectory;
    
    private final EntryHandlersManager handlersManager = spy(new DefaultEntryHandlersManager(Collections.emptyMap(), false, ContentPackage2FeatureModelConverter.SlingInitialContentPolicy.KEEP, ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT));

    @Before
    public void setUp() throws Exception {
        AclManager aclManager = new DefaultAclManager();

        converter = new ContentPackage2FeatureModelConverter()
                .setEntryHandlersManager(handlersManager)
                .setAclManager(aclManager);

        outputDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        FeaturesManager featuresManager = new DefaultFeaturesManager(true, 5, outputDirectory, null, null, null, aclManager);
        converter.setFeaturesManager(featuresManager)
                .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(outputDirectory))
                .setEmitter(DefaultPackagesEventsEmitter.open(outputDirectory));
    }

    @After
    public void tearDown() throws IOException {
        deleteDirTree(outputDirectory);
        converter.close();
    }

    /**
     * @see <a href="https://issues.apache.org/jira/browse/SLING-10754">SLING-10754</a>
     */
    @Test
    public void testSubTreeInContentXml() throws Exception {
        URL packageUrl = getClass().getResource("subtree_in_contentxml.zip");
        File packageFile = FileUtils.toFile(packageUrl);
        converter.convert(packageFile);

        File converted = new File(outputDirectory, "subtree_in_contentxml/0.0.0/subtree_in_contentxml-0.0.0-cp2fm-converted.zip");
        WorkspaceFilter filter = getWorkspaceFilter(converted);
        assertCoverage(filter);
    }

    /**
     * Same as {@link #testSubTreeInContentXml}, but 'subtree_in_contentxml_sibling.zip' contains an 
     * sibling /oak:index/custom-2 instead in the .content.xml, which is not covered by the filter, which only 
     * lists /oak:index/custom as filter root.
     * 
     * NOTE: the behaviour tested in this case relies on a bug in Jackrabbit FileVault that results in siblings of a 
     * filter-root being installed even if not covered by the workspace filter.
     * 
     * @see <a href="https://issues.apache.org/jira/browse/SLING-10754">SLING-10754</a> and 
     *      <a href="https://issues.apache.org/jira/browse/SLING-10760">SLING-10760</a>
     */
    @Test
    public void testSubTreeInContentXmlWithSibling() throws Exception {
        URL packageUrl = getClass().getResource("subtree_in_contentxml_sibling.zip");
        File packageFile = FileUtils.toFile(packageUrl);
        WorkspaceFilter filter = getWorkspaceFilter(packageFile);
        assertCoverage(filter);
        assertFalse(filter.covers("/oak:index/custom-2"));
        
        converter.convert(packageFile);

        File converted = new File(outputDirectory, "subtree_in_contentxml_sibling/0.0.0/subtree_in_contentxml_sibling-0.0.0-cp2fm-converted.zip");
        filter = getWorkspaceFilter(converted);
        assertCoverage(filter);
        assertFalse(filter.covers("/oak:index/custom-2"));
        
        try (ZipFile zipFile = new ZipFile(converted)) {
            ZipEntry entry = zipFile.getEntry("jcr_root/_oak_index/.content.xml");
            String content = IOUtils.toString(zipFile.getInputStream(entry), StandardCharsets.UTF_8);
            assertTrue(content.contains("<custom-2"));
        }
    }

    /**
     * Same as {@link #testSubTreeInContentXmlWithSibling()}, but 'subtree_in_contentxml_policy.zip' in addition contains 
     * policy node /oak:index/rep:policy in the .content.xml, which is not covered by the filter, which only 
     * lists /oak:index/custom as filter root.
     * 
     * @see <a href="https://issues.apache.org/jira/browse/SLING-10754">SLING-10754</a> and 
     *      <a href="https://issues.apache.org/jira/browse/SLING-10760">SLING-10760</a>
     */
    @Test
    public void testSubTreeInContentXmlWithPolicy() throws Exception {
        URL packageUrl = getClass().getResource("subtree_in_contentxml_policy.zip");
        File packageFile = FileUtils.toFile(packageUrl);
        WorkspaceFilter filter = getWorkspaceFilter(packageFile);
        assertCoverage(filter);
        assertFalse(filter.covers("/oak:index/rep:policy"));

        converter.convert(packageFile);

        File converted = new File(outputDirectory, "subtree_in_contentxml_policy/0.0.0/subtree_in_contentxml_policy-0.0.0-cp2fm-converted.zip");
        filter = getWorkspaceFilter(converted);
        assertCoverage(filter);
        assertFalse(filter.covers("/oak:index/rep:policy"));

        try (ZipFile zipFile = new ZipFile(converted)) {
            ZipEntry entry = zipFile.getEntry("jcr_root/_oak_index/.content.xml");
            String content = IOUtils.toString(zipFile.getInputStream(entry), StandardCharsets.UTF_8);
            assertTrue(content.contains("<rep:policy"));
        }
        // FIXME: SLING-10760
        //verify(handlersManager).getEntryHandlerByEntryPath("/jcr_root/_oak_index/_rep_policy.xml");
    }
    
    private void assertCoverage(@NotNull WorkspaceFilter filter) {
        assertFalse(filter.covers("/"));
        assertFalse(filter.covers("/oak:index"));
        assertFalse(filter.covers("/oak:index/uuid"));

        assertTrue(filter.covers("/oak:index/custom"));
        assertTrue(filter.covers("/oak:index/custom/indexRules"));
        assertTrue(filter.covers("/oak:index/custom/indexRules/nt:unstructured"));
        assertTrue(filter.covers("/oak:index/custom/indexRules/nt:unstructured/properties"));
    }
}