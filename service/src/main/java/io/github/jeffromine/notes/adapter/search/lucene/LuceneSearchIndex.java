package io.github.jeffromine.notes.adapter.search.lucene;

import io.github.jeffromine.notes.application.port.SearchIndex;
import io.github.jeffromine.notes.domain.model.NoteDocument;
import io.github.jeffromine.notes.domain.model.SearchHit;
import io.github.jeffromine.notes.domain.model.SearchQuery;
import io.github.jeffromine.notes.domain.model.SearchResult;
import io.github.jeffromine.notes.domain.model.Viewer;
import io.github.jeffromine.notes.domain.model.Visibility;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldCollectorManager;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.QueryBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The development search index: Lucene in memory. Elasticsearch is Lucene underneath, so ranking here behaves like
 * the Elasticsearch adapter's, which is why this is preferred over hand-rolled token matching for development.
 *
 * <p>Every read is filtered to the viewer: own notes, ORGANIZATION notes, and TEAM notes of the viewer's teams.
 */
public final class LuceneSearchIndex implements SearchIndex, AutoCloseable {

    static final String ID = "id";
    static final String TITLE = "title";
    static final String BODY = "body";
    static final String ENTRIES = "entries";
    static final String ATTACHMENTS = "attachments";
    static final String TAG = "tag";
    static final String TAG_TEXT = "tagtext";
    static final String OWNER = "owner";
    static final String TEAM = "team";
    static final String VISIBILITY = "visibility";
    static final String ARCHIVED = "archived";
    static final String UPDATED = "updated";
    static final String CREATED = "created";
    static final String SNIPPET = "snippet";
    static final String[] TEXT_FIELDS = {TITLE, BODY, ENTRIES, ATTACHMENTS, TAG_TEXT};

    private final Analyzer analyzer = new StandardAnalyzer();
    private final ByteBuffersDirectory directory = new ByteBuffersDirectory();
    private final IndexWriter writer;
    private final SearcherManager searchers;

    public LuceneSearchIndex() {
        try {
            writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
            writer.commit();
            searchers = new SearcherManager(writer, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void index(NoteDocument d) {
        Document doc = new Document();
        doc.add(new StringField(ID, d.id().toString(), Field.Store.YES));
        doc.add(new TextField(TITLE, d.title(), Field.Store.YES));
        doc.add(new TextField(BODY, d.bodyText(), Field.Store.NO));
        doc.add(new TextField(ENTRIES, d.entriesText(), Field.Store.NO));
        doc.add(new TextField(ATTACHMENTS, d.attachmentsText(), Field.Store.NO));
        for (String tag : d.tags()) {
            doc.add(new StringField(TAG, tag, Field.Store.YES));
            doc.add(new TextField(TAG_TEXT, tag.replace('-', ' '), Field.Store.NO));
        }
        doc.add(new StringField(OWNER, d.owner(), Field.Store.YES));
        doc.add(new StringField(TEAM, d.teamId() == null ? "" : d.teamId().toString(), Field.Store.YES));
        doc.add(new StringField(VISIBILITY, d.visibility().name(), Field.Store.YES));
        doc.add(new StringField(ARCHIVED, Boolean.toString(d.archived()), Field.Store.YES));
        doc.add(new NumericDocValuesField(UPDATED, d.updatedAt().toEpochMilli()));
        doc.add(new StoredField(UPDATED, d.updatedAt().toEpochMilli()));
        doc.add(new StoredField(CREATED, d.createdAt().toEpochMilli()));
        doc.add(new StoredField(SNIPPET, d.toHit(0).snippet()));
        try {
            writer.updateDocument(new Term(ID, d.id().toString()), doc);
            searchers.maybeRefreshBlocking();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(UUID noteId) {
        try {
            writer.deleteDocuments(new Term(ID, noteId.toString()));
            searchers.maybeRefreshBlocking();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void clear() {
        try {
            writer.deleteAll();
            searchers.maybeRefreshBlocking();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public SearchResult search(SearchQuery query, Viewer viewer) {
        BooleanQuery.Builder b = new BooleanQuery.Builder();
        boolean hasText = query.q().map(q -> !q.isBlank()).orElse(false);
        if (hasText) {
            b.add(textQuery(query.q().get()), BooleanClause.Occur.MUST);
        } else {
            b.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
        }
        b.add(viewerFilter(viewer), BooleanClause.Occur.FILTER);
        query.tag().ifPresent(tag -> b.add(new TermQuery(new Term(TAG, tag)), BooleanClause.Occur.FILTER));
        query.teamId().ifPresent(team -> b.add(new TermQuery(new Term(TEAM, team.toString())), BooleanClause.Occur.FILTER));
        query.visibility().ifPresent(v -> b.add(new TermQuery(new Term(VISIBILITY, v.name())), BooleanClause.Occur.FILTER));
        if (!query.includeArchived()) {
            b.add(new TermQuery(new Term(ARCHIVED, "false")), BooleanClause.Occur.FILTER);
        }
        boolean byRelevance = query.sort() == SearchQuery.Sort.RELEVANCE && hasText;
        return run(b.build(), byRelevance, query.offset(), query.limit());
    }

    @Override
    public List<SearchHit> related(UUID noteId, Viewer viewer, int limit) {
        IndexSearcher searcher = acquire();
        try {
            TopDocs self = searcher.search(new TermQuery(new Term(ID, noteId.toString())), 1);
            if (self.totalHits.value() == 0) {
                return List.of();
            }
            MoreLikeThis mlt = new MoreLikeThis(searcher.getIndexReader());
            mlt.setAnalyzer(analyzer);
            mlt.setFieldNames(TEXT_FIELDS);
            mlt.setMinTermFreq(1);
            mlt.setMinDocFreq(1);
            mlt.setMinWordLen(3);
            Query like = mlt.like(self.scoreDocs[0].doc);
            BooleanQuery.Builder b = new BooleanQuery.Builder();
            b.add(like, BooleanClause.Occur.MUST);
            b.add(new TermQuery(new Term(ID, noteId.toString())), BooleanClause.Occur.MUST_NOT);
            b.add(viewerFilter(viewer), BooleanClause.Occur.FILTER);
            b.add(new TermQuery(new Term(ARCHIVED, "false")), BooleanClause.Occur.FILTER);
            return run(b.build(), true, 0, limit).items();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            release(searcher);
        }
    }

    /** Like Elasticsearch's multi_match over the text fields, with the title weighted three times. */
    private Query textQuery(String text) {
        QueryBuilder qb = new QueryBuilder(analyzer);
        BooleanQuery.Builder any = new BooleanQuery.Builder();
        for (String field : TEXT_FIELDS) {
            Query q = qb.createBooleanQuery(field, text, BooleanClause.Occur.SHOULD);
            if (q == null) {
                continue;
            }
            any.add(field.equals(TITLE) ? new BoostQuery(q, 3f) : q, BooleanClause.Occur.SHOULD);
        }
        any.setMinimumNumberShouldMatch(1);
        return any.build();
    }

    static Query viewerFilter(Viewer viewer) {
        BooleanQuery.Builder any = new BooleanQuery.Builder();
        any.add(new TermQuery(new Term(OWNER, viewer.username())), BooleanClause.Occur.SHOULD);
        any.add(new TermQuery(new Term(VISIBILITY, Visibility.ORGANIZATION.name())), BooleanClause.Occur.SHOULD);
        Set<UUID> teams = viewer.teamIds();
        if (!teams.isEmpty()) {
            BooleanQuery.Builder teamClause = new BooleanQuery.Builder();
            teamClause.add(new TermQuery(new Term(VISIBILITY, Visibility.TEAM.name())), BooleanClause.Occur.MUST);
            BooleanQuery.Builder anyTeam = new BooleanQuery.Builder();
            for (UUID team : teams) {
                anyTeam.add(new TermQuery(new Term(TEAM, team.toString())), BooleanClause.Occur.SHOULD);
            }
            anyTeam.setMinimumNumberShouldMatch(1);
            teamClause.add(anyTeam.build(), BooleanClause.Occur.MUST);
            any.add(teamClause.build(), BooleanClause.Occur.SHOULD);
        }
        any.setMinimumNumberShouldMatch(1);
        return any.build();
    }

    private SearchResult run(Query query, boolean byRelevance, int offset, int limit) {
        IndexSearcher searcher = acquire();
        try {
            int wanted = offset + limit;
            TopDocs top;
            if (byRelevance) {
                top = searcher.search(query, new TopScoreDocCollectorManager(wanted, Integer.MAX_VALUE));
            } else {
                Sort newest = new Sort(new SortField(UPDATED, SortField.Type.LONG, true));
                top = searcher.search(query, new TopFieldCollectorManager(newest, wanted, Integer.MAX_VALUE));
            }
            StoredFields stored = searcher.storedFields();
            List<SearchHit> hits = new ArrayList<>();
            for (int i = offset; i < top.scoreDocs.length; i++) {
                ScoreDoc sd = top.scoreDocs[i];
                hits.add(toHit(stored.document(sd.doc), Float.isNaN(sd.score) ? 0 : sd.score));
            }
            return new SearchResult(hits, top.totalHits.value());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            release(searcher);
        }
    }

    private static SearchHit toHit(Document doc, double score) {
        String team = doc.get(TEAM);
        Set<String> tags = Set.of(doc.getValues(TAG));
        return new SearchHit(
                UUID.fromString(doc.get(ID)),
                doc.get(TITLE),
                doc.get(SNIPPET),
                tags,
                doc.get(OWNER),
                team.isEmpty() ? null : UUID.fromString(team),
                Visibility.valueOf(doc.get(VISIBILITY)),
                Boolean.parseBoolean(doc.get(ARCHIVED)),
                Instant.ofEpochMilli(doc.getField(UPDATED).numericValue().longValue()),
                score);
    }

    private IndexSearcher acquire() {
        try {
            return searchers.acquire();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void release(IndexSearcher searcher) {
        try {
            searchers.release(searcher);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            searchers.close();
            writer.close();
            directory.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static BytesRef bytes(String s) {
        return new BytesRef(s);
    }
}
