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
package org.apache.sling.feature.cpconverter.vltpkg;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.StringWriter;
import java.util.Calendar;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.config.MetaInf;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.packaging.SubPackageHandling;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Test;

public class PackagesEventsEmitterTest {

    private static final PackageId ID_NESTED_CHILD = new PackageId("apache/sling", "nested-child", "1.0.0");
    private static final PackageId ID_APPLICATION_CHILD = new PackageId("apache/sling", "application-child", "1.0.0");
    private static final PackageId ID_CONTENT_CHILD = new PackageId("apache/sling", "content-child", "1.0.0");
    private static final PackageId ID_PARENT = new PackageId("apache/sling", "parent", "1.0.0");

    @Test
    public void justCheckEmissions() {
        VaultPackage parent = mock(VaultPackage.class);
        when(parent.getPackageType()).thenReturn(PackageType.MIXED);
        when(parent.getId()).thenReturn(ID_PARENT);
        File cpFile = new File("/org/apache/sling/content-package.zip").getAbsoluteFile();
		when(parent.getFile()).thenReturn(cpFile);
        when(parent.getDependencies()).thenReturn(new Dependency[0]);

        StringWriter stringWriter = new StringWriter();
        PackagesEventsEmitter emitter = new DefaultPackagesEventsEmitter(stringWriter);
        emitter.start();
        emitter.startPackage(parent);

        VaultPackage contentChild = mock(VaultPackage.class);
        when(contentChild.getPackageType()).thenReturn(PackageType.CONTENT);
        when(contentChild.getId()).thenReturn(ID_CONTENT_CHILD);
        when(contentChild.getDependencies()).thenReturn(new Dependency[]{new Dependency(ID_PARENT), new Dependency(ID_APPLICATION_CHILD)});
        emitter.startSubPackage("/jcr_root/etc/packages/org/apache/sling/content-child-1.0.zip", contentChild);
        emitter.endSubPackage();

        VaultPackage applicationChild = mock(VaultPackage.class);
        when(applicationChild.getPackageType()).thenReturn(PackageType.APPLICATION);
        when(applicationChild.getId()).thenReturn(ID_APPLICATION_CHILD);
        when(applicationChild.getDependencies()).thenReturn(new Dependency[]{new Dependency(ID_PARENT)});
        emitter.startSubPackage("/jcr_root/etc/packages/org/apache/sling/application-child-1.0.zip", applicationChild);

        VaultPackage nestedChild = mock(VaultPackage.class);
        when(nestedChild.getPackageType()).thenReturn(PackageType.CONTAINER);
        when(nestedChild.getId()).thenReturn(ID_NESTED_CHILD);
        when(nestedChild.getDependencies()).thenReturn(new Dependency[]{new Dependency(ID_APPLICATION_CHILD)});
        emitter.startSubPackage("/jcr_root/etc/packages/org/apache/sling/nested-child-1.0.zip", nestedChild);
        emitter.endSubPackage();

        // applicationChild
        emitter.endSubPackage();

        emitter.endPackage();
        emitter.end();

        String actual = stringWriter.toString();

        String expected = cpFile + ",apache/sling:parent:1.0.0,MIXED,,," + System.lineSeparator() + 
        		cpFile + ",apache/sling:application-child:1.0.0,APPLICATION,apache/sling:parent:1.0.0,/jcr_root/etc/packages/org/apache/sling/application-child-1.0.zip," + cpFile + "!/jcr_root/etc/packages/org/apache/sling/application-child-1.0.zip" + System.lineSeparator() + 
        		cpFile + ",apache/sling:content-child:1.0.0,CONTENT,apache/sling:parent:1.0.0,/jcr_root/etc/packages/org/apache/sling/content-child-1.0.zip," + cpFile + "!/jcr_root/etc/packages/org/apache/sling/content-child-1.0.zip" + System.lineSeparator() + 
        		cpFile + ",apache/sling:nested-child:1.0.0,CONTAINER,apache/sling:application-child:1.0.0,/jcr_root/etc/packages/org/apache/sling/nested-child-1.0.zip," + cpFile + "!/jcr_root/etc/packages/org/apache/sling/application-child-1.0.zip!/jcr_root/etc/packages/org/apache/sling/nested-child-1.0.zip" + System.lineSeparator();
        assertTrue(actual.endsWith(expected));
    }
    
    @Test
    public void coverDepOnlyPackage() throws RepositoryException, PackageException {
        VaultPackage pkg = DefaultPackagesEventsEmitter.getDepOnlyPackage(ID_NESTED_CHILD, new Dependency[0]);
        assertFalse(pkg.requiresRoot());
        assertNull(pkg.getSubPackageHandling());
        assertNull(pkg.getProperty(null));
        assertNull(pkg.getPackageType());
        assertNull(pkg.getLastWrappedBy());
        assertNull(pkg.getLastWrapped());
        assertNull(pkg.getLastModifiedBy());
        assertNull(pkg.getLastModified());
        assertNull(pkg.getDescription());
        assertNull(pkg.getDateProperty(null));
        assertNull(pkg.getCreatedBy());
        assertNull(pkg.getCreated());
        assertNull(pkg.getACHandling());
        assertFalse(pkg.isValid());
        assertFalse(pkg.isClosed());
        assertEquals(0, pkg.getSize());
        assertNull(pkg.getProperties());
        assertNull(pkg.getMetaInf());
        assertNull(pkg.getFile());
        assertNull(pkg.getArchive());
        pkg.extract(null, null);
        pkg.close();
    }

}
