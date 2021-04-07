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
package org.apache.sling.feature.cpconverter.handlers;

import com.google.common.collect.Lists;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.cpconverter.ContentPackage2FeatureModelConverter;
import org.apache.sling.feature.cpconverter.accesscontrol.AclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.DefaultAclManager;
import org.apache.sling.feature.cpconverter.accesscontrol.Mapping;
import org.apache.sling.feature.cpconverter.features.DefaultFeaturesManager;
import org.apache.sling.feature.cpconverter.features.FeaturesManager;
import org.apache.sling.repoinit.parser.impl.RepoInitParserService;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.StringReader;
import java.util.Collection;

import static org.apache.sling.feature.cpconverter.Util.normalize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class RepoInitTest {

    private static final String REPOINIT_PID = "org.apache.sling.jcr.repoinit.RepositoryInitializer";
    private static final String PATH_PREFIX = "/jcr_root/apps/asd/config.publish/" + REPOINIT_PID;

    private static final String REPOINIT_CONVERSION_PATH = "/jcr_root/apps/asd/config.publish/" + REPOINIT_PID + "-conversion-test.config";

    private final AbstractConfigurationEntryHandler configurationEntryHandler;
    private final boolean enforcePrincipalBasedAcSetup;
    private final String enforcedPath;

    private final String name;

    @Parameterized.Parameters(name = "name={1}")
    public static Collection<Object[]> parameters() {
        return Lists.newArrayList(
                new Object[] { true, "Enforce principal-based ac setup" },
                new Object[] { false, "Don't enforce principal-based ac setup" });
    };

    public RepoInitTest(boolean enforcePrincipalBasedAcSetup, String name) {
        this.configurationEntryHandler = new ConfigurationEntryHandler();
        this.enforcePrincipalBasedAcSetup = enforcePrincipalBasedAcSetup;
        this.enforcedPath = (enforcePrincipalBasedAcSetup) ? "/home/users/system/cq:services" : null;
        this.name = name;
    }

    @Test
    public void parseConversionRepoInit() throws Exception {
        String path = PATH_PREFIX + "-conversion-test.config";
        String result = PATH_PREFIX + "-conversion-result.config";

        Extension expectedExtension;
        if (enforcePrincipalBasedAcSetup) {
            expectedExtension = extractExtensions(result, false, false);
        } else {
            expectedExtension = extractExtensions(path, false, false);
        }
        Extension extension = extractExtensions(path, enforcePrincipalBasedAcSetup, false);
        assertNotNull(expectedExtension);
        assertNotNull(extension);
        String txt = normalize(extension.getText());
        assertEquals(name, normalize(expectedExtension.getText()), txt);

        // verify that the generated repo-init is valid
        assertFalse(name, new RepoInitParserService().parse(new StringReader(txt)).isEmpty());
    }

    @Test
    public void parseConversionOmittedForServiceUserRepoInit() throws Exception {
        String path = PATH_PREFIX + "-conversion-test.config";

        Extension expectedExtension = extractExtensions(path, false, true);
        Extension extension = extractExtensions(path, enforcePrincipalBasedAcSetup, true);
        assertNotNull(expectedExtension);
        assertNotNull(extension);
        String txt = normalize(extension.getText());
        assertEquals(name, normalize(expectedExtension.getText()), txt);

        // verify that the generated repo-init is valid
        assertFalse(name, new RepoInitParserService().parse(new StringReader(txt)).isEmpty());
    }

    @Test
    public void parseNoConversionRepoInit() throws Exception {
        String expectedPath = (enforcePrincipalBasedAcSetup) ? PATH_PREFIX + "-no-conversion-result.config" : PATH_PREFIX + "-no-conversion-test.config";

        Extension expectedExtension = extractExtensions(expectedPath, false, false);
        Extension extension = extractExtensions(PATH_PREFIX + "-no-conversion-test.config", enforcePrincipalBasedAcSetup, false);
        assertNotNull(expectedExtension);
        assertNotNull(extension);
        String txt = normalize(extension.getText());
        assertEquals(name, normalize(expectedExtension.getText()), txt);

        // verify that the generated repo-init is valid
        assertFalse(name, new RepoInitParserService().parse(new StringReader(txt)).isEmpty());
    }

    @Test
    public void parseNoConversionWithDiffRepoInit() throws Exception {
        // NOTE: create-path statements with default primary type and set-property cannot be converted 1:1
        // See SLING-10231, SLING-10238 and FIXMEs in DefaultVisitor
        String path = PATH_PREFIX + "-no-conv-with-diff.config";

        String resultTxt = normalize("set properties on /test\n"+
        "set testprop{String} to \"one=two\"\n"+
        "set testprop{String} to \"\\\"one=two\\\"\"\n"+
        "set sling:ResourceType{String} to \"/x/y/z\"\n"+
        "default someInteger{Long} to 42\n"+
        "set someFlag{Boolean} to true\n"+
        "default someDate{Date} to \"2020-03-19T11:39:33.437+05:30\"\n"+
        "set quotedMix{String} to \"quoted\",\"non-quoted\",\"the last \\\" one\"\n"+
        "set aStringMultiValue{String} to \"one\",\"two\",\"three\"\n"+
        "set aLongMultiValue{Long} to 1,2,3\n"+
        "set curlyBracketsAndDoubleQuotes{String} to \"{\\\"one, two\\\":\\\"three, four\\\"}\"\n"+
        "set curlyBracketsAndSingleQuotes{String} to \"{'five, six':'seven,eight'}\"\n"+
        "end");
        Extension extension = extractExtensions(path, enforcePrincipalBasedAcSetup, false);
        assertNotNull(extension);
        String expectedTxt = (enforcePrincipalBasedAcSetup) ? resultTxt : normalize(extractExtensions(path, false, false).getText());
        String txt = normalize(extension.getText());
        assertEquals(name, expectedTxt, txt);

        // verify that the generated repo-init is valid
        assertFalse(name, new RepoInitParserService().parse(new StringReader(txt)).isEmpty());
    }

    private Extension extractExtensions(@NotNull String path, boolean enforcePrincipalBasedAcSetup, boolean addMappingById) throws Exception {
        Archive archive = mock(Archive.class);
        Archive.Entry entry = mock(Archive.Entry.class);

        when(entry.getName()).thenReturn(path.substring(path.lastIndexOf('/') + 1));
        when(archive.openInputStream(entry)).thenReturn(getClass().getResourceAsStream(path.substring(1)));

        Feature feature = new Feature(new ArtifactId("org.apache.sling", "org.apache.sling.cp2fm", "0.0.1", null, null));
        FeaturesManager featuresManager = spy(DefaultFeaturesManager.class);
        when(featuresManager.getTargetFeature()).thenReturn(feature);
        doCallRealMethod().when(featuresManager).addConfiguration(anyString(), anyString(), anyString(), any());
        when(featuresManager.getRunMode(anyString())).thenReturn(feature);

        AclManager aclManager = spy(new DefaultAclManager((enforcePrincipalBasedAcSetup) ? enforcedPath : null, "system"));
        if (addMappingById) {
            aclManager.addMapping(new Mapping("org.apache.sling.testbundle:sub1=su1"));
            aclManager.addMapping(new Mapping("org.apache.sling.testbundle:sub2=su2"));
            aclManager.addMapping(new Mapping("org.apache.sling.testbundle:sub3=su3"));
            aclManager.addMapping(new Mapping("org.apache.sling.testbundle=su-second-script"));
        }

        ((DefaultFeaturesManager) featuresManager).setAclManager(aclManager);

        ContentPackage2FeatureModelConverter converter = mock(ContentPackage2FeatureModelConverter.class);
        when(converter.getFeaturesManager()).thenReturn(featuresManager);
        when(converter.getAclManager()).thenReturn(aclManager);

        configurationEntryHandler.handle(path, archive, entry, converter);
        return featuresManager.getRunMode("publish").getExtensions().getByName(Extension.EXTENSION_NAME_REPOINIT);
    }

}