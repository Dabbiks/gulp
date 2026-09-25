package dev.gulp.api.ui;

import dev.gulp.api.event.Subscription;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * An observable list. Besides the whole-list listeners of {@link Observable} it reports each change with its index
 * ({@link #subscribeChanges}), so {@link ListView} and {@link ItemGrid} update only the rows that changed.
 *
 * <pre>{@code
 * ListState<Quest> quests = ListState.of();
 * listView(quests, quest -> row(label(quest.name()), spacer(), label(quest.progress())));
 * quests.add(new Quest("Find the key"));
 * quests.remove(0);
 * }</pre>
 *
 * @param <T> the element type
 */
public final class ListState<T> implements Observable<List<T>> {

    /** Kinds of list change. */
    public enum Kind {
        /** An element was inserted at the index. */
        ADD,
        /** The element at the index was removed. */
        REMOVE,
        /** The element at the index was replaced. */
        SET,
        /** Everything was replaced; the index is -1. */
        RESET
    }

    /**
     * One change of the list.
     *
     * @param kind what happened
     * @param index the position, -1 for {@link Kind#RESET}
     * @param oldValue the removed or replaced element
     * @param newValue the added or new element
     * @param <T> the element type
     */
    public record Change<T>(
            Kind kind,
            int index,
            @Nullable T oldValue,
            @Nullable T newValue) {}

    private final List<T> items = new ArrayList<>();
    private final List<T> view = Collections.unmodifiableList(items);
    private final Listeners<List<T>> listeners = new Listeners<>();
    private final Listeners<Change<T>> changes = new Listeners<>();

    private ListState() {}

    /**
     * Creates an empty list.
     *
     * @param <T> the element type
     * @return the list
     */
    public static <T> ListState<T> of() {
        return new ListState<>();
    }

    /**
     * Creates a list with elements.
     *
     * @param initial the first elements
     * @param <T> the element type
     * @return the list
     */
    public static <T> ListState<T> of(Collection<? extends T> initial) {
        ListState<T> list = new ListState<>();
        list.items.addAll(initial);
        return list;
    }

    /**
     * Returns a read-only view of the elements.
     *
     * @return the live view
     */
    @Override
    public List<T> get() {
        Listeners.read(this);
        return view;
    }

    /**
     * Returns an element.
     *
     * @param index the position
     * @return the element
     */
    public T get(int index) {
        Listeners.read(this);
        return items.get(index);
    }

    /**
     * Returns the number of elements.
     *
     * @return the size
     */
    public int size() {
        Listeners.read(this);
        return items.size();
    }

    /**
     * Appends an element.
     *
     * @param value the element
     */
    public void add(T value) {
        add(items.size(), value);
    }

    /**
     * Inserts an element.
     *
     * @param index the position
     * @param value the element
     */
    public void add(int index, T value) {
        items.add(index, value);
        changed(new Change<>(Kind.ADD, index, null, value));
    }

    /**
     * Replaces an element.
     *
     * @param index the position
     * @param value the new element
     * @return the old element
     */
    public T set(int index, T value) {
        T old = items.set(index, value);
        changed(new Change<>(Kind.SET, index, old, value));
        return old;
    }

    /**
     * Removes an element.
     *
     * @param index the position
     * @return the removed element
     */
    public T remove(int index) {
        T old = items.remove(index);
        changed(new Change<>(Kind.REMOVE, index, old, null));
        return old;
    }

    /**
     * Removes the first equal element.
     *
     * @param value the element
     * @return {@code true} if it was found
     */
    public boolean removeValue(T value) {
        int index = items.indexOf(value);
        if (index < 0) {
            return false;
        }
        remove(index);
        return true;
    }

    /**
     * Swaps two elements.
     *
     * @param a one position
     * @param b another position
     */
    public void swap(int a, int b) {
        if (a == b) {
            return;
        }
        T first = items.get(a);
        set(a, items.get(b));
        set(b, first);
    }

    /**
     * Replaces all elements.
     *
     * @param values the new elements
     */
    public void setAll(Collection<? extends T> values) {
        items.clear();
        items.addAll(values);
        changed(new Change<>(Kind.RESET, -1, null, null));
    }

    /** Removes all elements. */
    public void clear() {
        setAll(List.of());
    }

    private void changed(Change<T> change) {
        changes.notify(change);
        listeners.notify(view);
    }

    @Override
    public Subscription subscribe(Consumer<? super List<T>> listener) {
        return listeners.add(listener, null);
    }

    /**
     * Calls a listener with every single change.
     *
     * @param listener the listener
     * @return cancels the subscription
     */
    public Subscription subscribeChanges(Consumer<? super Change<T>> listener) {
        return changes.add(listener, null);
    }

    @Override
    public String toString() {
        return "ListState" + items;
    }
}
