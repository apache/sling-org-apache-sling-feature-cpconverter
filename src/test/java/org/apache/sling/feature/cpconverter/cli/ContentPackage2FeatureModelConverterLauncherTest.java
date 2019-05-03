package org.apache.sling.feature.cpconverter.cli;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.packaging.CyclicDependencyException;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContentPackage2FeatureModelConverterLauncherTest {
    
    /**
     * Test package A-1.0. Depends on B and C-1.X
     * Test package B-1.0. Depends on C
     */
    private static String[] TEST_PACKAGES_INPUT = {"test_c-1.0.zip","test_a-1.0.zip","test_b-1.0.zip"}; 
    private static String[] TEST_PACKAGES_OUTPUT = {"test_c-1.0.zip","test_b-1.0.zip","test_a-1.0.zip"}; 
    
    private static String[] TEST_PACKAGES_CYCLIC_DEPENDENCY = {"test_d-1.0.zip","test_c-1.0.zip","test_a-1.0.zip","test_b-1.0.zip","test_e-1.0.zip"}; 
    

    @Test
    public void testPackageOrdering() throws CyclicDependencyException {
        ContentPackage2FeatureModelConverterLauncher launcher = new ContentPackage2FeatureModelConverterLauncher();
        Logger logger = LoggerFactory.getLogger("test");
        List<File> contentPackages = new ArrayList<File>();
        
        for (String pkgName : TEST_PACKAGES_INPUT) {
            URL packageUrl = getClass().getResource(pkgName);
            contentPackages.add(FileUtils.toFile(packageUrl));
        }
        List<File> ordered = launcher.order(contentPackages, logger);
        Iterator<File> fileIt = ordered.iterator();
        for (String expected : TEST_PACKAGES_OUTPUT) {
            File next = fileIt.next();
            assertEquals(expected, next.getName());
        }
        
    }
    
    @Test(expected = CyclicDependencyException.class)
    public void testDependencyCycle() throws CyclicDependencyException {
        ContentPackage2FeatureModelConverterLauncher launcher = new ContentPackage2FeatureModelConverterLauncher();
        Logger logger = LoggerFactory.getLogger("test");
        List<File> contentPackages = new ArrayList<File>();
        
        for (String pkgName : TEST_PACKAGES_CYCLIC_DEPENDENCY) {
            URL packageUrl = getClass().getResource(pkgName);
            contentPackages.add(FileUtils.toFile(packageUrl));
        }
        launcher.order(contentPackages, logger);
    }

}
