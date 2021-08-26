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
package org.apache.sling.feature.cpconverter.handlers;

import org.apache.jackrabbit.vault.util.PathUtil;
import org.apache.sling.feature.cpconverter.shared.AbstractJcrNodeParser;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

public class DefaultEntryParser extends AbstractJcrNodeParser<Set<String>> {
    
    private final LinkedList<String> currentPath = new LinkedList<>();
    private final Set<String> coveredNodePaths = new LinkedHashSet<>();
    
    
    public DefaultEntryParser(@NotNull String repositoryPath) {
        currentPath.push(repositoryPath);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        if (JCR_ROOT.equals(qName)) {
            return;
        }
        
        String path = PathUtil.append(currentPath.peek(), qName);
        currentPath.push(path);
        coveredNodePaths.add(path);
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        currentPath.pop();
    }

    @Override
    protected void onJcrRootElement(String uri, String localName, String qName, Attributes attributes) {
        // nothing to do
    }

    @Override
    public Set<String> getParsingResult() {
        return coveredNodePaths;
    }
}