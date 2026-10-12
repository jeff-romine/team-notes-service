package io.github.jeffromine.notes.domain.core;

import java.util.List;

/**
 * The outcome of applying a command to a state: either the events it produced plus the state after folding them,
 * or the guard's rejection. A rejection carries no events and leaves the state untouched.
 */
public sealed interface Decision<E, S> {

    record Accepted<E, S>(List<E> events, S state) implements Decision<E, S> {
        public Accepted {
            events = List.copyOf(events);
        }
    }

    record Rejected<E, S>(Rejection rejection) implements Decision<E, S> {}
}
