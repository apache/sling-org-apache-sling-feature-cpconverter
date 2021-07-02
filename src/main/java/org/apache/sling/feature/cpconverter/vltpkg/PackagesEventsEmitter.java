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

import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.jetbrains.annotations.NotNull;

public interface PackagesEventsEmitter {

    /**
     * Package converter starts
     */
    void start();

    /**
     * Package converter ends
     */
    void end();

    /**
     * Marks the start of the given package
     * 
     * @param id The id of the package to be converted.
     * @param vaultPackage the package to be converted.
     */
    void startPackage(@NotNull PackageId id, @NotNull VaultPackage vaultPackage);

    /**
     * Marks the end of the given package
     * 
     * @param id The (original) id of the original package as passed to {@link #startPackage(PackageId, VaultPackage)}. 
     * @param vaultPackage the converted package.
     */
    void endPackage(@NotNull PackageId id, @NotNull VaultPackage vaultPackage);

    /**
     * Marks the start of the given sub package
     * 
     * @param path The path
     * @param id The id of the sub package.
     * @param vaultPackage the sub package
     */
    void startSubPackage(@NotNull String path, @NotNull PackageId id, @NotNull VaultPackage vaultPackage);

    /**
     * Marks the end of the given sub package.
     * 
     * @param path The path
     * @param id The id of the original sub package as passed to {@link #startSubPackage(String, PackageId, VaultPackage)}.
     * @param vaultPackage the converted package
     */
    void endSubPackage(@NotNull String path, @NotNull PackageId id, @NotNull VaultPackage vaultPackage);
}
