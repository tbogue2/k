// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import org.kframework.backend.java.kil.ConstrainedTerm;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Variable;
import org.kframework.backend.java.util.Utils;

import java.util.Map;

public class TestgenState {
    /**
     * Field used for caching the hash code
     */
    private transient int hashCode = Utils.NO_HASHCODE;

    private final ConstrainedTerm term;

    public TestgenState(ConstrainedTerm term) {
        this.term = term;
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

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof TestgenState)) {
            return false;
        }

        TestgenState testgenState = (TestgenState) object;
        return term.equals(testgenState.term);
    }

    @Override
    public int hashCode() {
        int h = hashCode;
        if (h == Utils.NO_HASHCODE) {
            h = 1;
            h = h * Utils.HASH_PRIME + term.hashCode();
            h = h == 0 ? 1 : h;
            hashCode = h;
        }
        return h;
    }

    @Override
    public String toString() {
        String result = "Term: " + term + "\n";
        result = result + "Substitution:\n";
        result = result + "Depth Limits:\n";
        return result;
    }

}
