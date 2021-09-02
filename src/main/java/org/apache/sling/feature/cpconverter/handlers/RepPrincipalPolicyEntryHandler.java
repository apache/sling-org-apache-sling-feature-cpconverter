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
package org.apache.sling.feature.cpconverter.handlers;

import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.sling.feature.cpconverter.accesscontrol.AccessControlEntry;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import javax.xml.transform.sax.TransformerHandler;
import java.util.LinkedList;
import java.util.List;

import static org.apache.jackrabbit.JcrConstants.JCR_PRIMARYTYPE;

public final class RepPrincipalPolicyEntryHandler extends AbstractPolicyEntryHandler {

    public RepPrincipalPolicyEntryHandler() {
        super("/jcr_root(.*/)_rep_principalPolicy.xml");
    }

    @Override
    @NotNull AbstractPolicyParser createPolicyParser(@NotNull RepoPath repositoryPath, @NotNull AclManager aclManager, @NotNull TransformerHandler handler) {
        return new RepPrincipalPolicyParser(repositoryPath,
                aclManager,
                handler);
    }

    private static final class RepPrincipalPolicyParser extends AbstractPolicyParser {

        private static final String REP_RESTRICTIONS = "rep:Restrictions";

        private static final String REP_PRINCIPAL_POLICY = "rep:PrincipalPolicy";

        private static final String REP_PRINCIPAL_ENTRY = "rep:PrincipalEntry";

        private static final String REP_EFFECTIVE_PATH = "rep:effectivePath";

        private final LinkedList<AccessControlEntry> aces = new LinkedList<>();

        private boolean processCurrentAce = false;

        private String principalName = null;

        public RepPrincipalPolicyParser(RepoPath repositoryPath, AclManager aclManager, TransformerHandler handler) {
            super(REP_PRINCIPAL_POLICY, repositoryPath, aclManager, handler);
        }

        @Override
        protected void onJcrRootElement(String uri, String localName, String qName, Attributes attributes) {
            super.onJcrRootElement(uri, localName, qName, attributes);
            principalName = attributes.getValue(REP_PRINCIPAL_NAME);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            if (onRepAclNode) {
                String primaryType = attributes.getValue(JCR_PRIMARYTYPE);
                if (REP_PRINCIPAL_ENTRY.equals(primaryType)) {
                    if (principalName == null) {
                        throw new IllegalStateException("isolated principal-based access control entry. no principal found.");
                    }
                    List<String> privileges = extractValues(attributes.getValue(REP_PRIVILEGES));
                    RepoPath effectivePath = new RepoPath(extractEffectivePath(attributes.getValue(REP_EFFECTIVE_PATH)));

                    AccessControlEntry ace = new AccessControlEntry(true, privileges, effectivePath, true);
                    // NOTE: nt-definition doesn't allow for jr2-type restrictions defined right below the entry.
                    // instead always requires rep:restrictions child node
                    processCurrentAce = aclManager.addAccessControlEntry(principalName, ace);
                    if (processCurrentAce) {
                        aces.add(ace);
                    } else {
                        hasRejectedNodes = true;
                    }
                } else if (REP_RESTRICTIONS.equals(primaryType) && !aces.isEmpty() && processCurrentAce) {
                    AccessControlEntry ace = aces.peek();
                    aces.add(ace);
                    addRestrictions(ace, attributes);
                }
            } else {
                super.startElement(uri, localName, qName, attributes);
            }

            if (!onRepAclNode || !processCurrentAce) {
                handler.startElement(uri, localName, qName, attributes);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (onRepAclNode && processCurrentAce && !aces.isEmpty()) {
                aces.pop();
            } else {
                processCurrentAce = false;
                principalName = null;
                handler.endElement(uri, localName, qName);
            }
        }

        @Override
        boolean isRestriction(@NotNull String attributeName) {
            if ((REP_EFFECTIVE_PATH.equals(attributeName))) {
                return false;
            } else {
                return super.isRestriction(attributeName);
            }
        }
        
        @NotNull
        private static String extractEffectivePath(@Nullable String value) {
            if (value == null || value.isEmpty()) {
                return "";
            }
            return DocViewProperty.parse(REP_EFFECTIVE_PATH, value).values[0];
        }
    }
}
