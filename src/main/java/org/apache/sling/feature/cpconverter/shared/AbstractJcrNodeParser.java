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
package org.apache.sling.feature.cpconverter.shared;

import static org.apache.jackrabbit.JcrConstants.JCR_PRIMARYTYPE;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public abstract class AbstractJcrNodeParser<O> extends DefaultHandler {

    public static final String JCR_ROOT = "jcr:root";

    private static final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();

    private final List<String> primaryTypes;

    protected String detectedPrimaryType;

    protected AbstractJcrNodeParser(@NotNull String... primaryTypes) {
        this.primaryTypes = Arrays.asList(primaryTypes);
    }

    public O parse(InputStream input) throws IOException {
        try {
            SAXParser saxParser = saxParserFactory.newSAXParser();
            saxParser.parse(input, this);
            return getParsingResult();    
        } catch ( final ParserConfigurationException | SAXException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (JCR_ROOT.equals(qName)) {
            String primaryType = attributes.getValue(JCR_PRIMARYTYPE);
            onJcrRootNode(uri, localName, qName, attributes, primaryType);
        }
    }

    protected void onJcrRootNode(String uri, String localName, String qName, Attributes attributes, String primaryType) {
        if (this.primaryTypes.contains(primaryType)) {
            detectedPrimaryType = primaryType;
            onJcrRootElement(uri, localName, qName, attributes);
        }
    }

    protected abstract void onJcrRootElement(String uri, String localName, String qName, Attributes attributes);

    protected abstract O getParsingResult();
}
