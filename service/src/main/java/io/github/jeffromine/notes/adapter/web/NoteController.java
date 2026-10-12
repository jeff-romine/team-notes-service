package io.github.jeffromine.notes.adapter.web;

import io.github.jeffromine.notes.adapter.web.NoteDtos.NoteRequest;
import io.github.jeffromine.notes.adapter.web.NoteDtos.NoteResponse;
import io.github.jeffromine.notes.adapter.web.NoteDtos.NoteUpdateRequest;
import io.github.jeffromine.notes.adapter.web.NoteDtos.PageResponse;
import io.github.jeffromine.notes.application.CommandContext;
import io.github.jeffromine.notes.application.NoteCommands;
import io.github.jeffromine.notes.application.NoteQueries;
import io.github.jeffromine.notes.domain.core.NoteCommand;
import io.github.jeffromine.notes.domain.core.NoteState;
import io.github.jeffromine.notes.domain.model.Cursor;
import io.github.jeffromine.notes.domain.model.Note;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class NoteController {

    private final NoteCommands commands;
    private final NoteQueries queries;
    private final CommandContext context;

    public NoteController(NoteCommands commands, NoteQueries queries, CommandContext context) {
        this.commands = commands;
        this.queries = queries;
        this.context = context;
    }

    @PostMapping("/notes")
    public ResponseEntity<NoteResponse> create(@Valid @RequestBody NoteRequest body, Principal principal) {
        NoteState state = commands.handle(new NoteCommand.CreateNote(principal.getName(), context.now(), context.newId(),
                body.title(), body.body(), body.formatOrPlain(), body.tagsOrEmpty(), body.visibility(), body.teamId()));
        Note note = state.note().orElseThrow();
        return ResponseEntity.created(URI.create("/api/notes/" + note.id())).body(response(state));
    }

    @GetMapping("/notes")
    public PageResponse<NoteResponse> myNotes(
            @RequestParam Optional<String> tag,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam Optional<String> cursor,
            Principal principal) {
        return PageResponse.of(
                queries.myNotes(principal.getName(), tag, includeArchived, limit, cursor.map(Cursor::decode)),
                n -> NoteResponse.of(n, null, null));
    }

    @GetMapping("/teams/{teamId}/notes")
    public PageResponse<NoteResponse> teamNotes(
            @PathVariable UUID teamId,
            @RequestParam Optional<String> tag,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam Optional<String> cursor,
            Principal principal) {
        return PageResponse.of(
                queries.teamNotes(principal.getName(), teamId, tag, includeArchived, limit, cursor.map(Cursor::decode)),
                n -> NoteResponse.of(n, null, null));
    }

    @GetMapping("/notes/{id}")
    public NoteResponse get(@PathVariable UUID id, Principal principal) {
        return NoteResponse.of(queries.get(principal.getName(), id));
    }

    @GetMapping(path = "/notes/{id}/html", produces = MediaType.TEXT_HTML_VALUE)
    public String html(@PathVariable UUID id, Principal principal) {
        return queries.html(principal.getName(), id);
    }

    @PutMapping("/notes/{id}")
    public NoteResponse update(@PathVariable UUID id, @Valid @RequestBody NoteUpdateRequest body, Principal principal) {
        return response(commands.handle(new NoteCommand.UpdateNote(principal.getName(), context.now(), id, body.version(),
                body.title(), body.body(), body.formatOrPlain(), body.tagsOrEmpty(), body.visibility(), body.teamId())));
    }

    @PostMapping("/notes/{id}/archive")
    public NoteResponse archive(@PathVariable UUID id, Principal principal) {
        return response(commands.handle(new NoteCommand.ArchiveNote(principal.getName(), context.now(), id)));
    }

    @PostMapping("/notes/{id}/unarchive")
    public NoteResponse unarchive(@PathVariable UUID id, Principal principal) {
        return response(commands.handle(new NoteCommand.UnarchiveNote(principal.getName(), context.now(), id)));
    }

    @DeleteMapping("/notes/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
        commands.handle(new NoteCommand.DeleteNote(principal.getName(), context.now(), id));
        return ResponseEntity.noContent().build();
    }

    private static NoteResponse response(NoteState state) {
        return NoteResponse.of(state.note().orElseThrow(), state.entries().size(), state.attachments().size());
    }
}
