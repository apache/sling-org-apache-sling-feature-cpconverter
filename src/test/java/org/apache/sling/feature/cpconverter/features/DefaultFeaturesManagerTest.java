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
package org.apache.sling.feature.cpconverter.features;

import static org.junit.Assert.assertEquals;

import org.apache.sling.feature.ArtifactId;
import org.junit.Test;

public class DefaultFeaturesManagerTest {

    @Test
    public void testGetFeatureFileName() {
        ArtifactId id = ArtifactId.parse("org.apache.sling:feature-name:1.0.0");
        assertEquals("org.apache.sling-feature-name", DefaultFeaturesManager.getFeatureFileName(id, null));
        id = ArtifactId.parse("org.apache.sling:feature-name:osgi-feature:myclassifier:1.0.0");
        assertEquals("org.apache.sling-feature-name-myclassifier", DefaultFeaturesManager.getFeatureFileName(id, ""));
        id = ArtifactId.parse("org.apache.sling:feature-name:1.0.0");
        assertEquals("prefix-org.apache.sling-feature-name", DefaultFeaturesManager.getFeatureFileName(id, "prefix-"));
    }

}
