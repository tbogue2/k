// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.krun.tools;

import com.google.inject.Inject;
import org.kframework.attributes.Source;
import org.kframework.kil.Attributes;
import org.kframework.kil.Definition;
import org.kframework.kil.Module;
import org.kframework.kil.Term;
import org.kframework.kil.loader.Context;
import org.kframework.krun.KRunExecutionException;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.api.KRunResult;
import org.kframework.krun.api.TestgenResults;
import org.kframework.parser.TermLoader;
import org.kframework.transformation.Transformation;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.inject.Main;

import java.util.Set;

public interface Testgen {

    public static class Tool implements Transformation<Void, KRunResult> {

        private final KRunOptions options;
        private final Context context;
        private final Stopwatch sw;
        private final Term initialConfiguration;
        private final Testgen generator;
        private final TermLoader termLoader;

        @Inject
        protected Tool(
                KRunOptions options,
                @Main Context context,
                Stopwatch sw,
                @Main Term initialConfiguration,
                @Main Testgen generator,
                @Main TermLoader termLoader) {
            this.options = options;
            this.context = context;
            this.sw = sw;
            this.initialConfiguration = initialConfiguration;
            this.generator = generator;
            this.termLoader = termLoader;
        }

        @Override
        public KRunResult run(Void v, Attributes a) {
            System.out.println("initialConfiguration:\n" + initialConfiguration.toString());
            System.out.println("Hello, testgen!");
            return new TestgenResults("No interesting results yet.");
        }

        @Override
        public String getName() {
            return "--testgen";
        }
    }
}
