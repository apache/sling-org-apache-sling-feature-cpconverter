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

import org.apache.sling.feature.cpconverter.accesscontrol.AccessControlEntry;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.shared.AbstractJcrNodeParser;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import javax.xml.transform.sax.TransformerHandler;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class AbstractPolicyParser extends AbstractJcrNodeParser<Boolean> {

    static final String REP_RESTRICTIONS = "rep:Restrictions";
    static final String REP_PRINCIPAL_NAME = "rep:principalName";

    private static final String REP_PRIVILEGES = "rep:privileges";
    private static final String[] RESTRICTIONS = new String[] { "rep:glob", "rep:ntNames", "rep:prefixes", "rep:itemNames" };

    private static final Pattern typeIndicatorPattern = Pattern.compile("\\{[^\\}]+\\}\\[(.+)\\]");

    private final RepoPath repositoryPath;

    final TransformerHandler handler;
    final AclManager aclManager;

    boolean onRepAclNode = false;
    // ACL processing result
    boolean hasRejectedNodes = false;

    public AbstractPolicyParser(@NotNull String primaryType, @NotNull RepoPath repositoryPath, @NotNull AclManager aclManager, @NotNull TransformerHandler handler) {
        super(primaryType);
        this.handler = handler;
        this.repositoryPath = repositoryPath;
        this.aclManager = aclManager;
    }

    static @Nullable String extractValue(@Nullable String expression) {
        if (expression == null || expression.isEmpty()) {
            return expression;
        }

        Matcher matcher = typeIndicatorPattern.matcher(expression);
        if (matcher.matches()) {
            return matcher.group(1);
        }

        return expression;
    }

    static void addRestrictions(@NotNull AccessControlEntry ace, @NotNull Attributes attributes) {
        for (String restriction : RESTRICTIONS) {
            String v = extractValue(attributes.getValue(restriction));

            if (v != null && !v.isEmpty()) {
                ace.addRestriction(restriction + ',' + v);
            }
        }
    }

    AccessControlEntry createEntry(boolean isAllow, @NotNull Attributes attributes) {
        return new AccessControlEntry(isAllow, Objects.requireNonNull(extractValue(attributes.getValue(REP_PRIVILEGES))), repositoryPath);
    }

    @Override
    public void startDocument() throws SAXException {
        handler.startDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        handler.endDocument();
    }

    @Override
    protected void onJcrRootElement(String uri, String localName, String qName, Attributes attributes) {
        onRepAclNode = true;
    }

    @Override
    protected Boolean getParsingResult() {
        return hasRejectedNodes;
    }
}