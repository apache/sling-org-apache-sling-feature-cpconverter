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
package org.apache.sling.feature.cpconverter.repoinit;

import org.apache.sling.repoinit.parser.operations.AclLine;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class ConversionMap {

    private final Map<Key, List<AclLine>> map = new LinkedHashMap<>();

    void put(@NotNull String principalName, @NotNull String options, @NotNull AclLine line) {
        List<AclLine> lineList = map.computeIfAbsent(new Key(principalName, options), k -> new ArrayList<>());
        lineList.add(line);
    }

    void putAll(@NotNull String principalName, @NotNull String options, @NotNull Collection<AclLine> lines) {
        List<AclLine> lineList = map.computeIfAbsent(new Key(principalName, options), k -> new ArrayList<>());
        lineList.addAll(lines);
    }

    void generateRepoInit(@NotNull Formatter formatter) {
        for (Map.Entry<Key, List<AclLine>> entry : map.entrySet()) {
            String principalName = entry.getKey().principalName;
            String options = entry.getKey().options;
            AccessControlVisitor.generateRepoInit(formatter, "set principal ACL for %s%s%n", true, principalName, options, entry.getValue());
        }
        map.clear();
    }

    private static final class Key {
        private final String principalName;
        private final String options;

        private Key(@NotNull String principalName, @NotNull String options) {
            this.principalName = principalName;
            this.options = options;
        }

        @Override
        public int hashCode() {
            return Objects.hash(principalName, options);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof Key) {
                Key other = (Key) obj;
                return principalName.equals(other.principalName) && options.equals(other.options);
            } else {
                return false;
            }
        }
    }
}