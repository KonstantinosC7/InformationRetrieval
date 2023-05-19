module lucene {
	requires org.apache.lucene.core;
	requires org.apache.lucene.queryparser;
	requires org.apache.lucene.sandbox;
	requires org.apache.lucene.highlighter;
	requires org.apache.lucene.memory;
	requires org.apache.lucene.suggest;
	requires jdk.unsupported;
	//requires transitive javafx.graphics;
	requires javafx.base;
    requires javafx.controls;
    
    requires javafx.fxml;
	requires javafx.web;
	requires jdk.jsobject;
	requires java.logging;
    //requires javafx.graphics;
    opens presentation_results to javafx.fxml,javafx.graphics;
  
    exports presentation_results;    
}