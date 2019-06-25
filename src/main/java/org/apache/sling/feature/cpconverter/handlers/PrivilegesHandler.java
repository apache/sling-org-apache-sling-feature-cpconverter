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

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.acl.AclManager;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class PrivilegesHandler extends AbstractRegexEntryHandler {

    private static final String PRIVILEGE = "privilege";

    private static final String NAME = "name";

    private final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();

    public PrivilegesHandler() {
        super("META-INF/vault/privileges\\.xml");
    }

    @Override
    public void handle(String path, Archive archive, Entry entry, ContentPackage2FeatureModelConverter converter)
            throws Exception {
        SAXParser saxParser = saxParserFactory.newSAXParser();
        AclManager aclManager = converter.getAclManager();
        PrivilegeHandler handler = new PrivilegeHandler(aclManager);
        saxParser.parse(archive.openInputStream(entry), handler);
    }

    private static final class PrivilegeHandler extends DefaultHandler {

        private final AclManager aclManager;

        public PrivilegeHandler(AclManager aclManager) {
            this.aclManager = aclManager;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            if (PRIVILEGE.equals(qName)) {
                String privilege = attributes.getValue(NAME);
                if (privilege != null && !privilege.isEmpty()) {
                    aclManager.addPrivilege(privilege);
                }
            }
        }

    }

}
