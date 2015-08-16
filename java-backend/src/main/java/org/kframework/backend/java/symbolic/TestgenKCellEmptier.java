// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import org.kframework.backend.java.kil.CellCollection;
import org.kframework.backend.java.kil.CellLabel;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Variable;
import org.kframework.kil.ASTNode;


/**
 * Replaces the contents of every k cell in the initial configuration with a symbolic
 * variable of the appropriate Sort.
 *
 * This is not the eventual way to create the testgen's initial configuration.
 * Instead we want to accept a full configuration with symbolic variables anywhere.
 */
public class TestgenKCellEmptier extends CopyOnWriteTransformer {

    public TestgenKCellEmptier(TermContext context) {
        super(context);
    }

    public static Term emptyK(Term term, TermContext context) {
        TestgenKCellEmptier evaluator = new TestgenKCellEmptier(context);
        return (Term) term.accept(evaluator);
    }

    @Override
    public ASTNode transform(CellCollection cellCollection) {
        boolean changed = false;
        CellCollection.Builder builder = CellCollection.builder(context.definition());
        for (CellCollection.Cell cell : cellCollection.cells().values()) {
            Term transformedContent;
            if(cell.cellLabel().equals(CellLabel.K)) {
                transformedContent = Variable.getAnonVariable(cell.content().sort());
            }
            else {
                transformedContent = (Term) cell.content().accept(this);
            }
            builder.put(cell.cellLabel(), transformedContent);
            changed = changed || cell.content() != transformedContent;
        }
        for (Term term : cellCollection.baseTerms()) {
            Term transformedTerm = (Term) term.accept(this);
            builder.concatenate(transformedTerm);
            changed = changed || term != transformedTerm;
        }
        return changed ? builder.build() : cellCollection;
    }

}
