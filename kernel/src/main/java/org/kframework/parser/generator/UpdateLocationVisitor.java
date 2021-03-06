// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.parser.generator;

import org.kframework.attributes.Location;
import org.kframework.kil.ASTNode;
import org.kframework.kil.visitors.BasicVisitor;

public class UpdateLocationVisitor extends BasicVisitor {
    private int currentStartLine;
    private int currentStartColumn;
    private int cachedStartLine;
    private int cachedStartColumn;

    public UpdateLocationVisitor(int currentStartLine, int currentStartColumn,
                                 int  cachedStartLine, int  cachedStartColumn) {
        super(null);
        this.currentStartLine   = currentStartLine;
        this.currentStartColumn = currentStartColumn;
        this.cachedStartColumn  = cachedStartColumn;
        this.cachedStartLine    = cachedStartLine;
    }

    public Void visit(ASTNode node, Void _void) {
        node.setLocation(updateLocation(currentStartLine, currentStartColumn, cachedStartLine, cachedStartColumn, node.getLocation()));
        return null;
    }

    public static Location updateLocation(  int currentStartLine,
                                            int currentStartColumn,
                                            int cachedStartLine,
                                            int cachedStartColumn,
                                            Location loc) {
        if (loc == null) {
            return null;
        }
        int startLine   = loc.startLine();
        int startColumn = loc.startColumn();
        int endLine     = loc.endLine();
        int endColumn   = loc.endColumn();

        int columnOffset = currentStartColumn - cachedStartColumn;
        int lineOffset = currentStartLine - cachedStartLine;
        // offset the column only if on the first line
        if (startLine == cachedStartLine) {
            startColumn += columnOffset;
            if (endLine == cachedStartLine)
                endColumn += columnOffset;
        }

        startLine += lineOffset;
        endLine   += lineOffset;
        return new Location(startLine, startColumn, endLine, endColumn);
    }

    @Override
    public boolean cache() {
        return true;
    }
}
