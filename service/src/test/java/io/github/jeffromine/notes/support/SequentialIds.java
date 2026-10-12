package io.github.jeffromine.notes.support;

import io.github.jeffromine.notes.application.port.IdGenerator;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Predictable ids: 00000000-0000-0000-0000-000000000001, ...000002, ... */
public final class SequentialIds implements IdGenerator {
    private final AtomicLong next = new AtomicLong(1);

    @Override
    public UUID next() {
        return new UUID(0, next.getAndIncrement());
    }
}
