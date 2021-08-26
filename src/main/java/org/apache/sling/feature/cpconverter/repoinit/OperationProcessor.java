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
import org.apache.sling.repoinit.parser.operations.Operation;
import org.apache.sling.repoinit.parser.operations.OperationVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.Formatter;
import java.util.List;

public class OperationProcessor {

    public void apply(@NotNull List<Operation> ops, @NotNull Formatter formatter, @NotNull EnforceInfo enforceInfo) throws ConverterException {
        try {
            ConversionMap toConvert = new ConversionMap();

            OperationVisitor[] visitors = {
                    new DefaultVisitor(formatter),
                    new SystemUserVisitor(formatter, enforceInfo),
                    new AccessControlVisitor(formatter, enforceInfo, toConvert)
            };
    
            for (Operation op : ops) {
                for (OperationVisitor v : visitors) {
                    op.accept(v);
                }
            }
    
            // finally generate repo-init statements for acl-statements that are recorded as to be
            // converted to principal-based setup.
            toConvert.generateRepoInit(formatter);    
        } catch ( final OperatorConverterException oce) {
            throw oce.getConverterException();
        }
    }
}