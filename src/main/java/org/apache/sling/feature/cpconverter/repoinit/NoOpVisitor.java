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
package org.apache.sling.feature.cpconverter.repoinit;

import org.apache.sling.repoinit.parser.operations.AddGroupMembers;
import org.apache.sling.repoinit.parser.operations.CreateGroup;
import org.apache.sling.repoinit.parser.operations.CreatePath;
import org.apache.sling.repoinit.parser.operations.CreateServiceUser;
import org.apache.sling.repoinit.parser.operations.CreateUser;
import org.apache.sling.repoinit.parser.operations.DeleteGroup;
import org.apache.sling.repoinit.parser.operations.DeleteServiceUser;
import org.apache.sling.repoinit.parser.operations.DeleteUser;
import org.apache.sling.repoinit.parser.operations.DisableServiceUser;
import org.apache.sling.repoinit.parser.operations.OperationVisitor;
import org.apache.sling.repoinit.parser.operations.RegisterNamespace;
import org.apache.sling.repoinit.parser.operations.RegisterNodetypes;
import org.apache.sling.repoinit.parser.operations.RegisterPrivilege;
import org.apache.sling.repoinit.parser.operations.RemoveGroupMembers;
import org.apache.sling.repoinit.parser.operations.SetAclPaths;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipalBased;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipals;
import org.apache.sling.repoinit.parser.operations.SetProperties;
import org.jetbrains.annotations.NotNull;

import java.util.List;

abstract class NoOpVisitor implements OperationVisitor {

    static String listToString(@NotNull List<String> list) {
        if (list.isEmpty()) {
            return "";
        } else {
            return String.join(",", list);
        }
    }

    @Override
    public void visitCreateGroup(CreateGroup createGroup) {
    }

    @Override
    public void visitDeleteGroup(DeleteGroup deleteGroup) {
    }

    @Override
    public void visitCreateUser(CreateUser createUser) {
    }

    @Override
    public void visitDeleteUser(DeleteUser deleteUser) {
    }

    @Override
    public void visitCreateServiceUser(CreateServiceUser createServiceUser) {
    }

    @Override
    public void visitDeleteServiceUser(DeleteServiceUser deleteServiceUser) {
    }

    @Override
    public void visitSetAclPrincipal(SetAclPrincipals setAclPrincipals) {
    }

    @Override
    public void visitSetAclPaths(SetAclPaths setAclPaths) {
    }

    @Override
    public void visitSetAclPrincipalBased(SetAclPrincipalBased setAclPrincipalBased) {
    }

    @Override
    public void visitCreatePath(CreatePath createPath) {
    }

    @Override
    public void visitRegisterNamespace(RegisterNamespace registerNamespace) {
    }

    @Override
    public void visitRegisterNodetypes(RegisterNodetypes registerNodetypes) {
    }

    @Override
    public void visitRegisterPrivilege(RegisterPrivilege registerPrivilege) {
    }

    @Override
    public void visitDisableServiceUser(DisableServiceUser disableServiceUser) {
    }

    @Override
    public void visitAddGroupMembers(AddGroupMembers addGroupMembers) {
    }

    @Override
    public void visitRemoveGroupMembers(RemoveGroupMembers removeGroupMembers) {
    }

    @Override
    public void visitSetProperties(SetProperties setProperties) {
    }
}