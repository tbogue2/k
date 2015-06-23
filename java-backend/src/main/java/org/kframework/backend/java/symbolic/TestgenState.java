// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import org.kframework.backend.java.kil.ConstrainedTerm;
import org.kframework.kil.Term;

import java.util.Map;

public class TestgenState {
    private ConstrainedTerm term;
    private Map<String, Term> substitution;
    private TestgenDepthLimits depthLimits;

    public TestgenState(ConstrainedTerm term, Map<String, Term> substitution,
                        TestgenDepthLimits depthLimits) {
        this.term = term;
        this.substitution = substitution;
        this.depthLimits = depthLimits;
    }

    public ConstrainedTerm getTerm() {
        return term;
    }

    public Map<String, Term> substitution() {
        return substitution;
    }

    public TestgenDepthLimits getDepthLimits() {
        return depthLimits;
    }

    @Override
    public String toString() {
        String result = "Term: " + term + "\n";
        result = result + "Substitution:\n";
        for(String var : substitution.keySet()) {
            result = result + "\t" + var + " -> " + substitution.get(var) + "\n";
        }
        result = result + "Depth Limits: { " + depthLimits + " }";
        return result;
    }

}
