package org.apache.sling.feature.cpconverter.handlers;

import org.apache.sling.feature.Extension;

final class ParseResult {

    private final Extension repoinitExtension;
    private final String excludedAcls;

    public ParseResult(Extension repoinitExtension, String excludedAcls) {
        this.repoinitExtension = repoinitExtension;
        this.excludedAcls = excludedAcls;
    }

    public Extension getRepoinitExtension() {
        return repoinitExtension;
    }

    public String getExcludedAcls() {
        return excludedAcls;
    }
}
