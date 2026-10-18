package org.antoined.workspace.layout;

import org.antoined.workspace.Rect;
import org.antoined.workspace.WorkspaceLayout;

import java.util.function.IntToDoubleFunction;

/**
 * Items laid out one after another along an axis. Each item may have its own length along
 * the axis (width when horizontal, height when vertical); the cross size is uniform.
 */
public class LinearLayout implements WorkspaceLayout<Integer> {

    public enum Orientation { HORIZONTAL, VERTICAL }

    private final Orientation orientation;
    private final double crossSize;
    private final double gap;
    private IntToDoubleFunction lengthOf;

    public LinearLayout(Orientation orientation, double itemLength, double crossSize, double gap) {
        this(orientation, i -> itemLength, crossSize, gap);
    }

    public LinearLayout(Orientation orientation, IntToDoubleFunction lengthOf, double crossSize, double gap) {
        this.orientation = orientation;
        this.lengthOf = lengthOf;
        this.crossSize = crossSize;
        this.gap = gap;
    }

    public void setLengthOf(IntToDoubleFunction lengthOf) {
        this.lengthOf = lengthOf;
    }

    public Orientation getOrientation() { return orientation; }

    @Override
    public Rect bounds(Integer index) {
        double offset = 0;
        for (int i = 0; i < index; i++) {
            offset += lengthOf.applyAsDouble(i) + gap;
        }
        double len = lengthOf.applyAsDouble(index);
        return orientation == Orientation.HORIZONTAL
                ? new Rect(offset, 0, len, crossSize)
                : new Rect(0, offset, crossSize, len);
    }
}
