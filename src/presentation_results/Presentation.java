package presentation_results;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;

import indexDocuments.IndexCreation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;
import netscape.javascript.JSObject;
import searchDocuments.DocumentsSearcher;
import searchDocuments.QueryHistory;

public class Presentation extends Application {
    // Declaration of UI components
    private ComboBox<String> searchField;
    private Button searchButton;
    private CheckBox lyricsCheckBox;
    private CheckBox artistCheckBox;
    private CheckBox songCheckBox;
    private WebView resultArea;
    private Button previousPageButton;
    private Button nextPageButton;
    private Label pageNumberLabel;
    private int currentPage;
    private DocumentsSearcher luceneSearch;
    private QueryHistory qHistory;
    private int totalPages;
    private CheckBox alphabeticalGroupingCheckBox;
    private Label totalResultsLabel;
    private static final String docPath = "inputFiles/spotify_1000_songs_.csv";
    private static final String songIndexPath = "indexFiles/songIndex";
    private static final String queryHistoryIndexPath = "indexFiles/QueryHistoryIndex";

    public static void main(String[] args) throws IOException {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Check if the song index exists, if not, create it
        File directory = new File(songIndexPath);
        File[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            IndexCreation indexCreation = new IndexCreation(docPath, songIndexPath);
        }

        try {
            luceneSearch = new DocumentsSearcher(songIndexPath);
            qHistory = new QueryHistory(queryHistoryIndexPath);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        primaryStage.setTitle("Lyrics Search");

        VBox root = new VBox(10); // layout with 10px spacing

        // Search panel with text field, search button, and checkboxes
        HBox searchPanel = new HBox(10);
        searchField = new ComboBox<>();
        searchField.setPromptText("Enter your search query");
        searchField.setEditable(true);
        searchButton = new Button("Search");
        lyricsCheckBox = new CheckBox("Lyrics");
        artistCheckBox = new CheckBox("Artist");
        songCheckBox = new CheckBox("Song");
        alphabeticalGroupingCheckBox = new CheckBox("Alphabetical Grouping");
        searchPanel.getChildren().addAll(searchField, searchButton, lyricsCheckBox, artistCheckBox, songCheckBox, alphabeticalGroupingCheckBox);
        root.getChildren().add(searchPanel);

        totalResultsLabel = new Label();
        // Result area for search results
        resultArea = new WebView();
        VBox.setVgrow(resultArea, Priority.ALWAYS);
        root.getChildren().add(resultArea);

        // Pagination panel with previous and next buttons
        HBox paginationPanel = new HBox(10);
        previousPageButton = new Button("Previous");
        nextPageButton = new Button("Next");
        pageNumberLabel = new Label();
        paginationPanel.getChildren().addAll(previousPageButton, nextPageButton, pageNumberLabel);
        root.getChildren().add(paginationPanel);

        setUpActions();

        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();
    }

    private void setUpActions() {
        // Action for the search button
        searchButton.setOnAction(event -> {
            try {
                performSearch();
            } catch (ParseException | IOException | InvalidTokenOffsetsException e) {
                e.printStackTrace();
            }
        });

        // Action for pressing Enter in the search field
        searchField.addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
            if (keyEvent.getCode() == KeyCode.ENTER) {
                try {
                    performSearch();
                } catch (ParseException | IOException | InvalidTokenOffsetsException e) {
                    e.printStackTrace();
                }
            }
        });

        // Action for alphabeticalGroupingCheckBox
        alphabeticalGroupingCheckBox.setOnAction(event -> {
            try {
                performSearch();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Action for the previous page button
        previousPageButton.setOnAction(event -> {
            try {
                performPreviousPageSearch();
            } catch (ParseException | IOException e) {
                e.printStackTrace();
            }
        });

        // Action for the next page button
        nextPageButton.setOnAction(event -> {
            try {
                performNextPageSearch();
            } catch (ParseException | IOException | InvalidTokenOffsetsException e) {
                e.printStackTrace();
            }
        });

        // Initialize the Timeline for debouncing input
        Timeline debounceTimeline = new Timeline();
        debounceTimeline.setCycleCount(1); // Execute only once

        searchField.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            // Cancel any pending debounce
            debounceTimeline.stop();

            // Schedule a new debounce
            debounceTimeline.getKeyFrames().clear();
            debounceTimeline.getKeyFrames().add(new KeyFrame(Duration.millis(1500), event -> {
                try {
                    List<String> suggestions = qHistory.getQueriesSuggestions(newValue);
                    if (!suggestions.isEmpty()) {
                        searchField.getItems().clear();
                        searchField.getItems().addAll(suggestions);
                        searchField.show();
                    } else {
                        searchField.hide();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));
            debounceTimeline.playFromStart();
        });
    }

    // Update this method to set the total pages
    private void performSearch() throws ParseException, IOException, InvalidTokenOffsetsException {
        String query = searchField.getEditor().getText();
        if (query.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Please enter a query.");
            return;
        }

        Sort sort = null; // Define sort variable within the scope of the method
        if (alphabeticalGroupingCheckBox.isSelected()) {
            sort = new Sort(new SortField("artist", SortField.Type.STRING), new SortField("song", SortField.Type.STRING));
        }

        luceneSearch.clearSearchHistory(); // Clear the search history
        qHistory.addQueryToHistory(query); // Add query to the query history
        Set<String> fields = new HashSet<>();
        if (lyricsCheckBox.isSelected()) {
            fields.add("lyrics");
        }
        if (artistCheckBox.isSelected()) {
            fields.add("artist");
        }
        if (songCheckBox.isSelected()) {
            fields.add("song");
        }
        DocumentsSearcher.SearchResult result = luceneSearch.search(query, fields, alphabeticalGroupingCheckBox.isSelected());
        updateResultArea(result);
        currentPage = 1;
        totalPages = result.getTotalPages(); // Set total pages
        totalResultsLabel.setText("Total Results: " + result.totalHits);
        updatePageNumberLabel();
    }

    // Update this method to handle next page search
    private void performNextPageSearch() throws ParseException, IOException, InvalidTokenOffsetsException {
        if (currentPage < totalPages) {
            String query = searchField.getEditor().getText();
            Sort sort = null; // Define sort variable within the scope of the method
            if (alphabeticalGroupingCheckBox.isSelected()) {
                sort = new Sort(new SortField("artist", SortField.Type.STRING), new SortField("song", SortField.Type.STRING));
            }
            Set<String> fields = new HashSet<>();
            if (lyricsCheckBox.isSelected()) {
                fields.add("lyrics");
            }
            if (artistCheckBox.isSelected()) {
                fields.add("artist");
            }
            if (songCheckBox.isSelected()) {
                fields.add("song");
            }
            DocumentsSearcher.SearchResult result = luceneSearch.nextPage(query, fields, sort);
            if (result != null) {
                updateResultArea(result);
                currentPage++; // Increment currentPage after updating the result area
                totalResultsLabel.setText("Total Results: " + result.totalHits);
                updatePageNumberLabel();
            }
        }
    }

    // Update this method to handle previous page search
    private void performPreviousPageSearch() throws ParseException, IOException {
        if (currentPage > 1) {
            String query = searchField.getEditor().getText();
            Sort sort = null; // Define sort variable within the scope of the method
            if (alphabeticalGroupingCheckBox.isSelected()) {
                sort = new Sort(new SortField("artist", SortField.Type.STRING), new SortField("song", SortField.Type.STRING));
            }
            Set<String> fields = new HashSet<>();
            if (lyricsCheckBox.isSelected()) {
                fields.add("lyrics");
            }
            if (artistCheckBox.isSelected()) {
                fields.add("artist");
            }
            if (songCheckBox.isSelected()) {
                fields.add("song");
            }
            DocumentsSearcher.SearchResult result = luceneSearch.prevPage(query, fields);
            if (result != null) {
                updateResultArea(result);
                currentPage--; // Decrement currentPage after updating the result area
                totalResultsLabel.setText("Total Results: " + result.totalHits);
                updatePageNumberLabel();
            }
        }
    }

    private void updateResultArea(DocumentsSearcher.SearchResult result) {
        StringBuilder html = new StringBuilder("<html><body>");
        for (Document doc : result.getDocuments()) {
            String artist = doc.get("artist");
            String song = doc.get("song");
            String lyrics = doc.get("lyrics");
            String lyricsPreview = lyrics.length() > 200 ? lyrics.substring(0, 200) + "..." : lyrics; // Truncate lyrics to 200 characters
            for (String keyword : result.getKeywords()) {
                lyricsPreview = lyricsPreview.replace(keyword, "<span style='background: yellow;'>" + keyword + "</span>");
            }
            html.append("<h1><a href='#' onClick=\"java.showFullLyrics('").append(song).append("')\">").append(artist).append(" - ").append(song).append("</a></h1>");
            html.append("<p>").append(lyricsPreview).append("</p>");
        }
        html.append("<script>function showFullLyrics(songTitle) { java.showFullLyrics(songTitle); }</script>");
        html.append("</body></html>");
        WebEngine webEngine = resultArea.getEngine();
        webEngine.loadContent(html.toString());

        webEngine.setJavaScriptEnabled(true); // Enable JavaScript
        webEngine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.setMember("java", new JavaBridge(result)); // Setup Java-JS bridge
            }
        });
    }

    public class JavaBridge {
        DocumentsSearcher.SearchResult result;

        public JavaBridge(DocumentsSearcher.SearchResult result) {
            this.result = result;
        }

        public void showFullLyrics(String songTitle) {
            for (Document doc : result.getDocuments()) {
                if (doc.get("song").equals(songTitle)) {
                    String artist = doc.get("artist");
                    String fullLyrics = doc.get("lyrics");
                    Platform.runLater(() -> {
                        Stage lyricsStage = new Stage();
                        lyricsStage.setTitle(songTitle + " Full Lyrics");
                        VBox lyricsLayout = new VBox(10);

                        // Artist label
                        Text artistLabel = new Text("Artist: ");
                        artistLabel.setStyle("-fx-font-weight: bold;");
                        Text artistText = new Text(artist);
                        HBox artistBox = new HBox(artistLabel, artistText);
                        lyricsLayout.getChildren().add(artistBox);

                        // Song Title label
                        Text songTitleLabel = new Text("Song Title: ");
                        songTitleLabel.setStyle("-fx-font-weight: bold;");
                        Text songTitleText = new Text(songTitle);
                        HBox songTitleBox = new HBox(songTitleLabel, songTitleText);
                        lyricsLayout.getChildren().add(songTitleBox);

                        // Lyrics label
                        Text lyricsLabel = new Text("Lyrics:");
                        lyricsLabel.setStyle("-fx-font-weight: bold;");
                        Text lyricsText = new Text(fullLyrics);
                        lyricsText.setWrappingWidth(800);

                        Button closeButton = new Button("Close");
                        closeButton.setOnAction(event -> {
                            lyricsStage.close();
                            updateResultArea(this.result);
                        });
                        lyricsLayout.getChildren().addAll(lyricsLabel, lyricsText, closeButton);

                        Scene lyricsScene = new Scene(lyricsLayout, 800, 600);
                        lyricsStage.setScene(lyricsScene);
                        lyricsStage.show();
                    });
                    break;
                }
            }
        }
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Update this method to display total pages
    private void updatePageNumberLabel() {
        pageNumberLabel.setText("Page " + currentPage + " of " + totalPages);
    }
}
