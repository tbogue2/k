// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import com.google.inject.Inject;
import org.kframework.backend.java.kil.CellLabel;
import org.kframework.backend.java.kil.ConstrainedTerm;
import org.kframework.backend.java.kil.Definition;
import org.kframework.backend.java.kil.GlobalContext;
import org.kframework.backend.java.kil.Rule;
import org.kframework.backend.java.kil.Sort;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Variable;
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
import org.kframework.utils.errorsystem.KExceptionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JavaSymbolicTestgen implements Testgen {

    private final JavaSymbolicExecutor executor;
    private final GlobalContext globalContext;
    private final KILtoBackendJavaKILTransformer transformer;
    private final KExceptionManager kem;

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
        this.globalContext = globalContext;
        this.transformer = transformer;
        globalContext.setDefinition(definition);
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

    private TestgenState createInitialState(ConstrainedTerm initialTerm, int depth) {
        Map<String, org.kframework.kil.Term> substitution = new HashMap<String, org.kframework.kil.Term>();
        TestgenDepthLimits depthLimits = new TestgenDepthLimits(depth);
        return new TestgenState(initialTerm, substitution, depthLimits);
    }

    @Override
    public TestgenResults generate(Integer depth, org.kframework.kil.Term cfg) {

        if (depth == null) {
            depth = -1;
        }

        ConstrainedTerm initialTerm = createInitialTerm(cfg);
        TestgenState initialState = createInitialState(initialTerm, depth);
        System.out.println("initialState:\n" + initialState);

        return new TestgenResults("Results not yet properly specified.");
        
    }
}
