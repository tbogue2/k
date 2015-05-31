// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.unparser;

import com.google.inject.Inject;
import org.kframework.kil.Attributes;
import org.kframework.krun.api.TestgenResults;
import org.kframework.transformation.Transformation;

public class PrintTestgenResults implements Transformation<TestgenResults, String> {

    @Inject
    public PrintTestgenResults() {}

    @Override
    public String run(TestgenResults results, Attributes a) {
        return "Testgen results:\n" + results.getResults() + "\n";
    }

    @Override
    public String getName() {
        return "Print testgen results";
    }

}
