package io.github.jeffromine.notes;

import io.github.jeffromine.notes.config.NotesProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.cassandra.autoconfigure.CassandraAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.cassandra.autoconfigure.DataCassandraAutoConfiguration;
import org.springframework.boot.data.cassandra.autoconfigure.DataCassandraRepositoriesAutoConfiguration;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchRestClientAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The Cassandra and Elasticsearch auto-configurations are excluded here and imported again by the profile-specific
 * configurations ({@code cassandra}, {@code elasticsearch}), so the default profile never tries to reach a server.
 */
@SpringBootApplication(exclude = {
        CassandraAutoConfiguration.class,
        DataCassandraAutoConfiguration.class,
        DataCassandraRepositoriesAutoConfiguration.class,
        ElasticsearchClientAutoConfiguration.class,
        ElasticsearchRestClientAutoConfiguration.class})
@EnableConfigurationProperties(NotesProperties.class)
@EnableScheduling
public class NotesApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotesApplication.class, args);
    }
}
