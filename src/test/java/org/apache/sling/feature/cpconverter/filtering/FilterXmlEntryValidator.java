package org.apache.sling.feature.cpconverter.filtering;

import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FilterXmlEntryValidator {

    private final DefaultWorkspaceFilter expectedWorkSpaceFilter = new DefaultWorkspaceFilter();
    private final DefaultWorkspaceFilter actualWorkSpaceFilter = new DefaultWorkspaceFilter();

    public FilterXmlEntryValidator(File expectedFile, InputStream actualInputStream) throws IOException, ConfigurationException {
        this.expectedWorkSpaceFilter.load(expectedFile);
        this.actualWorkSpaceFilter.load(actualInputStream);
    }

    public void validate() {
        boolean roughEqual =  actualWorkSpaceFilter.equals(expectedWorkSpaceFilter);
        assertTrue("Filter XML validation: Base equal comparison failed between expected filter and actual filter",roughEqual);

        for(PathFilterSet pathFilterSet : expectedWorkSpaceFilter.getFilterSets()){
            PathFilterSet candidateFilter = actualWorkSpaceFilter.getFilterSets()
                                                        .stream()
                                                        .filter((filter) ->
                                                                filter.getRoot().equals(pathFilterSet.getRoot()
                                                        )).findFirst().orElseThrow(NullPointerException::new);
            assertEquals("Filter XML validation: import mode is not equal between expected filter and actual filter", pathFilterSet.getImportMode(), candidateFilter.getImportMode());
        }


    }
}
