[<img src="http://sling.apache.org/res/logos/sling.png"/>](http://sling.apache.org)

# Apache Sling Content-Package to Feature Model converter

This tool aims to provide to Apache Sling users an easy-to-use conversion tool which is able to convert `content-package` archives to the new _Sling Feature Model_.

## Introduction

`content-package`s are zipped archives containing OSGi bundles, OSGi configurations and resources (and nested `content-package`s as well), aside metadata, that can be used to install content into a _JCR_ repository using the [Apache Jackrabbit FileVault](http://jackrabbit.apache.org/filevault/) packaging runtime.

OTOH, [Apache Sling Feature](https://github.com/apache/sling-org-apache-sling-feature) allows users to describe an entire OSGi-based application based on reusable components and includes everything related to this application, including bundles, configuration, framework properties, capabilities, requirements and custom artifacts.

The _Apache Sling Content Package to Feature Model converter_ (referred as _cp2fm_) is a tool able to extract OSGI bundles, OSGi configurations, resources and iteratively scan nested `content-package`s from an input `content-package` and create one (or more) _Apache Sling Feature_ model files and deploy the extracted OSGi bundles in a directory which structure is compliant the _Apache Maven_ repository conventions.

## Understanding the Input

As exposed above, `content-package`s are archives, compressed with the ZIP algorithm, which contain:

 * OSGi bundles, conventionally found under the `jcr_root/apps/<application>/install(.runMode)/<bundle>.jar` path; typically, OSGi bundles are also valid _Apache Maven_ artifacts, that means that they contain _Apache Maven_ metadata files such as `META-INF/maven/<groupId>/<artifactId>/pom.(xml|properties)`;
 * OSGi configurations, conventionally found under the `jcr_root/apps/<application>/config(.runMode)/<configuration>.<extension>` path;
 * nested `content-package`s, conventionally found under the `jcr_root/etc/packages/<package-name>.zip` path;
 * Metadata files, under the `META-INF/` directory;
 * any other kind of resource.

### a content-package sample

We can have a look at what's inside a `test-content-package.zip` test `content-package` included in the `cp2fm` test resources:

```
$ unzip -l ./content-package-2-feature-model/src/test/resources/org/apache/sling/cp2fm/test-content-package.zip 
Archive:  content-package-2-feature-model/src/test/resources/org/apache/sling/cp2fm/test-content-package.zip
  Length      Date    Time    Name
---------  ---------- -----   ----
        0  03-12-2019 17:31   META-INF/
       69  03-12-2019 17:31   META-INF/MANIFEST.MF
        0  03-12-2019 17:06   jcr_root/
        0  03-12-2019 17:06   jcr_root/etc/
        0  03-12-2019 17:06   jcr_root/etc/packages/
        0  03-12-2019 17:30   jcr_root/etc/packages/asd/
    34493  03-12-2019 17:30   jcr_root/etc/packages/asd/test-bundles.zip
     8333  03-12-2019 17:09   jcr_root/etc/packages/asd/test-content.zip
     7235  03-12-2019 17:08   jcr_root/etc/packages/asd/test-configurations.zip
        0  03-12-2019 15:28   META-INF/maven/
        0  03-12-2019 15:29   META-INF/maven/org.apache.sling/
        0  02-28-2019 14:27   META-INF/maven/org.apache.sling/org.apache.sling.feature.cpconverter.all/
     1231  03-12-2019 15:30   META-INF/maven/org.apache.sling/org.apache.sling.feature.cpconverter.all/pom.xml
      127  03-12-2019 15:30   META-INF/maven/org.apache.sling/org.apache.sling.feature.cpconverter.all/pom.properties
        0  03-12-2019 17:06   META-INF/vault/
      892  03-12-2019 15:32   META-INF/vault/settings.xml
      840  03-12-2019 15:47   META-INF/vault/properties.xml
     3579  03-12-2019 15:33   META-INF/vault/config.xml
      267  03-12-2019 15:50   META-INF/vault/filter.xml
---------                     -------
    63214                     20 files
```

Where the `test-bundles.zip` is a nested `content-package` wrapping OSGi bundles:

```
$ unzip -l test-bundles.zip 
Archive:  test-bundles.zip
  Length      Date    Time    Name
---------  ---------- -----   ----
        0  03-12-2019 17:30   META-INF/
       69  03-12-2019 17:30   META-INF/MANIFEST.MF
        0  03-11-2019 23:39   jcr_root/
        0  03-11-2019 23:31   jcr_root/apps/
        0  03-12-2019 17:26   jcr_root/apps/asd/
        0  03-11-2019 23:32   jcr_root/apps/asd/install/
    13288  12-06-2018 12:30   jcr_root/apps/asd/install/test-framework.jar
        0  03-12-2019 17:16   jcr_root/apps/asd/install.publish/
     7210  03-12-2019 17:15   jcr_root/apps/asd/install.publish/test-api.jar
        0  03-12-2019 17:18   jcr_root/apps/asd/install.author/
     7735  03-12-2019 17:17   jcr_root/apps/asd/install.author/test-api.jar
        0  03-11-2019 23:42   META-INF/maven/
        0  03-11-2019 23:43   META-INF/maven/org.apache.sling/
        0  02-28-2019 14:26   META-INF/maven/org.apache.sling/org.apache.sling.feature.cpconverter.bundles/
     1229  03-12-2019 10:22   META-INF/maven/org.apache.sling/org.apache.sling.feature.cpconverter.bundles/pom.xml
      131  03-12-2019 00:26   META-INF/maven/org.apache.sling/org.apache.sling.feature.cpconverter.bundles/pom.properties
        0  03-12-2019 12:41   META-INF/vault/
      888  03-12-2019 00:28   META-INF/vault/settings.xml
      954  03-12-2019 15:33   META-INF/vault/properties.xml
     3571  03-12-2019 00:27   META-INF/vault/config.xml
      891  03-12-2019 00:28   META-INF/vault/filter.xml
      842  03-12-2019 00:27   META-INF/vault/filter-plugin-generated.xml
---------                     -------
    79844                     29 files
```

the `test-configurations.zip` contains OSGi configurations:

```
$ unzip -l test-configurations.zip 
Archive:  test-configurations.zip
  Length      Date    Time    Name
---------  ---------- -----   ----
        0  03-12-2019 17:08   META-INF/
       69  03-12-2019 17:08   META-INF/MANIFEST.MF
        0  03-12-2019 10:21   META-INF/maven/
        0  03-12-2019 10:21   META-INF/maven/org.apache.sling/
        0  02-28-2019 14:25   META-INF/maven/org.apache.sling/org.apache.sling.feature.cpconverter.config/
     1228  03-12-2019 10:24   META-INF/maven/org.apache.sling/org.apache.sling.feature.cpconverter.config/pom.xml
      129  03-12-2019 10:22   META-INF/maven/org.apache.sling/org.apache.sling.feature.cpconverter.config/pom.properties
        0  03-12-2019 13:23   META-INF/vault/
       94  02-28-2019 14:25   META-INF/vault/settings.xml
      664  03-12-2019 15:13   META-INF/vault/properties.xml
     3579  02-28-2019 14:25   META-INF/vault/config.xml
      175  03-12-2019 10:37   META-INF/vault/filter.xml
        0  02-28-2019 14:25   jcr_root/
        0  03-12-2019 10:17   jcr_root/apps/
        0  02-28-2019 14:25   jcr_root/apps/asd/
        0  03-12-2019 10:17   jcr_root/apps/asd/config/
      438  02-28-2019 14:25   jcr_root/apps/asd/config/org.apache.sling.commons.log.LogManager.factory.config-asd-retail.xml
        0  03-12-2019 10:18   jcr_root/apps/asd/config.publish/
      377  02-28-2019 14:25   jcr_root/apps/asd/config.publish/org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended-asd-retail.xml
      244  02-28-2019 14:25   jcr_root/apps/.content.xml
---------                     -------
    25441                     23 files
```

and the `test-content.zip` package includes resources of various nature:

```
$ unzip -l test-content.zip 
Archive:  test-content.zip
  Length      Date    Time    Name
---------  ---------- -----   ----
        0  03-12-2019 17:09   META-INF/
       69  03-12-2019 17:09   META-INF/MANIFEST.MF
        0  03-12-2019 11:31   META-INF/maven/
        0  03-12-2019 11:31   META-INF/maven/org.apache.sling/
        0  02-28-2019 14:26   META-INF/maven/org.apache.sling/org.apache.sling.feature.cpconverter.content/
     1229  03-12-2019 11:32   META-INF/maven/org.apache.sling/org.apache.sling.feature.cpconverter.content/pom.xml
      131  03-12-2019 11:32   META-INF/maven/org.apache.sling/org.apache.sling.feature.cpconverter.content/pom.properties
        0  03-12-2019 12:40   META-INF/vault/
      118  02-28-2019 14:26   META-INF/vault/settings.xml
      859  03-12-2019 15:12   META-INF/vault/properties.xml
     3571  03-12-2019 12:42   META-INF/vault/config.xml
      895  03-12-2019 12:57   META-INF/vault/filter.xml
       72  02-28-2019 14:26   META-INF/vault/filter-plugin-generated.xml
        0  03-12-2019 12:30   jcr_root/
        0  03-12-2019 12:31   jcr_root/content/
        0  03-12-2019 12:31   jcr_root/content/asd/
     1021  02-28-2019 14:26   jcr_root/content/asd/.content.xml
     6924  02-28-2019 14:26   jcr_root/content/asd/resources.xml
---------                     -------
    39481                     22 files
```

## Mapping and the Output

All metadata are mainly collected inside one or more, depending by declared run modes in the installation and configuration paths, _Feature_ model files:

```json
$ cat asd.retail.all.json 
{
  "id":"org.apache.sling:asd.retail.all:slingosgifeature:cp2fm-converted-feature:0.0.1",
  "description":"Combined package for asd.Retail",
  "bundles":[
    {
      "id":"org.apache.felix:org.apache.felix.framework:6.0.1",
      "start-order":"5"
    }
  ],
  "configurations":{
    "org.apache.sling.commons.log.LogManager.factory.config-asd-retail":{
      "org.apache.sling.commons.log.pattern":"{0,date,yyyy-MM-dd HH:mm:ss.SSS} {4} [{3}] {5}",
      "org.apache.sling.commons.log.names":[
        "we.retail"
      ],
      "org.apache.sling.commons.log.level":"info",
      "org.apache.sling.commons.log.file":"logs/project-we-retail.log"
    }
  },
  "content-packages:ARTIFACTS|true":[
    "org.apache.sling:asd.retail.all:zip:cp2fm-converted-feature:0.0.1"
  ]
}
```

the `publish` run mode leads the tool to generate a separated _Apache Sling Feature_ model file:

```json
$ cat asd.retail.all-publish.json 
{
  "id":"org.apache.sling:asd.retail.all:slingosgifeature:cp2fm-converted-feature-publish:0.0.1",
  "bundles":[
    {
      "id":"org.apache.sling:org.apache.sling.models.api:1.3.8",
      "start-order":"5"
    }
  ],
  "configurations":{
    "org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended-asd-retail":{
      "user.mapping":[
        "com.asd.sample.we.retail.core:orders=[commerce-orders-service]",
        "com.asd.sample.we.retail.core:frontend=[content-reader-service]"
      ]
    }
  }
}
```

### Binaries

All detected bundles are collected in an _Apache Maven repository_ compliant directory, all other resources are collected in a new `content-package`, usually classified as `cp2fm-converted-feature`, created while scanning the packages, which contains _content only_.

```
$ tree bundles/
artifacts/
└── org
    └── apache
        ├── felix
        │   └── org.apache.felix.framework
        │       └── 6.0.1
        │           ├── org.apache.felix.framework-6.0.1.jar
        │           └── org.apache.felix.framework-6.0.1.pom
        └── sling
            ├── asd.retail.all
            │   └── 0.0.1
            │       ├── asd.retail.all-0.0.1-cp2fm-converted-feature.zip
            │       └── asd.retail.all-0.0.1.pom
            ├── org.apache.sling.api
            │   └── 2.20.0
            │       ├── org.apache.sling.api-2.20.0.jar
            │       └── org.apache.sling.api-2.20.0.pom
            └── org.apache.sling.models.api
                └── 1.3.8
                    ├── org.apache.sling.models.api-1.3.8.jar
                    └── org.apache.sling.models.api-1.3.8.pom

12 directories, 8 files
```

### Supported configurations

All OSGi configuration formats are supported:

 * _Property_ files, which extensions are `.properties` or `.cfg`, see the related [documentation](https://sling.apache.org/documentation/bundles/configuration-installer-factory.html#property-files-cfg);
 * Configuration Files, which extension is `.config`, see the related [documentation](https://sling.apache.org/documentation/bundles/configuration-installer-factory.html#configuration-files-config);
 * JSON format, which extension is `.cfg.json`, see the related [documentation](https://blog.osgi.org/2018/06/osgi-r7-highlights-configuration-admin.html)
 * `sling:OsgiConfig` content nodes, typically `.xml` files.

During the conversion process, all these formats will be parsed and then added in the `configuration` section of the _Sling Feature Model_ file.

### Run Modes

As shown above, run modes in the path lead the tool to create a dedicated _Apache Sling Feature_ model file containing all interested OSGi configurations/bundles.

### Known limitations

Multiple Run Modes are not supported yet.

## Sample APIs

```java
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.**.*; // not real Java syntax

...

List<String> apiRegions = ...;

DefaultFeaturesManager featuresManager = new DefaultFeaturesManager(mergeConfigurations,
                                                                    bundlesStartOrder,
                                                                    featureModelsOutputDirectory,
                                                                    artifactIdOverride,
                                                                    fmPrefix,
                                                                    properties);
if (apiRegions != null) {
    featuresManager.setAPIRegions(apiRegions);
}

ContentPackage2FeatureModelConverter converter = new ContentPackage2FeatureModelConverter(strictValidation)
                                                 .setFeaturesManager(featuresManager)
                                                 .setBundlesDeployer(new DefaultArtifactsDeployer(artifactsOutputDirectory))
                                                 .setEntryHandlersManager(new DefaultEntryHandlersManager())
                                                 .setAclManager(new DefaultAclManager())
                                                 .setEmitter(DefaultPackagesEventsEmitter.open(featureModelsOutputDirectory));

if (filteringPatterns != null && filteringPatterns.length > 0) {
    RegexBasedResourceFilter filter = new RegexBasedResourceFilter();

    for (String filteringPattern : filteringPatterns) {
        filter.addFilteringPattern(filteringPattern);
    }

    converter.setResourceFilter(filter);
}

File[] contentPackages = ...;

converter.convert(contentPackages);
```

The `org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter#convert(File[] contentPackages)` method performs a two-phases algorithm, which:

 * all content-packages dependencies are computed in order to built the correct content-packages processing sequence;
 * all entries of each content-package is scanned and processed accordingly.

### Features Manager service

The [org.apache.sling.feature.cpconverter.features.FeaturesManager](./src/main/java/org/apache/sling/feature/cpconverter/features/FeaturesManager.java), backed by the default implementation [org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager](./src/main/java/org/apache/sling/feature/cpconverter/features/DefaultFeaturesManager.java), is the service responsible to build and collect the Feature Models while scanning the input content-packages.

The additional [org.apache.sling.feature.cpconverter.features.RunmodeMapper](./src/main/java/org/apache/sling/feature/cpconverter/features/RunmodeMapper.java) component, embedded in the `DefaultFeaturesManager`, will take care to create a `runmode.mapping`, in the same Feature Model target directory, where all features will be indexed by runmodes, i.e.:

```
$ cat /my-target-project/src/main/features/runmode.mapping 
#File edited by the Apache Sling Content Package to Sling Feature converter
#Fri Aug 30 12:47:56 CEST 2019
production=org.apache.test.mytest-sample-site.ui.apps-production.json,org.apache.test.components.all-production.json
(default)=org.apache.test.mytest-sample-site.ui.content.json,org.apache.test.mytest-sample-site.ui.apps.json,org.apache.test.components.all.json
```

### Bundles deployer

The [org.apache.sling.feature.cpconverter.artifacts.ArtifactsDeployer](./src/main/java/org/apache/sling/feature/cpconverter/artifacts/ArtifactsDeployer.java) service is designed to let the conversion tool be integrated in external services, i.e. _Apache Maven_.

The [default implementation](./src/main/java/org/apache/sling/feature/cpconverter/artifacts/DefaultArtifactsDeployer.java) just copies bundles in the target output directory, according to the _Apache Maven_ repository layout.

Bundles are collected in an _Apache Maven repository_ compliant directory, all other resources are collected in a new `content-package` created while scanning the packages:

```
$ tree bundles/
artifacts/
└── org
    └── apache
        ├── felix
        │   └── org.apache.felix.framework
        │       └── 6.0.1
        │           ├── org.apache.felix.framework-6.0.1.jar
        │           └── org.apache.felix.framework-6.0.1.pom
        └── sling
            ├── asd.retail.all
            │   └── 0.0.1
            │       ├── asd.retail.all-0.0.1-cp2fm-converted-feature.zip
            │       └── asd.retail.all-0.0.1.pom
            ├── org.apache.sling.api
            │   └── 2.20.0
            │       ├── org.apache.sling.api-2.20.0.jar
            │       └── org.apache.sling.api-2.20.0.pom
            └── org.apache.sling.models.api
                └── 1.3.8
                    ├── org.apache.sling.models.api-1.3.8.jar
                    └── org.apache.sling.models.api-1.3.8.pom

12 directories, 8 files
```

_Apache Maven GAVs_ are extracted from nested bundles metadata and are renamed according to the _Apache Maven_ conventions; if no _Apache Maven GAVs_ are provided, OSGi `Bundle-SymbolicName`, `Bundle-Name` and `Bundle-Version` metadata will be used to supply the missing informations.

#### Local Maven Repo as Cache

The converter will create a Maven Dependency folder structure and create a POM file for any converted Content Package file.
This will allow subsequent Feature tools to find and process them.

The **group and artifact id** of the converted Content Package and Bundles is taken from the file itself (Content Package's Vault Properties file, Bundle's Headers).
Because these sources might not correspond with the CPs or Bundles regular Maven place the Converter will place them accordingly to the found data hence in a new place.

This does not bother the Sling Feature Maven Plugin nor the Feature Launcher as they are still able to find the dependencies if placed in the Local Maven Repo.

### Handler Service

In order to make the tool extensible, the [org.apache.sling.feature.cpconverter.handlers.EntryHandler](./src/main/java/org/apache/sling/feature/cpconverter/spi/EntryHandler.java) interface is declared to handle different kind of resources, have a look at the [org.apache.sling.feature.cpconverter.handlers](src/main/java/org/apache/sling/feature/cpconverter/handlers) package to see the default implementations.

If users want to handle special resource type, all they have to do is providing their `org.apache.sling.feature.cpconverter.handlers.EntryHandler` service implementation and declaring them in the `META-INF/services/org.apache.sling.feature.cpconverter.handlers.EntryHandler` classpath resource file, on order to let the `ServiceLoader` including it in the `content-package` scan.

All handlers are managed by the [org.apache.sling.feature.cpconverter.handlers.EntryHandlersManager](./src/main/java/org/apache/sling/feature/cpconverter/spi/EntryHandlersManager.java) service, which default implementation is [org.apache.sling.feature.cpconverter.handlers.DefaultEntryHandlersManager](./src/main/java/org/apache/sling/feature/cpconverter/spi/DefaultEntryHandlersManager.java)

#### Built-in handlers

|  Entry type | Entry regular expression | Handler |
|:-----------:|:------------------------:|:-------:|
| Node types | `/META-INF/vault/nodetypes\.cnd` | [org.apache.sling.feature.cpconverter.handlers.NodeTypesEntryHandler](./src/main/java/org/apache/sling/feature/cpconverter/handlers/NodeTypesEntryHandler.java) |
| Privileges | `/META-INF/vault/privileges\.xml` | [org.apache.sling.feature.cpconverter.handlers.PrivilegesHandler](./src/main/java/org/apache/sling/feature/cpconverter/handlers/PrivilegesHandler.java) |
| Rep Policy | `/jcr_root(*/)_rep_policy.xml` | [org.apache.sling.feature.cpconverter.handlers.RepPolicyEntryHandler](./src/main/java/org/apache/sling/feature/cpconverter/handlers/RepPolicyEntryHandler.java) |
| System Users | `/jcr_root(/home/users/*/)\.content.xml` | [org.apache.sling.feature.cpconverter.handlers.SystemUsersEntryHandler](./src/main/java/org/apache/sling/feature/cpconverter/handlers/SystemUsersEntryHandler.java) |
| Sub content-packages | `/jcr_root/(etc/packages\|apps/*/install[\.${runMode}]/*.zip` | [org.apache.sling.feature.cpconverter.handlers.ContentPackageEntryHandler](./src/main/java/org/apache/sling/feature/cpconverter/handlers/ContentPackageEntryHandler.java) |
| OSGi Bundle | `/jcr_root/(apps\|libs)/*/install[\.${runMode}]/[startLevel/]*.jar` | [org.apache.sling.feature.cpconverter.handlers.BundleEntryHandler](./src/main/java/org/apache/sling/feature/cpconverter/handlers/BundleEntryHandler.java) |
| OSGi Configuration | `/jcr_root/(apps\|libs)/*/config[\.${runMode}]/*.config` | [org.apache.sling.feature.cpconverter.handlers.ConfigurationEntryHandler](./src/main/java/org/apache/sling/feature/cpconverter/handlers/ConfigurationEntryHandler.java) |
| OSGi JSON Configuration | `/jcr_root/(apps\|libs)/*/config[\.${runMode}]/*.cfg.json` | [org.apache.sling.feature.cpconverter.handlers.JsonConfigurationEntryHandler](./src/main/java/org/apache/sling/feature/cpconverter/handlers/JsonConfigurationEntryHandler.java) |
| OSGi Properties Configuration | `/jcr_root/(apps\|libs)/*/config[.${runMode}]/*.cfg.properties` | [org.apache.sling.feature.cpconverter.handlers.PropertiesConfigurationEntryHandler](./src/main/java/org/apache/sling/feature/cpconverter/handlers/PropertiesConfigurationEntryHandler.java) |
| OSGi XML Configuration | `/jcr_root/(apps\|libs)/*/config[\.${runMode}]/*.xml` | [org.apache.sling.feature.cpconverter.handlers.XmlConfigurationEntryHandler](./src/main/java/org/apache/sling/feature/cpconverter/handlers/XmlConfigurationEntryHandler.java) |

Everything else, unless users will deploy in the classpath a custom `org.apache.sling.feature.cpconverter.handlers.EntryHandler` implementation, is considered as part of the final content-package, handled by the [org.apache.sling.feature.cpconverter.handlers.ContentPackageEntryHandler](./src/main/java/org/apache/sling/feature/cpconverter/handlers/ContentPackageEntryHandler.java)

### ACL Management

While scanning the input content-package(s), all ACLs entries are handled by the [org.apache.sling.feature.cpconverter.acl.AclManager](./src/main/java/org/apache/sling/feature/cpconverter/acl/AclManager.java) service which is in charge of collecting and computing:

 * System Users;
 * System Users related ACLs;
 * Privileges;
 * Node types registrations;
 * Any `repoinit` additional instruction.
 
Default implementation is provided by [org.apache.sling.feature.cpconverter.acl.DefaultAclManager](./src/main/java/org/apache/sling/feature/cpconverter/acl/DefaultAclManager.java).

#### Please note

ACLs are set in the `repoinit` section for detected System Users _only_, all other ACLs will be 1:1 copied in the related `/jcr_root(/home/users/*/)\.content.xml` file, which will contain filtered ACLs.

### Content-Packages events

SAX-alike events are emitted while processing input (sub-)content-packages, all them are handled by the [org.apache.sling.feature.cpconverter.vltpkg.PackagesEventsEmitter](./src/main/java/org/apache/sling/feature/cpconverter/vltpkg/PackagesEventsEmitter.java).

The default implementation [org.apache.sling.feature.cpconverter.vltpkg.DefaultPackagesEventsEmitter](./src/main/java/org/apache/sling/feature/cpconverter/vltpkg/DefaultPackagesEventsEmitter.java) will take care of creating, in the same Feature Model target directory, a `content-packages.csv` index file, where all (sub-)content-packages will be enlisted with their related complete path, i.e.:

```
# File created on Fri Aug 30 12:47:55 CEST 2019 by the Apache Sling Content Package to Sling Feature converter
# content-package path, content-package ID, content-package type, content-package parent ID, path in parent content-package, absolute path
/Users/asfuser/content-packages/org.apache.test.components.all-2.5.1-SNAPSHOT.zip,org/apache/test:org.apache.test.components.all:2.5.1-SNAPSHOT,MIXED,,,
/Users/asfuser/content-packages/org.apache.test.components.all-2.5.1-SNAPSHOT.zip,org/apache/test:org.apache.test.components.content:2.5.1-SNAPSHOT,APPLICATION,org/apache/test:org.apache.test.components.all:2.5.1-SNAPSHOT,/jcr_root/etc/packages/org/apache/test/org.apache.test.components.content-2.5.1-SNAPSHOT.zip,/Users/asfuser/content-packages/org.apache.test.components.all-2.5.1-SNAPSHOT.zip!/jcr_root/etc/packages/org/apache/test/org.apache.test.components.content-2.5.1-SNAPSHOT.zip
/Users/asfuser/content-packages/org.apache.test.components.all-2.5.1-SNAPSHOT.zip,org/apache/test:org.apache.test.components.config:2.5.1-SNAPSHOT,APPLICATION,org/apache/test:org.apache.test.components.all:2.5.1-SNAPSHOT,/jcr_root/etc/packages/org/apache/test/org.apache.test.components.config-2.5.1-SNAPSHOT.zip,/Users/asfuser/content-packages/org.apache.test.components.all-2.5.1-SNAPSHOT.zip!/jcr_root/etc/packages/org/apache/test/org.apache.test.components.config-2.5.1-SNAPSHOT.zip
```

The `!` character is used to separate nested sub-content-packages path.

## The CLI Tool

The tool is distributed with a commodity package containing all is needed in order to launch the `ContentPackage2FeatureModelConverter` form the shell:

```
$ unzip -l org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT.zip 
Archive:  org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT.zip
  Length      Date    Time    Name
---------  ---------- -----   ----
        0  03-13-2019 15:58   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/
        0  03-13-2019 15:58   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/bin/
        0  03-13-2019 15:58   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/
     4605  02-27-2019 16:30   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/README.md
   801904  02-28-2019 14:55   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/jackrabbit-spi-commons-2.19.1.jar
    14744  02-11-2019 15:44   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/osgi.annotation-6.0.1.jar
    35919  02-11-2019 15:44   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/org.osgi.service.component.annotations-1.3.0.jar
    23575  02-11-2019 15:44   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/org.osgi.service.metatype.annotations-1.3.0.jar
    34518  02-27-2019 15:28   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/org.apache.felix.scr.annotations-1.11.0.jar
    45199  03-13-2019 15:58   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT.jar
    17489  03-13-2019 15:58   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/LICENSE
   588337  02-11-2019 12:49   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/commons-collections-3.2.2.jar
   108555  02-11-2019 15:45   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/xz-1.8.jar
    52873  03-05-2019 17:31   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/plexus-classworlds-2.6.0.jar
   165965  03-05-2019 18:02   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/maven-model-3.6.0.jar
      178  02-27-2019 15:56   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/NOTICE
   745712  02-28-2019 10:02   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/org.apache.jackrabbit.vault-3.2.6.jar
  2374421  02-27-2019 15:28   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/biz.aQute.bndlib-3.2.0.jar
     3263  03-13-2019 15:58   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/bin/cp2sf.bat
    69246  02-11-2019 12:49   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/jcr-2.0.jar
   113508  02-11-2019 12:36   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/org.apache.felix.converter-1.0.0.jar
    12548  02-11-2019 12:36   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/org.osgi.util.function-1.0.0.jar
   176142  02-11-2019 12:35   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/org.apache.felix.utils-1.11.0.jar
   155618  03-04-2019 00:12   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/org.apache.felix.configadmin-1.9.12.jar
    75443  03-05-2019 14:58   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/plexus-io-3.1.1.jar
    57954  02-11-2019 12:39   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/snappy-0.4.jar
   148098  02-11-2019 12:39   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/xbean-reflect-3.7.jar
     3808  03-13-2019 15:58   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/bin/cp2sf
   214788  02-11-2019 15:44   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/commons-io-2.6.jar
    26081  02-11-2019 12:36   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/geronimo-json_1.0_spec-1.0-alpha-1.jar
    90358  02-11-2019 12:35   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/johnzon-core-1.0.0.jar
    14769  02-11-2019 12:35   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/org.osgi.annotation.versioning-1.0.0.jar
   475256  02-11-2019 12:35   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/osgi.core-6.0.0.jar
    28688  02-11-2019 12:48   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/slf4j-api-1.7.6.jar
    28561  02-28-2019 14:55   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/jackrabbit-spi-2.19.1.jar
   403186  02-28-2019 14:55   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/jackrabbit-jcr-commons-2.19.1.jar
    49017  03-04-2019 15:12   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/jackrabbit-api-2.19.1.jar
   260371  03-05-2019 14:58   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/plexus-utils-3.1.1.jar
   639592  02-11-2019 12:39   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/google-collections-1.0.jar
    10684  02-11-2019 12:48   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/slf4j-simple-1.7.6.jar
   164159  02-11-2019 12:48   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/org.apache.sling.feature.io-1.0.0.jar
   289040  02-11-2019 12:36   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/org.apache.felix.configurator-1.0.4.jar
   591748  02-11-2019 15:45   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/commons-compress-1.18.jar
   242435  02-27-2019 15:58   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/picocli-3.6.0.jar
   115238  02-11-2019 12:48   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/org.apache.sling.feature-1.0.0.jar
    18587  02-11-2019 15:46   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/annotations-16.0.3.jar
   191914  03-05-2019 14:58   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/plexus-archiver-4.1.0.jar
   229982  03-05-2019 17:31   org.apache.sling.feature.cpconverter-0.0.1-SNAPSHOT/lib/plexus-container-default-2.0.0.jar
---------                     -------
  9914076                     48 files
```

once the package is decompressed, open the shell and type:

```
$ ./bin/cp2sf -h
Usage: cp2fm [-hmqsvX] -a=<artifactsOutputDirectory> [-b=<bundlesStartOrder>]
             [-i=<artifactIdOverride>] -o=<featureModelsOutputDirectory>
             [-p=<fmPrefix>] [-D=<String=String>]...
             [-f=<filteringPatterns>]... [-r=<apiRegions>]...
             content-packages...
Apache Sling Content Package to Sling Feature converter
      content-packages...   The content-package input file(s).
  -a, --artifacts-output-directory=<artifactsOutputDirectory>
                            The output directory where the artifacts will be
                              deployed.
  -b, --bundles-start-order=<bundlesStartOrder>
                            The order to start detected bundles.
  -D, --define=<String=String>
                            Define a system property
  -f, --filtering-patterns=<filteringPatterns>
                            Regex based pattern(s) to reject content-package archive
                              entries.
  -h, --help                Display the usage message.
  -i, --artifact-id=<artifactIdOverride>
                            The optional Artifact Id the Feature File will have,
                              once generated; it will be derived, if not specified.
  -m, --merge-configurations
                            Flag to mark OSGi configurations with same PID will be
                              merged, the tool will fail otherwise.
  -o, --features-output-directory=<featureModelsOutputDirectory>
                            The output directory where the Feature File will be
                              generated.
  -p, --fm-prefix=<fmPrefix>
                            The optional prefix of the output file
  -q, --quiet               Log errors only.
  -r, --api-region=<apiRegions>
                            The API Regions assigned to the generated features
  -s, --strict-validation   Flag to mark the content-package input file being strict
                              validated.
  -v, --version             Display version information.
  -X, --verbose             Produce execution debug output.
  -Z, --fail-on-mixed-packages
                            Fail the conversion if the resulting attached
                              content-package is MIXED type
Copyright(c) 2019 The Apache Software Foundation.
```

to see all the available options; a sample execution could look like:

```
$ ./bin/cp2sf -v -b 20 -c /content-package-2-feature-model/src/test/resources/org/apache/sling/cp2fm/test-content-package.zip -a /cache -o /tmp
```

Argument Files for Long Command Lines:

```
# argfile
# comments are supported

-v
-b 20
-c /content-package-2-feature-model/src/test/resources/org/apache/sling/cp2fm/test-content-package.zip
-o /tmp
```

then execute the command

```
$ ./bin/cp2sf @argfile
````

## Failures and Restrictions

There could be cases where default handlers would be not enough to create pure content `content-pacake(s)` archives, by enabling the `-Z` option in the CLI tool, or via [ContentPackage2FeatureModelConverter#setFailOnMixedPackages(boolean)](./src/main/java/org/apache/sling/feature/cpconverter/ContentPackage2FeatureModelConverter.java#L151) API, the converter will fail the process if the resulting `content-pacake(s)` is of MIXED type.
