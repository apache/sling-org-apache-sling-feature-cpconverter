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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.acl.DefaultAclManager;
import org.apache.sling.feature.cpconverter.artifacts.DefaultArtifactsDeployer;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.filtering.RegexBasedResourceFilter;
import org.apache.sling.feature.cpconverter.handlers.DefaultEntryHandlersManager;
import org.apache.sling.feature.cpconverter.vltpkg.DefaultPackagesEventsEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "cp2fm",
    description = "Apache Sling Content Package to Sling Feature converter",
    footer = "Copyright(c) 2019 The Apache Software Foundation."
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

    @Option(names = {"-D", "--define"}, description = "Define a system property", required = false)
    private Map<String, String> properties = new HashMap<>();

    @Parameters(arity = "1..*", paramLabel = "content-packages", description = "The content-package input file(s).")
    private File[] contentPackages;

    @Option(names = { "-Z", "--fail-on-mixed-packages" }, description = "Fail the conversion if the resulting attached content-package is MIXED type", required = false)
    private boolean failOnMixedPackages = false;

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

        // Add the Shutdown Hook to the Java virtual machine
        // in order to destroy all the allocated resources
        Runtime.getRuntime().addShutdownHook(new ShutDownHook(logger));

        if (printVersion) {
            printVersion(logger);
        }

        logger.info(appName);
        logger.info("");

        try {
            DefaultFeaturesManager featuresManager = new DefaultFeaturesManager(mergeConfigurations,
                                                            bundlesStartOrder,
                                                            featureModelsOutputDirectory,
                                                            artifactIdOverride,
                                                            fmPrefix,
                                                            properties);
            if (apiRegions != null)
                featuresManager.setAPIRegions(apiRegions);

            ContentPackage2FeatureModelConverter converter = new ContentPackage2FeatureModelConverter(strictValidation)
                                                             .setFeaturesManager(featuresManager)
                                                             .setBundlesDeployer(new DefaultArtifactsDeployer(artifactsOutputDirectory))
                                                             .setEntryHandlersManager(new DefaultEntryHandlersManager())
                                                             .setAclManager(new DefaultAclManager())
                                                             .setEmitter(DefaultPackagesEventsEmitter.open(featureModelsOutputDirectory))
                                                             .setFailOnMixedPackages(failOnMixedPackages)
                                                             .setDropContent(true);

            if (filteringPatterns != null && filteringPatterns.length > 0) {
                RegexBasedResourceFilter filter = new RegexBasedResourceFilter();

                for (String filteringPattern : filteringPatterns) {
                    filter.addFilteringPattern(filteringPattern);
                }

                converter.setResourceFilter(filter);
            }

            converter.convert(contentPackages);

            logger.info( "+-----------------------------------------------------+" );
            logger.info("{} SUCCESS", appName);
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

            System.exit(1);
        }
    }

    private static void printVersion(final Logger logger) {
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

    private static final String getOsFamily() {
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

    public static void main(String[] args) {
        CommandLine.run(new ContentPackage2FeatureModelConverterLauncher(), args);
    }

}
