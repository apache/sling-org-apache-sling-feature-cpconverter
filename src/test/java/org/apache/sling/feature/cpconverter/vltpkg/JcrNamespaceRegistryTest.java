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

import org.apache.jackrabbit.commons.cnd.ParseException;
import org.junit.Assert;
import org.junit.Test;

import javax.jcr.NamespaceException;
import javax.jcr.RepositoryException;
import java.io.IOException;

import static javax.jcr.NamespaceRegistry.NAMESPACE_XML;
import static javax.jcr.NamespaceRegistry.PREFIX_XML;

public class JcrNamespaceRegistryTest{
    
    

    public void test_unregister() throws RepositoryException, ParseException, IOException {
        JcrNamespaceRegistry jcrNamespaceRegistry = new JcrNamespaceRegistry();

        Assert.assertEquals(9, jcrNamespaceRegistry.getURIs().length);
        jcrNamespaceRegistry.unregisterNamespace(NAMESPACE_XML);
        Assert.assertEquals(8, jcrNamespaceRegistry.getURIs().length);

        //throws javax.jcr.NamespaceException: No URI for prefix 'xml' declared.
        Assert.assertNull(jcrNamespaceRegistry.getURI(PREFIX_XML));
    }
    
   
    public void test_get_prefix_exception() throws RepositoryException, ParseException, IOException {

        JcrNamespaceRegistry jcrNamespaceRegistry = new JcrNamespaceRegistry();
        Assert.assertNull(jcrNamespaceRegistry.getPrefix("_Asd"));
    }


    public void test_get_uri_exception() throws RepositoryException, ParseException, IOException {

        JcrNamespaceRegistry jcrNamespaceRegistry = new JcrNamespaceRegistry();
        Assert.assertNull(jcrNamespaceRegistry.getURI("_Asd"));
    }
    
}