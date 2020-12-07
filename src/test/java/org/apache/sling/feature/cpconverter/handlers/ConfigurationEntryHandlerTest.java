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
package org.apache.sling.feature.cpconverter.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Configurations;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.io.json.ConfigurationJSONWriter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConfigurationEntryHandlerTest {

    private static final String EXPECTED_PID = "org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl";
    private static final String REPOINIT_PID = "org.apache.sling.jcr.repoinit.RepositoryInitializer";
    private static final String REPOINIT_TESTCONFIG_PATH = "/jcr_root/apps/asd/config.author/" + REPOINIT_PID + "-test.config";
    private static final String TYPED_TESTCONFIG_PATH = "/jcr_root/apps/asd/config/" + EXPECTED_PID + ".typed.xml";
    private static final String EXPECTED_REPOINIT = "create service user test-user\n" + 
        "    set ACL for test-user\n" + 
        "        allow    jcr:read    on /conf\n" + 
        "    end\n" +
        "create service user test-user2\n" + 
        "    set ACL for test-user2\n" + 
        "        allow    jcr:read    on /conf\n" + 
        "    end";

    private static final String EXPECTED_TYPED_CONFIG = "{\n" + 
        "  \"org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.typed\":  {\n" + 
        "    \"user.default\":\"admin\",\n" + 
        "    \"test.longproperty\":123,\n" + 
        "    \"test.doubleproperty\":1.23,\n" + 
        "    \"test.dateproperty\":1604743842669,\n" + 
        "    \"test.booleanproperty\":true,\n" + 
        "    \"user.mapping\":[\n" + 
        "      \"com.adobe.acs.acs-aem-samples-bundle=admin\",\n" + 
        "      \"com.adobe.acs.acs-aem-samples-bundle:sample-service=oauthservice\"\n" + 
        "    ]\n" + 
        "  }\n" + 
        "}";
    
    private final String resourceConfiguration;

    private final int expectedConfigurationsSize;
    private final int expectedConfigurationsEntrySize;

    private final AbstractConfigurationEntryHandler configurationEntryHandler;
    private final String expectedRunMode;

    public ConfigurationEntryHandlerTest(String resourceConfiguration,
                                         int expectedConfigurationsSize,
                                         int expectedConfigurationsEntrySize,
                                         AbstractConfigurationEntryHandler configurationEntryHandler, 
                                         String expectedRunMode) {
        this.resourceConfiguration = resourceConfiguration;
        this.expectedConfigurationsSize = expectedConfigurationsSize;
        this.expectedConfigurationsEntrySize = expectedConfigurationsEntrySize;
        this.configurationEntryHandler = configurationEntryHandler;
        this.expectedRunMode = expectedRunMode;
    }

    @Test
    public void doesNotMatch() {
        assertFalse(configurationEntryHandler.matches("/this/is/a/path/not/pointing/to/a/valid/configuration.asd"));
    }

    @Test
    public void matches() {
        assertTrue(resourceConfiguration, configurationEntryHandler.matches(resourceConfiguration));
    }

    @Test
    public void parseConfiguration() throws Exception {
        Archive archive = mock(Archive.class);
        Entry entry = mock(Entry.class);

        when(entry.getName()).thenReturn(resourceConfiguration.substring(resourceConfiguration.lastIndexOf('/') + 1));
        when(archive.openInputStream(entry)).thenReturn(getClass().getResourceAsStream(resourceConfiguration.substring(1)));

        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));
        FeaturesManager featuresManager = spy(DefaultFeaturesManager.class);
        when(featuresManager.getTargetFeature()).thenReturn(feature);
        doCallRealMethod().when(featuresManager).addConfiguration(anyString(), anyString(), any());
        when(featuresManager.getRunMode(anyString())).thenReturn(feature);
        ContentPackage2FeatureModelConverter converter = mock(ContentPackage2FeatureModelConverter.class);
        when(converter.getFeaturesManager()).thenReturn(featuresManager);

        configurationEntryHandler.handle(resourceConfiguration, archive, entry, converter);

        Configurations configurations = featuresManager.getRunMode(expectedRunMode).getConfigurations();

        assertEquals(expectedConfigurationsSize, configurations.size());

        if (this.resourceConfiguration.equals(REPOINIT_TESTCONFIG_PATH)) {
            assertEquals(EXPECTED_REPOINIT, featuresManager.getRunMode(expectedRunMode).getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT).getText());
        }

        if (expectedConfigurationsSize != 0) {
            Configuration configuration = configurations.get(0);

            assertTrue(configuration.getPid(), configuration.getPid().startsWith(EXPECTED_PID));

            if (configuration.getPid().contains(".empty")) {
                assertTrue(configuration.getProperties().isEmpty());
            } else {
                assertEquals("Unmatching size: " + configuration.getProperties().size(), expectedConfigurationsEntrySize, configuration.getProperties().size());
            }
            // type & value check for typed configuration
            if (this.resourceConfiguration.equals(TYPED_TESTCONFIG_PATH)) {
                Writer writer = new StringWriter();
                ConfigurationJSONWriter.write(writer, configurations);
                assertEquals(EXPECTED_TYPED_CONFIG, writer.toString());
            }
        }
    }

    @Parameters
    public static Collection<Object[]> data() {
        String path = "/jcr_root/apps/asd/config/";

        return Arrays.asList(new Object[][] {
            { path + EXPECTED_PID + ".empty.cfg", 1, 2, new PropertiesConfigurationEntryHandler(), null },
            { path + EXPECTED_PID + ".cfg", 1, 2, new PropertiesConfigurationEntryHandler(), null },

            { path + EXPECTED_PID + ".empty.cfg.json", 1, 2, new JsonConfigurationEntryHandler(), null },
            { path + EXPECTED_PID + ".cfg.json", 1, 2, new JsonConfigurationEntryHandler(), null },

            { path + EXPECTED_PID + ".empty.config", 1, 2, new ConfigurationEntryHandler(), null },
            { path + EXPECTED_PID + ".config", 1, 2, new ConfigurationEntryHandler(), null },

            { path + EXPECTED_PID + ".empty.xml", 1, 2, new XmlConfigurationEntryHandler(), null },
            { path + EXPECTED_PID + ".xml", 1, 2, new XmlConfigurationEntryHandler(), null },

            { path + EXPECTED_PID + ".empty.config.xml", 1, 2, new XmlConfigurationEntryHandler(), null },
            { path + EXPECTED_PID + ".config.xml", 1, 2, new XmlConfigurationEntryHandler(), null },

            
            { path + EXPECTED_PID + ".empty.xml.cfg", 1, 2, new PropertiesConfigurationEntryHandler(), null },
            { path + EXPECTED_PID + ".xml.cfg", 1, 2, new PropertiesConfigurationEntryHandler(), null },

            // runmode aware folders
            { "/jcr_root/apps/asd/config.author/" + EXPECTED_PID + ".config", 1, 2, new ConfigurationEntryHandler(), "author" },
            { REPOINIT_TESTCONFIG_PATH, 0, 2, new ConfigurationEntryHandler() , "author"},
            { "/jcr_root/apps/asd/config.publish/" + EXPECTED_PID + ".config", 1, 2, new ConfigurationEntryHandler(), "publish" },

            //test typed config
            { TYPED_TESTCONFIG_PATH, 1, 6, new XmlConfigurationEntryHandler(), null }
        });
    }

}
