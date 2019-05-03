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

import java.io.InputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public abstract class AbstractJcrNodeParser<O> extends DefaultHandler {

    private static final String JCR_ROOT = "jcr:root";

    private static final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();

    private final String primaryType;

    public AbstractJcrNodeParser(String primaryType) {
        this.primaryType = primaryType;
    }

    public O parse(InputStream input) throws Exception {
        SAXParser saxParser = saxParserFactory.newSAXParser();
        saxParser.parse(input, this);
        return getParsingResult();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (JCR_ROOT.equals(qName)) {
            String primaryType = attributes.getValue(JCR_PRIMARYTYPE);
            onJcrRootNode(uri, localName, qName, attributes, primaryType);
        }
    }

    protected final String getPrimaryType() {
        return primaryType;
    }

    protected void onJcrRootNode(String uri, String localName, String qName, Attributes attributes, String primaryType) throws SAXException {
        if (this.primaryType.equals(primaryType)) {
            onJcrRootElement(uri, localName, qName, attributes);
        }
    }

    protected abstract void onJcrRootElement(String uri, String localName, String qName, Attributes attributes) throws SAXException;

    protected abstract O getParsingResult();

}
