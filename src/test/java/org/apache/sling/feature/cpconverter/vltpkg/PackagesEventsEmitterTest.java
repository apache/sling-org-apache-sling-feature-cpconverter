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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.StringWriter;

import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.Test;

public class PackagesEventsEmitterTest {

    @Test
    public void justCheckEmissions() {
        VaultPackage parent = mock(VaultPackage.class);
        when(parent.getPackageType()).thenReturn(PackageType.MIXED);
        when(parent.getId()).thenReturn(new PackageId("apache/sling", "parent", "1.0.0"));
        when(parent.getFile()).thenReturn(new File("/org/apache/sling/content-package.zip"));

        StringWriter stringWriter = new StringWriter();
        PackagesEventsEmitter emitter = new DefaultPackagesEventsEmitter(stringWriter);
        emitter.start();
        emitter.startPackage(parent);

        VaultPackage contentChild = mock(VaultPackage.class);
        when(contentChild.getPackageType()).thenReturn(PackageType.CONTENT);
        when(contentChild.getId()).thenReturn(new PackageId("apache/sling", "content-child", "1.0.0"));
        emitter.startSubPackage("/jcr_root/etc/packages/org/apache/sling/content-child-1.0.zip", contentChild);
        emitter.endSubPackage();

        VaultPackage applicationChild = mock(VaultPackage.class);
        when(applicationChild.getPackageType()).thenReturn(PackageType.APPLICATION);
        when(applicationChild.getId()).thenReturn(new PackageId("apache/sling", "application-child", "1.0.0"));
        emitter.startSubPackage("/jcr_root/etc/packages/org/apache/sling/application-child-1.0.zip", applicationChild);

        VaultPackage nestedChild = mock(VaultPackage.class);
        when(nestedChild.getPackageType()).thenReturn(PackageType.CONTAINER);
        when(nestedChild.getId()).thenReturn(new PackageId("apache/sling", "nested-child", "1.0.0"));
        emitter.startSubPackage("/jcr_root/etc/packages/org/apache/sling/nested-child-1.0.zip", nestedChild);
        emitter.endSubPackage();

        // applicationChild
        emitter.endSubPackage();

        emitter.endPackage();
        emitter.end();

        String actual = stringWriter.toString();

        String expected = "/org/apache/sling/content-package.zip,apache/sling:parent:1.0.0,MIXED,,,\n" + 
                "/org/apache/sling/content-package.zip,apache/sling:content-child:1.0.0,CONTENT,apache/sling:parent:1.0.0,/jcr_root/etc/packages/org/apache/sling/content-child-1.0.zip,/org/apache/sling/content-package.zip!/jcr_root/etc/packages/org/apache/sling/content-child-1.0.zip\n" + 
                "/org/apache/sling/content-package.zip,apache/sling:application-child:1.0.0,APPLICATION,apache/sling:parent:1.0.0,/jcr_root/etc/packages/org/apache/sling/application-child-1.0.zip,/org/apache/sling/content-package.zip!/jcr_root/etc/packages/org/apache/sling/application-child-1.0.zip\n" + 
                "/org/apache/sling/content-package.zip,apache/sling:nested-child:1.0.0,CONTAINER,apache/sling:application-child:1.0.0,/jcr_root/etc/packages/org/apache/sling/nested-child-1.0.zip,/org/apache/sling/content-package.zip!/jcr_root/etc/packages/org/apache/sling/application-child-1.0.zip!/jcr_root/etc/packages/org/apache/sling/nested-child-1.0.zip\n";
        assertTrue(actual.endsWith(expected));
    }

}
