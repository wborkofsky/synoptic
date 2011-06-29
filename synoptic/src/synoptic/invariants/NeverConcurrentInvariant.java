package synoptic.invariants;

import java.util.List;

import synoptic.model.DistEventType;
import synoptic.model.interfaces.INode;
import synoptic.util.InternalSynopticException;

public class NeverConcurrentInvariant extends BinaryInvariant {
    public NeverConcurrentInvariant(DistEventType typeFirst,
            DistEventType typeSecond, String relation) {
        super(typeFirst, typeSecond, relation);
    }

    // ///////////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        return first.toString() + " NeverConcurrentWith(" + relation + ") "
                + second.toString();
    }

    // TODO: eliminate the copying of hashCode() and equals() below with a copy
    // in AlwaysConcurrentInvariant

    // NOTE: this invariant is symmetric -- ACwith(x,y) == ACwith(y,x)
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = getClass().hashCode();

        int f = (first == null ? 0 : first.hashCode());
        int s = (second == null ? 0 : second.hashCode());

        result = prime * result + (f + s);
        result = prime * result + (relation == null ? 0 : relation.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        BinaryInvariant other = (BinaryInvariant) obj;
        if (first == null) {
            if (other.first != null) {
                return false;
            }
        }
        if (second == null) {
            if (other.second != null) {
                return false;
            }
        }

        if (!(first.equals(other.first) && second.equals(other.second))
                && !(second.equals(other.first) && first.equals(other.second))) {
            return false;
        }

        if (relation == null) {
            if (other.relation != null) {
                return false;
            }
        } else if (!relation.equals(other.relation)) {
            return false;
        }

        return true;
    }

    /**
     * TODO: cannot be easily shortened?
     */
    @Override
    public <T extends INode<T>> List<T> shorten(List<T> trace) {
        return trace;
    }

    @Override
    public String getShortName() {
        return "NCwith";
    }

    @Override
    public String getLongName() {
        return "NeverConcurrentWith";
    }

    @Override
    public String getLTLString() {
        throw new InternalSynopticException(
                "LTL string cannot be composed for concurrency invariants");
    }
}
