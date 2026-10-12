package io.github.jeffromine.notes.application;

import io.github.jeffromine.notes.domain.core.Rejection;

/**
 * The shell's boundary exception: a guard refused the command, or a read found nothing the caller may see. The web
 * layer maps the wrapped {@link Rejection} to a status. The core itself never throws this; the shell does, once
 * the pure decision is in hand.
 */
public final class RejectionException extends RuntimeException {

    private final Rejection rejection;

    public RejectionException(Rejection rejection) {
        super(rejection.toString());
        this.rejection = rejection;
    }

    public Rejection rejection() {
        return rejection;
    }
}
