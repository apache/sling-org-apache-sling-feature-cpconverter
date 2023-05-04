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
import org.apache.sling.repoinit.parser.operations.AddMixins;
import org.apache.sling.repoinit.parser.operations.CreateGroup;
import org.apache.sling.repoinit.parser.operations.CreatePath;
import org.apache.sling.repoinit.parser.operations.CreateUser;
import org.apache.sling.repoinit.parser.operations.DeleteAclPaths;
import org.apache.sling.repoinit.parser.operations.DeleteAclPrincipalBased;
import org.apache.sling.repoinit.parser.operations.DeleteAclPrincipals;
import org.apache.sling.repoinit.parser.operations.DeleteGroup;
import org.apache.sling.repoinit.parser.operations.DeleteServiceUser;
import org.apache.sling.repoinit.parser.operations.DeleteUser;
import org.apache.sling.repoinit.parser.operations.DisableServiceUser;
import org.apache.sling.repoinit.parser.operations.EnsureAclPrincipalBased;
import org.apache.sling.repoinit.parser.operations.EnsureNodes;
import org.apache.sling.repoinit.parser.operations.RegisterNamespace;
import org.apache.sling.repoinit.parser.operations.RegisterNodetypes;
import org.apache.sling.repoinit.parser.operations.RegisterPrivilege;
import org.apache.sling.repoinit.parser.operations.RemoveAcePaths;
import org.apache.sling.repoinit.parser.operations.RemoveAcePrincipalBased;
import org.apache.sling.repoinit.parser.operations.RemoveAcePrincipals;
import org.apache.sling.repoinit.parser.operations.RemoveGroupMembers;
import org.apache.sling.repoinit.parser.operations.RemoveMixins;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipalBased;
import org.apache.sling.repoinit.parser.operations.SetProperties;
import org.jetbrains.annotations.NotNull;

import java.util.Formatter;

class DefaultVisitor extends NoOpVisitor {

    private final Formatter formatter;

    DefaultVisitor(@NotNull Formatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public void visitCreateGroup(@NotNull CreateGroup createGroup) {
        formatter.format("%s", createGroup.asRepoInitString());
    }

    @Override
    public void visitDeleteGroup(@NotNull DeleteGroup deleteGroup) {
        formatter.format("%s", deleteGroup.asRepoInitString());
    }

    @Override
    public void visitCreateUser(@NotNull CreateUser createUser) {
        formatter.format("%s", createUser.asRepoInitString());
    }

    @Override
    public void visitDeleteUser(DeleteUser deleteUser) {
        formatter.format("%s", deleteUser.asRepoInitString());
    }

    @Override
    public void visitDeleteServiceUser(DeleteServiceUser deleteServiceUser) {
        formatter.format("%s", deleteServiceUser.asRepoInitString());
    }

    @Override
    public void visitSetAclPrincipalBased(SetAclPrincipalBased setAclPrincipalBased) {
        formatter.format("%s", setAclPrincipalBased.asRepoInitString());
    }

    @Override
    public void visitEnsureAclPrincipalBased(EnsureAclPrincipalBased ensureAclPrincipalBased) {
        formatter.format("%s", ensureAclPrincipalBased.asRepoInitString());
    }

    @Override
    public void visitCreatePath(CreatePath createPath) {
        formatter.format("%s", createPath.asRepoInitString());
    }
    
    @Override
    public void visitEnsureNodes(EnsureNodes en) {
        formatter.format("%s", en.asRepoInitString());
    }

    @Override
    public void visitRegisterNamespace(RegisterNamespace registerNamespace) {
        formatter.format("%s", registerNamespace.asRepoInitString());
    }

    @Override
    public void visitRegisterNodetypes(RegisterNodetypes registerNodetypes) {
        formatter.format("%s", registerNodetypes.asRepoInitString());
    }

    @Override
    public void visitRegisterPrivilege(RegisterPrivilege registerPrivilege) {
        formatter.format("%s", registerPrivilege.asRepoInitString());
    }

    @Override
    public void visitDisableServiceUser(DisableServiceUser disableServiceUser) {
        formatter.format("%s", disableServiceUser.asRepoInitString());
    }

    @Override
    public void visitRemoveAcePrincipal(RemoveAcePrincipals s) {
        formatter.format("%s", s.asRepoInitString());
    }

    @Override
    public void visitRemoveAcePaths(RemoveAcePaths s) {
        formatter.format("%s", s.asRepoInitString());
    }

    @Override
    public void visitRemoveAcePrincipalBased(RemoveAcePrincipalBased s) {
        formatter.format("%s", s.asRepoInitString());
    }

    @Override
    public void visitDeleteAclPrincipals(DeleteAclPrincipals s) {
        formatter.format("%s", s.asRepoInitString());
    }

    @Override
    public void visitDeleteAclPaths(DeleteAclPaths s) {
        formatter.format("%s", s.asRepoInitString());
    }

    @Override
    public void visitDeleteAclPrincipalBased(DeleteAclPrincipalBased s) {
        formatter.format("%s", s.asRepoInitString());
    }

    @Override
    public void visitAddMixins(AddMixins s) {
        formatter.format("%s", s.asRepoInitString());
    }

    @Override
    public void visitRemoveMixins(RemoveMixins s) {
        formatter.format("%s", s.asRepoInitString());
    }

    @Override
    public void visitAddGroupMembers(AddGroupMembers addGroupMembers) {
        formatter.format("%s", addGroupMembers.asRepoInitString());
    }

    @Override
    public void visitRemoveGroupMembers(RemoveGroupMembers removeGroupMembers) {
        formatter.format("%s", removeGroupMembers.asRepoInitString());
    }

    @Override
    public void visitSetProperties(SetProperties setProperties) {
        formatter.format("%s", setProperties.asRepoInitString());
    }
}