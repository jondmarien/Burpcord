package tech.chron0.burpcord.core;

import burp.api.montoya.core.BurpSuiteEdition;

/**
 * Immutable snapshot of the running Burp Suite product name and version.
 */
public final class BurpSuiteInfo {

    private final String productName;
    private final String fullVersionString;
    private final BurpSuiteEdition edition;

    public BurpSuiteInfo(String productName, String fullVersionString, BurpSuiteEdition edition) {
        this.productName = productName;
        this.fullVersionString = fullVersionString;
        this.edition = edition;
    }

    public String productName() {
        return productName;
    }

    public String fullVersionString() {
        return fullVersionString;
    }

    public BurpSuiteEdition edition() {
        return edition;
    }
}
