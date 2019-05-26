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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.shared.AbstractContentPackage2FeatureModelConverterTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.inject.Inject;
import com.google.inject.name.Named;

@RunWith(Parameterized.class)
public final class BundleEntryHandlerTest extends AbstractContentPackage2FeatureModelConverterTest {

    private final String bundleLocation;

    private final String runMode;

    @Inject
    private BundleEntryHandler bundleEntryHandler;

    @Inject
    private FeaturesManager featuresManager;

    @Inject
    @Named("features.artifacts.outdir")
    private File testDirectory;

    public BundleEntryHandlerTest(String bundleLocation, String runMode) {
        this.bundleLocation = bundleLocation;
        this.runMode = runMode;
    }

    @Test
    public void doesNotMatch() {
        assertFalse(bundleEntryHandler.matches("jcr_root/not/a/valid/recognised/bundle.jar"));
    }

    @Test
    public void matches() {
        assertTrue(bundleEntryHandler.matches(bundleLocation));
    }

    @Test
    public void matchesOnLibsDir() {
        assertTrue(bundleEntryHandler.matches(bundleLocation.replace("/apps/", "/libs/")));
    }

    @Test
    public void deployBundle() throws Exception {
        Archive archive = mock(Archive.class);
        Entry entry = mock(Entry.class);

        when(entry.getName()).thenReturn("test-framework.jar");
        when(archive.openInputStream(entry)).then(new Answer<InputStream>() {

            @Override
            public InputStream answer(InvocationOnMock invocation) throws Throwable {
                return getClass().getResourceAsStream(bundleLocation);
            }

        });

        featuresManager.init("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1");

        bundleEntryHandler.handle(bundleLocation, archive, entry);

        assertTrue(new File(testDirectory, "org/apache/felix/org.apache.felix.framework/6.0.1/org.apache.felix.framework-6.0.1.pom").exists());
        assertTrue(new File(testDirectory, "org/apache/felix/org.apache.felix.framework/6.0.1/org.apache.felix.framework-6.0.1.jar").exists());

        Feature feature = featuresManager.getRunMode(runMode);
        assertFalse(feature.getBundles().isEmpty());
        assertEquals(1, feature.getBundles().size());
        assertEquals("org.apache.felix:org.apache.felix.framework:6.0.1", feature.getBundles().get(0).getId().toMvnId());
    }

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { "jcr_root/apps/asd/install/test-framework-no-pom.jar", null },
            { "jcr_root/apps/asd/install/test-framework.jar", null },
            { "jcr_root/apps/asd/install.author/test-framework.jar", "author" },
            { "jcr_root/apps/asd/install.publish/test-framework.jar", "publish" }
        });
    }

}
