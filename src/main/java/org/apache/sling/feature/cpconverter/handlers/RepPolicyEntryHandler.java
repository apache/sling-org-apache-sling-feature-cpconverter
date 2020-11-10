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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.acl.Acl;
import org.apache.sling.feature.cpconverter.acl.AclManager;
import org.apache.sling.feature.cpconverter.shared.AbstractJcrNodeParser;
import org.apache.sling.feature.cpconverter.shared.RepoPath;

import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import static org.apache.jackrabbit.JcrConstants.JCR_PRIMARYTYPE;

public final class RepPolicyEntryHandler extends AbstractRegexEntryHandler {

    private final SAXTransformerFactory saxTransformerFactory = (SAXTransformerFactory) TransformerFactory.newInstance();

    public RepPolicyEntryHandler() {
        super("/jcr_root(.*/)_rep_policy.xml");
    }

    @Override
    public void handle(String path, Archive archive, Entry entry, ContentPackage2FeatureModelConverter converter)
            throws Exception {
        String resourcePath;
        Matcher matcher = getPattern().matcher(path);
        // we are pretty sure it matches, here
        if (matcher.matches()) {
            resourcePath = matcher.group(1);
        } else {
            throw new IllegalStateException("Something went terribly wrong: pattern '"
                                            + getPattern().pattern()
                                            + "' should have matched already with path '"
                                            + path
                                            + "' but it does not, currently");
        }

        TransformerHandler handler = saxTransformerFactory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.getTransformer().setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        handler.getTransformer().setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter stringWriter = new StringWriter();
        handler.setResult(new StreamResult(stringWriter));

        RepPolicyParser systemUserParser = new RepPolicyParser(new RepoPath(resourcePath),
                                                               new RepoPath(PlatformNameFormat.getRepositoryPath(resourcePath)),
                                                               converter.getAclManager(),
                                                               handler);
        boolean hasRejectedAcls;

        try (InputStream input = archive.openInputStream(entry)) {
            hasRejectedAcls = systemUserParser.parse(input);
        }

        if (hasRejectedAcls) {
            try (Reader reader = new StringReader(stringWriter.toString());
                    OutputStreamWriter writer = new OutputStreamWriter(converter.getMainPackageAssembler().createEntry(path))) {
                IOUtils.copy(reader, writer);
            }
        }
    }

    private static final class RepPolicyParser extends AbstractJcrNodeParser<Boolean> {

        private static final String REP_ACL = "rep:ACL";

        private static final String REP_GRANT_ACE = "rep:GrantACE";

        private static final String REP_DENY_ACE = "rep:DenyACE";

        private static final String REP_RESTRICTIONS = "rep:Restrictions";

        private static final String REP_PRINCIPAL_NAME = "rep:principalName";

        private static final String REP_PRIVILEGES = "rep:privileges";

        private static final Map<String, String> operations = new HashMap<>();

        static {
            operations.put(REP_GRANT_ACE, "allow");
            operations.put(REP_DENY_ACE, "deny");
        }

        private static final String[] RESTRICTIONS = new String[] { "rep:glob", "rep:ntNames", "rep:prefixes", "rep:itemNames" };

        private static final Pattern typeIndicatorPattern = Pattern.compile("\\{[^\\}]+\\}\\[(.+)\\]");

        private final Stack<Acl> acls = new Stack<>();

        private final RepoPath path;

        private final RepoPath repositoryPath;

        private final AclManager aclManager;

        private final TransformerHandler handler;

        private boolean onRepAclNode = false;

        // ACL processing result
        private boolean hasRejectedNodes = false;

        // just internal pointer for every iteration
        private boolean processCurrentAcl = false;

        public RepPolicyParser(RepoPath path, RepoPath repositoryPath, AclManager aclManager, TransformerHandler handler) {
            super(REP_ACL);
            this.path = path;
            this.repositoryPath = repositoryPath;
            this.aclManager = aclManager;
            this.handler = handler;
        }

        @Override
        public void startDocument() throws SAXException {
            handler.startDocument();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            if (onRepAclNode) {
                String primaryType = attributes.getValue(JCR_PRIMARYTYPE);
                if (REP_GRANT_ACE.equals(primaryType) || REP_DENY_ACE.equals(primaryType)) {
                    String principalName = attributes.getValue(REP_PRINCIPAL_NAME);

                    String operation = operations.get(primaryType);

                    String privileges = extractValue(attributes.getValue(REP_PRIVILEGES));

                    Acl acl = new Acl(operation, privileges, path, repositoryPath);

                    processCurrentAcl = aclManager.addAcl(principalName, acl);
                    if (processCurrentAcl) {
                        acls.add(acl);
                    } else {
                        hasRejectedNodes = true;
                    }
                } else if (REP_RESTRICTIONS.equals(primaryType) && !acls.isEmpty()) {
                    if (processCurrentAcl) {
                        acls.add(acls.peek());
                        for (String restriction : RESTRICTIONS) {
                            String path = extractValue(attributes.getValue(restriction));

                            if (path != null && !path.isEmpty()) {
                                acls.peek().addRestriction(restriction + ',' + path);
                            }
                        }
                    }
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
            if (onRepAclNode && processCurrentAcl && !acls.isEmpty()) {
                acls.pop();
            } else {
                processCurrentAcl = false;
                handler.endElement(uri, localName, qName);
            }
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

        private static String extractValue(String expression) {
            if (expression == null || expression.isEmpty()) {
                return expression;
            }

            Matcher matcher = typeIndicatorPattern.matcher(expression);
            if (matcher.matches()) {
                return matcher.group(1);
            }

            return expression;
        }

    }

}
