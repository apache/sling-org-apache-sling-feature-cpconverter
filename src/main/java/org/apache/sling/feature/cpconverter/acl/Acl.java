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
package org.apache.sling.feature.cpconverter.acl;

import org.apache.sling.feature.cpconverter.shared.RepoPath;

import java.util.LinkedList;
import java.util.List;

/**
 * Simple single ACL statement representation.
 */
public final class Acl {

    private final String operation;

    private final String privileges;

    private final RepoPath path;

    private final RepoPath repositoryPath;

    private final List<String> restrictions = new LinkedList<>();

    public Acl(String operation, String privileges, RepoPath path, RepoPath repositoryPath) {
        this.operation = operation;
        this.privileges = privileges;
        this.path = path;
        this.repositoryPath = repositoryPath;
    }

    public void addRestriction(String restriction) {
        if (restriction != null && !restriction.isEmpty()) {
            restrictions.add(restriction);
        }
    }

    public String getOperation() {
        return operation;
    }

    public String getPrivileges() {
        return privileges;
    }

    public RepoPath getPath() {
        return path;
    }

    public RepoPath getRepositoryPath() {
        return repositoryPath;
    }

    public List<String> getRestrictions() {
        return restrictions;
    }

    @Override
    public String toString() {
        return "Acl [operation="
               + operation
               + ", privileges="
               + privileges
               + ", path="
               + path
               + ", restrictions="
               + restrictions
               + "]";
    }

}
