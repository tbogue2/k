// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import org.kframework.backend.java.kil.ConstrainedTerm;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Variable;
import org.kframework.backend.java.util.Utils;

import java.util.Map;
import java.util.Set;

public class TestgenState {
    /**
     * Field used for caching the hash code
     */
    private transient int hashCode = Utils.NO_HASHCODE;

    private final ConstrainedTerm term;

    private final Map<Variable, TestgenDepthLimits> depthLimits;

    //these are intentionally left out of the equals and hash methods
    private final int remainingRuntime;
    private final int depth;

    public TestgenState(ConstrainedTerm term, Map<Variable, TestgenDepthLimits> depthLimits, int remainingRuntime, int depth) {
        this.term = term;
        this.depthLimits = depthLimits;
        this.remainingRuntime = remainingRuntime;
        this.depth = depth;
    }

    public ConstrainedTerm getConstrainedTerm() {
        return term;
    }

    public Term getTerm() {
        return term.term();
    }

    public TermContext getTermContext() {
        return term.termContext();
    }

    public ConjunctiveFormula getConstraint() {
        return term.constraint();
    }

    public Map<Variable, Term> substitution() {
        return term.constraint().substitution();
    }

    public Map<Variable, TestgenDepthLimits> depthLimits() {
        return depthLimits;
    }

    public TestgenDepthLimits getDepthLimit(Variable var) {
        return depthLimits.get(var);
    }

    public Set<Variable> getDepthLimitKeys() {
        return depthLimits.keySet();
    }

    public int getRemainingRuntime() {
        return remainingRuntime;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof TestgenState)) {
            return false;
        }

        TestgenState testgenState = (TestgenState) object;
        return term.equals(testgenState.term) &&
               depthLimits.equals(testgenState.depthLimits);
    }

    @Override
    public int hashCode() {
        int h = hashCode;
        if (h == Utils.NO_HASHCODE) {
            h = 1;
            h = h * Utils.HASH_PRIME + term.hashCode();
            h = h * Utils.HASH_PRIME + depthLimits.hashCode();
            h = h == 0 ? 1 : h;
            hashCode = h;
        }
        return h;
    }

    @Override
    public String toString() {
        String result = "Term:\n" + term + "\n";
        result = result + "Depth Limits:\n";
        for(Variable var: depthLimits.keySet()) {
            result = result + var + " -> {" + depthLimits.get(var) + "}\n";
        }
        result = result + "Remaining Runtime: " + remainingRuntime + "\n";
        return result;
    }

}
