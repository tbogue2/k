// Copyright (c) 2015 K Team. All Rights Reserved.

package org.kframework.kore.compile;


import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;
import org.kframework.kore.*;

import static org.kframework.kore.KORE.*;
import static org.kframework.kore.compile.ConfigurationInfo.Multiplicity.*;

public class CompilerTest {
    final ConcretizeConfiguration pass = new ConcretizeConfiguration(new TestConfiguration() {{
        addCell(null, "<T>");
        addCell("<T>", "<ts>");
        addCell("<T>", "<state>");
        addCell("<ts>", "<t>", STAR);
        addCell("<ts>", "<scheduler>");
        addCell("<t>", "<k>");
        addCell("<t>", "<env>");
        addCell("<t>", "<msg>", STAR);
        addCell("<msg>", "<msgId>");
    }});

    @Test
    public void testOneLeafCellNoCompletion() {
        K term = cell("<k>", intToToken(2));
        K expected = cell("<k>", intToToken(2));
        Assert.assertEquals(expected, pass.concretizeCell(term));
    }

    @Test
    public void testTwoCellsNoCompletion() {
        K term = cell("<t>", cell("<k>", intToToken(2)));
        K expected = cell("<t>", cell("<k>", intToToken(2)));
        Assert.assertEquals(expected, pass.concretizeCell(term));
    }

    @Test
    public void testTwoCellsCompletion() {
        K term = cell("<ts>", cell("<k>", intToToken(2)));
        K expected = cell("<ts>", cell("<t>", cell("<k>", intToToken(2))));
        Assert.assertEquals(expected, pass.concretizeCell(term));
    }

    @Test
    public void testMultiplicitySeparate() {
        K term = cell("<ts>", cell("<k>", intToToken(1)), cell("<k>", intToToken(2)));
        K expected = cell("<ts>", cell("<t>", cell("<k>", intToToken(1))),
                cell("<t>", cell("<k>", intToToken(2))));
        Assert.assertEquals(expected, pass.concretizeCell(term));
    }

    @Test
    public void testMultiplicityShared() {
        K term = cell("<ts>", cell("<k>", intToToken(1)), cell("<env>", intToToken(2)));
        K expected = cell("<ts>", cell("<t>", cell("<k>", intToToken(1)), cell("<env>", intToToken(2))));
        Assert.assertEquals(expected, pass.concretizeCell(term));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAmbiguityError() {
        K term = cell("<ts>", cell("<k>", intToToken(1)), cell("<k>", intToToken(2)), cell("<env>", intToToken(2)));
        pass.concretizeCell(term);
    }

    @Test
    public void testDeep2() {
        Assert.assertEquals(Lists.newArrayList(cell("<ts>", cell("<t>", intToToken(1)), cell("<t>", intToToken(2)))),
                pass.makeParents(KLabel("<ts>"), false, Lists.newArrayList(cell("<t>", intToToken(1)), cell("<t>", intToToken(2)))));
    }

    @Test
    public void testDeep() {
        K term = cell("<T>", cell("<k>", intToToken(1)), cell("<k>", intToToken(2)));
        K expected = cell("<T>", cell("<ts>", cell("<t>", cell("<k>", intToToken(1))),
                cell("<t>", cell("<k>", intToToken(2)))));
        Assert.assertEquals(expected, pass.concretizeCell(term));
    }

    @Test
    public void testRewrites() {
        K term = cell("<T>", cell("<k>", intToToken(1)), KRewrite(cell("<k>", intToToken(2)), cell("<k>")));
        K expected = cell("<T>", cell("<ts>",
                cell("<t>", cell("<k>", intToToken(1))),
                cell("<t>", KRewrite(cell("<k>", intToToken(2)), cell("<k>")))));
        Assert.assertEquals(expected, pass.concretizeCell(term));
    }

    @Test
    public void testRewriteWithCells() {
        K term = cell("<T>", cell("<k>", intToToken(1)), KRewrite(cells(cell("<k>", intToToken(2)), cell("<msg>")), cell("<k>")));
        K expected = cell("<T>", cell("<ts>",
                cell("<t>", cell("<k>", intToToken(1))),
                cell("<t>", KRewrite(cells(cell("<k>", intToToken(2)), cell("<msg>")), cell("<k>")))));
        Assert.assertEquals(expected, pass.concretizeCell(term));
    }

    @Test
    public void testEmptySide() {
        K term = cell("<T>", cell("<k>"), KRewrite(cell("<msg>"), cells()));
        K expected = cell("<T>", cell("<ts>", cell("<t>", cell("<k>"), KRewrite(cell("<msg>"), cells()))));
        Assert.assertEquals(expected, pass.concretizeCell(term));
    }

    @Test
    public void testTwoRewritesFit() {
        K term = cell("<T>", KRewrite(cells(), cell("<k>", intToToken(1))),
                KRewrite(cell("<k>", intToToken(2)), cells()));
        K expected = cell("<T>", cell("<ts>", cell("<t>",
                KRewrite(cells(), cell("<k>", intToToken(1))),
                KRewrite(cell("<k>", intToToken(2)), cells()))));
        Assert.assertEquals(expected, pass.concretizeCell(term));
    }

    @Test
    public void testThreeRewritesSplit() {
        K term = cell("<T>",
                KRewrite(cells(cell("<k>"),cell("<env>")), cells()),
                KRewrite(cell("<env>"), cell("<k>")),
                KRewrite(cell("<k>"), cell("<k>")));
        K expected = cell("<T>", cell("<ts>",
                cell("<t>", KRewrite(cells(cell("<k>"),cell("<env>")), cells())),
                cell("<t>", KRewrite(cell("<env>"), cell("<k>"))),
                cell("<t>", KRewrite(cell("<k>"), cell("<k>")))));
        Assert.assertEquals(expected, pass.concretizeCell(term));
    }

    final KApply dots = KApply(KLabel("#dots"));

    @Test
    public void testDotsApart() {
        K term = cell("<T>", dots, cell("<k>", intToToken(1)), cell("<k>", intToToken(2)));
        K expected = cell("<T>", dots, cell("<ts>", dots,
                cell("<t>", dots, cell("<k>", intToToken(1)), dots),
                cell("<t>", dots, cell("<k>", intToToken(2)), dots)
                , dots), dots);
        Assert.assertEquals(expected, pass.concretizeCell(term));
    }

    @Test
    public void testDotsTogether() {
        K term = cell("<ts>", dots, cell("<k>", intToToken(0)), cell("<env>",intToToken(2)));
        K expected = cell("<ts>", dots, cell("<t>", dots,
                cell("<k>", intToToken(0)), cell("<env>",intToToken(2)),
                dots), dots);
        Assert.assertEquals(expected, pass.concretizeCell(term));
    }

    @Test
    public void testNestedCompletion() {
        K term = cell("<T>",
                cell("<t>", cell("<msg>", intToToken(0)), cell("<msgId>", intToToken(1))),
                cell("<k>", intToToken(2)),
                cell("<env>", intToToken(3)),
                cell("<msgId>", intToToken(4)),
                cell("<msgId>", intToToken(5)),
                cell("<t>", cell("<k>", intToToken(6))));
        K expected = cell("<T>",cell("<ts>",
                cell("<t>", cell("<msg>", intToToken(0)), cell("<msg>", cell("<msgId>", intToToken(1)))),
                cell("<t>", cell("<k>", intToToken(6))),
                cell("<t>", cell("<k>", intToToken(2)), cell("<env>", intToToken(3)),
                    cell("<msg>", cell("<msgId>", intToToken(4))),
                    cell("<msg>", cell("<msgId>", intToToken(5))))
                ));
        Assert.assertEquals(expected, pass.concretize(term));

    }

    @Test
    public void testLeafContent() {
        K term = cell("<T>", cell("<k>",
                KSequence(KApply(KLabel("_+_"), KVariable("I"), KVariable("J")),
                        KVariable("Rest"))));
        K expected = cell("<T>", cell("<ts>", cell("<t>", cell("<k>",
                KSequence(KApply(KLabel("_+_"), KVariable("I"), KVariable("J")),
                                KVariable("Rest"))))));
        Assert.assertEquals(expected, pass.concretize(term));
    }

    KApply cell(String name, K... ks) {
        return KApply(KLabel(name), ks);
    }

    KApply cells(K... ks) {
        return KApply(KLabel("#cells"), ks);
    }
}
