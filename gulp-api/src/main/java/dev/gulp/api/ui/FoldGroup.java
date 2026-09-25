package dev.gulp.api.ui;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Foldable sections of which at most one is open (an accordion).
 *
 * <pre>{@code
 * FoldGroup group = new FoldGroup();
 * foldable("Weapons", weapons).group(group);
 * foldable("Armour", armour).group(group);
 * }</pre>
 */
public final class FoldGroup {

    private final List<Foldable> members = new ArrayList<>();

    /** Creates an empty group. */
    public FoldGroup() {}

    void join(Foldable foldable) {
        if (!members.contains(foldable)) {
            members.add(foldable);
        }
    }

    void opened(Foldable foldable) {
        for (Foldable member : members) {
            if (member != foldable && member.isOpen()) {
                member.open(false);
            }
        }
    }

    /**
     * Returns the open section.
     *
     * @return the section, or {@code null} if all are closed
     */
    public @Nullable Foldable open() {
        for (Foldable member : members) {
            if (member.isOpen()) {
                return member;
            }
        }
        return null;
    }
}
