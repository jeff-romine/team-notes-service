package io.github.jeffromine.notes.adapter.web;

import io.github.jeffromine.notes.adapter.web.EntryDtos.EntryRequest;
import io.github.jeffromine.notes.adapter.web.EntryDtos.EntryResponse;
import io.github.jeffromine.notes.adapter.web.EntryDtos.EntryUpdateRequest;
import io.github.jeffromine.notes.application.CommandContext;
import io.github.jeffromine.notes.application.NoteCommands;
import io.github.jeffromine.notes.application.NoteQueries;
import io.github.jeffromine.notes.domain.core.NoteCommand;
import io.github.jeffromine.notes.domain.core.NoteState;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

/** The whiteboard: entries appended by anyone who can see the note, each with its own author and version. */
@RestController
@RequestMapping(path = "/api/notes/{noteId}/entries", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntryController {

    private final NoteCommands commands;
    private final NoteQueries queries;
    private final CommandContext context;

    public EntryController(NoteCommands commands, NoteQueries queries, CommandContext context) {
        this.commands = commands;
        this.queries = queries;
        this.context = context;
    }

    @GetMapping
    public List<EntryResponse> list(@PathVariable UUID noteId, Principal principal) {
        return queries.entries(principal.getName(), noteId).stream().map(EntryResponse::of).toList();
    }

    @PostMapping
    public ResponseEntity<EntryResponse> append(@PathVariable UUID noteId, @Valid @RequestBody EntryRequest body, Principal principal) {
        UUID entryId = context.newId();
        NoteState state = commands.handle(new NoteCommand.AppendEntry(principal.getName(), context.now(), noteId, entryId,
                body.body(), body.formatOrPlain()));
        return ResponseEntity.created(URI.create("/api/notes/" + noteId + "/entries/" + entryId))
                .body(EntryResponse.of(state.entry(entryId).orElseThrow()));
    }

    @PutMapping("/{entryId}")
    public EntryResponse update(@PathVariable UUID noteId, @PathVariable UUID entryId,
                                @Valid @RequestBody EntryUpdateRequest body, Principal principal) {
        NoteState state = commands.handle(new NoteCommand.UpdateEntry(principal.getName(), context.now(), noteId, entryId,
                body.version(), body.body(), body.formatOrPlain()));
        return EntryResponse.of(state.entry(entryId).orElseThrow());
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> delete(@PathVariable UUID noteId, @PathVariable UUID entryId, Principal principal) {
        commands.handle(new NoteCommand.DeleteEntry(principal.getName(), context.now(), noteId, entryId));
        return ResponseEntity.noContent().build();
    }
}
