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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.Objects;
import java.util.function.Function;

import javax.jcr.PropertyType;

import org.apache.felix.cm.json.Configurations;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.apache.sling.feature.cpconverter.shared.AbstractJcrNodeParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;

public final class XmlConfigurationEntryHandler extends AbstractConfigurationEntryHandler {

    public XmlConfigurationEntryHandler() {
        super("xml");
    }

    @Override
    protected @Nullable Dictionary<String, Object> parseConfiguration(@NotNull String name, @NotNull InputStream input) {
        try {
            return new JcrConfigurationParser().parse(input);
        } catch (Exception e) {
            logger.warn("Current OSGi configuration does not represent a valid XML document, see nested exceptions", e);
            return null;
        }
    }

    private static final class JcrConfigurationParser extends AbstractJcrNodeParser<Dictionary<String, Object>> {

        private static final String SLING_OSGICONFIG = "sling:OsgiConfig";

        private Dictionary<String, Object> configuration;

        public JcrConfigurationParser() {
            super(SLING_OSGICONFIG);
        }

        @Override
        protected void onJcrRootElement(String uri, String localName, String qName, Attributes attributes) {
            configuration = Configurations.newConfiguration();

            for (int i = 0; i < attributes.getLength(); i++) {
                String attributeQName = attributes.getQName(i);

                // ignore jcr: and similar properties
                if (attributeQName.indexOf(':') == -1) {
                    String attributeValue = attributes.getValue(i);
                    if (isValid(attributeValue)) {
                        DocViewProperty property = DocViewProperty.parse(attributeQName, attributeValue);
                        Object[] values = getValues(property);
                        if (values.length == 0) {
                            // ignore empty values (either property.values were empty or value mapping resulted in null 
                            // results that got filtered)
                            continue;
                        }
                        if (!property.isMulti) {
                            // first element to be used in case of single-value property
                            configuration.put(attributeQName, values[0]);
                        } else {
                            configuration.put(attributeQName, values);
                        }
                    }
                }
            }
        }
        
        private static boolean isValid(@Nullable String s) {
            return !(s == null || s.isEmpty());
        }
        
        @NotNull 
        private static Object[] getValues(@NotNull DocViewProperty property) {
            Object[] values;
            switch (property.type) {
                case PropertyType.DATE:
                    // Date was never properly supported as osgi configs don't support dates so converting to millis 
                    // Scenario should just be theoretical
                    values = mapValues(property.values, s -> {
                        Calendar cal = ISO8601.parse(s);
                        return (cal != null) ? cal.getTimeInMillis() : null;
                    });
                    break;
                case PropertyType.DOUBLE:
                    values = mapValues(property.values, Double::parseDouble);
                    break;
                case PropertyType.LONG:
                    values = mapValues(property.values, Long::parseLong);
                    break;
                case PropertyType.BOOLEAN:
                    values = mapValues(property.values, Boolean::valueOf);
                    break;
                default:
                    values = property.values;
            }
            return values;
        }
        
        @NotNull 
        private static Object[] mapValues(@NotNull String[] strValues, Function<String, Object> function) {
            return Arrays.stream(strValues).filter(JcrConfigurationParser::isValid).map(function).filter(Objects::nonNull).toArray();
        }

        @Override
        protected @NotNull Dictionary<String, Object> getParsingResult() {
            return configuration;
        }
    }

}
