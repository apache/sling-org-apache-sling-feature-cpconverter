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
package org.apache.sling.feature.cpconverter.accesscontrol;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public class Mapping {

    private final String serviceName;

    private final String subServiceName;

    private final String userName;

    private final Set<String> principalNames;

    /**
     * Copied from https://github.com/apache/sling-org-apache-sling-serviceusermapper/blob/master/src/main/java/org/apache/sling/serviceusermapping/Mapping.java
     */
    public Mapping(final String spec) {

        final int colon = spec.indexOf(':');
        final int equals = spec.indexOf('=');

        if (colon == 0 || equals <= 0) {
            throw new IllegalArgumentException("serviceName is required");
        } else if (equals == spec.length() - 1) {
            throw new IllegalArgumentException("userName or principalNames is required");
        } else if (colon + 1 == equals) {
            throw new IllegalArgumentException("serviceInfo must not be empty");
        }

        if (colon < 0 || colon > equals) {
            this.serviceName = spec.substring(0, equals);
            this.subServiceName = null;
        } else {
            this.serviceName = spec.substring(0, colon);
            this.subServiceName = spec.substring(colon + 1, equals);
        }

        String s = spec.substring(equals + 1);
        if (s.charAt(0) == '[' && s.charAt(s.length()-1) == ']') {
            this.userName = null;
            this.principalNames = extractPrincipalNames(s);
        } else {
            this.userName = s;
            this.principalNames = null;
        }
    }

    /**
     * Copied from https://github.com/apache/sling-org-apache-sling-serviceusermapper/blob/master/src/main/java/org/apache/sling/serviceusermapping/Mapping.java
     */
    private static Set<String> extractPrincipalNames(String s) {
        String[] sArr = s.substring(1, s.length() - 1).split(",");
        Set<String> set = new LinkedHashSet<>();
        for (String name : sArr) {
            String n = name.trim();
            if (!n.isEmpty()) {
                set.add(n);
            }
        }
        return set;
    }

    public boolean mapsUser(@NotNull String userId) {
        return userId.equals(this.userName);
    }

    public boolean mapsPrincipal(@NotNull String principalName) {
        return this.principalNames != null && principalNames.contains(principalName);
    }
}