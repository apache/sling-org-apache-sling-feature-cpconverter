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

import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.accesscontrol.EnforceInfo;
import org.apache.sling.repoinit.parser.impl.WithPathOptions;
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

        try {
            if (enforceInfo.enforcePrincipalBased(id)) {
                CreateServiceUser operation = new CreateServiceUser(id, new WithPathOptions(enforceInfo.calculateEnforcedIntermediatePath(path), true));
                formatter.format("%s", operation.asRepoInitString());
            } else {
                formatter.format("%s", createServiceUser.asRepoInitString());
            }    
        } catch ( final ConverterException ce) {
            throw new OperatorConverterException(ce);
        }
    }
}