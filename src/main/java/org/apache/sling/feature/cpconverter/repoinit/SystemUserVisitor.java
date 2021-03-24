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

import org.apache.sling.feature.cpconverter.accesscontrol.EnforceInfo;
import org.apache.sling.repoinit.parser.operations.CreateServiceUser;
import org.jetbrains.annotations.NotNull;

import java.util.Formatter;

class SystemUserVisitor extends NoOpVisitor {

    private final Formatter formatter;
    private final EnforceInfo enforceInfo;

    SystemUserVisitor(@NotNull Formatter formatter, @NotNull EnforceInfo enforceInfo) {
        this.formatter = formatter;
        this.enforceInfo = enforceInfo;
    }
    @Override
    public void visitCreateServiceUser(CreateServiceUser createServiceUser) {
        String id = createServiceUser.getUsername();
        String path = createServiceUser.getPath();
        enforceInfo.recordSystemUserIds(id);

        if (enforceInfo.enforcePrincipalBased(id)) {
            formatter.format("create service user %s with forced path %s%n", id, enforceInfo.calculateEnforcedIntermediatePath(path));
        } else if (path == null || path.isEmpty()) {
            formatter.format("create service user %s%n", id);
        } else {
            String forced = (createServiceUser.isForcedPath()) ? "forced " : "";
            formatter.format("create service user %s with %spath %s%n", id, forced, path);
        }
    }
}