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

import static org.apache.sling.feature.cpconverter.Util.normalize;
import static org.apache.sling.feature.cpconverter.Util.normalizeUnchecked;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Configurations;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.DefaultAclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.Mapping;
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
    private static final String EXPECTED_REPOINIT = normalizeUnchecked("create service user test-user\n" +
        "    set ACL for test-user\n" + 
        "        allow    jcr:read    on /conf\n" + 
        "    end\n" +
        " create service user test-user2\n" + 
        "    set ACL for test-user2\n" + 
        "        allow    jcr:read    on /conf\n" + 
        "    end\n" +
        " create path /test\n" +
        "    set properties on /test\n" +
        "        set testprop to \"one=two\"\n" +
        "    end")
        ;

    private static final String EXPECTED_TYPED_CONFIG = "{\n" + 
        "  \"org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.typed\":  {\n" + 
        "    \"user.default\":\"admin\",\n" + 
        "    \"test.longproperty\":123,\n" + 
        "    \"test.doubleproperty\":1.23,\n" + 
        "    \"test.dateproperty\":1604743842669,\n" + 
        "    \"test.booleanproperty\":true,\n" + 
        "    \"user.mapping\":[\n" + 
        "      \"org.apache.sling.testbundle:sub-service-1=service1\",\n" +
        "      \"org.apache.sling.testbundle:sub-service-2=[service1,service2]\",\n" +
        "      \"org.apache.sling.testbundle=[service1,external-service-user]\"\n" +
        "    ]\n" +
        "  }\n" + 
        "}";

    private static final String EXPECTED_TYPED_CONFIG_WITH_ENFORCED_PRINCIPAL_MAPPING = "{\n" +
            "  \"org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.typed\":  {\n" +
            "    \"user.default\":\"admin\",\n" +
            "    \"test.longproperty\":123,\n" +
            "    \"test.doubleproperty\":1.23,\n" +
            "    \"test.dateproperty\":1604743842669,\n" +
            "    \"test.booleanproperty\":true,\n" +
            "    \"user.mapping\":[\n" +
            "      \"org.apache.sling.testbundle:sub-service-1=[service1]\",\n" +
            "      \"org.apache.sling.testbundle:sub-service-2=[service1,service2]\",\n" +
            "      \"org.apache.sling.testbundle=[service1,external-service-user]\"\n" +
            "    ]\n" +
            "  }\n" +
            "}";
    
    private final String resourceConfiguration;

    private final int expectedConfigurationsSize;
    private final int expectedConfigurationsEntrySize;
    private final int expectedMappings;

    private final AbstractConfigurationEntryHandler configurationEntryHandler;
    private final String expectedRunMode;
    private final boolean enforceServiceMappingByPrincipal;

    public ConfigurationEntryHandlerTest(String resourceConfiguration,
                                         int expectedConfigurationsSize,
                                         int expectedConfigurationsEntrySize,
                                         int expectedMappings,
                                         AbstractConfigurationEntryHandler configurationEntryHandler, 
                                         String expectedRunMode,
                                         boolean enforceServiceMappingByPrincipal) {
        this.resourceConfiguration = resourceConfiguration;
        this.expectedConfigurationsSize = expectedConfigurationsSize;
        this.expectedConfigurationsEntrySize = expectedConfigurationsEntrySize;
        this.expectedMappings = expectedMappings;

        this.configurationEntryHandler = configurationEntryHandler;

        this.expectedRunMode = expectedRunMode;
        this.enforceServiceMappingByPrincipal = enforceServiceMappingByPrincipal;
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
        ((DefaultFeaturesManager) featuresManager).setEnforceServiceMappingByPrincipal(enforceServiceMappingByPrincipal);
        when(featuresManager.getTargetFeature()).thenReturn(feature);
        doCallRealMethod().when(featuresManager).addConfiguration(anyString(), any(), anyString(), any());
        when(featuresManager.getRunMode(anyString())).thenReturn(feature);
        ContentPackage2FeatureModelConverter converter = mock(ContentPackage2FeatureModelConverter.class);
        when(converter.getFeaturesManager()).thenReturn(featuresManager);
        AclManager aclManager = spy(new DefaultAclManager());
        when(converter.getAclManager()).thenReturn(aclManager);
        ((DefaultFeaturesManager) featuresManager).setAclManager(aclManager);

        configurationEntryHandler.handle(resourceConfiguration, archive, entry, converter);

        Configurations configurations = featuresManager.getRunMode(expectedRunMode).getConfigurations();

        assertEquals(expectedConfigurationsSize, configurations.size());

        if (this.resourceConfiguration.equals(REPOINIT_TESTCONFIG_PATH)) {
            assertEquals(EXPECTED_REPOINIT, normalize(featuresManager.getRunMode(expectedRunMode).getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT).getText()));
        }

        if (expectedConfigurationsSize != 0) {
            Configuration configuration = configurations.get(0);

            assertTrue(configuration.getPid(), configuration.getPid().startsWith(EXPECTED_PID));

            Dictionary<String,Object> props = configuration.getProperties();
            if (configuration.getPid().contains(".empty")) {
                assertTrue(props.isEmpty());
            } else {
                assertEquals("Unmatching size: " + props.size(), expectedConfigurationsEntrySize, configuration.getProperties().size());
                verify(aclManager, times(expectedMappings)).addMapping(any(Mapping.class));
            }
            // type & value check for typed configuration
            if (this.resourceConfiguration.equals(TYPED_TESTCONFIG_PATH)) {
                Writer writer = new StringWriter();
                ConfigurationJSONWriter.write(writer, configurations);
                if (enforceServiceMappingByPrincipal) {
                    assertEquals(EXPECTED_TYPED_CONFIG_WITH_ENFORCED_PRINCIPAL_MAPPING, writer.toString());
                } else {
                    assertEquals(EXPECTED_TYPED_CONFIG, writer.toString());
                }
            }
        }
    }

    @Parameters
    public static Collection<Object[]> data() {
        String path = "/jcr_root/apps/asd/config/";

        return Arrays.asList(new Object[][] {
            { path + EXPECTED_PID + ".empty.cfg", 1, 2, 0, new PropertiesConfigurationEntryHandler(), null, false},
            { path + EXPECTED_PID + ".cfg", 1, 2, 1, new PropertiesConfigurationEntryHandler(), null, false},
            { path + EXPECTED_PID + ".cfg.dir", 0, 0, 0, new PropertiesConfigurationEntryHandler(), null, false},
            { path + EXPECTED_PID + ".cfg.dir/.content.xml", 0, 0, 0, new PropertiesConfigurationEntryHandler(), null, false},


            { path + EXPECTED_PID + ".empty.cfg.json", 1, 2, 0, new JsonConfigurationEntryHandler(), null, true },
            { path + EXPECTED_PID + ".cfg.json", 1, 2, 3, new JsonConfigurationEntryHandler(), null, true },

            { path + EXPECTED_PID + ".empty.config", 1, 2, 0, new ConfigurationEntryHandler(), null, false },
            { path + EXPECTED_PID + ".config", 1, 2, 3, new ConfigurationEntryHandler(), null, false },

            { path + EXPECTED_PID + ".empty.xml", 1, 2, 0, new XmlConfigurationEntryHandler(), null, true },
            { path + EXPECTED_PID + ".xml", 1, 2, 3, new XmlConfigurationEntryHandler(), null, true },

            { path + EXPECTED_PID + ".empty.config.xml", 1, 2, 0, new XmlConfigurationEntryHandler(), null, false },
            { path + EXPECTED_PID + ".config.xml", 1, 2, 3, new XmlConfigurationEntryHandler(), null, false },

            
            { path + EXPECTED_PID + ".empty.xml.cfg", 1, 2, 0,  new PropertiesConfigurationEntryHandler(), null, true },
            { path + EXPECTED_PID + ".xml.cfg", 1, 2, 1, new PropertiesConfigurationEntryHandler(), null, true },

            // runmode aware folders
            { "/jcr_root/apps/asd/config.author/" + EXPECTED_PID + ".config", 1, 2, 3, new ConfigurationEntryHandler(), "author", false },
            { REPOINIT_TESTCONFIG_PATH, 0, 2, 1, new ConfigurationEntryHandler() , "author", true},
            { "/jcr_root/apps/asd/config.publish/" + EXPECTED_PID + ".config", 1, 2, 3, new ConfigurationEntryHandler(), "publish", false },

            //test typed config
            { TYPED_TESTCONFIG_PATH, 1, 6, 3, new XmlConfigurationEntryHandler(), null, true },
            // configuration in "install" folder
            { "/jcr_root/apps/asd/install.publish/" + EXPECTED_PID + ".config", 1, 2, 3, new ConfigurationEntryHandler(), "publish", false },
        });
    }

}
