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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.Mapping;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.junit.Test;

public class JsonConfigurationEntryHandlerTest {

    @Test(expected = IOException.class)
    public void invalidConfigurationThrowsException() throws Exception {
        String resourceConfiguration = "/jcr_root/apps/asd/config/org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.INVALID.cfg.json";

        Archive archive = mock(Archive.class);
        Entry entry = mock(Entry.class);

        when(entry.getName()).thenReturn(resourceConfiguration.substring(resourceConfiguration.lastIndexOf('/') + 1));
        when(archive.openInputStream(entry)).thenReturn(getClass().getResourceAsStream(resourceConfiguration.substring(1)));

        try(ContentPackage2FeatureModelConverter converter = new ContentPackage2FeatureModelConverter()) {

            new JsonConfigurationEntryHandler().handle(resourceConfiguration, archive, entry, converter);
        }
    }

    @Test
    public void validConfigurationThrowsException() throws Exception {
        String resourceConfiguration = "/jcr_root/apps/asd/config/org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.cfg.json";

        Archive archive = mock(Archive.class);
        Entry entry = mock(Entry.class);

        when(entry.getName()).thenReturn(resourceConfiguration.substring(resourceConfiguration.lastIndexOf('/') + 1));
        when(archive.openInputStream(entry)).thenReturn(getClass().getResourceAsStream(resourceConfiguration.substring(1)));

        AclManager aclManager = mock(AclManager.class);
        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));
        FeaturesManager featuresManager = spy(DefaultFeaturesManager.class);
        when(featuresManager.getTargetFeature()).thenReturn(feature);

        try(ContentPackage2FeatureModelConverter converter = new ContentPackage2FeatureModelConverter()) {
            converter.setFeaturesManager(featuresManager);
            converter.setAclManager(aclManager);
            ((DefaultFeaturesManager) featuresManager).setAclManager(aclManager);

            new JsonConfigurationEntryHandler().handle(resourceConfiguration, archive, entry, converter);

            verify(aclManager, times(3)).addMapping(any(Mapping.class));
        }
    }

}
