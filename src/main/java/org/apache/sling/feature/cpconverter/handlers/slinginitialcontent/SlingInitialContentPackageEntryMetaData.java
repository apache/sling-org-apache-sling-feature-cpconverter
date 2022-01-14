package org.apache.sling.feature.cpconverter.handlers.slinginitialcontent;

public class SlingInitialContentPackageEntryMetaData {
    
    private final boolean isFileDescriptorEntry;
    private String recomputedPackageEntryPath;

    public SlingInitialContentPackageEntryMetaData(boolean isFileDescriptorEntry, String recomputedPackageEntryPath) {
        this.isFileDescriptorEntry = isFileDescriptorEntry;
        this.recomputedPackageEntryPath = recomputedPackageEntryPath;
    }

    public boolean isFileDescriptorEntry() {
        return isFileDescriptorEntry;
    }

    public String getRecomputedPackageEntryPath() {
        return recomputedPackageEntryPath;
    }
}
