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

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class DefaultHandlerTest {
    
    private final VaultPackageAssembler assembler = mock(VaultPackageAssembler.class);
    
    @Test
    public void testMatch() {
        DefaultHandler handler = new DefaultHandler(assembler, true);
        assertTrue(handler.matches(""));
        assertTrue(handler.matches("/"));
        assertTrue(handler.matches("/jcr_root/.content.xml"));
        assertTrue(handler.matches("/jcr_root/content"));
        assertTrue(handler.matches("/jcr_root/apps"));
        assertTrue(handler.matches("/jcr_root/content/_rep_policy.xml"));

        verifyNoInteractions(assembler);
    }
    
    @Test
    public void testHandleInstallHooksTrue() throws Exception {
        Archive archive = mock(Archive.class);
        Archive.Entry entry = mock(Archive.Entry.class);
        try(ContentPackage2FeatureModelConverter converter = new ContentPackage2FeatureModelConverter()) {

            DefaultHandler handler = new DefaultHandler(assembler, true);
            handler.handle("/" + Constants.META_DIR + "/" + Constants.HOOKS_DIR, archive, entry, converter);
            
            verifyNoInteractions(assembler, archive, entry);
        }
    }

    @Test
    public void testHandleInstallHooksFalse() throws Exception {
        Archive archive = mock(Archive.class);
        Archive.Entry entry = mock(Archive.Entry.class);
        try(ContentPackage2FeatureModelConverter converter = new ContentPackage2FeatureModelConverter()) {
            String path = "/" + Constants.META_DIR + "/" + Constants.HOOKS_DIR + "/subdir";

            DefaultHandler handler = new DefaultHandler(assembler, false);
            handler.handle(path, archive, entry, converter);

            verifyNoInteractions(archive, entry);
            verify(assembler).addEntry(path, archive, entry);
        }
    }

    @Test
    public void testHandleRegularPath() throws Exception {
        Archive archive = mock(Archive.class);
        Archive.Entry entry = mock(Archive.Entry.class);
        try(ContentPackage2FeatureModelConverter converter = new ContentPackage2FeatureModelConverter()) {
            String path = "/" + Constants.ROOT_DIR + "/content" + Constants.DOT_CONTENT_XML;

            DefaultHandler handler = new DefaultHandler(assembler, true);
            handler.handle(path, archive, entry, converter);

            verifyNoInteractions(archive, entry);
            verify(assembler).addEntry(path, archive, entry);
        }
    }
}