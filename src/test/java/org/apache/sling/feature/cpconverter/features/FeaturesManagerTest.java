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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FeaturesManagerTest {

    private FeaturesManager featuresManager;

    @Before
    public void setUp() {
        featuresManager = new DefaultFeaturesManager();
    }

    @After
    public void tearDown() {
        featuresManager = null;
    }

    @Test(expected = IllegalStateException.class)
    public void getRunModeRequiresConvertInvoked() {
        featuresManager.getRunMode(null);
    }

    @Test(expected = NullPointerException.class)
    public void deployLocallyAndaddArtifactRequiresNonNullInput() throws Exception {
        featuresManager.addArtifact(null, null, null, null, null, null);
    }

    @Test(expected = NullPointerException.class)
    public void deployLocallyAndaddArtifactRequiresNonNullGroupId() throws Exception {
        featuresManager.addArtifact(null, null, null, null, null, null);
    }

    @Test(expected = NullPointerException.class)
    public void deployLocallyAndaddArtifactRequiresNonNullArtifactId() throws Exception {
        featuresManager.addArtifact(null, "org.apache.sling", null, null, null, null);
    }

    @Test(expected = NullPointerException.class)
    public void deployLocallyAndaddArtifactRequiresNonNullVersion() throws Exception {
        featuresManager.addArtifact(null, "org.apache.sling", "org.apache.sling.cm2fm", null, null, null);
    }

    @Test(expected = NullPointerException.class)
    public void deployLocallyAndaddArtifactRequiresNonNullType() throws Exception {
        featuresManager.addArtifact(null, "org.apache.sling", "org.apache.sling.cm2fm", "0.0.1", null, null);
    }

}
