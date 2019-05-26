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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;

import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.fs.io.Archive.Entry;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Configurations;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.feature.cpconverter.shared.AbstractContentPackage2FeatureModelConverterTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.google.inject.Inject;
import com.google.inject.Injector;

@RunWith(Parameterized.class)
public class ConfigurationEntryHandlerTest extends AbstractContentPackage2FeatureModelConverterTest {

    private static final String EXPECTED_PID = "org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl";

    private final String resourceConfiguration;

    private final String runMode;

    private final int expectedConfigurationsSize;

    private final AbstractConfigurationEntryHandler configurationEntryHandler;

    @Inject
    private FeaturesManager featuresManager;

    @Inject
    private Injector injector;

    public ConfigurationEntryHandlerTest(String resourceConfiguration,
                                         String runMode,
                                         int expectedConfigurationsSize,
                                         AbstractConfigurationEntryHandler configurationEntryHandler) {
        this.resourceConfiguration = resourceConfiguration;
        this.runMode = runMode;
        this.expectedConfigurationsSize = expectedConfigurationsSize;
        this.configurationEntryHandler = configurationEntryHandler;
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
        when(archive.openInputStream(entry)).thenReturn(getClass().getResourceAsStream(resourceConfiguration));

        featuresManager.init("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1");

        injector.injectMembers(configurationEntryHandler);
        configurationEntryHandler.handle(resourceConfiguration, archive, entry);

        Configurations configurations = featuresManager.getRunMode(runMode).getConfigurations();

        assertEquals(expectedConfigurationsSize, configurations.size());

        if (expectedConfigurationsSize > 0) {
            Configuration configuration = configurations.get(0);

            assertTrue(configuration.getPid(), configuration.getPid().startsWith(EXPECTED_PID));
            assertEquals("Unmatching size: " + configuration.getProperties().size(), 2, configuration.getProperties().size());
        }
    }

    @Parameters
    public static Collection<Object[]> data() {
        String path = "jcr_root/apps/asd/config/";

        return Arrays.asList(new Object[][] {
            { path + EXPECTED_PID + ".empty.cfg", null, 0, new PropertiesConfigurationEntryHandler() },
            { path + EXPECTED_PID + ".cfg", null, 1, new PropertiesConfigurationEntryHandler() },

            { path + EXPECTED_PID + ".empty.cfg.json", null, 0, new JsonConfigurationEntryHandler() },
            { path + EXPECTED_PID + ".cfg.json", null, 1, new JsonConfigurationEntryHandler() },

            { path + EXPECTED_PID + ".empty.config", null, 0, new ConfigurationEntryHandler() },
            { path + EXPECTED_PID + ".config", null, 1, new ConfigurationEntryHandler() },

            { path + EXPECTED_PID + ".empty.xml", null, 0, new XmlConfigurationEntryHandler() },
            { path + EXPECTED_PID + ".xml", null, 1, new XmlConfigurationEntryHandler() },

            { path + EXPECTED_PID + ".empty.xml.cfg", null, 0, new PropertiesConfigurationEntryHandler() },
            { path + EXPECTED_PID + ".xml.cfg", null, 1, new PropertiesConfigurationEntryHandler() },

            // runmode aware folders
            { "jcr_root/apps/asd/config.author/" + EXPECTED_PID + ".config", "author", 1, new ConfigurationEntryHandler() },
            { "jcr_root/apps/asd/config.publish/" + EXPECTED_PID + ".config", "publish", 1, new ConfigurationEntryHandler() },
        });
    }

}
