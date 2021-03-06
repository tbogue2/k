// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.kil;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.kframework.backend.java.symbolic.ConjunctiveFormula;
import org.kframework.backend.java.symbolic.DisjunctiveFormula;
import org.kframework.backend.java.symbolic.PatternExpander;
import org.kframework.backend.java.symbolic.Transformer;
import org.kframework.backend.java.symbolic.Visitor;
import org.kframework.backend.java.util.Utils;
import org.kframework.kil.ASTNode;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;


/**
 * A K term associated with symbolic constraints.
 *
 * @author AndreiS
 */
public class ConstrainedTerm extends JavaSymbolicObject {

    public static class Data {
        public final Term term;
        public final ConjunctiveFormula constraint;
        public Data(Term term, ConjunctiveFormula constraint) {
            super();
            this.term = term;
            this.constraint = constraint;
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((constraint == null) ? 0 : constraint.hashCode());
            result = prime * result + ((term == null) ? 0 : term.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Data other = (Data) obj;
            if (constraint == null) {
                if (other.constraint != null)
                    return false;
            } else if (!constraint.equals(other.constraint))
                return false;
            if (term == null) {
                if (other.term != null)
                    return false;
            } else if (!term.equals(other.term))
                return false;
            return true;
        }

    }

    private Data data;

    private final TermContext context;

    public ConstrainedTerm(Term term, ConjunctiveFormula constraint) {
        Data data = new Data(term, constraint);
        this.data = data;
        this.context = TermContext.of(data.constraint.termContext().global(),
                term, data.constraint.termContext().getCounter());
    }

    public ConstrainedTerm(Term term, TermContext context) {
        this(term, ConjunctiveFormula.of(context));
    }

    public TermContext termContext() {
        return context;
    }

    public ConjunctiveFormula constraint() {
        return data.constraint;
    }

    public boolean implies(ConstrainedTerm constrainedTerm) {
        return matchImplies(constrainedTerm) != null;
    }

    public ConstrainedTerm expandPatterns(boolean narrowing) {
        ConstrainedTerm result = this;
        while (true) {
            PatternExpander patternExpander = new PatternExpander(
                    result.constraint(),
                    narrowing,
                    context);
            ConstrainedTerm expandedTerm = (ConstrainedTerm) result.accept(patternExpander);
            if (expandedTerm == result) {
                break;
            }
            result = new ConstrainedTerm(
                    expandedTerm.term(),
                    expandedTerm.constraint().add(patternExpander.extraConstraint()).simplify());
        }

        return result;
    }

    /**
     * Checks if this constrained term implies the given constrained term, assuming the variables
     * occurring only in the given constrained term (but not in this constrained term) are
     * existentially quantified.
     */
    public ConjunctiveFormula matchImplies(ConstrainedTerm constrainedTerm) {
        ConjunctiveFormula constraint = ConjunctiveFormula.of(constrainedTerm.termContext())
                .add(data.constraint.substitution())
                .add(data.term, constrainedTerm.data.term)
                .simplifyBeforePatternFolding();
        if (constraint.isFalse()) {
            return null;
        }

        /* apply pattern folding */
        constraint = constraint.simplifyModuloPatternFolding()
                .add(constrainedTerm.data.constraint)
                .simplifyModuloPatternFolding();
        if (constraint.isFalse()) {
            return null;
        }

        constraint = constraint.expandPatterns(false).simplifyModuloPatternFolding();
        if (constraint.isFalse()) {
            return null;
        }

        Set<Variable> rightOnlyVariables = Sets.difference(constraint.variableSet(), variableSet());
        constraint = constraint.orientSubstitution(rightOnlyVariables);

        ConjunctiveFormula leftHandSide = data.constraint;
        ConjunctiveFormula rightHandSide = constraint.removeBindings(rightOnlyVariables);
        if (!leftHandSide.implies(rightHandSide, rightOnlyVariables)) {
            return null;
        }

        return data.constraint.addAndSimplify(constraint);
    }

    public Term term() {
        return data.term;
    }

    /**
     * Unifies this constrained term with another constrained term.
     *
     * @param constrainedTerm
     *            another constrained term
     * @return solutions to the unification problem
     */
    public List<ConjunctiveFormula> unify(ConstrainedTerm constrainedTerm) {
        /* unify the subject term and the pattern term without considering those associated constraints */
        ConjunctiveFormula constraint = ConjunctiveFormula.of(constrainedTerm.termContext())
                .add(term(), constrainedTerm.term())
                .simplify();
        if (constraint.isFalse()) {
            return Collections.emptyList();
        }

        List<ConjunctiveFormula> candidates = constraint.getDisjunctiveNormalForm().conjunctions().stream()
                .map(c -> c.addAndSimplify(constrainedTerm.constraint()))
                .filter(c -> !c.isFalse())
                .map(ConjunctiveFormula::getDisjunctiveNormalForm)
                .map(DisjunctiveFormula::conjunctions)
                .flatMap(List::stream)
                .map(ConjunctiveFormula::simplify)
                .filter(c -> !c.isFalse())
                .collect(Collectors.toList());

        List<ConjunctiveFormula> solutions = Lists.newArrayList();
        for (ConjunctiveFormula candidate : candidates) {
            candidate = candidate.orientSubstitution(constrainedTerm.variableSet());

            ConjunctiveFormula solution = candidate.addAndSimplify(constraint());
            if (solution.isFalse()) {
                continue;
            }

            /* OPTIMIZATION: if no narrowing happens, the constraint remains unchanged;
             * thus, there is no need to check satisfiability or expand patterns */
            if (!candidate.isMatching(constrainedTerm.variableSet())) {
                if (solution.isFalse() || solution.checkUnsat()) {
                    continue;
                }

                // TODO(AndreiS): find a better place for pattern expansion
                solution = solution.expandPatterns(true).simplify();
                if (solution.isFalse() || solution.checkUnsat()) {
                    continue;
                }
            }

            assert solution.disjunctions().isEmpty();
            solutions.add(solution);
        }

        return solutions;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ConstrainedTerm)) {
            return false;
        }

        ConstrainedTerm constrainedTerm = (ConstrainedTerm) object;
        return data.equals(constrainedTerm.data);
    }

    @Override
    public int hashCode() {
        int h = hashCode;
        if (h == Utils.NO_HASHCODE) {
            h = 1;
            h = h * Utils.HASH_PRIME + data.hashCode();
            h = h == 0 ? 1 : h;
            hashCode = h;
        }
        return h;
    }

    @Override
    public String toString() {
        return data.term + ConjunctiveFormula.SEPARATOR + data.constraint;
    }

    @Override
    public ASTNode accept(Transformer transformer) {
        return transformer.transform(this);
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
