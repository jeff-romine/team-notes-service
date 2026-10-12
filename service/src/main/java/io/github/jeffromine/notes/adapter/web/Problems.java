package io.github.jeffromine.notes.adapter.web;

import io.github.jeffromine.notes.domain.core.Rejection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.List;
import java.util.Map;

/** RFC 9457 problem details for every rejection, with stable {@code urn:notes:problem:<slug>} types. */
final class Problems {

    private Problems() {}

    static ProblemDetail of(HttpStatus status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:notes:problem:" + slug));
        problem.setTitle(title);
        return problem;
    }

    static ProblemDetail from(Rejection rejection) {
        return switch (rejection) {
            case Rejection.NotFound r -> of(HttpStatus.NOT_FOUND, "not-found", "Not found", r.what() + " not found");
            case Rejection.Forbidden r -> of(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", r.reason());
            case Rejection.VersionConflict r -> {
                ProblemDetail p = of(HttpStatus.CONFLICT, "version-conflict", "Version conflict",
                        "the version you sent is stale; the current version is " + r.currentVersion());
                p.setProperty("currentVersion", r.currentVersion());
                yield p;
            }
            case Rejection.Invalid r -> {
                ProblemDetail p = of(HttpStatus.BAD_REQUEST, "invalid", "Invalid request", "one or more fields are invalid");
                p.setProperty("errors", r.errors().stream()
                        .map(e -> Map.of("field", e.field(), "message", e.message()))
                        .toList());
                yield p;
            }
            case Rejection.LastAdmin r -> of(HttpStatus.CONFLICT, "last-admin", "Last admin",
                    "the team would be left without an admin");
            case Rejection.Unsupported r -> of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-type", "Unsupported type",
                    "attachments of type " + r.contentType() + " are not accepted");
            case Rejection.TooLarge r -> {
                ProblemDetail p = of(HttpStatus.PAYLOAD_TOO_LARGE, "too-large", "Too large",
                        "the attachment exceeds " + r.limitBytes() + " bytes");
                p.setProperty("limitBytes", r.limitBytes());
                yield p;
            }
        };
    }

    static ProblemDetail invalid(List<Map<String, String>> errors) {
        ProblemDetail p = of(HttpStatus.BAD_REQUEST, "invalid", "Invalid request", "one or more fields are invalid");
        p.setProperty("errors", errors);
        return p;
    }
}
