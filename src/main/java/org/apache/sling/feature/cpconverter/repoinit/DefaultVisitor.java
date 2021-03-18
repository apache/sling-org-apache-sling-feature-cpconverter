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

import org.apache.jackrabbit.util.ISO8601;
import org.apache.sling.feature.cpconverter.shared.NodeTypeUtil;
import org.apache.sling.repoinit.parser.operations.AddGroupMembers;
import org.apache.sling.repoinit.parser.operations.CreateGroup;
import org.apache.sling.repoinit.parser.operations.CreatePath;
import org.apache.sling.repoinit.parser.operations.CreateUser;
import org.apache.sling.repoinit.parser.operations.DeleteGroup;
import org.apache.sling.repoinit.parser.operations.DeleteServiceUser;
import org.apache.sling.repoinit.parser.operations.DeleteUser;
import org.apache.sling.repoinit.parser.operations.DisableServiceUser;
import org.apache.sling.repoinit.parser.operations.PathSegmentDefinition;
import org.apache.sling.repoinit.parser.operations.PropertyLine;
import org.apache.sling.repoinit.parser.operations.RegisterNamespace;
import org.apache.sling.repoinit.parser.operations.RegisterNodetypes;
import org.apache.sling.repoinit.parser.operations.RegisterPrivilege;
import org.apache.sling.repoinit.parser.operations.RemoveGroupMembers;
import org.apache.sling.repoinit.parser.operations.SetProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Calendar;
import java.util.Formatter;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

class DefaultVisitor extends NoOpVisitor {

    private final Formatter formatter;

    DefaultVisitor(@NotNull Formatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public void visitCreateGroup(@NotNull CreateGroup createGroup) {
        String path = createGroup.getPath();
        if (path == null || path.isEmpty()) {
            formatter.format("create group %s%n", createGroup.getGroupname());
        } else {
            String forced = (createGroup.isForcedPath()) ? "forced " : "";
            formatter.format("create group %s with %spath %s%n", createGroup.getGroupname(), forced, path);
        }
    }

    @Override
    public void visitDeleteGroup(@NotNull DeleteGroup deleteGroup) {
        formatter.format("delete group %s%n", deleteGroup.getGroupname());
    }

    @Override
    public void visitCreateUser(@NotNull CreateUser createUser) {
        String path = createUser.getPath();
        if (path == null || path.isEmpty()) {
            formatter.format("create user %s%s%n", createUser.getUsername(), getPwString(createUser));
        } else {
            String forced = (createUser.isForcedPath()) ? "forced " : "";
            formatter.format("create user %s with %spath %s%s%n", createUser.getUsername(), forced, path, getPwString(createUser));
        }
    }

    @NotNull
    private static String getPwString(@NotNull CreateUser createUser) {
        String pw = createUser.getPassword();
        if (pw == null || pw.isEmpty()) {
            return "";
        } else {
            String enc = (createUser.getPasswordEncoding() != null) ? "{"+createUser.getPasswordEncoding()+"} " :  "";
            return " with password "+ enc + createUser.getPassword();
        }
    }

    @Override
    public void visitDeleteUser(DeleteUser deleteUser) {
        formatter.format("delete user %s%n", deleteUser.getUsername());
    }

    @Override
    public void visitDeleteServiceUser(DeleteServiceUser deleteServiceUser) {
        formatter.format("delete service user %s%n", deleteServiceUser.getUsername());
    }

    @Override
    public void visitCreatePath(CreatePath createPath) {
        // FIXME: see SLING-10231
        //        the CreatePath operation doesn't allow to retrieve the default primary type
        //        therefore the generated statement may not be identical to the original one.
        StringBuilder sb = new StringBuilder();
        for (PathSegmentDefinition psd : createPath.getDefinitions()) {
            sb.append("/").append(psd.getSegment()).append("(").append(psd.getPrimaryType());
            List<String> mixins = psd.getMixins();
            if (mixins != null && !mixins.isEmpty()) {
                sb.append(" mixin ").append(listToString(mixins));
            }
            sb.append(")");
        }
        formatter.format("create path %s%n", sb.toString());
    }

    @Override
    public void visitRegisterNamespace(RegisterNamespace registerNamespace) {
        formatter.format("register namespace ( %s ) %s%n", registerNamespace.getPrefix(), registerNamespace.getURI());
    }

    @Override
    public void visitRegisterNodetypes(RegisterNodetypes registerNodetypes) {
        try {
            for (String nodetypeRegistrationSentence : NodeTypeUtil.generateRepoInitLines(new BufferedReader(new StringReader(registerNodetypes.getCndStatements())))) {
                formatter.format("%s%n", nodetypeRegistrationSentence);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    @Override
    public void visitRegisterPrivilege(RegisterPrivilege registerPrivilege) {
        formatter.format("%s%n", registerPrivilege.toString());
    }

    @Override
    public void visitDisableServiceUser(DisableServiceUser disableServiceUser) {
        // FIXME : see SLING-10235
        String reason = disableServiceUser.getParametersDescription();
        String id = disableServiceUser.getUsername();
        if (reason.startsWith(id + " : ")) {
            reason = reason.substring((id + " : ").length());
        }
        formatter.format("disable service user %s : \"%s\"%n", disableServiceUser.getUsername(), reason);

    }

    @Override
    public void visitAddGroupMembers(AddGroupMembers addGroupMembers) {
        formatter.format("add %s to group %s%n", listToString(addGroupMembers.getMembers()), addGroupMembers.getGroupname());
    }

    @Override
    public void visitRemoveGroupMembers(RemoveGroupMembers removeGroupMembers) {
        formatter.format("remove %s from group %s%n", listToString(removeGroupMembers.getMembers()), removeGroupMembers.getGroupname());
    }

    @Override
    public void visitSetProperties(SetProperties setProperties) {
        // FIXME: see SLING-10238 for type and quoted values that cannot be generated
        //        exactly as they were originally defined in repo-init
        formatter.format("set properties on %s%n", listToString(setProperties.getPaths()));
        for (PropertyLine line : setProperties.getPropertyLines()) {
            String type = (line.getPropertyType()==null) ? "" : "{"+line.getPropertyType().name()+"}";
            String values = valuesToString(line.getPropertyValues(), line.getPropertyType());
            if (line.isDefault()) {
                formatter.format("default %s%s to %s%n", line.getPropertyName(), type, values);
            } else {
                formatter.format("set %s%s to %s%n", line.getPropertyName(), type, values);
            }
        }
        formatter.format("end%n");
    }

    private static String valuesToString(@NotNull List<Object> values, @Nullable PropertyLine.PropertyType type) {
        List<String> strings = values.stream()
                .map(o -> {
                    if (type == null || type == PropertyLine.PropertyType.String) {
                        String escapequotes = Objects.toString(o, "").replace("\"", "\\\"");
                        return "\"" + escapequotes + "\"";
                    } else if (type == PropertyLine.PropertyType.Date) {
                        return "\"" + ISO8601.format((Calendar) o) + "\"";
                    } else {
                        return Objects.toString(o, null);
                    }
                })
                .collect(Collectors.toList());
        return listToString(strings);
    }
}