package io.github.jeffromine.notes.adapter.web;

import io.github.jeffromine.notes.adapter.web.AttachmentDtos.AttachmentResponse;
import io.github.jeffromine.notes.application.AttachmentUploads;
import io.github.jeffromine.notes.application.CommandContext;
import io.github.jeffromine.notes.application.NoteCommands;
import io.github.jeffromine.notes.application.NoteQueries;
import io.github.jeffromine.notes.domain.core.NoteCommand;
import io.github.jeffromine.notes.domain.model.Attachment;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/notes/{noteId}/attachments")
public class AttachmentController {

    private final AttachmentUploads uploads;
    private final NoteCommands commands;
    private final NoteQueries queries;
    private final CommandContext context;

    public AttachmentController(AttachmentUploads uploads, NoteCommands commands, NoteQueries queries, CommandContext context) {
        this.uploads = uploads;
        this.commands = commands;
        this.queries = queries;
        this.context = context;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AttachmentResponse> list(@PathVariable UUID noteId, Principal principal) {
        return queries.attachments(principal.getName(), noteId).stream().map(AttachmentResponse::of).toList();
    }

    /** Multipart with one part named {@code file}; an optional {@code filename} parameter overrides the part's name. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AttachmentResponse> upload(
            @PathVariable UUID noteId,
            @RequestPart("file") MultipartFile file,
            @RequestParam Optional<String> filename,
            Principal principal) {
        String name = filename.orElseGet(() -> file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename());
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        try (InputStream in = file.getInputStream()) {
            Attachment a = uploads.add(principal.getName(), noteId, name, contentType, file.getSize(), in);
            return ResponseEntity.created(URI.create("/api/notes/" + noteId + "/attachments/" + a.id()))
                    .body(AttachmentResponse.of(a));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID noteId, @PathVariable UUID attachmentId, Principal principal) {
        Attachment a = queries.attachment(principal.getName(), noteId, attachmentId);
        InputStream in = queries.openAttachment(principal.getName(), noteId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.contentType()))
                .contentLength(a.size())
                .eTag("\"" + a.sha256() + "\"")
                .header("Content-Disposition",
                        ContentDisposition.attachment().filename(a.filename(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(in));
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID noteId, @PathVariable UUID attachmentId, Principal principal) {
        commands.handle(new NoteCommand.RemoveAttachment(principal.getName(), context.now(), noteId, attachmentId));
        return ResponseEntity.noContent().build();
    }
}
