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
package org.apache.sling.feature.cpconverter;

import static com.google.inject.name.Names.named;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertNotNull;

import org.apache.commons.io.FileUtils;
import org.apache.sling.feature.cpconverter.artifacts.ArtifactsDeployer;
import org.apache.sling.feature.cpconverter.inject.ContentPackage2FeatureModelConverterModule;
import org.apache.sling.feature.cpconverter.shared.AbstractContentPackage2FeatureModelConverterTest;
import org.junit.Test;

import com.google.inject.Inject;

public class IdOverrideTest extends AbstractContentPackage2FeatureModelConverterTest {

    @Inject
    private ContentPackage2FeatureModelConverter converter;

    @Inject
    private ArtifactsDeployer artifactsDeployer;

    @Override
    protected void configure() {
        super.configure();

        install(new ContentPackage2FeatureModelConverterModule());

        bindConstant().annotatedWith(named("packagemanager.validation.strict")).to(true);
        bindConstant().annotatedWith(named("features.configurations.merge")).to(true);
        bindConstant().annotatedWith(named("features.bundles.startOrder")).to(5);

        bindConstant().annotatedWith(named("features.artifacts.idoverride")).to("${project.groupId}:${project.artifactId}:slingosgifeature:asd.test.all-1.0.0:${project.version}");
    }

    @Test
    public void overrideFeatureId() throws Exception {
        URL packageUrl = getClass().getResource("test-content-package.zip");
        File packageFile = FileUtils.toFile(packageUrl);

        File outputDirectory = artifactsDeployer.getBundlesDirectory();

        converter.convert(packageFile);

        verifyFeatureFile(outputDirectory,
                          "asd.retail.all.json",
                          "${project.groupId}:${project.artifactId}:slingosgifeature:asd.test.all-1.0.0:${project.version}",
                          Arrays.asList("org.apache.felix:org.apache.felix.framework:6.0.1"),
                          Arrays.asList("org.apache.sling.commons.log.LogManager.factory.config~asd-retail"),
                          Arrays.asList("asd.sample:asd.retail.all:zip:cp2fm-converted:0.0.1"));
        verifyFeatureFile(outputDirectory,
                          "asd.retail.all-author.json",
                          "${project.groupId}:${project.artifactId}:slingosgifeature:asd.test.all-1.0.0-author:${project.version}",
                          Arrays.asList("org.apache.sling:org.apache.sling.api:2.20.0"),
                          Collections.emptyList(),
                          Collections.emptyList());
        verifyFeatureFile(outputDirectory,
                          "asd.retail.all-publish.json",
                          "${project.groupId}:${project.artifactId}:slingosgifeature:asd.test.all-1.0.0-publish:${project.version}",
                          Arrays.asList("org.apache.sling:org.apache.sling.models.api:1.3.8"),
                          Arrays.asList("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended~asd-retail"),
                          Collections.emptyList());

        ZipFile zipFile = new ZipFile(new File(outputDirectory, "asd/sample/asd.retail.all/0.0.1/asd.retail.all-0.0.1-cp2fm-converted.zip"));
        for (String expectedEntry : new String[] {
                "jcr_root/content/asd/.content.xml",
                "jcr_root/content/asd/resources.xml",
                "jcr_root/apps/.content.xml",
                "META-INF/vault/properties.xml",
                "META-INF/vault/config.xml",
                "META-INF/vault/settings.xml",
                "META-INF/vault/filter.xml",
                "META-INF/vault/definition/.content.xml",
                "jcr_root/etc/packages/asd/test-bundles.zip",
                "jcr_root/etc/packages/asd/test-configurations.zip",
                "jcr_root/etc/packages/asd/test-content.zip",
                }) {
            assertNotNull(zipFile.getEntry(expectedEntry));
        }
        zipFile.close();
    }

}
