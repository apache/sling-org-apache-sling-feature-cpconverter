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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.artifacts.LocalMavenRepositoryArtifactsDeployer;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.BundleSlingInitialContentExtractor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(Parameterized.class)
public final class BundleEntryHandlerTest {

    private final String bundleLocation;

    private final EntryHandler bundleEntryHandler;

    private final int startOrder;

    public BundleEntryHandlerTest(String bundleLocation, EntryHandler bundleEntryHandler, int startOrder) {
        this.bundleLocation = bundleLocation;
        this.bundleEntryHandler = bundleEntryHandler;
        this.startOrder = startOrder;
    }

    @Test
    public void doesNotMatch() {
        assertFalse(bundleEntryHandler.matches("/jcr_root/not/a/valid/recognised/bundle.jar"));
    }

    @Test
    public void matches() {
        assertTrue(bundleEntryHandler.matches(bundleLocation));
    }

    @Test
    public void matchesOnLibsDir() {
        assertTrue(bundleEntryHandler.matches(bundleLocation.replace("/apps/", "/libs/")));
    }

    private void deleteDirTree(File dir) throws IOException {
        Path tempDir = dir.toPath();
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    @Test
    public void deployBundle() throws Exception {
        Archive archive = mock(Archive.class);
        Entry entry = mock(Entry.class);

        when(entry.getName()).thenReturn("test-framework.jar");
        when(archive.openInputStream(entry)).then(new Answer<InputStream>() {

            @Override
            public InputStream answer(InvocationOnMock invocation) throws Throwable {
                return getClass().getResourceAsStream(bundleLocation.substring(1));
            }

        });

        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));
        FeaturesManager featuresManager = spy(DefaultFeaturesManager.class);
        when(featuresManager.getTargetFeature()).thenReturn(feature);
        when(featuresManager.getRunMode(anyString())).thenReturn(feature);
        doCallRealMethod().when(featuresManager).addArtifact(anyString(), any(ArtifactId.class));

        ContentPackage2FeatureModelConverter converter = mock(ContentPackage2FeatureModelConverter.class);

        File testDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + "_tst_" + System.currentTimeMillis());
        File tmpDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + "_tmp_" + System.currentTimeMillis());
        tmpDirectory.mkdirs();
        try {

            when(converter.getArtifactsDeployer()).thenReturn(new LocalMavenRepositoryArtifactsDeployer(testDirectory));
            when(converter.getFeaturesManager()).thenReturn(featuresManager);
            when(converter.getTempDirectory()).thenReturn(tmpDirectory);
            bundleEntryHandler.handle(bundleLocation, archive, entry, converter);

            assertTrue(new File(testDirectory, "org/apache/felix/org.apache.felix.framework/6.0.1/org.apache.felix.framework-6.0.1.pom").exists());
            assertTrue(new File(testDirectory, "org/apache/felix/org.apache.felix.framework/6.0.1/org.apache.felix.framework-6.0.1.jar").exists());

            assertFalse(featuresManager.getTargetFeature().getBundles().isEmpty());
            assertEquals(1, feature.getBundles().size());
            assertEquals("org.apache.felix:org.apache.felix.framework:6.0.1", feature.getBundles().get(0).getId().toMvnId());
            assertEquals(startOrder, feature.getBundles().get(0).getStartOrder());
        } finally {
            deleteDirTree(testDirectory);
            deleteDirTree(tmpDirectory);
        }
    }

    @Parameters
    public static Collection<Object[]> data() {
        final BundleEntryHandler bundleEntryHandler = new BundleEntryHandler();

        return Arrays.asList(new Object[][] {
                { "/jcr_root/apps/asd/install/test-framework-no-pom.jar", bundleEntryHandler, 20 },
                { "/jcr_root/apps/asd/install/test-framework.jar", bundleEntryHandler, 20 },
                { "/jcr_root/apps/asd/install/9/test-framework.jar", bundleEntryHandler, 9 },
                { "/jcr_root/apps/asd/install.author/test-framework.jar", bundleEntryHandler, 20 },
                { "/jcr_root/apps/asd/install.author/9/test-framework.jar", bundleEntryHandler, 9 },
                { "/jcr_root/apps/asd/install.publish/test-framework.jar", bundleEntryHandler, 20 },
                { "/jcr_root/apps/asd/config.publish/test-framework.jar", bundleEntryHandler, 20 }
        });
    }

}
