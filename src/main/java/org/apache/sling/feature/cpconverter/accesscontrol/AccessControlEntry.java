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
package org.apache.sling.feature.cpconverter.accesscontrol;

import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.apache.sling.repoinit.parser.operations.AclLine;
import org.apache.sling.repoinit.parser.operations.RestrictionClause;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Simple single ACL statement representation.
 */
public final class AccessControlEntry {

    private final boolean isAllow;

    private final List<String> privileges;

    private final RepoPath repositoryPath;

    private final List<RestrictionClause> restrictions = new LinkedList<>();

    private final boolean isPrincipalBased;

    public AccessControlEntry(boolean isAllow, @NotNull List<String> privileges, @NotNull RepoPath repositoryPath) {
        this(isAllow, privileges, repositoryPath, false);
    }

    public AccessControlEntry(boolean isAllow, @NotNull List<String> privileges, @NotNull RepoPath repositoryPath, boolean isPrincipalBased) {
        this.isAllow = isAllow;
        this.privileges = privileges;
        this.repositoryPath = repositoryPath;
        this.isPrincipalBased = isPrincipalBased;
    }

    public void addRestriction(@NotNull String restrictionName, List<String> values) {
        if (!restrictionName.isEmpty()) {
            restrictions.add(new RestrictionClause(restrictionName, values));
        }
    }

    public @NotNull RepoPath getRepositoryPath() {
        return repositoryPath;
    }

    public boolean isPrincipalBased() {
        return isPrincipalBased;
    }

    @NotNull
    public AclLine asAclLine(@NotNull String path) {
        AclLine line = new AclLine(isAllow ? AclLine.Action.ALLOW : AclLine.Action.DENY);
        line.setProperty(AclLine.PROP_PATHS, Collections.singletonList(path));
        line.setProperty(AclLine.PROP_PRIVILEGES, privileges);
        line.setRestrictions(restrictions);
        return line;
    }

    @Override
    public String toString() {
        return "Acl [isAllow="
               + isAllow
               + ", privileges="
               + privileges
               + ", path="
               + repositoryPath
               + ", restrictions="
               + restrictions
               + ", isPrincipalBased="
               + isPrincipalBased
               + "]";
    }

}
