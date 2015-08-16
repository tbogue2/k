// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import com.google.common.collect.BiMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.IntNum;
import com.microsoft.z3.Model;
import org.kframework.backend.java.builtins.BoolToken;
import org.kframework.backend.java.builtins.FreshOperations;
import org.kframework.backend.java.builtins.IntToken;
import org.kframework.backend.java.indexing.RuleIndex;
import org.kframework.backend.java.kil.CellCollection;
import org.kframework.backend.java.kil.CellLabel;
import org.kframework.backend.java.kil.ConstrainedTerm;
import org.kframework.backend.java.kil.Definition;
import org.kframework.backend.java.kil.GlobalContext;
import org.kframework.backend.java.kil.JavaSymbolicObject;
import org.kframework.backend.java.kil.Rule;
import org.kframework.backend.java.kil.Sort;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Variable;
import org.kframework.backend.java.util.Z3Wrapper;
import org.kframework.backend.java.kil.Term;
import org.kframework.kil.loader.Context;
import org.kframework.krun.api.TestgenResults;
import org.kframework.krun.tools.Testgen;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class JavaSymbolicTestgen implements Testgen {

    private final JavaSymbolicExecutor executor;
    private final GlobalContext globalContext;
    private final KILtoBackendJavaKILTransformer transformer;
    private final KExceptionManager kem;
    private final Z3Wrapper z3;
    private final Stopwatch sw;
    private RuleIndex ruleIndex;
    private final Definition definition;

    private boolean depthLimitsReached;
    private boolean addedStructure;

    private final Context context;

    //TODO(tom): make these options
    public static final int RUNTIME_LIMIT = 500;
    public static final int SEARCH_STATE_LIMIT = 5000;
    public static final int Z3_TIMEOUT = 10000;
    public static final boolean DEPTH_FIRST = true;


    @Inject
    JavaSymbolicTestgen(
            Context context,
            GlobalContext globalContext,
            JavaSymbolicExecutor executor,
            KILtoBackendJavaKILTransformer transformer,
            Definition definition,
            KExceptionManager kem,
            Z3Wrapper z3,
            Stopwatch sw) {
        this.context = context;
        this.executor = executor;
        this.transformer = transformer;
        this.globalContext = globalContext;
        globalContext.setDefinition(definition);
        ruleIndex = definition.getIndex();
        this.definition = definition;
        this.kem = kem;
        this.z3 = z3;
        this.sw = sw;

        depthLimitsReached = false;
        addedStructure = false;
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

    private Map<Variable, TestgenDepthLimits> getInitialDepthLimits(
            ConstrainedTerm initialTerm,
            int depth) {
        Map<Variable, TestgenDepthLimits> depthLimits = new HashMap<Variable, TestgenDepthLimits>();
        for(Variable var : initialTerm.variableSet()) {
            depthLimits.put(
                    var,
                    new TestgenDepthLimits(
                            depth,
                            "",
                            "",
                            new HashSet<Variable>(),
                            0,
                            new HashMap<String, Integer>()
                    )
            );
        }
        return depthLimits;
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

    private static ConstrainedTerm buildPatternResult(
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

        /* rename rule variables in the rule LHS (this is the pattern) */
        Term term = rule.leftHandSide().substituteWithBinders(freshSubstitution, constraint.termContext());
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
    //sets depthLimitsReached and addedStructure
    private Map<Variable, TestgenDepthLimits> getNextDepthLimits(
            TestgenState currentState,
            Rule rule,
            ConjunctiveFormula unificationConstraint,
            ConstrainedTerm term) {

        //copy previous depth limits
        Map<Variable, TestgenDepthLimits> nextDepthLimits = new HashMap<Variable, TestgenDepthLimits>();
        nextDepthLimits.putAll(currentState.depthLimits());

        //any new constraints with old variables counts as added structure to that variable,
        //so we must decrement the remaining depth for that variable

        //look through constraints
        for(Equality eq : term.constraint().equalities()) {
            //check the constraint is new
            if(!currentState.getConstraint().equalities().contains(eq)) {
                //go through variables in the constraint's lhs
                for(Variable var : eq.leftHandSide().variableSet()) {
                    //check the variable is old
                    if(currentState.depthLimits().containsKey(var)) {
                        //if the remaining depth is already 0, we cannot add structure to the variable
                        if(currentState.depthLimits().get(var).getRemainingDepth() == 0) {
                            depthLimitsReached = true;
                            return nextDepthLimits;
                        }

                        //update remaining depth
                        nextDepthLimits.put(
                                var,
                                new TestgenDepthLimits(
                                        currentState.depthLimits().get(var).getRemainingDepth() - 1,
                                        currentState.depthLimits().get(var).getRejectName(),
                                        currentState.depthLimits().get(var).getSequenceName(),
                                        currentState.depthLimits().get(var).getSequenceVariables(),
                                        currentState.depthLimits().get(var).getRemainingSequencing(),
                                        currentState.depthLimits().get(var).getNestingLimits()
                                )
                        );
                        addedStructure = true;
                    }
                }
                //same for rhs of constraint
                for(Variable var : eq.rightHandSide().variableSet()) {
                    if(currentState.depthLimits().containsKey(var)) {
                        if(currentState.depthLimits().get(var).getRemainingDepth() == 0) {
                            depthLimitsReached = true;
                            return nextDepthLimits;
                        }
                        nextDepthLimits.put(
                                var,
                                new TestgenDepthLimits(
                                        currentState.depthLimits().get(var).getRemainingDepth() - 1,
                                        currentState.depthLimits().get(var).getRejectName(),
                                        currentState.depthLimits().get(var).getSequenceName(),
                                        currentState.depthLimits().get(var).getSequenceVariables(),
                                        currentState.depthLimits().get(var).getRemainingSequencing(),
                                        currentState.depthLimits().get(var).getNestingLimits()
                                )
                        );
                        addedStructure = true;
                    }
                }
            }
        }

        //new variables will be on the right-hand side of one of the unification substitutions
        for (Variable lhs : unificationConstraint.substitution().keySet()) {

            //we only care about variables that are part of the term,
            //not those used solely in matching the rule
            if (currentState.getTerm().variableSet().contains(lhs)) {

                //if the rule has a name matching this var's reject, the limits have been reached
                if(rule.containsAttribute(currentState.getDepthLimit(lhs).getRejectName())) {
                    depthLimitsReached = true;
                    return nextDepthLimits;
                }

                //gather the new variables created by unification
                Set<Variable> newVariables = new HashSet<Variable>();
                for (Variable var : term.constraint().substitution().get(lhs).variableSet()) {
                    newVariables.add(var);
                }

                //decide if the new variables are part of a sequence
                int remainingSequencing = 0;
                String sequenceName = "";
                Set<Variable> sequenceVariables = new HashSet<Variable>();

                if(rule.containsAttribute(currentState.getDepthLimit(lhs).getSequenceName())) {
                    //check that further sequencing is allowed on this variable
                    if(currentState.getDepthLimit(lhs).getRemainingSequencing() == 0) {
                        depthLimitsReached = true;
                        return nextDepthLimits;
                    }

                    remainingSequencing = currentState.getDepthLimit(lhs).getRemainingSequencing() - 1;
                    sequenceName = currentState.getDepthLimit(lhs).getSequenceName();
                    sequenceVariables.addAll(currentState.getDepthLimit(lhs).getSequenceVariables());
                    sequenceVariables.addAll(newVariables);

                    //change the old depth limits for the other sequence variables
                    for(Variable var : currentState.getDepthLimit(lhs).getSequenceVariables()) {
                        nextDepthLimits.put(
                                var,
                                new TestgenDepthLimits(
                                        currentState.depthLimits().get(var).getRemainingDepth(),
                                        currentState.depthLimits().get(var).getRejectName(),
                                        sequenceName,
                                        sequenceVariables,
                                        remainingSequencing,
                                        currentState.depthLimits().get(var).getNestingLimits()
                                )
                        );
                    }
                } else if (rule.containsAttribute("testgen-seqlimit")) {
                    try {
                        //parse the attribute value
                        String seqlimit = rule.getAttribute("testgen-seqlimit");
                        String[] seqlimitsplit = seqlimit.split(",");
                        sequenceName = seqlimitsplit[0].trim();
                        remainingSequencing = Integer.parseInt(seqlimitsplit[1].trim());
                        sequenceVariables.addAll(newVariables);
                    } catch(Exception e) {
                        throw KEMException.criticalError(e.getMessage(), e);
                    }
                }

                //check each variable on the right hand side of the substitution
                for (Variable var : term.constraint().substitution().get(lhs).variableSet()) {

                    //do not override depth limits of variables that are already mapped
                    if( !currentState.depthLimits().containsKey(var)) {

                        //if the variable has a remaining depth of 0, the depth limits have been reached
                        if(currentState.getDepthLimit(lhs).getRemainingDepth() == 0) {
                            depthLimitsReached = true;
                            return nextDepthLimits;
                        }

                        boolean unboundParentDepth = false;
                        boolean ruleDepthExists = false;

                        //get the remaining depth of the parent
                        int parentRemainingDepth = currentState.getDepthLimit(lhs).getRemainingDepth();
                        if (currentState.getDepthLimit(lhs).getRemainingDepth() < 0) {
                            parentRemainingDepth = Integer.MAX_VALUE;
                            unboundParentDepth = true;
                        }

                        //get the remaining depth limit imposed by the applied rule, if there is one
                        int ruleRemainingDepth = parentRemainingDepth;
                        if (rule.containsAttribute("testgen-depthlimit")) {
                            try {
                                ruleRemainingDepth = Integer.parseInt(rule.getAttribute("testgen-depthlimit"));
                                ruleDepthExists = true;
                            } catch(NumberFormatException e) {
                                throw KEMException.criticalError("Rule attribute \"testgen-depthlimit\" with value \"" +
                                        rule.getAttribute("testgen-depthlimit") + "\" could not be parsed as an int.", e);
                            }
                        }

                        //set the remaining depth
                        int remainingDepth = -1;
                        if(!unboundParentDepth || ruleDepthExists) {
                            remainingDepth = Math.min(parentRemainingDepth - 1, ruleRemainingDepth);
                        }

                        //get reject next name
                        String rejectName = "";
                        if(rule.containsAttribute("testgen-rejectnext")) {
                            rejectName = rule.getAttribute("testgen-rejectnext");
                        }

                        //determine nesting limits
                        Map<String, Integer> parentNestingLimits = currentState.depthLimits().get(lhs).getNestingLimits();
                        Map<String, Integer> childNestingLimits = new HashMap<String, Integer>();

                        for(String name : parentNestingLimits.keySet()) {
                            int remainingNesting = parentNestingLimits.get(name);
                            if(rule.containsAttribute(name)) {
                                //if the variable has a remaining nesting depth of 0 for the applied rule,
                                //the depth limits have been reached
                                if(parentNestingLimits.get(name) == 0) {
                                    depthLimitsReached = true;
                                    return nextDepthLimits;
                                }
                                remainingNesting -= 1;
                            }
                            childNestingLimits.put(name, remainingNesting);
                        }
                        //add new nesting limits if there are any in the rule
                        if(rule.containsAttribute("testgen-nestlimit")) {
                            try {
                                //parse the attribute value
                                String nestlimit = rule.getAttribute("testgen-nestlimit");
                                String[] nestlimits = nestlimit.split(";");
                                for(String n : nestlimits) {
                                    String[] nestlimitsplit = n.trim().split(",");
                                    String nestlimitName = nestlimitsplit[0].trim();
                                    Integer remainingNesting = Integer.parseInt(nestlimitsplit[1].trim());
                                    if(childNestingLimits.containsKey(nestlimitName)) {
                                        remainingNesting = Math.min(remainingNesting, childNestingLimits.get(nestlimitName));
                                    }
                                    childNestingLimits.put(nestlimitName, remainingNesting);
                                }
                            } catch(Exception e) {
                                throw KEMException.criticalError(e.getMessage(), e);
                            }
                        }

                        nextDepthLimits.put(
                                var,
                                new TestgenDepthLimits(
                                        remainingDepth,
                                        rejectName,
                                        sequenceName,
                                        sequenceVariables,
                                        remainingSequencing,
                                        childNestingLimits
                                )
                        );
                        addedStructure = true;
                    }
                }
            }
        }

        return nextDepthLimits;
    }

    //randomly permutes the indices of the weights list, taking the weights into consideration
    public List<Integer> permute(Random random, List<Integer> weights) {
        List<Integer> permutation = new ArrayList<Integer>();
        List<Double> sampleValues = new ArrayList<Double>();
        for(int i = 0; i < weights.size(); ++i) {
            //this is the quantile function for the exponential distribution
            double curValue = -Math.log(random.nextDouble()) / weights.get(i);
            //insert the value and index into their Lists, keeping them together,
            //and keeping the sampleValues sorted
            permutation.add(i);
            sampleValues.add(curValue);
            int insertIdx = i;
            while(insertIdx > 0 && sampleValues.get(insertIdx) < sampleValues.get(insertIdx - 1)) {
                Collections.swap(permutation, insertIdx, insertIdx - 1);
                Collections.swap(sampleValues, insertIdx, insertIdx - 1);
                --insertIdx;
            }
        }
        return permutation;
    }

    @Override
    public TestgenResults generate(
            Integer depth,
            Integer bound,
            org.kframework.kil.Rule pattern,
            org.kframework.kil.Term cfg) {

        if (depth == null) {
            depth = -1;
        }
        if (bound == null) {
            bound = Integer.MAX_VALUE;
        }

        // The pattern needs to be a rewriten in order for the transformer to be
        // able to handle it, so we need to give it a right-hand-side.
        org.kframework.kil.Cell c = new org.kframework.kil.Cell();
        c.setLabel("generatedTop");
        c.setContents(new org.kframework.kil.Bag());
        pattern.setBody(new org.kframework.kil.Rewrite(pattern.getBody(), c, context));
        Rule goalPatternRule = transformer.transformAndEval(pattern);
        ConstrainedTerm goalPattern = buildPattern(goalPatternRule, TermContext.of(globalContext));

        ConstrainedTerm initialTerm = createInitialTerm(cfg);
        Map<Variable, TestgenDepthLimits> initialDepthLimits = getInitialDepthLimits(initialTerm, depth);
        TestgenState initialState = new TestgenState(initialTerm, initialDepthLimits, RUNTIME_LIMIT, 0);

        Random random = new Random(System.currentTimeMillis());

        int results = 0;
        List<String> testgenResults = new LinkedList<String>();

        fullSearch:
        do {

            Deque<TestgenState> deque = new LinkedList<TestgenState>();
            Set<TestgenState> visited = new HashSet<TestgenState>();
            deque.push(initialState);
            visited.add(initialState);

            int states = 0;
            int prev_results = results;

            newSearch:
            while (!deque.isEmpty() && states < SEARCH_STATE_LIMIT) {

                states++;
                boolean foundPriority = false;

                TestgenState currentState;
                if(DEPTH_FIRST) {
                    currentState = deque.pop();
                } else {
                    currentState = deque.poll();
                }
                List<Rule> rules = definition.rules();

                List<TestgenState> nextStates = new ArrayList<TestgenState>();
                List<Integer> nextStatesWeights = new ArrayList<Integer>();

                //check if this state matches the goal pattern
                assert Sets.intersection(currentState.getTerm().variableSet(),
                        currentState.getConstraint().substitution().keySet()).isEmpty();

                for (ConjunctiveFormula unificationConstraint : currentState.getConstrainedTerm().unify(goalPattern)) {

                    //collect the unnarrowed variables
                    Set<Variable> unnarrowedVars = new HashSet<Variable>();
                    Map<Variable, Term> z3sub = new HashMap<Variable, Term>();

                    for (Variable var : currentState.getConstrainedTerm().variableSet()) {

                        //get the default value for the variable
                        ConstrainedTerm lhs = null;
                        ConstrainedTerm rhs = null;
                        boolean foundDefault = false;
                        for (Rule rule : rules) {

                            //check only rules with the proper tags
                            if (!rule.containsAttribute("testgen-unreachable")) {
                                continue;
                            }
                            lhs = new ConstrainedTerm(
                                    CellCollection.singleton(CellLabel.K, var, definition),
                                    TermContext.of(globalContext));

                            List<Term> kCellContents = rule.leftHandSide().getCellContentsByName(CellLabel.K);

                            ConstrainedTerm rulePattern = new ConstrainedTerm(
                                    CellCollection.singleton(CellLabel.K, kCellContents.get(0), definition),
                                    ConjunctiveFormula.of(lhs.termContext()).add(rule.lookups()).addAll(rule.requires()));


                            for (ConjunctiveFormula unreachableSub : lhs.unify(rulePattern)) {

                                rhs = buildResult(rule, unreachableSub);

                                //we just take the first
                                foundDefault = true;
                                break;
                            }
                            if (foundDefault) {
                                break;
                            }
                        }
                        unnarrowedVars.add(var);
                        if (foundDefault) {
                            z3sub.put(new Variable(var.name(), var.sort()), rhs.term().getCellContentsByName(CellLabel.K).get(0));
                        }
                    }

                    //add any substitutions directly required by the constraints
                    for (Variable var : currentState.substitution().keySet()) {
                        z3sub.put(var, currentState.substitution().get(var));
                    }
                    for (Variable var : unificationConstraint.substitution().keySet()) {
                        z3sub.put(var, unificationConstraint.substitution().get(var));
                    }

                    String query = KILtoSMTLib.translateConstraint(currentState.getConstraint().addAndSimplify(unificationConstraint));

                    //get a model for the Ints using z3
                    Model model = z3.getModelForQueury(query, Z3_TIMEOUT);

                    if (model != null) {
                        try {
                            for (FuncDecl decl : model.getConstDecls()) {
                                Expr ex = model.getConstInterp(decl);
                                if(decl.getRange().toString().equals("Int")) {
                                    z3sub.put(new Variable(decl.getName().toString(), Sort.INT), IntToken.of(((IntNum) ex).getInt()));
                                }
                                else if(decl.getRange().toString().equals("Bool")) {
                                    z3sub.put(new Variable(decl.getName().toString(), Sort.BOOL), BoolToken.of(ex.isTrue()));
                                }
                                else {
                                    //TODO(tom):generalize to other sorts
                                    throw new Exception("z3 returned an unsupported sort: " + decl.getRange().toString());
                                }
                            }
                            model.dispose();
                        } catch (Exception e) {
                            throw KEMException.criticalError(e.getMessage(), e);
                        }
                    }

                    JavaSymbolicObject nextResult = initialTerm.substitute(currentState.substitution(), initialTerm.termContext())
                            .substitute(z3sub, initialTerm.termContext());
                    results++;
                    testgenResults.add(nextResult.toString());
                    sw.printIntermediate("Result " + results);

                    System.out.println("Initial term:\n\t" + nextResult);

                    if(results >= bound) {
                        break fullSearch;
                    }
                    if(DEPTH_FIRST) {
                        break newSearch;
                    }
                }

                //find which rules apply
                //note: this should use the ruleIndex like in the krun search, but there where issues with it
                //not providing rules that should match which I could not solve - Tom
                for (Rule rule : rules) {

                    if (rule.containsAttribute("testgen-ignore") ||
                            rule.containsAttribute("testgen-unreachable")) {
                        continue;
                    }

                    ConstrainedTerm rulePattern = buildPattern(rule, currentState.getTermContext());

                    for (ConjunctiveFormula unificationConstraint : currentState.getConstrainedTerm().unify(rulePattern)) {

                        ConstrainedTerm result;
                        if(rule.containsAttribute("testgen-pattern")) {
                            result = buildPatternResult(rule, unificationConstraint);
                        }
                        else {
                            result = buildResult(rule, unificationConstraint);
                        }

                        //check depth limits and create them for the new term
                        depthLimitsReached = false;
                        addedStructure = false;

                        Map<Variable, TestgenDepthLimits> nextDepthLimits =
                                getNextDepthLimits(currentState, rule, unificationConstraint, result);

                        if (depthLimitsReached) {
                            continue;
                        }

                        //check runtime limits
                        int nextRuntimeLimit = RUNTIME_LIMIT;

                        if (!addedStructure) {
                            if (currentState.getRemainingRuntime() == 0) {
                                break newSearch;
                            }
                            nextRuntimeLimit = currentState.getRemainingRuntime() - 1;
                        }


                        if(!rule.containsAttribute("testgen-componly") || !addedStructure) {
                            TestgenState nextState = new TestgenState(result, nextDepthLimits, nextRuntimeLimit, currentState.getDepth() + 1);

                            if (!visited.contains(nextState)) {
                                if (rule.containsAttribute("testgen-priority")) {
                                    nextStates.add(0, nextState);
                                    foundPriority = true;
                                }
                                else {
                                    nextStates.add(nextState);
                                }
                                visited.add(nextState);
                                if (rule.containsAttribute("testgen-weight")) {
                                    try {
                                        nextStatesWeights.add(Integer.parseInt(rule.getAttribute("testgen-weight")));
                                    } catch (NumberFormatException e) {
                                        throw KEMException.criticalError("Rule attribute \"testgen-weight\" with value \"" +
                                                rule.getAttribute("testgen-weight") + "\" could not be parsed as an int.", e);
                                    }
                                }
                                else {
                                    nextStatesWeights.add(1);
                                }
                            }
                        }
                    }
                }
                if (!nextStates.isEmpty()) {
                    if(!foundPriority) {
                        if (DEPTH_FIRST) {
                            List<Integer> nextStateOrder = permute(random, nextStatesWeights);
                            for (int i = nextStateOrder.size() - 1; i >= 0; --i) {
                                deque.push(nextStates.get(nextStateOrder.get(i)));
                            }
                        }
                        else {
                            for (TestgenState next : nextStates) {
                                deque.offer(next);
                            }
                        }
                    }
                    else {
                        deque.offer(nextStates.get(0));
                    }
                }
            }
        } while (DEPTH_FIRST && results < bound);

        return new TestgenResults(testgenResults);
    }

}
