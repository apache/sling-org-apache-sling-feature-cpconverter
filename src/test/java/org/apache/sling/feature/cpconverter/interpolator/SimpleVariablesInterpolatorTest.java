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
package org.apache.sling.feature.cpconverter.interpolator;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SimpleVariablesInterpolatorTest {

    private VariablesInterpolator interpolator;

    @Before
    public void setUp() {
        interpolator = new SimpleVariablesInterpolator();
    }

    @After
    public void tearDown() {
        interpolator = null;
    }

    @Test(expected = NullPointerException.class)
    public void doesNotAcceptNullInpoutString() {
        interpolator.interpolate(null, null);
    }

    @Test
    public void nullPropertiesReturnOriginalString() {
        doesNotIntepolate(null);
    }

    @Test
    public void emptyPropertiesReturnOriginalString() {
        doesNotIntepolate(new HashMap<String, String>());
    }

    @Test
    public void variableNotFoundWillNotBeInterpolated() {
        Map<String, String> properties = new HashMap<>();
        properties.put("that.variable", "just a sample value");
        doesNotIntepolate(properties);
    }

    private void doesNotIntepolate(Map<String, String> properties) {
        String expected = "${{this}}${{will}}${{not}}${{be}}${{interpolated}}";
        String actual = interpolator.interpolate(expected, properties);
        assertEquals(expected, actual);
    }

    @Test
    public void correctInterpolation() {
        Map<String, String> properties = new HashMap<>();
        properties.put("filename", "asd.test.dev");
        String format = "${project.groupId}:${project.artifactId}:slingosgifeature:${{filename}}:${project.version}";
        String expected = "${project.groupId}:${project.artifactId}:slingosgifeature:asd.test.dev:${project.version}";
        String actual = interpolator.interpolate(format, properties);
        assertEquals(expected, actual);
    }

}
