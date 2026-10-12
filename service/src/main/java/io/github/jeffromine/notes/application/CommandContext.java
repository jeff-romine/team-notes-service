package io.github.jeffromine.notes.application;

import io.github.jeffromine.notes.application.port.IdGenerator;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Enriches intent into a command: the request time and fresh ids. These are the two impure inputs a decision
 * needs, and they are produced here, in the shell, before the command reaches the core. Time is truncated to
 * milliseconds so the in-memory store and Cassandra (millisecond timestamps) order identically.
 */
public record CommandContext(Clock clock, IdGenerator ids) {

    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    public UUID newId() {
        return ids.next();
    }
}
