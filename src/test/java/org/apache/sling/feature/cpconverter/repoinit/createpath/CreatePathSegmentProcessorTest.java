package org.apache.sling.feature.cpconverter.repoinit.createpath;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl;
import org.apache.sling.feature.cpconverter.shared.RepoPath;
import org.apache.sling.feature.cpconverter.vltpkg.VaultPackageAssembler;
import org.apache.sling.repoinit.parser.operations.CreatePath;
import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.assertNotNull;

public class CreatePathSegmentProcessorTest {

    private File testDirectory = new File(System.getProperty("java.io.tmpdir"), getClass().getName() + '_' + System.currentTimeMillis());
    
    private Collection<VaultPackageAssembler> packageAssemblers = new ArrayList<>();


    private VaultPackage createVaultPackage(String location) throws IOException {
        URL resource = CreatePathSegmentProcessorTest.class.getResource(location);
        File file = FileUtils.toFile(resource);
        assertNotNull(file);
        return new PackageManagerImpl().open(file);
    }
    
    @Test
    public void testNothingFound() throws IOException {

        
        VaultPackage vaultPackage = createVaultPackage("test-a-1.0.zip"); 
        CreatePath cp = new CreatePath("sling:Folder");
        RepoPath repoPath = new RepoPath("/apps/mysite/clientlibs/mysite-all");

        prepareVaultPackageAssemblers(vaultPackage);
    
        CreatePathSegmentProcessor processor = new CreatePathSegmentProcessor(repoPath, packageAssemblers, cp);
        processor.processSegments();
        
        System.out.println(cp.asRepoInitString());
    }

    @NotNull
    private void prepareVaultPackageAssemblers(VaultPackage... vaultPackages) throws IOException {
        
        for(VaultPackage vaultPackage: vaultPackages){
            VaultPackageAssembler assembler = VaultPackageAssembler.create(testDirectory, vaultPackage, false, false);
            
            //for these test purposes we want to copy over the contents of the existing test package and pretend we are "assembling" it
            Archive archive = vaultPackage.getArchive();
            
            Archive.Entry parentEntry = archive.getEntry("/jcr_root");

            
            addEntryToAssembler(assembler, "/jcr_root", archive, archive.getEntry("/jcr_root"));
            
            
            packageAssemblers.add(assembler);
        }
        
    }
    
    private void addEntryToAssembler(VaultPackageAssembler assembler, String parentPath, Archive archive, Archive.Entry parentEntry) throws IOException {

       // assembler.addEntry(parentPath, archive, parentEntry);
        
        for(Archive.Entry childEntry: parentEntry.getChildren()) {
            String childPath = parentPath + "/" + childEntry.getName();

            boolean isFile = StringUtils.contains(childEntry.getName(), '.');

            if (isFile) {
                InputStream input = archive.openInputStream(childEntry);
                assembler.addEntry(childPath, input);
            } 
           
            
            addEntryToAssembler(assembler, childPath, archive, childEntry);
        }
        
        
    }


}