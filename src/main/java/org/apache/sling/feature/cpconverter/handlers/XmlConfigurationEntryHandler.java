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

import java.io.InputStream;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.sling.feature.cpconverter.shared.AbstractJcrNodeParser;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public final class XmlConfigurationEntryHandler extends AbstractConfigurationEntryHandler {

    public XmlConfigurationEntryHandler() {
        super("xml");
    }

    @Override
    protected Dictionary<String, Object> parseConfiguration(String name, InputStream input) throws Exception {
        JcrConfigurationHandler configurationHandler = new JcrConfigurationHandler();
        try {
            return configurationHandler.parse(input);
        } catch (Exception e) {
            logger.warn("Current OSGi configuration does not represent a valid XML document, see nested exceptions", e);
            return null;
        }
    }

    private static final class JcrConfigurationHandler extends AbstractJcrNodeParser<Dictionary<String, Object>> {

        private static final String SLING_OSGICONFIG = "sling:OsgiConfig";

        private Dictionary<String, Object> configuration = null;

        public JcrConfigurationHandler() {
            super(SLING_OSGICONFIG);
        }

        @Override
        protected void onJcrRootElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            configuration = new Hashtable<>();

            for (int i = 0; i < attributes.getLength(); i++) {
                String attributeQName = attributes.getQName(i);

                // ignore jcr: and similar properties
                if (attributeQName.indexOf(':') == -1) {
                    String attributeValue = attributes.getValue(i);

                    if (attributeValue != null && !attributeValue.isEmpty()) {
                        DocViewProperty property = DocViewProperty.parse(attributeQName, attributeValue);

                        if (property.values.length > 0) {
                            if (property.isMulti) {
                                configuration.put(attributeQName, property.values);
                            } else {
                                configuration.put(attributeQName, property.values[0]);
                            }
                        }
                    }
                }
            }
        }

        @Override
        protected Dictionary<String, Object> getParsingResult() {
            return configuration;
        }

    }

}
