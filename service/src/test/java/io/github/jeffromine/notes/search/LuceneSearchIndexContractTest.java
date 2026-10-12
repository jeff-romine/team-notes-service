package io.github.jeffromine.notes.search;

import io.github.jeffromine.notes.adapter.search.lucene.LuceneSearchIndex;
import io.github.jeffromine.notes.application.port.SearchIndex;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class LuceneSearchIndexContractTest extends AbstractSearchIndexContractTest {

    static LuceneSearchIndex index;

    @BeforeAll
    static void open() {
        index = new LuceneSearchIndex();
    }

    @AfterAll
    static void close() {
        index.close();
    }

    @Override
    protected SearchIndex index() {
        return index;
    }
}
