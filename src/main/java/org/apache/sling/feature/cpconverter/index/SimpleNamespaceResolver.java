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
package org.apache.sling.feature.cpconverter.index;

import javax.jcr.NamespaceException;

import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;

/**
 * A simple resolver that is aware of the default {@value #OAK_PREFIX} prefix and namespace
 */
public class SimpleNamespaceResolver implements NamespaceResolver {

    // copied from oak-spi-core/NamespaceConstants to not add a dependency on Oak
    static final String OAK_PREFIX = "oak";
    static final String OAK_NAMESPACE = "http://jackrabbit.apache.org/oak/ns/1.0";

    @Override
    public String getURI(String prefix) throws NamespaceException {
        if ( OAK_PREFIX.equals(prefix) )
            return OAK_NAMESPACE;
        return null;
    }

    @Override
    public String getPrefix(String uri) throws NamespaceException {
        if (OAK_NAMESPACE.equals(uri) )
            return OAK_PREFIX;

        return null;
    }
}