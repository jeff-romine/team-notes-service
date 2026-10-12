package io.github.jeffromine.notes.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.github.jeffromine.notes.support.Fixtures.ALICE;
import static io.github.jeffromine.notes.support.Fixtures.NOTE_1;
import static io.github.jeffromine.notes.support.Fixtures.T0;
import static io.github.jeffromine.notes.support.Fixtures.privateNote;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorTest {

    @Test
    void roundTrips() {
        Cursor c = new Cursor(T0, NOTE_1);
        assertThat(Cursor.decode(c.encode())).isEqualTo(c);
    }

    @Test
    void refusesGarbage() {
        assertThatThrownBy(() -> Cursor.decode("not-a-cursor")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cursor.decode("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uuidOrderIsUnsignedLikeCassandra() {
        UUID high = UUID.fromString("ffffffff-0000-0000-0000-000000000000");
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertThat(Note.compareUnsigned(high, low)).isPositive();
        assertThat(high.compareTo(low)).isNegative();
        Note newer = new Note(high, ALICE, null, "t", "b", BodyFormat.PLAIN, java.util.Set.of(), Visibility.PRIVATE, false, 1, T0, T0);
        Note older = privateNote(low, ALICE);
        assertThat(Note.NEWEST_FIRST.compare(newer, older)).isNegative();
        assertThat(new Cursor(T0, high).isBefore(older)).isTrue();
        assertThat(new Cursor(T0, low).isBefore(newer)).isFalse();
    }
}
