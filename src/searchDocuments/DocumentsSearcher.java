package searchDocuments;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class DocumentsSearcher {

    private static final int PAGE_SIZE = 10;
    private final Directory directory;
    private final Analyzer analyzer;
    private final IndexSearcher iSearch;
    private List<TopDocs> foundDocsHistory = new ArrayList<>();  // save docs from every page i turn
    private List<SearchResult> resultHistory = new ArrayList<>();  // save results from every page I turn

    public DocumentsSearcher(String filePath) throws IOException {
        this.directory = FSDirectory.open(Paths.get(filePath));
        DirectoryReader iReader = DirectoryReader.open(this.directory);
        this.iSearch = new IndexSearcher(iReader);  // index searcher
        this.analyzer = new StandardAnalyzer();
    }

    // Search method with an optional parameter to sort the results alphabetically
    public SearchResult search(String textToFind, Set<String> fields, boolean sortAlphabetically) throws ParseException, IOException {
        Sort sort = null;
        if (sortAlphabetically) {
            sort = new Sort(new SortField("artist", SortField.Type.STRING), new SortField("song", SortField.Type.STRING));
        }

        // Pass the sort order to the performSearch method
        return performSearch(textToFind, fields, null, sort);
    }

    // Method to retrieve the next page of search results
    public SearchResult nextPage(String textToFind, Set<String> fields, Sort sort) throws ParseException, IOException {
        SearchResult lastResult = resultHistory.get(resultHistory.size() - 1);
        int totalResults = lastResult.getTotalPages() * PAGE_SIZE;
        int currentPage = foundDocsHistory.size();
        if (totalResults <= currentPage * PAGE_SIZE) {
            System.out.println("You are on the last page. There are no more results.");
            return null;
        }

        ScoreDoc lastScoreDoc = null;
        TopDocs lastFoundDocs = foundDocsHistory.get(foundDocsHistory.size() - 1);
        if (lastFoundDocs.scoreDocs.length > 0) {
            lastScoreDoc = lastFoundDocs.scoreDocs[lastFoundDocs.scoreDocs.length - 1];
        }
        return performSearch(textToFind, fields, lastScoreDoc, sort);
    }

    // Method to retrieve the previous page of search results
    public SearchResult prevPage(String textToFind, Set<String> fields) throws ParseException, IOException {
        if (foundDocsHistory.size() < 1) {  // we cannot go back
            System.out.println("You are on the first page. There are no previous results.");
            return null;
        }
        foundDocsHistory.remove(foundDocsHistory.size() - 1);
        resultHistory.remove(resultHistory.size() - 1);
        
        return resultHistory.get(resultHistory.size() - 1);  // return the previous page
    }

    // Private method to perform the search operation
    private SearchResult performSearch(String textToFind, Set<String> fields, ScoreDoc lastScoreDoc, Sort sort) throws ParseException, IOException {
        if (fields.isEmpty()) {  // default search in all fields
            fields = new HashSet<>(Arrays.asList("artist", "song", "lyrics"));
        }
        if (sort == null) {
            sort = Sort.RELEVANCE;
        }
        
        // Search specified fields
        MultiFieldQueryParser qp = new MultiFieldQueryParser(fields.toArray(new String[0]), this.analyzer);
        Query query;
        try (TokenStream tokenStream = analyzer.tokenStream(null, new StringReader(textToFind))) {
            query = qp.parse(textToFind);
        }

        // Search the index with pagination and sorting options
        TopDocs foundDocs = iSearch.searchAfter(lastScoreDoc, query, PAGE_SIZE, sort);
        foundDocsHistory.add(foundDocs);
        List<String> keywords = tokenizeString(this.analyzer, textToFind);

        SearchResult result = new SearchResult(foundDocs.totalHits, getDocumentsList(foundDocs), keywords, foundDocs.scoreDocs);
        resultHistory.add(result);  // save the results of searching
        
        // Total found documents
        System.out.println("Total Results :: " + foundDocs.totalHits + " for <" + textToFind + ">");
        return result;
    }

    // Tokenize a given string using the specified analyzer
    public List<String> tokenizeString(Analyzer analyzer, String string) {
        List<String> result = new ArrayList<>();
        try (TokenStream stream = analyzer.tokenStream(null, new StringReader(string))) {
            stream.reset();
            while (stream.incrementToken()) {
                result.add(stream.getAttribute(CharTermAttribute.class).toString());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    // Clear the search history
    public void clearSearchHistory() {
        resultHistory.clear();
        foundDocsHistory.clear();
    }

    // Retrieve the list of documents from TopDocs
    private List<Document> getDocumentsList(TopDocs foundDocs) throws IOException {
        List<Document> documentsLst = new ArrayList<>();
        for (ScoreDoc sd : foundDocs.scoreDocs) {
            Document d = this.iSearch.doc(sd.doc);
            documentsLst.add(d);
        }
     
        return documentsLst;
    }

    public class SearchResult {
        public final TotalHits totalHits;
        private final List<Document> documents;
        private final List<String> keywords;
        //private final TopDocs topDocs;  // add this field to store TopDocs
        private final ScoreDoc[] scoreDocs;
        public SearchResult(TotalHits totalHits, List<Document> documents, List<String> keywords, ScoreDoc[] scoreDocs) {  // modify the constructor accordingly
            this.totalHits = totalHits;
            this.documents = documents;
            this.keywords = keywords;
            //this.topDocs = topDocs;
            this.scoreDocs = scoreDocs;
        }
        public ScoreDoc [] getScoreDocs() {
            return this.scoreDocs;
        }
        // Get the total number of pages based on the page size
        public int getTotalPages() {
            return (int) Math.ceil((double) totalHits.value / PAGE_SIZE);
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public List<Document> getDocuments() {
            return documents;
        }

       
    }

    public IndexSearcher getIndexReader() {
        return iSearch;
    }

}

