package io.github.jeffromine.notes.adapter.search.lucene;

import io.github.jeffromine.notes.application.port.SearchIndex;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!elasticsearch")
public class LuceneSearchConfig {

    @Bean(destroyMethod = "close")
    SearchIndex searchIndex() {
        return new LuceneSearchIndex();
    }
}
