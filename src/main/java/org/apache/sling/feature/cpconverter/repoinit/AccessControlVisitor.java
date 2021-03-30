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

import org.apache.sling.feature.cpconverter.accesscontrol.EnforceInfo;
import org.apache.sling.repoinit.parser.operations.AclLine;
import org.apache.sling.repoinit.parser.operations.SetAclPaths;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipals;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class AccessControlVisitor extends NoOpVisitor {

    private final Formatter formatter;
    private final EnforceInfo enforceInfo;
    private final ConversionMap toConvert;

    AccessControlVisitor(@NotNull Formatter formatter, @NotNull EnforceInfo enforceInfo,
                         @NotNull ConversionMap toConvert) {
        this.formatter = formatter;
        this.enforceInfo = enforceInfo;
        this.toConvert = toConvert;
    }

    @Override
    public void visitSetAclPrincipal(SetAclPrincipals setAclPrincipals) {
        Map<List<AclLine>, List<String>> notConverted = convertLines(setAclPrincipals);

        // re-create original repo-init statements for all principals/lines that don't get converted
        if (!notConverted.isEmpty()) {
            for (Map.Entry<List<AclLine>, List<String>> entry : notConverted.entrySet()) {
                List<AclLine> lines = entry.getKey();
                List<String> principalNames = entry.getValue();
                SetAclPrincipals operation = new SetAclPrincipals(principalNames, lines, setAclPrincipals.getOptions());
                formatter.format("%s", operation.asRepoInitString());
            }
        }
    }

    /**
     * Return a list of AclLine that don't get converted.
     */
    @NotNull
    private Map<List<AclLine>, List<String>> convertLines(@NotNull SetAclPrincipals setAclPrincipals) {
        Map<List<AclLine>, List<String>> notConverted = new HashMap<>();

        List<AclLine> allLines = new ArrayList<>(setAclPrincipals.getLines());

        List<AclLine> removeLines = new ArrayList<>(allLines);
        removeLines.removeIf(line -> !isRemoveAction(line));

        List<AclLine> lines = new ArrayList<>(allLines);
        lines.removeAll(removeLines);

        if (!lines.isEmpty()) {
            for (String principalName : setAclPrincipals.getPrincipals()) {
                if (enforcePrincipalBased(principalName)) {
                    toConvert.putAll(principalName, setAclPrincipals.getOptions(), lines);
                    if (!removeLines.isEmpty()) {
                        List<String> principalNames = notConverted.computeIfAbsent(removeLines, k -> new ArrayList<>());
                        principalNames.add(principalName);
                    }
                } else {
                    List<String> principalNames = notConverted.computeIfAbsent(allLines, k -> new ArrayList<>());
                    principalNames.add(principalName);
                }
            }
        } else {
            notConverted.put(allLines, setAclPrincipals.getPrincipals());
        }
        return notConverted;
    }

    @Override
    public void visitSetAclPaths(SetAclPaths setAclPaths) {
        List<AclLine> lines = new ArrayList<>();
        for (AclLine line : setAclPaths.getLines()) {
            List<String> principalNames = new ArrayList<>(line.getProperty(AclLine.PROP_PRINCIPALS));
            if (!isRemoveAction(line)) {
                for (String principalName : line.getProperty(AclLine.PROP_PRINCIPALS)) {
                    if (enforcePrincipalBased(principalName)) {
                        AclLine newLine = createAclLine(line, null, setAclPaths.getPaths());
                        toConvert.put(principalName, setAclPaths.getOptions(), newLine);
                        principalNames.remove(principalName);
                    }
                }
            }

            if (principalNames.equals(line.getProperty(AclLine.PROP_PRINCIPALS))) {
                // nothing to convert -> use the original line
                lines.add(line);
            } else if (!principalNames.isEmpty()) {
                // re-create modified ACLLine without the principals that will be converted
                AclLine modified = createAclLine(line, principalNames, null);
                lines.add(modified);
            }
        }

        if (!lines.isEmpty()) {
            SetAclPaths operation = new SetAclPaths(setAclPaths.getPaths(), lines, setAclPaths.getOptions());
            formatter.format("%s", operation.asRepoInitString());
        }
    }

    private boolean enforcePrincipalBased(@NotNull String principalName) {
        return enforceInfo.enforcePrincipalBased(principalName);
    }

    @NotNull
    private static AclLine createAclLine(@NotNull AclLine base, @Nullable List<String> principalNames, @Nullable List<String> paths) {
        AclLine al = new AclLine(base.getAction());
        if (principalNames != null) {
            al.setProperty(AclLine.PROP_PRINCIPALS, principalNames);
        }
        if (paths != null && !paths.isEmpty()) {
            al.setProperty(AclLine.PROP_PATHS, paths);
        }
        al.setProperty(AclLine.PROP_PRIVILEGES, base.getProperty(AclLine.PROP_PRIVILEGES));
        al.setProperty(AclLine.PROP_NODETYPES, base.getProperty(AclLine.PROP_NODETYPES));
        al.setRestrictions(base.getRestrictions());
        return al;
    }

    private static boolean isRemoveAction(@NotNull AclLine line) {
        AclLine.Action action = line.getAction();
        return action == AclLine.Action.REMOVE_ALL || action == AclLine.Action.REMOVE;
    }
}