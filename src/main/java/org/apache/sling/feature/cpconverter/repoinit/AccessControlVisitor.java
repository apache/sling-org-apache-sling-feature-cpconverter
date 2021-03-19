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

import com.google.common.collect.ImmutableList;
import org.apache.sling.feature.cpconverter.accesscontrol.EnforceInfo;
import org.apache.sling.repoinit.parser.operations.AclLine;
import org.apache.sling.repoinit.parser.operations.RestrictionClause;
import org.apache.sling.repoinit.parser.operations.SetAclPaths;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipalBased;
import org.apache.sling.repoinit.parser.operations.SetAclPrincipals;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class AccessControlVisitor extends NoOpVisitor {

    private final Formatter formatter;
    private final EnforceInfo enforceInfo;
    private final ConversionMap toConvert;
    private final Set<String> systemUserIds;

    AccessControlVisitor(@NotNull Formatter formatter, @NotNull EnforceInfo enforceInfo,
                         @NotNull ConversionMap toConvert, @NotNull Set<String> systemUserIds) {
        this.formatter = formatter;
        this.enforceInfo = enforceInfo;
        this.toConvert = toConvert;
        this.systemUserIds = systemUserIds;
    }

    @Override
    public void visitSetAclPrincipal(SetAclPrincipals setAclPrincipals) {
        String optionString = getAclOptionsString(setAclPrincipals.getOptions());
        Map<List<AclLine>, List<String>> notConverted = convertLines(setAclPrincipals, optionString);

        // re-create original repo-init statements for all principals/lines that don't get converted
        if (!notConverted.isEmpty()) {
            for (Map.Entry<List<AclLine>, List<String>> entry : notConverted.entrySet()) {
                List<AclLine> lines = entry.getKey();
                List<String> principalNames = entry.getValue();
                if (lines.stream().anyMatch(line -> {
                    List<String> paths = line.getProperty(AclLine.PROP_PATHS);
                    return paths == null || paths.isEmpty();
                })) {
                    generateRepoInit(formatter, "set repository ACL for %s%s%n", true, listToString(principalNames), optionString, lines);
                } else {
                    generateRepoInit(formatter, "set ACL for %s%s%n", true, listToString(principalNames), optionString, lines);
                }
            }
        }
    }

    /**
     * Return a list of AclLine that don't get converted.
     */
    @NotNull
    private Map<List<AclLine>, List<String>> convertLines(@NotNull SetAclPrincipals setAclPrincipals, @NotNull String optionString) {
        Map<List<AclLine>, List<String>> notConverted = new HashMap<>();

        List<AclLine> allLines = new ArrayList<>(setAclPrincipals.getLines());

        List<AclLine> removeLines = new ArrayList<>(allLines);
        removeLines.removeIf(line -> !isRemoveAction(line));

        List<AclLine> lines = new ArrayList<>(allLines);
        lines.removeAll(removeLines);

        if (!lines.isEmpty()) {
            for (String principalName : setAclPrincipals.getPrincipals()) {
                if (enforcePrincipalBased(principalName)) {
                    toConvert.putAll(principalName, optionString, lines);
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
        String optionString = getAclOptionsString(setAclPaths.getOptions());
        List<AclLine> lines = new ArrayList<>();
        for (AclLine line : setAclPaths.getLines()) {
            List<String> principalNames = new ArrayList<>(line.getProperty(AclLine.PROP_PRINCIPALS));
            if (!isRemoveAction(line)) {
                for (String principalName : line.getProperty(AclLine.PROP_PRINCIPALS)) {
                    if (enforcePrincipalBased(principalName)) {
                        AclLine newLine = createAclLine(line, null, setAclPaths.getPaths());
                        toConvert.put(principalName, optionString, newLine);
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
            generateRepoInit(formatter, "set ACL on %s%s%n", false, pathsToString(setAclPaths.getPaths()), optionString, lines);
        }
    }

    @Override
    public void visitSetAclPrincipalBased(SetAclPrincipalBased setAclPrincipalBased) {
        generateRepoInit(formatter, "set principal ACL for %s%s%n", true, listToString(setAclPrincipalBased.getPrincipals()), getAclOptionsString(setAclPrincipalBased.getOptions()), setAclPrincipalBased.getLines());
    }

    private boolean enforcePrincipalBased(@NotNull String principalName) {
        return systemUserIds.contains(principalName) && enforceInfo.enforcePrincipalBased(principalName);
    }

    static void generateRepoInit(@NotNull Formatter formatter, @NotNull String start, boolean hasPathLines, @NotNull String principalsOrPaths,
                                 @NotNull String optionString, @NotNull Collection<AclLine> lines) {
        formatter.format(start, principalsOrPaths, optionString);
        for (AclLine line : lines) {
            String action = actionToString(line.getAction());
            String privileges = privilegesToString(line.getAction(), line.getProperty(AclLine.PROP_PRIVILEGES));
            String onOrFor;
            if (hasPathLines) {
                String pathStr = pathsToString(line.getProperty(AclLine.PROP_PATHS));
                onOrFor = (pathStr.isEmpty()) ? "" : " on " + pathStr;
            } else {
                onOrFor = " for " + listToString(line.getProperty(AclLine.PROP_PRINCIPALS));
            }
            formatter.format("    %s %s%s%s%s%n", action, privileges, onOrFor,
                    nodetypesToString(line.getProperty(AclLine.PROP_NODETYPES)),
                    restrictionsToString(line.getRestrictions()));
        }
        formatter.format("end%n");
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

    @NotNull
    private static String getAclOptionsString(@NotNull List<String> options) {
        return (options.isEmpty()) ? "" : " (ACLOptions="+ listToString(options)+")";
    }

    private static boolean isRemoveAction(@NotNull AclLine line) {
        AclLine.Action action = line.getAction();
        return action == AclLine.Action.REMOVE_ALL || action == AclLine.Action.REMOVE;
    }

    @NotNull
    private static String privilegesToString(@NotNull AclLine.Action action, @NotNull List<String> privileges) {
        return (action == AclLine.Action.REMOVE_ALL) ? "*" : listToString(privileges);
    }

    @NotNull
    private static String pathsToString(@NotNull List<String> paths) {
        return listToString(paths.stream()
                .map(s -> {
                    String homestr = ":home:";
                    if (s.startsWith(homestr)) {
                        return "home(" + s.substring(homestr.length(), s.lastIndexOf('#')) +")";
                    } else {
                        return s;
                    }
                })
                .collect(Collectors.toList()));
    }

    @NotNull
    private static String nodetypesToString(@NotNull List<String> nodetypes) {
        return (nodetypes.isEmpty()) ? "" : " nodetypes " + listToString(nodetypes);
    }

    @NotNull
    private static String restrictionsToString(@NotNull List<RestrictionClause> restrictionClauses) {
        StringBuilder sb = new StringBuilder();
        for (RestrictionClause rc : restrictionClauses) {
            sb.append(" restriction(").append(rc.getName());
            for (String v : rc.getValues()) {
                sb.append(",").append(v);
            }
            sb.append(')');
        }
        return sb.toString();
    }

    @NotNull
    private static String actionToString(@NotNull AclLine.Action action) {
        switch (action) {
            case DENY: return "deny";
            case REMOVE: return "remove";
            case REMOVE_ALL: return "remove";
            default: return "allow";
        }
    }
}