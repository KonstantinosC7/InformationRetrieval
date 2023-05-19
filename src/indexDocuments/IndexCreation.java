package indexDocuments;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

public class IndexCreation {
	
	private final Directory directory;
	private final Analyzer analyzer;
	private final IndexWriterConfig config;
	private IndexWriter iWriter; 
	
	public IndexCreation(String csvPath, String indexPath) throws IOException {
		// Store the index on disk
		this.directory = FSDirectory.open(Paths.get(indexPath));
		
		
		//lowercases, removes stop words,..
		this.analyzer = new StandardAnalyzer();
		
		// IndexWriter Configuration
		this.config = new IndexWriterConfig(analyzer);
		config.setOpenMode(OpenMode.CREATE);	// Creates a new index.
		
		// IndexWriter writes new index file to directory
		this.iWriter = new IndexWriter (directory, config);
		
		buildIndex(csvPath);
	}
	
	public void buildIndex(String filePath) throws IOException {
		BufferedReader reader = new BufferedReader (new FileReader(filePath));
        String line;

        // Read the first line
        String columnNames = reader.readLine();

        // Read in the remaining lines and extract the relevant fields
        while ((line = reader.readLine()) != null) {
        	int firstCommaIndex = line.indexOf(",");
        	int secondCommaIndex = line.indexOf(",", firstCommaIndex + 1);
            String artist = line.substring(0, line.indexOf(","));					// get the artist name
            String song = line.substring(firstCommaIndex + 1, secondCommaIndex);	// get the song name
            String lyrics = line.substring(secondCommaIndex+1);						// get the lyrics of the song
         
            buildDocuments(artist, song, lyrics);
        }
        reader.close();	
        
        // close index
        this.iWriter.close();
	}
	
	private void buildDocuments(String artist, String song, String lyrics) throws IOException {
	    // Create a new document
	    Document doc = new Document();
		
	    // Add the "song" field to the document as a TextField
	    doc.add(new TextField("song", song, Field.Store.YES));
		
	    // Add the "song" field to the document as a SortedDocValuesField
	    // This allows for sorting and faceting on the "song" field
	    doc.add(new SortedDocValuesField("song", new BytesRef(song)));

	    //same for artist and lyrics
	    doc.add(new TextField("artist", artist, Field.Store.YES));
	    doc.add(new SortedDocValuesField("artist", new BytesRef(artist)));
	    doc.add(new TextField("lyrics", lyrics, Field.Store.YES));
	    doc.add(new SortedDocValuesField("lyrics", new BytesRef(lyrics)));

	    // Add the document to the index writer
	    this.iWriter.addDocument(doc);
	}

}
