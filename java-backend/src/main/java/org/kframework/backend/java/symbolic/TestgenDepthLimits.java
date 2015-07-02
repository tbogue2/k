package org.kframework.backend.java.symbolic;

import org.kframework.backend.java.util.Utils;

/**
 * Created by tom on 6/19/15.
 */
public class TestgenDepthLimits {

    /**
     * Field used for caching the hash code
     */
    private transient int hashCode = Utils.NO_HASHCODE;

    private final int remainingDepth;

    public TestgenDepthLimits(int remainingDepth) {
        this.remainingDepth = remainingDepth;
    }

    public int getRemainingDepth() {
        return remainingDepth;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof TestgenDepthLimits)) {
            return false;
        }

        TestgenDepthLimits testgenDepthLimits = (TestgenDepthLimits) object;
        return remainingDepth == testgenDepthLimits.remainingDepth;
    }

    @Override
    public int hashCode() {
        int h = hashCode;
        if (h == Utils.NO_HASHCODE) {
            h = 1;
            h = h * Utils.HASH_PRIME + remainingDepth;
            h = h == 0 ? 1 : h;
            hashCode = h;
        }
        return h;
    }

    @Override
    public String toString() {
        return "Remaining depth: " + remainingDepth;
    }
}
