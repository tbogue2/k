// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.krun.tools;

import com.google.inject.Inject;
import org.kframework.compile.utils.CompilerStepDone;
import org.kframework.compile.utils.RuleCompilerSteps;
import org.kframework.kil.ASTNode;
import org.kframework.kil.Attribute;
import org.kframework.kil.Attributes;
import org.kframework.kil.Cell;
import org.kframework.kil.Rule;
import org.kframework.kil.Sentence;
import org.kframework.kil.Sort;
import org.kframework.kil.Term;
import org.kframework.kil.Variable;
import org.kframework.kil.loader.Context;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.api.KRunResult;
import org.kframework.krun.api.TestgenResults;
import org.kframework.parser.TermLoader;
import org.kframework.transformation.Transformation;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.errorsystem.ParseFailedException;
import org.kframework.utils.inject.Main;

public interface Testgen {
    /**
     * @return the generated results
     */
    public abstract TestgenResults generate(Integer depth, Integer bound, Rule pattern, Term cfg);

    public static class Tool implements Transformation<Void, KRunResult> {

        private final KRunOptions options;
        private final Context context;
        private final Stopwatch sw;
        private final KExceptionManager kem;
        private final Term initialConfiguration;
        private final Testgen generator;
        private final TermLoader termLoader;

        @Inject
        protected Tool(
                KRunOptions options,
                @Main Context context,
                KExceptionManager kem,
                Stopwatch sw,
                @Main Term initialConfiguration,
                @Main Testgen generator,
                @Main TermLoader termLoader) {
            this.options = options;
            this.context = context;
            this.sw = sw;
            this.kem = kem;
            this.initialConfiguration = initialConfiguration;
            this.generator = generator;
            this.termLoader = termLoader;
        }

        //From Executor.Tool
        public class SearchPattern {
            public final RuleCompilerSteps steps;
            public final Rule patternRule;

            public SearchPattern(ASTNode pattern) {
                steps = new RuleCompilerSteps(context, kem);
                try {
                    pattern = steps.compile(new Rule((Sentence) pattern), null);
                } catch (CompilerStepDone e) {
                    pattern = (ASTNode) e.getResult();
                }
                patternRule = new Rule((Sentence) pattern);
                sw.printIntermediate("Parsing search pattern");
            }
        }

        @Override
        public KRunResult run(Void v, Attributes a) {
            ASTNode pattern = pattern(options.pattern);
            SearchPattern searchPattern = new SearchPattern(pattern);
            sw.printIntermediate("Testgen start");
            TestgenResults reults = generator.generate(options.depth, options.bound, searchPattern.patternRule, initialConfiguration);
            sw.printIntermediate("Testgen end");
            return reults;
        }

        //From Executor.Tool
        public ASTNode pattern(String pattern) throws ParseFailedException {
            String patternToParse = pattern;
            if (pattern == null) {
                patternToParse = KRunOptions.DEFAULT_PATTERN;
            }
            if (patternToParse.equals(KRunOptions.DEFAULT_PATTERN)) {
                Sentence s = new Sentence();
                s.setBody(new Cell("generatedTop", new Variable("B", Sort.BAG)));
                s.addAttribute(Attribute.ANYWHERE);
                return s;
            }
            return termLoader.parsePattern(
                    patternToParse,
                    null,
                    Sort.BAG);
        }

        @Override
        public String getName() {
            return "--testgen";
        }
    }
}
