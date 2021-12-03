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
package org.apache.sling.feature.cpconverter.repoinit;

import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.repoinit.parser.impl.RepoInitParserService;
import org.apache.sling.repoinit.parser.operations.Operation;

import java.io.StringReader;
import java.util.Formatter;
import java.util.List;

public class Util {
    public static String normalize(String repoinit) throws RepoInitParsingException {
        RepoInitParser parser = new RepoInitParserService();
        List<Operation> operations = parser.parse(new StringReader(repoinit));
        try ( Formatter formatter = new Formatter()) {
            for (Operation op : operations) {
                formatter.format("%s", op.asRepoInitString());
            }
            return formatter.out().toString();    
        }
    }

    public static String normalizeUnchecked(String repoinit) {
        try {
            return normalize(repoinit);
        } catch (RepoInitParsingException e) {
            throw new RuntimeException(e);
        }
    }
}
