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
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import javax.xml.transform.sax.TransformerHandler;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import static org.apache.jackrabbit.JcrConstants.JCR_PRIMARYTYPE;

public class RepPolicyEntryHandler extends AbstractPolicyEntryHandler {

    public RepPolicyEntryHandler() {
        this("/jcr_root(.*/)_rep_policy.xml");
    }

    RepPolicyEntryHandler(@NotNull String regex) {
        super(regex);
    }

    @NotNull
    AbstractPolicyParser createPolicyParser(@NotNull RepoPath repositoryPath, @NotNull AclManager aclManager, @NotNull TransformerHandler handler) {
        return new RepPolicyParser(repositoryPath, aclManager, handler);
    }

    static final class RepPolicyParser extends AbstractPolicyParser {

        private static final String REP_ACL = "rep:ACL";
        private static final String REP_GRANT_ACE = "rep:GrantACE";
        private static final String REP_DENY_ACE = "rep:DenyACE";
        private static final Map<String, Boolean> operations = new HashMap<>();
        static {
            operations.put(REP_GRANT_ACE, true);
            operations.put(REP_DENY_ACE, false);
        }

        private final LinkedList<AccessControlEntry> entries = new LinkedList<>();

        // just internal pointer for every iteration
        private boolean processCurrentAce = false;

        public RepPolicyParser(RepoPath repositoryPath, AclManager aclManager, TransformerHandler handler) {
            super(REP_ACL, repositoryPath, aclManager, handler);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            if (onRepAclNode) {
                String primaryType = attributes.getValue(JCR_PRIMARYTYPE);
                if (isAccessControlEntry(primaryType)) {
                    String principalName = attributes.getValue(REP_PRINCIPAL_NAME);
                    AccessControlEntry ace = createEntry(operations.get(primaryType), attributes);
                    // handle restrictions added in jr2 format (i.e. not located below rep:restrictions node)
                    addRestrictions(ace, attributes);

                    processCurrentAce = aclManager.addAcl(principalName, ace);
                    if (processCurrentAce) {
                        entries.add(ace);
                    } else {
                        hasRejectedNodes = true;
                    }
                } else if (REP_RESTRICTIONS.equals(primaryType) && !entries.isEmpty() && processCurrentAce) {
                    AccessControlEntry ace = entries.peek();
                    entries.add(ace);
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
            if (onRepAclNode && processCurrentAce && !entries.isEmpty()) {
                entries.pop();
            } else {
                processCurrentAce = false;
                handler.endElement(uri, localName, qName);
            }
        }
        
        private static boolean isAccessControlEntry(@Nullable String primaryType) {
            return REP_GRANT_ACE.equals(primaryType) || REP_DENY_ACE.equals(primaryType);
        }
    }
}
