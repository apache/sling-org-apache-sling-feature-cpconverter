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

import static org.apache.jackrabbit.JcrConstants.JCR_PRIMARYTYPE;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.acl.Acl;
import org.apache.sling.feature.cpconverter.acl.AclManager;
import org.apache.sling.feature.cpconverter.shared.AbstractJcrNodeParser;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public final class RepPolicyEntryHandler extends AbstractRegexEntryHandler {

    public RepPolicyEntryHandler() {
        super("/jcr_root(.*/)_rep_policy.xml");
    }

    @Override
    public void handle(String path, Archive archive, Entry entry, ContentPackage2FeatureModelConverter converter)
            throws Exception {
        Matcher matcher = getPattern().matcher(path);
        // we are pretty sure it matches, here
        if (matcher.matches()) {
            path = matcher.group(1);
        } else {
            throw new IllegalStateException("Something went terribly wrong: pattern '"
                                            + getPattern().pattern()
                                            + "' should have matched already with path '"
                                            + path
                                            + "' but it does not, currently");
        }

        RepPolicyParser systemUserParser = new RepPolicyParser(path, converter.getAclManager());
        try (InputStream input = archive.openInputStream(entry)) {
            systemUserParser.parse(input);
        }
    }

    private static final class RepPolicyParser extends AbstractJcrNodeParser<Void> {

        private static final String REP_ACL = "rep:ACL";

        private static final String REP_GRANT_ACE = "rep:GrantACE";

        private static final String REP_DENY_ACE = "rep:DenyACE";

        private static final String REP_RESTRICTIONS = "rep:Restrictions";

        private static final String REP_PRINCIPAL_NAME = "rep:principalName";

        private static final String REP_PRIVILEGES = "rep:privileges";

        private static final String[] RESTRICTIONS = new String[] { "rep:glob", "rep:ntNames", "rep:prefixes", "rep:itemNames" };

        private static final Map<String, String> operations = new HashMap<>();

        static {
            operations.put(REP_GRANT_ACE, "allow");
            operations.put(REP_DENY_ACE, "deny");
        }

        private final Stack<Acl> acls = new Stack<>();

        private final String path;

        private final AclManager aclManager;

        private boolean onRepAclNode = false;

        public RepPolicyParser(String path, AclManager aclManager) {
            super(REP_ACL);
            this.path = path;
            this.aclManager = aclManager;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            if (onRepAclNode) {
                String primaryType = attributes.getValue(JCR_PRIMARYTYPE);
                if (REP_GRANT_ACE.equals(primaryType) || REP_DENY_ACE.equals(primaryType)) {
                    String principalName = attributes.getValue(REP_PRINCIPAL_NAME);

                    String operation = operations.get(primaryType);

                    String privileges = attributes.getValue(REP_PRIVILEGES);
                    int beginIndex = privileges.indexOf('[') + 1;
                    int endIndex = privileges.indexOf(']');
                    privileges = privileges.substring(beginIndex, endIndex);

                    Acl acl = new Acl(operation, privileges, Paths.get(path));

                    acls.add(aclManager.addAcl(principalName, acl));
                } else if (REP_RESTRICTIONS.equals(primaryType) && !acls.isEmpty()) {
                    for (String restriction : RESTRICTIONS) {
                        String path = attributes.getValue(restriction);

                        if (path != null && !path.isEmpty()) {
                            acls.peek().addRestriction(restriction + ',' + path);
                        }
                    }
                }
            } else {
                super.startElement(uri, localName, qName, attributes);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (onRepAclNode && !acls.isEmpty()) {
                acls.pop();
            }
        }

        @Override
        protected void onJcrRootElement(String uri, String localName, String qName, Attributes attributes) {
            onRepAclNode = true;
        }

        @Override
        protected Void getParsingResult() {
            return null;
        }

    }

}
