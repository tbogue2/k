package org.kframework.backend.java.symbolic;

/**
 * Created by tom on 6/19/15.
 */
public class TestgenDepthLimits {

    private int remainingDepth;

    public TestgenDepthLimits(int remainingDepth) {
        this.remainingDepth = remainingDepth;
    }

    public int getRemainingDepth() {
        return remainingDepth;
    }

    @Override
    public String toString() {
        return "Remaining depth: " + remainingDepth;
    }
}
