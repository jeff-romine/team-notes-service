package io.github.jeffromine.notes.domain.core;

import io.github.jeffromine.notes.domain.model.Attachment;
import io.github.jeffromine.notes.domain.model.Entry;
import io.github.jeffromine.notes.domain.model.Membership;
import io.github.jeffromine.notes.domain.model.Note;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Who may do what. Pure functions over the records; shared by the deciders (writes) and the queries (reads).
 *
 * <ul>
 *   <li>A note is visible to its owner, to members of its team when it is TEAM, and to everyone when ORGANIZATION.</li>
 *   <li>Only the owner changes the note's own fields, archives or deletes it.</li>
 *   <li>Anyone who can see a non-archived note may append entries and attachments to it.</li>
 *   <li>An entry is edited by its author; it is deleted by its author or the note's owner. Same for attachments.</li>
 * </ul>
 */
public final class AccessPolicy {

    private AccessPolicy() {}

    public static boolean canSee(Note note, String actor, Set<UUID> actorTeamIds) {
        if (note.owner().equals(actor)) {
            return true;
        }
        return switch (note.visibility()) {
            case PRIVATE -> false;
            case TEAM -> actorTeamIds.contains(note.teamId());
            case ORGANIZATION -> true;
        };
    }

    public static boolean canModifyNote(Note note, String actor) {
        return note.owner().equals(actor);
    }

    /** Appending entries or attachments: visible and not archived. */
    public static boolean canContribute(Note note, String actor, Set<UUID> actorTeamIds) {
        return !note.archived() && canSee(note, actor, actorTeamIds);
    }

    public static boolean canModifyEntry(Entry entry, String actor) {
        return entry.author().equals(actor);
    }

    public static boolean canDeleteEntry(Note note, Entry entry, String actor) {
        return entry.author().equals(actor) || note.owner().equals(actor);
    }

    public static boolean canDeleteAttachment(Note note, Attachment attachment, String actor) {
        return attachment.uploadedBy().equals(actor) || note.owner().equals(actor);
    }

    public static boolean isMember(List<Membership> memberships, String actor) {
        return memberships.stream().anyMatch(m -> m.username().equals(actor));
    }

    public static boolean isAdmin(List<Membership> memberships, String actor) {
        return memberships.stream().anyMatch(m -> m.username().equals(actor) && m.role() == Membership.Role.ADMIN);
    }
}
