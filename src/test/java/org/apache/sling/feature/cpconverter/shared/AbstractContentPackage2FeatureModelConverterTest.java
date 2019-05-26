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
package org.apache.sling.feature.cpconverter.shared;

import static com.google.inject.Guice.createInjector;
import static com.google.inject.name.Names.named;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.inject.ContentPackage2FeatureModelConverterModule;
import org.apache.sling.feature.io.json.FeatureJSONReader;
import org.junit.Before;

import com.google.inject.AbstractModule;

public abstract class AbstractContentPackage2FeatureModelConverterTest extends AbstractModule {

    @Before
    public void setUp() {
        createInjector(this).injectMembers(this);
    }

    @Override
    protected void configure() {
        install(new ContentPackage2FeatureModelConverterModule());

        bindConstant().annotatedWith(named("packagemanager.validation.strict")).to(true);
        bindConstant().annotatedWith(named("features.configurations.merge")).to(false);
        bindConstant().annotatedWith(named("features.bundles.startOrder")).to(5);

        File outdir = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
        bind(File.class).annotatedWith(named("features.outdir")).toInstance(outdir);
        bind(File.class).annotatedWith(named("features.artifacts.outdir")).toInstance(outdir);
    }

    protected final void verifyFeatureFile(File outputDirectory,
                                           String name,
                                           String expectedArtifactId,
                                           List<String> expectedArtifacts,
                                           List<String> expectedConfigurations,
                                           List<String> expectedContentPackagesExtensions) throws Exception {
        File featureFile = new File(outputDirectory, name);
        assertTrue(featureFile + " was not correctly created", featureFile.exists());

        try (Reader reader = new FileReader(featureFile)) {
            Feature feature = FeatureJSONReader.read(reader, featureFile.getAbsolutePath());

            assertEquals(expectedArtifactId, feature.getId().toMvnId());

            for (String expectedArtifact : expectedArtifacts) {
                assertTrue(expectedArtifact + " not found in Feature " + expectedArtifactId, feature.getBundles().containsExact(ArtifactId.fromMvnId(expectedArtifact)));
                verifyInstalledArtifact(outputDirectory, expectedArtifact);
            }

            for (String expectedConfiguration : expectedConfigurations) {
                assertNotNull(expectedConfiguration + " not found in Feature " + expectedArtifactId, feature.getConfigurations().getConfiguration(expectedConfiguration));
            }

            for (String expectedContentPackagesExtension : expectedContentPackagesExtensions) {
                assertTrue(expectedContentPackagesExtension + " not found in Feature " + expectedArtifactId,
                           feature.getExtensions().getByName("content-packages").getArtifacts().containsExact(ArtifactId.fromMvnId(expectedContentPackagesExtension)));
                verifyInstalledArtifact(outputDirectory, expectedContentPackagesExtension);
            }
        }
    }

    private void verifyInstalledArtifact(File outputDirectory, String coordinates) {
        ArtifactId bundleId = ArtifactId.fromMvnId(coordinates);

        StringTokenizer tokenizer = new StringTokenizer(bundleId.getGroupId(), ".");
        while (tokenizer.hasMoreTokens()) {
            outputDirectory = new File(outputDirectory, tokenizer.nextToken());
        }

        outputDirectory = new File(outputDirectory, bundleId.getArtifactId());
        outputDirectory = new File(outputDirectory, bundleId.getVersion());

        StringBuilder bundleFileName = new StringBuilder()
                                       .append(bundleId.getArtifactId())
                                       .append('-')
                                       .append(bundleId.getVersion());
        if (bundleId.getClassifier() != null) {
            bundleFileName.append('-').append(bundleId.getClassifier());
        }
        bundleFileName.append('.').append(bundleId.getType());

        File bundleFile = new File(outputDirectory, bundleFileName.toString());
        assertTrue("Bundle " + bundleFile + " does not exist", bundleFile.exists());

        File pomFile = new File(outputDirectory, String.format("%s-%s.pom", bundleId.getArtifactId(), bundleId.getVersion()));
        assertTrue("POM file " + pomFile + " does not exist", pomFile.exists());
    }

}
