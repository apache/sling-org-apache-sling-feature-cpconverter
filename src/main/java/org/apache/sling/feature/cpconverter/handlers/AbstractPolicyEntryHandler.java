/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.feature.cpconverter.handlers;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.jetbrains.annotations.NotNull;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Matcher;

abstract class AbstractPolicyEntryHandler extends AbstractRegexEntryHandler {

    private final SAXTransformerFactory saxTransformerFactory = (SAXTransformerFactory) TransformerFactory.newInstance();

    AbstractPolicyEntryHandler(@NotNull String regex) {
        super(regex);
    }

    @Override
    public void handle(@NotNull String path, @NotNull Archive archive, @NotNull Archive.Entry entry, @NotNull ContentPackage2FeatureModelConverter converter, String runMode)
            throws IOException {
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

        try {
            TransformerHandler handler = saxTransformerFactory.newTransformerHandler();
            handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
            handler.getTransformer().setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            handler.getTransformer().setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter stringWriter = new StringWriter();
            handler.setResult(new StreamResult(stringWriter));

            AbstractPolicyParser policyParser = createPolicyParser(new RepoPath(PlatformNameFormat.getRepositoryPath(resourcePath)),
                    converter.getAclManager(),
                    handler);
            boolean hasRejectedNodes;
            try (InputStream input = archive.openInputStream(entry)) {
                hasRejectedNodes = policyParser.parse(input);
            }

            if (hasRejectedNodes) {
                try (Reader reader = new StringReader(stringWriter.toString());
                    OutputStreamWriter writer = new OutputStreamWriter(converter.getMainPackageAssembler().createEntry(path))) {
                    IOUtils.copy(reader, writer);
                }
            }
        } catch ( final TransformerConfigurationException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @NotNull
    abstract AbstractPolicyParser createPolicyParser(@NotNull RepoPath repositoryPath, @NotNull AclManager aclManager, @NotNull TransformerHandler handler);
}