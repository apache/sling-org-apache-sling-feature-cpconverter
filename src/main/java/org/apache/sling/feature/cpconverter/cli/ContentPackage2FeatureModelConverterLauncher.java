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
package org.apache.sling.feature.cpconverter.cli;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.ConverterException;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter.SlingInitialContentPolicy;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.DefaultAclManager;
import org.apache.sling.feature.cpconverter.artifacts.LocalMavenRepositoryArtifactsDeployer;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.filtering.RegexBasedResourceFilter;
import org.apache.sling.feature.cpconverter.handlers.DefaultEntryHandlersManager;
import org.apache.sling.feature.cpconverter.handlers.slinginitialcontent.BundleSlingInitialContentExtractor;
import org.apache.sling.feature.cpconverter.index.DefaultIndexManager;
import org.apache.sling.feature.cpconverter.shared.ConverterConstants;
import org.apache.sling.feature.cpconverter.vltpkg.DefaultPackagesEventsEmitter;
import org.apache.sling.feature.io.json.FeatureJSONReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "cp2fm",
    description = "Apache Sling Content Package to Sling Feature converter",
    footer = "Copyright(c) 2019-2021 The Apache Software Foundation."
)
public final class ContentPackage2FeatureModelConverterLauncher implements Runnable {

    @Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the usage message.")
    private boolean helpRequested;

    @Option(names = { "-X", "--verbose" }, description = "Produce execution debug output.")
    private boolean debug;

    @Option(names = { "-q", "--quiet" }, description = "Log errors only.")
    private boolean quiet;

    @Option(names = { "-v", "--version" }, description = "Display version information.")
    private boolean printVersion;

    @Option(names = { "-s", "--strict-validation" }, description = "Flag to mark the content-package input file being strict validated.", required = false, defaultValue = "false")
    private boolean strictValidation = false;

    @Option(names = { "-m", "--merge-configurations" }, description = "Flag to mark OSGi configurations with same PID will be merged, the tool will fail otherwise.", required = false, defaultValue = "false")
    private boolean mergeConfigurations = false;

    @Option(names = { "-b", "--bundles-start-order" }, description = "The order to start detected bundles.", required = false)
    private int bundlesStartOrder = 0;

    @Option(names = { "-f", "--filtering-patterns" }, description = "Regex based pattern(s) to reject content-package archive entries.", required = false)
    private String[] filteringPatterns;

    @Option(names = { "-a", "--artifacts-output-directory" }, description = "The output directory where the artifacts will be deployed.", required = true)
    private File artifactsOutputDirectory;

    @Option(names = { "-o", "--features-output-directory" }, description = "The output directory where the Feature File will be generated.", required = true)
    private File featureModelsOutputDirectory;

    @Option(names = { "-i", "--artifact-id" }, description = "The optional Artifact Id the Feature File will have, once generated; it will be derived, if not specified.", required = false)
    private String artifactIdOverride;

    @Option(names = { "-p", "--fm-prefix" }, description = "The optional prefix of the output file", required = false)
    private String fmPrefix;

    @Option(names = { "-r", "--api-region" }, description = "The API Regions assigned to the generated features", required = false)
    private List<String> apiRegions;

    @Option(names = { "-e", "--exports-to-region" }, description = "Packages exported by bundles in the content packages are exported in the named region", required = false)
    private String exportsToRegion;

    @Option(names = {"-D", "--define"}, description = "Define a system property", required = false)
    private Map<String, String> properties = new HashMap<>();

    @Parameters(arity = "1..*", paramLabel = "content-packages", description = "The content-package input file(s).")
    private File[] contentPackages;

    @Option(names = { "-Z", "--fail-on-mixed-packages" }, description = "Fail the conversion if the resulting attached content-package is MIXED type", required = false)
    private boolean failOnMixedPackages = false;

    @Option(names = { "--enforce-principal-based-supported-path" }, description = "Converts service user access control entries to principal-based setup using the given supported path.", required = false)
    private String enforcePrincipalBasedSupportedPath = null;

    @Option(names= {"--system-user-rel-path"}, description = "Relative path for system user as configured with Apache Jackrabbit Oak", required = false)
    private String systemUserRelPath = ConverterConstants.SYSTEM_USER_REL_PATH_DEFAULT;

    @Option(names = { "--enforce-servicemapping-by-principal" }, description = "Converts service user mappings with the form 'service:sub=userID' to 'service:sub=[principalname]'. Note, this may result in group membership no longer being resolved upon service login.", required = false)
    private boolean enforceServiceMappingByPrincipal = false;

    @Option(names = { "--entry-handler-config" }, description = "Config for entry handlers that support it (classname:<config-string>", required = false)
    private List<String> entryHandlerConfigs = null;

    @Option(names = { "--remove-install-hooks" }, description = "Removes both internal and external hooks from processed packages", required = false)
    private boolean removeInstallHooks = false;

    @Option(names = { "--seed-feature" }, description = "A url pointing to a feature that can be assumed to be around when the conversion result will be used", required = false)
    private String seedFeature = null;

    @Option(names = { "--disable-installer-policy" }, description = "Disables enforcing that OSGi configurations are only allowed below a folder called 'config' and OSGi bundles are only allowed below a folder called 'install'. Instead both are detected below either 'install' or 'config'.", required = false)
    private boolean disableInstallerPolicy = false;

    @Option(names = { "--disable-package-type-recalculation" }, description = "Disables the package recalculation", required = false)
    private boolean disablePackageTypeRecalculation = false;

    @Option(names = { "--content-type-package-policy" }, description = "Determines what to do with converted packages of type 'content'. Valid values: ${COMPLETION-CANDIDATES}.", required = false, showDefaultValue = Visibility.ALWAYS)
    private ContentPackage2FeatureModelConverter.PackagePolicy contentTypePackagePolicy = ContentPackage2FeatureModelConverter.PackagePolicy.DROP;

    @Option(names = { "-u", "--unreferenced-artifacts-output-directory" }, description = "The output directory where unreferenced artifacts will be deployed.", required = false)
    private File unreferencedArtifactsOutputDirectory;

    @Option(names = { "--sling-initial-content-policy" }, description = "Determines what to do with Sling-Initial-Content found in embedded bundles. Valid values: ${COMPLETION-CANDIDATES}.", required = false, showDefaultValue = Visibility.ALWAYS)
    private SlingInitialContentPolicy slingInitialContentPolicy = SlingInitialContentPolicy.KEEP;

    @Override
    public void run() {
        if (quiet) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");
        } else if (debug) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        } else {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        }
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
        System.setProperty("org.slf4j.simpleLogger.levelInBrackets", "true");
        System.setProperty("org.slf4j.simpleLogger.showLogName", "false");

        String appName = getClass().getAnnotation(Command.class).description()[0];
        final Logger logger = LoggerFactory.getLogger(appName);

        //output the current tmp directory
        logger.debug("Using tmp directory {}", System.getProperty("java.io.tmpdir"));

        final long start = System.currentTimeMillis();

        if (printVersion) {
            printVersion(logger);
        }

        logger.info(appName);
        logger.info("");

        boolean exitWithError = false;

        try {
            try {
                AclManager aclManager = new DefaultAclManager(enforcePrincipalBasedSupportedPath, systemUserRelPath);

                DefaultFeaturesManager featuresManager = new DefaultFeaturesManager(mergeConfigurations,
                                                                bundlesStartOrder,
                                                                featureModelsOutputDirectory,
                                                                artifactIdOverride,
                                                                fmPrefix,
                                                                properties,
                                                                aclManager);

                featuresManager.setEnforceServiceMappingByPrincipal(enforceServiceMappingByPrincipal);

                if (seedFeature != null) {
                    try(Reader reader = new InputStreamReader(new URL(seedFeature).openStream(), "UTF-8")) {
                        Feature seed = FeatureJSONReader.read(reader, seedFeature);
                        System.out.println(seed);
                        featuresManager.addSeed(seed);
                    }
                }

                if (apiRegions != null)
                    featuresManager.setAPIRegions(apiRegions);

                if (exportsToRegion != null)
                    featuresManager.setExportToAPIRegion(exportsToRegion);

                Map<String, String> entryHandlerConfigsMap = new HashMap<>();
                if (entryHandlerConfigs != null) {
                    for (String config : entryHandlerConfigs) {
                        int idx = config.indexOf(':');
                        if (idx != -1) {
                            entryHandlerConfigsMap.put(config.substring(0, idx), config.substring(idx + 1));
                        }
                    }
                }

                try (ContentPackage2FeatureModelConverter converter = new ContentPackage2FeatureModelConverter(strictValidation, slingInitialContentPolicy,disablePackageTypeRecalculation)) {
                    BundleSlingInitialContentExtractor bundleSlingInitialContentExtractor = new BundleSlingInitialContentExtractor();
                    converter.setFeaturesManager(featuresManager)
                             .setBundlesDeployer(new LocalMavenRepositoryArtifactsDeployer(artifactsOutputDirectory))
                             .setBundleSlingInitialContentExtractor(bundleSlingInitialContentExtractor)
                             .setEntryHandlersManager(new DefaultEntryHandlersManager(entryHandlerConfigsMap, !disableInstallerPolicy, slingInitialContentPolicy, bundleSlingInitialContentExtractor, systemUserRelPath))
                             .setAclManager(aclManager)
                             .setIndexManager(new DefaultIndexManager())
                             .setEmitter(DefaultPackagesEventsEmitter.open(featureModelsOutputDirectory))
                             .setFailOnMixedPackages(failOnMixedPackages)
                             .setContentTypePackagePolicy(contentTypePackagePolicy);
                    if (unreferencedArtifactsOutputDirectory != null) {
                        converter.setUnreferencedArtifactsDeployer(new LocalMavenRepositoryArtifactsDeployer(unreferencedArtifactsOutputDirectory));
                    } else if (contentTypePackagePolicy == ContentPackage2FeatureModelConverter.PackagePolicy.PUT_IN_DEDICATED_FOLDER) {
                        throw new IllegalStateException("Argument '--content-type-package-policy PUT_IN_DEDICATED_FOLDER' requires argument '--unreferenced-artifacts-output-directory' as well!");
                    }
                    if (filteringPatterns != null && filteringPatterns.length > 0) {
                        RegexBasedResourceFilter filter = new RegexBasedResourceFilter();

                        for (String filteringPattern : filteringPatterns) {
                            filter.addFilteringPattern(filteringPattern);
                        }

                        converter.setResourceFilter(filter);
                    }
                    converter.convert(contentPackages);
                }

                logger.info( "+-----------------------------------------------------+" );
                logger.info("{} SUCCESS", appName);
            } catch (ConverterException ce) {
                logger.info( "+-----------------------------------------------------+" );
                logger.info("{} FAILURE", appName);
                logger.info( "+-----------------------------------------------------+" );
                logger.error("Unable to convert content-package {}: {}", contentPackages, ce.getMessage());
                logger.info( "+-----------------------------------------------------+" );
                exitWithError = true;
            } catch (Throwable t) {
                logger.info( "+-----------------------------------------------------+" );
                logger.info("{} FAILURE", appName);
                logger.info( "+-----------------------------------------------------+" );

                if (debug) {
                    logger.error("Unable to convert content-package {}:", contentPackages, t);
                } else {
                    logger.error("Unable to convert content-package {}: {}", contentPackages, t.getMessage());
                }

                logger.info( "+-----------------------------------------------------+" );

                exitWithError = true;
            }
        } finally {
            // format the uptime string
            Formatter uptimeFormatter = new Formatter();
            uptimeFormatter.format("Total time:");
    
            long uptime = System.currentTimeMillis() - start;
            if (uptime < 1000) {
                uptimeFormatter.format(" %s millisecond%s", uptime, (uptime > 1 ? "s" : ""));
            } else {
                long uptimeInSeconds = (uptime) / 1000;
                final long hours = uptimeInSeconds / 3600;
    
                if (hours > 0) {
                    uptimeFormatter.format(" %s hour%s", hours, (hours > 1 ? "s" : ""));
                }
    
                uptimeInSeconds = uptimeInSeconds - (hours * 3600);
                final long minutes = uptimeInSeconds / 60;
    
                if (minutes > 0) {
                    uptimeFormatter.format(" %s minute%s", minutes, (minutes > 1 ? "s" : ""));
                }
    
                uptimeInSeconds = uptimeInSeconds - (minutes * 60);
    
                if (uptimeInSeconds > 0) {
                    uptimeFormatter.format(" %s second%s", uptimeInSeconds, (uptimeInSeconds > 1 ? "s" : ""));
                }
            }
            logger.info(uptimeFormatter.toString());
    
            uptimeFormatter.close();
    
            logger.info("Finished at: {}", new Date());
    
            final Runtime runtime = Runtime.getRuntime();
            final int megaUnit = 1024 * 1024;
    
            logger.info("Final Memory: {}M/{}M",
                        (runtime.totalMemory() - runtime.freeMemory()) / megaUnit,
                        runtime.totalMemory() / megaUnit);
            logger.info("+-----------------------------------------------------+");
        }

        if ( exitWithError ) {
            System.exit(1);
        }
    }

    private static void printVersion(@NotNull final Logger logger) {
        logger.info("{} v{} (built on {})",
                System.getProperty("project.artifactId"),
                System.getProperty("project.version"),
                System.getProperty("build.timestamp"));
        logger.info("Java version: {}, vendor: {}",
                System.getProperty("java.version"),
                System.getProperty("java.vendor"));
        logger.info("Java home: {}", System.getProperty("java.home"));
        logger.info("Default locale: {}_{}, platform encoding: {}",
                System.getProperty("user.language"),
                System.getProperty("user.country"),
                System.getProperty("sun.jnu.encoding"));
        logger.info("Default Time Zone: {}", TimeZone.getDefault().getDisplayName());
        logger.info("OS name: \"{}\", version: \"{}\", arch: \"{}\", family: \"{}\"",
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                getOsFamily());
        logger.info("+-----------------------------------------------------+");
    }

    private static final @NotNull String getOsFamily() {
        String osName = System.getProperty("os.name").toLowerCase();
        String pathSep = System.getProperty("path.separator");

        if (osName.indexOf("windows") != -1) {
            return "windows";
        } else if (osName.indexOf("os/2") != -1) {
            return "os/2";
        } else if (osName.indexOf("z/os") != -1 || osName.indexOf("os/390") != -1) {
            return "z/os";
        } else if (osName.indexOf("os/400") != -1) {
            return "os/400";
        } else if (pathSep.equals(";")) {
            return "dos";
        } else if (osName.indexOf("mac") != -1) {
            if (osName.endsWith("x")) {
                return "mac"; // MACOSX
            }
            return "unix";
        } else if (osName.indexOf("nonstop_kernel") != -1) {
            return "tandem";
        } else if (osName.indexOf("openvms") != -1) {
            return "openvms";
        } else if (pathSep.equals(":")) {
            return "unix";
        }

        return "undefined";
    }

    public static void main(@NotNull String[] args) {
        CommandLine.run(new ContentPackage2FeatureModelConverterLauncher(), args);
    }

}
