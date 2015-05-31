// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.krun.api;

public class TestgenResults implements KRunResult {
    private String results;

    public TestgenResults(String results) {
        this.results = results;
    }

    public String getResults() {
        return results;
    }
}
