package io.github.jeffromine.notes.domain.core;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * given(state).when(command).expectEvents(...) over a pure decider. The helper is generic so both deciders use it.
 */
final class DeciderAssert<C, E, S> {

    private final S state;
    private final C command;
    private final Function<C, Optional<Rejection>> guard;
    private final BiFunction<C, S, List<E>> decide;

    private DeciderAssert(S state, C command, Function<C, Optional<Rejection>> guard, BiFunction<C, S, List<E>> decide) {
        this.state = state;
        this.command = command;
        this.guard = guard;
        this.decide = decide;
    }

    static Given<NoteCommand, NoteEvent, NoteState> givenNote(NoteState state) {
        return command -> new DeciderAssert<>(state, command,
                c -> NoteDecider.guard(c, state, Limits.defaults()), NoteDecider::decide);
    }

    static Given<TeamCommand, TeamEvent, TeamState> givenTeam(TeamState state) {
        return command -> new DeciderAssert<>(state, command,
                c -> TeamDecider.guard(c, state, Limits.defaults()), TeamDecider::decide);
    }

    interface Given<C, E, S> {
        DeciderAssert<C, E, S> when(C command);
    }

    @SafeVarargs
    final List<E> expectEvents(Class<? extends E>... types) {
        assertThat(guard.apply(command)).as("guard").isEmpty();
        List<E> events = decide.apply(command, state);
        List<Class<?>> actual = events.stream().<Class<?>>map(Object::getClass).toList();
        assertThat(actual).containsExactly(types);
        return events;
    }

    void expectNoEvents() {
        assertThat(guard.apply(command)).as("guard").isEmpty();
        assertThat(decide.apply(command, state)).isEmpty();
    }

    <R extends Rejection> R expectRejected(Class<R> type) {
        Optional<Rejection> rejection = guard.apply(command);
        assertThat(rejection).as("guard").isPresent();
        assertThat(rejection.get()).isInstanceOf(type);
        return type.cast(rejection.get());
    }
}
