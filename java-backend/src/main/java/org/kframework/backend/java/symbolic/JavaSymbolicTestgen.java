// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import com.google.common.collect.BiMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.kframework.backend.java.builtins.FreshOperations;
import org.kframework.backend.java.indexing.RuleIndex;
import org.kframework.backend.java.kil.CellLabel;
import org.kframework.backend.java.kil.ConstrainedTerm;
import org.kframework.backend.java.kil.Definition;
import org.kframework.backend.java.kil.GlobalContext;
import org.kframework.backend.java.kil.Rule;
import org.kframework.backend.java.kil.Sort;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Variable;
import org.kframework.backend.java.util.Coverage;
import org.kframework.compile.transformers.DataStructure2Cell;
import org.kframework.compile.utils.CompilerStepDone;
import org.kframework.compile.utils.ConfigurationSubstitutionVisitor;
import org.kframework.compile.utils.Substitution;
import org.kframework.kil.Attribute;
import org.kframework.kil.Module;
import org.kframework.backend.java.kil.Term;
import org.kframework.kil.loader.Context;
import org.kframework.krun.KRunExecutionException;
import org.kframework.krun.api.KRunProofResult;
import org.kframework.krun.api.TestgenResults;
import org.kframework.krun.tools.Testgen;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

public class JavaSymbolicTestgen implements Testgen {

    private final JavaSymbolicExecutor executor;
    private final GlobalContext globalContext;
    private final KILtoBackendJavaKILTransformer transformer;
    private final KExceptionManager kem;
    private RuleIndex ruleIndex;

    private final Context context;

    @Inject
    JavaSymbolicTestgen(
            Context context,
            GlobalContext globalContext,
            JavaSymbolicExecutor executor,
            KILtoBackendJavaKILTransformer transformer,
            Definition definition,
            KExceptionManager kem) {
        this.context = context;
        this.executor = executor;
        this.transformer = transformer;
        this.globalContext = globalContext;
        globalContext.setDefinition(definition);
        ruleIndex = definition.getIndex();
        this.kem = kem;
    }

    //Creates the initial term based on the configuration passed in
    //For now, this just replaces the k cell with a symbolic variable of the same sort
    //eventually, we want to be able to read in any configuration the user wants,
    //with symbolic variables as pieces of the k cell
    private ConstrainedTerm createInitialTerm(org.kframework.kil.Term cfg) {
        Term transformedTerm = transformer.transformAndEval(cfg);
        Term symbolicTerm = TestgenKCellEmptier.emptyK(transformedTerm, TermContext.of(globalContext));
        ConstrainedTerm initialTerm = new ConstrainedTerm(symbolicTerm, TermContext.of(globalContext));
        return initialTerm;
    }

    private void setInitialDepthLimits(
            Map<Variable, TestgenDepthLimits> depthLimits,
            ConstrainedTerm initialTerm,
            int depth) {
        for(Variable var : initialTerm.variableSet()) {
            depthLimits.put(var, new TestgenDepthLimits(depth));
        }
    }

    /**
     * Builds the pattern term used in unification by composing the left-hand
     * side of a specified rule and its preconditions.
     * (from SymbolicRewriter)
     */
    private static ConstrainedTerm buildPattern(Rule rule, TermContext context) {
        return new ConstrainedTerm(
                rule.leftHandSide(),
                ConjunctiveFormula.of(context).add(rule.lookups()).addAll(rule.requires()));
    }

    //(from SymbolicRewriter)
    private static ConstrainedTerm buildResult(
            Rule rule,
            ConjunctiveFormula constraint) {
        for (Variable variable : rule.freshConstants()) {
            constraint = constraint.add(
                    variable,
                    FreshOperations.fresh(variable.sort(), constraint.termContext()));
        }
        constraint = constraint.addAll(rule.ensures()).simplify();

        /* get fresh substitutions of rule variables */
        BiMap<Variable, Variable> freshSubstitution = Variable.getFreshSubstitution(rule.variableSet());

        /* rename rule variables in the constraints */
        constraint = ((ConjunctiveFormula) constraint.substituteWithBinders(freshSubstitution, constraint.termContext())).simplify();

        /* rename rule variables in the rule RHS */
        Term term = rule.rightHandSide().substituteWithBinders(freshSubstitution, constraint.termContext());
        /* apply the constraints substitution on the rule RHS */
        constraint = constraint.orientSubstitution(rule.boundVariables().stream()
                .map(freshSubstitution::get)
                .collect(Collectors.toSet()));
        term = term.substituteAndEvaluate(constraint.substitution(), constraint.termContext());
        /* eliminate bindings of rule variables */
        constraint = constraint.removeBindings(freshSubstitution.values());

        ConstrainedTerm result = new ConstrainedTerm(term, constraint);

        return result;
    }

    //Determine the depth limits for the child state,
    //based on the rule that was applies, the unification that occurred, and the resulting term
    private void updateDepthLimits(
            Map<Variable, TestgenDepthLimits> depthLimits,
            TestgenState currentState,
            Rule rule,
            ConjunctiveFormula unificationConstraint,
            ConstrainedTerm term) {

        //new variables will be on the right-hand side of one of the unification substitutions
        for (Variable lhs : unificationConstraint.substitution().keySet()) {

            //we only care about variables that are part of the term,
            //not those used solely in matching the rule
            if (currentState.getTerm().variableSet().contains(lhs)) {

                //check each variable on the right hand side of the substitution
                for (Variable var : term.constraint().substitution().get(lhs).variableSet()) {

                    //do not override depth limits of variables that are already mapped
                    if( !depthLimits.containsKey(var)) {

                        boolean unboundParentDepth = false;
                        boolean ruleDepthExists = false;

                        //get the remaining depth of the parent
                        int parentRemainingDepth = depthLimits.get(lhs).getRemainingDepth();
                        if (depthLimits.get(lhs).getRemainingDepth() < 0) {
                            parentRemainingDepth = Integer.MAX_VALUE;
                            unboundParentDepth = true;
                        }

                        //get the remaining depth limit imposed by the applied rule, if there is one
                        int ruleRemainingDepth = parentRemainingDepth;
                        if (rule.containsAttribute("testgen-depth")) {
                            try {
                                ruleRemainingDepth = Integer.parseInt(rule.getAttribute("testgen-depth"));
                                ruleDepthExists = true;
                            } catch(NumberFormatException e) {
                                //TODO(tom): output this exception properly
                                System.err.println("Rule attribute \"testgen-depth\" with value \"" +
                                        rule.getAttribute("testgen-depth") + "\" could not be parsed as an int.");
                            }
                        }

                        //set the remaining depth
                        int remainingDepth = -1;
                        if(!unboundParentDepth || ruleDepthExists) {
                            remainingDepth = Math.min(parentRemainingDepth - 1, ruleRemainingDepth);
                        }

                        depthLimits.put(var, new TestgenDepthLimits(remainingDepth));

                    }
                }
            }
        }
    }

    //Determine if the any depth limits have been passed by applying this rule and unification
    private boolean depthLimitsReached(
            Map<Variable, TestgenDepthLimits> depthLimits,
            TestgenState currentState,
            Rule rule,
            ConjunctiveFormula unificationConstraint,
            ConstrainedTerm term) {

        //the loops and conditions are the same as in getNextDepthLimits
        for (Variable lhs : unificationConstraint.substitution().keySet()) {
            if (currentState.getTerm().variableSet().contains(lhs)) {
                for (Variable var : term.constraint().substitution().get(lhs).variableSet()) {
                    if(!depthLimits.containsKey(var)) {
                        //if the parent (lhs) has a remaining depth of 0, we cannot deepen from it
                        if(depthLimits.get(lhs).getRemainingDepth() == 0) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public TestgenResults generate(
            Integer depth,
            org.kframework.kil.Rule pattern,
            org.kframework.kil.Term cfg) {

        // The pattern needs to be a rewrite in order for the transformer to be
        // able to handle it, so we need to give it a right-hand-side.
        org.kframework.kil.Cell c = new org.kframework.kil.Cell();
        c.setLabel("generatedTop");
        c.setContents(new org.kframework.kil.Bag());
        pattern.setBody(new org.kframework.kil.Rewrite(pattern.getBody(), c, context));
        Rule goalPattern = transformer.transformAndEval(pattern);

        System.out.println("Goal pattern:\n\t" + goalPattern.leftHandSide() + " => " + goalPattern.rightHandSide());
        if (goalPattern.requires() != null) {
            System.out.println("\trequires: " + goalPattern.requires());
        }
        for (Equality eq : goalPattern.lookups().equalities()) {
            System.out.println("\t" + eq.leftHandSide() + " = " + eq.rightHandSide());
        }

        if (depth == null) {
            depth = -1;
        }


        ConstrainedTerm initialTerm = createInitialTerm(cfg);
        TestgenState initialState = new TestgenState(initialTerm);

        Map<Variable, TestgenDepthLimits> depthLimits = new HashMap<Variable, TestgenDepthLimits>();
        setInitialDepthLimits(depthLimits, initialTerm, depth);

        Queue<TestgenState> queue = new LinkedList<TestgenState>();
        Set<TestgenState> visited = new HashSet<TestgenState>();
        queue.offer(initialState);
        visited.add(initialState);

        int count = 0;

        while(!queue.isEmpty() && count < 10000) {

            TestgenState currentState = queue.poll();
            List<Rule> rules = ruleIndex.getRules(currentState.getTerm());

            //check if this state matches the goal pattern
            assert Sets.intersection(currentState.getTerm().variableSet(),
                    currentState.getConstraint().substitution().keySet()).isEmpty();
            List<org.kframework.backend.java.symbolic.Substitution<Variable, Term>> discoveredSearchResults = PatternMatcher.match(
                    currentState.getTerm(),
                    goalPattern,
                    currentState.getTermContext());
            for (org.kframework.backend.java.symbolic.Substitution<Variable, Term> searchResult : discoveredSearchResults) {
                System.out.println("Result:");
                for (Variable var : searchResult.keySet()) {
                    System.out.println("\t" + var + " -> " + searchResult.get(var));
                }
                //System.out.println(currentState);
                System.out.println("Initial term:\n\t" +
                        initialTerm.substitute(currentState.substitution(), initialTerm.termContext()));
                System.out.println("Constraint:\n\t" +
                        currentState.getConstraint() + "\n");
                //System.out.println("Hash...: " + currentState.hashCode() + "\n");
            }

            count++;
            //System.out.println(count++ + ":");
            //System.out.println("Queue size:" + queue.size());
            //System.out.println("currentState:\n" + currentState + "\n");

            for (Rule rule : rules) {
                ConstrainedTerm rulePattern = buildPattern(rule, currentState.getTermContext());

                for (ConjunctiveFormula unificationConstraint : currentState.getConstrainedTerm().unify(rulePattern)) {
                    //System.out.println("Unification Constraint:\n\t" + unificationConstraint);
                    ConstrainedTerm result = buildResult(rule, unificationConstraint);
                    /*System.out.println("Rule:\n\t" + rule.leftHandSide() + " => " + rule.rightHandSide());
                    if (rule.requires() != null) {
                        System.out.println("\trequires: " + rule.requires());
                    }
                    for (Equality eq : rule.lookups().equalities()) {
                        System.out.println("\t" + eq.leftHandSide() + " = " + eq.rightHandSide());
                    }
                    System.out.println("Result:\n\t" + result);
                    System.out.println("uniconst Substitution:");
                    for (Variable var : unificationConstraint.substitution().keySet()) {
                        System.out.println("\t" + var + " -> " + unificationConstraint.substitution().get(var));
                    }
                    System.out.println("resconst Substitution:");
                    for (Variable var : result.constraint().substitution().keySet()) {
                        System.out.println("\t" + var + " -> " + result.constraint().substitution().get(var));
                    }
                    System.out.println("Unbound Variables:");
                    for (Variable var : result.term().variableSet()) {
                        System.out.println("\t" + var);
                    }
                    System.out.println("Narrowed Variables? " +
                            (result.term().variableSet().equals(currentState.getTerm().variableSet()) ? "No" : "Yes"));
                    System.out.println("Additional Constraint? " +
                            (result.constraint().equals(currentState.getConstraint()) ? "No" : "Yes"));*/


                    if(depthLimitsReached(depthLimits, currentState, rule, unificationConstraint, result)) {
                        //System.out.println("DEPTH LIMIT REACHED!");
                        //System.out.println("\n\n\n\n");
                        continue;
                    }

                    updateDepthLimits(depthLimits, currentState, rule, unificationConstraint, result);
                    //System.out.println("New Depth Limits:");


                    TestgenState nextState = new TestgenState(result);

                    if( !visited.contains(nextState) ) {
                        queue.offer(nextState);
                        visited.add(nextState);
                    } else {
                        //System.out.println("State has already been visited!");
                    }

                    //System.out.println("\n\n\n\n");
                }
            }
        }
        System.out.println("Finished! count: " + count);
        return new TestgenResults("Results not yet properly specified.");
    }

}
