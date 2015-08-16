// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.krun.api;

import java.util.List;

public class TestgenResults implements KRunResult {
    private List<String> results;

    public TestgenResults(List<String> results) {
        this.results = results;
    }

    public List<String> getResults() {
        return results;
    }
}
