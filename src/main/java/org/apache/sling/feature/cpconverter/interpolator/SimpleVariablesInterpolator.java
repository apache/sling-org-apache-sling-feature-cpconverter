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

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SimpleVariablesInterpolator implements VariablesInterpolator {

    private final Pattern replacementPattern = Pattern.compile("\\$\\{\\{(.+?)\\}\\}");

    public String interpolate(String format, Map<String, String> properties) {
        requireNonNull(format, "Input string format must be not null");

        if (properties == null || properties.isEmpty()) {
            return format;
        }

        Matcher matcher = replacementPattern.matcher(format);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String variable = matcher.group(1);
            String resolved = properties.get(variable);

            if (resolved == null) {
                resolved = String.format("\\$\\{\\{%s\\}\\}", variable);
            }

            matcher.appendReplacement(result, resolved);
        }
        matcher.appendTail(result);
        return result.toString();
    }

}
