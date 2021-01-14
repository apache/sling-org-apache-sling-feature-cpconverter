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
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

abstract class AbstractUser {

    private final String id;

    private final RepoPath path;
    private final RepoPath intermediatePath;

    /**
     * @param id - the authorizableId to use.
     * @param path - the original repository path of the user in the content-package.
     * @param intermediatePath - the intermediate path the user should have - most likely the (direct) parent of the path.
     */
    protected AbstractUser(@NotNull String id, @NotNull RepoPath path, @NotNull RepoPath intermediatePath) {
        this.id = id;
        this.path = path;
        this.intermediatePath = intermediatePath;
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull RepoPath getPath() {
        return path;
    }

    public @NotNull RepoPath getIntermediatePath() {
        return intermediatePath;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hash(id);
        result = prime * result + Objects.hash(path);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        AbstractUser other = (AbstractUser) obj;
        return Objects.equals(id, other.getId()) && Objects.equals(path, other.getPath());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [id=" + id + ", path=" + path + "]";
    }
}
