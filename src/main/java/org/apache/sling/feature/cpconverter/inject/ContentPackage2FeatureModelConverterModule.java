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
package org.apache.sling.feature.cpconverter.inject;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.name.Names.named;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageManager;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.acl.AclManager;
import org.apache.sling.feature.cpconverter.acl.DefaultAclManager;
import org.apache.sling.feature.cpconverter.artifacts.ArtifactsDeployer;
import org.apache.sling.feature.cpconverter.artifacts.DefaultArtifactsDeployer;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.filtering.RegexBasedResourceFilter;
import org.apache.sling.feature.cpconverter.filtering.ResourceFilter;
import org.apache.sling.feature.cpconverter.handlers.BundleEntryHandler;
import org.apache.sling.feature.cpconverter.handlers.ConfigurationEntryHandler;
import org.apache.sling.feature.cpconverter.handlers.ContentPackageEntryHandler;
import org.apache.sling.feature.cpconverter.handlers.EntryHandler;
import org.apache.sling.feature.cpconverter.handlers.JsonConfigurationEntryHandler;
import org.apache.sling.feature.cpconverter.handlers.PropertiesConfigurationEntryHandler;
import org.apache.sling.feature.cpconverter.handlers.RepPolicyEntryHandler;
import org.apache.sling.feature.cpconverter.handlers.SystemUsersEntryHandler;
import org.apache.sling.feature.cpconverter.handlers.XmlConfigurationEntryHandler;
import org.apache.sling.feature.cpconverter.interpolator.SimpleVariablesInterpolator;
import org.apache.sling.feature.cpconverter.interpolator.VariablesInterpolator;
import org.apache.sling.feature.cpconverter.vltpkg.RecollectorVaultPackageScanner;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

public final class ContentPackage2FeatureModelConverterModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(File.class).annotatedWith(named("java.io.tmpdir")).toInstance(new File(System.getProperty("java.io.tmpdir")));

        bind(new TypeLiteral<Map<PackageId, String>>() {}).toInstance(new HashMap<PackageId, String>());
        bind(PackageManager.class).to(PackageManagerImpl.class).in(SINGLETON);
        bind(AclManager.class).to(DefaultAclManager.class).in(SINGLETON);
        bind(ArtifactsDeployer.class).to(DefaultArtifactsDeployer.class).in(SINGLETON);
        bind(FeaturesManager.class).to(DefaultFeaturesManager.class).in(SINGLETON);
        bind(ResourceFilter.class).to(RegexBasedResourceFilter.class).in(SINGLETON);

        // handlers
        Multibinder<EntryHandler> handlers = newSetBinder(binder(), EntryHandler.class);
        handlers.addBinding().to(BundleEntryHandler.class);
        handlers.addBinding().to(ConfigurationEntryHandler.class);
        handlers.addBinding().to(ContentPackageEntryHandler.class);
        handlers.addBinding().to(JsonConfigurationEntryHandler.class);
        handlers.addBinding().to(PropertiesConfigurationEntryHandler.class);
        handlers.addBinding().to(RepPolicyEntryHandler.class);
        handlers.addBinding().to(SystemUsersEntryHandler.class);
        handlers.addBinding().to(XmlConfigurationEntryHandler.class);

        bind(VariablesInterpolator.class).to(SimpleVariablesInterpolator.class).in(SINGLETON);
        bind(RecollectorVaultPackageScanner.class).in(SINGLETON);
        bind(ContentPackage2FeatureModelConverter.class).asEagerSingleton();
    }

}
