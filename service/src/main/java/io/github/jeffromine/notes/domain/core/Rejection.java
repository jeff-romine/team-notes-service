package io.github.jeffromine.notes.domain.core;

import java.util.List;
import java.util.UUID;

/**
 * A typed refusal from a guard. The core never throws for control flow: every reason a command cannot be applied is
 * one of these, and the shell maps each to an HTTP status.
 */
public sealed interface Rejection {

    /** The thing does not exist, or the actor may not know whether it exists. */
    record NotFound(String what) implements Rejection {}

    /** The actor can see the thing but may not do this to it. */
    record Forbidden(String reason) implements Rejection {}

    /** The command named a version that is no longer current. */
    record VersionConflict(long currentVersion) implements Rejection {}

    /** The command's content breaks a rule. */
    record Invalid(List<FieldError> errors) implements Rejection {
        public Invalid {
            errors = List.copyOf(errors);
        }

        public static Invalid of(String field, String message) {
            return new Invalid(List.of(new FieldError(field, message)));
        }
    }

    /** Removing or demoting this member would leave the team without an admin. */
    record LastAdmin(UUID teamId) implements Rejection {}

    /** The attachment's content type is not accepted. */
    record Unsupported(String contentType) implements Rejection {}

    /** The attachment exceeds the configured size. */
    record TooLarge(long limitBytes) implements Rejection {}

    record FieldError(String field, String message) {}
}
