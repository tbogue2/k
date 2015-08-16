package org.kframework.backend.java.symbolic;

import org.kframework.backend.java.kil.Variable;
import org.kframework.backend.java.util.Utils;

import java.util.Map;
import java.util.Set;

/**
 * Created by tom on 6/19/15.
 */
public class TestgenDepthLimits {

    /**
     * Field used for caching the hash code
     */
    private transient int hashCode = Utils.NO_HASHCODE;

    private final int remainingDepth;

    private final String rejectName;

    private final String sequenceName;

    private final Set<Variable> sequenceVariables;

    private final int remainingSequencing;

    private final Map<String, Integer> nestingLimits;

    public TestgenDepthLimits(
            int remainingDepth,
            String rejectName,
            String sequenceName,
            Set<Variable> sequenceVariables,
            int remainingSequencing,
            Map<String, Integer> nestingLimits) {
        this.remainingDepth = remainingDepth;
        this.rejectName = rejectName;
        this.sequenceName = sequenceName;
        this.sequenceVariables = sequenceVariables;
        this.remainingSequencing = remainingSequencing;
        this.nestingLimits = nestingLimits;
    }

    public int getRemainingDepth() {
        return remainingDepth;
    }

    public String getRejectName() {
        return rejectName;
    }

    public String getSequenceName() {
        return sequenceName;
    }

    public Set<Variable> getSequenceVariables() {
        return sequenceVariables;
    }

    public int getRemainingSequencing() {
        return remainingSequencing;
    }

    public Map<String, Integer> getNestingLimits() {
        return nestingLimits;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof TestgenDepthLimits)) {
            return false;
        }

        TestgenDepthLimits depthLimits = (TestgenDepthLimits) object;
        return remainingDepth == depthLimits.remainingDepth &&
               rejectName.equals(depthLimits.rejectName) &&
               sequenceName.equals(depthLimits.sequenceName) &&
               sequenceVariables.equals(depthLimits.sequenceVariables) &&
               remainingSequencing == depthLimits.remainingSequencing &&
               nestingLimits.equals(depthLimits.nestingLimits);
    }

    @Override
    public int hashCode() {
        int h = hashCode;
        if (h == Utils.NO_HASHCODE) {
            h = 1;
            h = h * Utils.HASH_PRIME + remainingDepth;
            h = h * Utils.HASH_PRIME + rejectName.hashCode();
            h = h * Utils.HASH_PRIME + sequenceName.hashCode();
            h = h * Utils.HASH_PRIME + sequenceVariables.hashCode();
            h = h * Utils.HASH_PRIME + remainingSequencing;
            h = h * Utils.HASH_PRIME + nestingLimits.hashCode();
            h = h == 0 ? 1 : h;
            hashCode = h;
        }
        return h;
    }

    @Override
    public String toString() {
        String retval =  "Remaining depth: " + remainingDepth +
               "\nReject Next Name: " + rejectName +
               "\nSequence Name: " + sequenceName +
               "\nSequence Set: {";
        for (Variable var : sequenceVariables) {
            retval += var + " ";
        }
        retval += "}\nRemaining Sequencing: " + remainingSequencing;
        retval += "\nNesting Limits:\n";
        for(String s : nestingLimits.keySet()) {
            retval += "\t" + s + " -> " + nestingLimits.get(s) + "\n";
        }
        return retval;
    }
}
