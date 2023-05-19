package searchDocuments;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class QueryHistory {
    private Directory directory;

    public QueryHistory(String indexPath) throws IOException {
        // Initialize the directory for the query history index
        this.directory = FSDirectory.open(Paths.get(indexPath));

        // Create the query history index if it doesn't exist
        createQueryHistoryIndex();
    }

    public void createQueryHistoryIndex() throws IOException {
        // Configure the index writer for the query history index
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(new StandardAnalyzer());
        indexWriterConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        try (IndexWriter writer = new IndexWriter(directory, indexWriterConfig)) {
            // Empty body, we just want to initialize the index if it doesn't exist
        }
    }

    public void addQueryToHistory(String query) throws IOException {
        if (isQueryInHistory(query)) {
            // If the query is already in history, do not add it
            return;
        }

        // Configure the index writer for the query history index
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(new StandardAnalyzer());
        indexWriterConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        try (IndexWriter writer = new IndexWriter(directory, indexWriterConfig)) {
            // Create a document to store the query
            Document doc = new Document();
            doc.add(new TextField("query", query, Field.Store.YES));

            // Add the document to the index
            writer.addDocument(doc);
            writer.commit();
        }
    }

    private boolean isQueryInHistory(String query) throws IOException {
        try (IndexReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            // Create a prefix query to match queries with the given prefix
            Query q = new PrefixQuery(new Term("query", query));

            // Perform the search and retrieve the top matching documents
            TopDocs topDocs = searcher.search(q, 1);

            // Check if any matching documents are found
            return topDocs.totalHits.value > 0;
        }
    }

    public List<String> getQueriesSuggestions(String textToFind) throws IOException {
        List<String> suggestions = new ArrayList<>();

        try (IndexReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            // Create a prefix query to match queries with the given prefix
            Query query = new PrefixQuery(new Term("query", textToFind));

            // Perform the search and retrieve the top matching documents
            TopDocs topDocs = searcher.search(query, 5);

            // Extract the suggestions from the matching documents
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                suggestions.add(doc.get("query"));
            }
        }

        return suggestions;
    }
}
