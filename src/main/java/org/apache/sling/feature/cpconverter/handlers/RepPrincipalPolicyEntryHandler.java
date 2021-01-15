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

import org.apache.sling.feature.cpconverter.accesscontrol.AccessControlEntry;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import javax.xml.transform.sax.TransformerHandler;
import java.util.Stack;

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

        private static final String REP_PRINCIPAL_NAME = "rep:principalName";

        private static final String REP_PRIVILEGES = "rep:privileges";

        private static final String REP_PRINCIPAL_POLICY = "rep:PrincipalPolicy";

        private static final String REP_PRINCIPAL_ENTRY = "rep:PrincipalEntry";

        private static final String REP_EFFECTIVE_PATH = "rep:effectivePath";

        private final Stack<AccessControlEntry> aces = new Stack<>();

        private boolean processCurrentAcl = false;

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
                    String privileges = extractValue(attributes.getValue(REP_PRIVILEGES));
                    RepoPath effectivePath = new RepoPath(attributes.getValue(REP_EFFECTIVE_PATH));

                    AccessControlEntry ace = new AccessControlEntry(true, privileges, effectivePath, true);

                    processCurrentAcl = aclManager.addAcl(principalName, ace);
                    if (processCurrentAcl) {
                        aces.add(ace);
                    } else {
                        hasRejectedNodes = true;
                    }
                } else if (REP_RESTRICTIONS.equals(primaryType) && !aces.isEmpty() && processCurrentAcl) {
                    AccessControlEntry ace = aces.peek();
                    aces.add(ace);
                    addRestrictions(ace, attributes);
                }
            } else {
                super.startElement(uri, localName, qName, attributes);
            }

            if (!onRepAclNode || !processCurrentAcl) {
                handler.startElement(uri, localName, qName, attributes);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (onRepAclNode && processCurrentAcl && !aces.isEmpty()) {
                aces.pop();
            } else {
                processCurrentAcl = false;
                principalName = null;
                handler.endElement(uri, localName, qName);
            }
        }
    }
}
