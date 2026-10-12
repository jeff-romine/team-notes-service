package io.github.jeffromine.notes.search;

import io.github.jeffromine.notes.application.port.SearchIndex;
import io.github.jeffromine.notes.domain.model.NoteDocument;
import io.github.jeffromine.notes.domain.model.SearchHit;
import io.github.jeffromine.notes.domain.model.SearchQuery;
import io.github.jeffromine.notes.domain.model.SearchResult;
import io.github.jeffromine.notes.domain.model.Viewer;
import io.github.jeffromine.notes.domain.model.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.github.jeffromine.notes.support.Fixtures.ALICE;
import static io.github.jeffromine.notes.support.Fixtures.BOB;
import static io.github.jeffromine.notes.support.Fixtures.CAROL;
import static io.github.jeffromine.notes.support.Fixtures.DATA;
import static io.github.jeffromine.notes.support.Fixtures.PLATFORM;
import static io.github.jeffromine.notes.support.Fixtures.T0;
import static org.assertj.core.api.Assertions.assertThat;

/** The behaviour both search adapters must share. Each test starts from a cleared index. */
public abstract class AbstractSearchIndexContractTest {

    protected abstract SearchIndex index();

    static final UUID PRIVATE_A = new UUID(1, 1);
    static final UUID TEAM_P = new UUID(1, 2);
    static final UUID TEAM_D = new UUID(1, 3);
    static final UUID ORG = new UUID(1, 4);
    static final UUID ARCHIVED = new UUID(1, 5);

    static final Viewer ALICE_V = new Viewer(ALICE, Set.of(PLATFORM));
    static final Viewer BOB_V = new Viewer(BOB, Set.of(PLATFORM, DATA));
    static final Viewer CAROL_V = new Viewer(CAROL, Set.of(DATA));

    static NoteDocument doc(UUID id, String owner, Visibility v, UUID team, String title, String body, Set<String> tags,
                            boolean archived, Instant updated) {
        return new NoteDocument(id, owner, team, title, body, tags, v, archived, "", "", 0, 0, T0, updated);
    }

    static NoteDocument doc(UUID id, String owner, Visibility v, UUID team, String title, String body, Set<String> tags,
                            boolean archived, Instant updated, String entries, String attachments) {
        return new NoteDocument(id, owner, team, title, body, tags, v, archived, entries, attachments, 1, 1, T0, updated);
    }

    @BeforeEach
    void seed() {
        index().clear();
        index().index(doc(PRIVATE_A, ALICE, Visibility.PRIVATE, null, "Alice private budget", "numbers", Set.of("finance"), false, T0));
        index().index(doc(TEAM_P, ALICE, Visibility.TEAM, PLATFORM, "Platform roadmap", "budget for the quarter", Set.of("plan"), false, T0.plusSeconds(10)));
        index().index(doc(TEAM_D, BOB, Visibility.TEAM, DATA, "Data pipeline", "ingest and transform", Set.of("plan"), false, T0.plusSeconds(20)));
        index().index(doc(ORG, CAROL, Visibility.ORGANIZATION, null, "Holiday schedule", "office closed", Set.of("hr"), false, T0.plusSeconds(30)));
        index().index(doc(ARCHIVED, CAROL, Visibility.ORGANIZATION, null, "Old budget", "archived budget", Set.of("finance"), true, T0.plusSeconds(40)));
    }

    static SearchQuery q(String text) {
        return new SearchQuery(Optional.ofNullable(text), Optional.empty(), Optional.empty(), Optional.empty(), false,
                SearchQuery.Sort.RELEVANCE, 10, 0);
    }

    static List<UUID> ids(SearchResult r) {
        return r.items().stream().map(SearchHit::id).toList();
    }

    @Test
    void everyViewerSeesExactlyWhatTheyMay() {
        assertThat(ids(index().search(q(null), ALICE_V))).containsExactlyInAnyOrder(PRIVATE_A, TEAM_P, ORG);
        assertThat(ids(index().search(q(null), BOB_V))).containsExactlyInAnyOrder(TEAM_P, TEAM_D, ORG);
        assertThat(ids(index().search(q(null), CAROL_V))).containsExactlyInAnyOrder(TEAM_D, ORG);
        assertThat(index().search(q(null), CAROL_V).total()).isEqualTo(2);
    }

    @Test
    void textMatchesAreRankedWithTitleAboveBody() {
        List<UUID> hits = ids(index().search(q("budget"), ALICE_V));
        assertThat(hits).containsExactly(PRIVATE_A, TEAM_P);
        assertThat(index().search(q("budget"), ALICE_V).items().getFirst().score()).isGreaterThan(0);
        assertThat(ids(index().search(q("nothing-matches-this"), ALICE_V))).isEmpty();
    }

    @Test
    void filtersNarrowByTagTeamVisibilityAndArchived() {
        SearchQuery tag = new SearchQuery(Optional.empty(), Optional.of("plan"), Optional.empty(), Optional.empty(), false, SearchQuery.Sort.NEWEST, 10, 0);
        assertThat(ids(index().search(tag, BOB_V))).containsExactly(TEAM_D, TEAM_P);
        SearchQuery team = new SearchQuery(Optional.empty(), Optional.empty(), Optional.of(PLATFORM), Optional.empty(), false, SearchQuery.Sort.NEWEST, 10, 0);
        assertThat(ids(index().search(team, BOB_V))).containsExactly(TEAM_P);
        SearchQuery org = new SearchQuery(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(Visibility.ORGANIZATION), true, SearchQuery.Sort.NEWEST, 10, 0);
        assertThat(ids(index().search(org, CAROL_V))).containsExactly(ARCHIVED, ORG);
        SearchQuery archivedBudget = new SearchQuery(Optional.of("budget"), Optional.empty(), Optional.empty(), Optional.empty(), true, SearchQuery.Sort.NEWEST, 10, 0);
        assertThat(ids(index().search(archivedBudget, CAROL_V))).containsExactly(ARCHIVED);
    }

    @Test
    void newestSortAndOffsetPaging() {
        SearchQuery newest = new SearchQuery(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false, SearchQuery.Sort.NEWEST, 2, 0);
        SearchResult page1 = index().search(newest, BOB_V);
        assertThat(ids(page1)).containsExactly(ORG, TEAM_D);
        assertThat(page1.total()).isEqualTo(3);
        SearchQuery page2q = new SearchQuery(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false, SearchQuery.Sort.NEWEST, 2, 2);
        assertThat(ids(index().search(page2q, BOB_V))).containsExactly(TEAM_P);
    }

    @Test
    void entryAndAttachmentTextAreSearchable_andReplaceWorks() {
        index().index(doc(TEAM_D, BOB, Visibility.TEAM, DATA, "Data pipeline", "ingest", Set.of("plan"), false, T0.plusSeconds(20),
                "comment mentions kestrel", "spec.pdf\nthe heron migration budget"));
        assertThat(ids(index().search(q("kestrel"), CAROL_V))).containsExactly(TEAM_D);
        assertThat(ids(index().search(q("heron"), CAROL_V))).containsExactly(TEAM_D);
        index().index(doc(TEAM_D, BOB, Visibility.TEAM, DATA, "Data pipeline", "ingest", Set.of("plan"), false, T0.plusSeconds(20)));
        assertThat(ids(index().search(q("kestrel"), CAROL_V))).isEmpty();
        assertThat(index().search(q(null), CAROL_V).total()).as("replace, not duplicate").isEqualTo(2);
    }

    @Test
    void deleteAndClear() {
        index().delete(ORG);
        assertThat(ids(index().search(q(null), CAROL_V))).containsExactly(TEAM_D);
        index().clear();
        assertThat(index().search(q(null), BOB_V).total()).isZero();
    }

    @Test
    void relatedFindsSimilarVisibleNotesAndNeverTheNoteItself() {
        index().index(doc(new UUID(2, 1), ALICE, Visibility.ORGANIZATION, null, "Quarter budget review", "budget numbers for the quarter review", Set.of("finance"), false, T0));
        index().index(doc(new UUID(2, 2), BOB, Visibility.PRIVATE, null, "Secret budget", "budget numbers quarter", Set.of("finance"), false, T0));
        List<UUID> related = index().related(PRIVATE_A, ALICE_V, 5).stream().map(SearchHit::id).toList();
        assertThat(related).doesNotContain(PRIVATE_A).doesNotContain(new UUID(2, 2));
        assertThat(related).contains(new UUID(2, 1));
        assertThat(index().related(new UUID(9, 9), ALICE_V, 5)).isEmpty();
    }
}
