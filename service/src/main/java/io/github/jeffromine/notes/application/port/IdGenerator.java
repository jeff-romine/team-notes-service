package io.github.jeffromine.notes.application.port;

import java.util.UUID;

/** Where new ids come from. The shell calls it; the core receives ids on commands. */
public interface IdGenerator {
    UUID next();
}
