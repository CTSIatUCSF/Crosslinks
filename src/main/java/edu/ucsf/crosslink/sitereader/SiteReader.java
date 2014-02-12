package edu.ucsf.crosslink.sitereader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.author.Author;

public abstract class SiteReader {
	
	private static final Logger LOG = Logger.getLogger(SiteReader.class.getName());

	private String affiliation;
	private String siteRoot;
	private List<Author> authors = new ArrayList<Author>();
	private List<Author> removeList = new ArrayList<Author>();
	private Map<String, String> cookies = new HashMap<String, String>();	

	private int getDocumentRetry = 10;
	private int getDocumentTimeout = 15000;
	private int getDocumentSleep = 1000;

	@Inject
	public SiteReader(@Named("Affiliation") String affiliation, @Named("BaseURL") String siteRoot) {
		this.affiliation = affiliation;
		this.siteRoot = siteRoot;
	}
	
	@Inject
	public void setDocumentReadParameters(@Named("getDocumentRetry") Integer getDocumentRetry, @Named("getDocumentTimeout") Integer getDocumentTimeout, 
			@Named("getDocumentSleep") Integer getDocumentSleep) {
		this.getDocumentRetry = getDocumentRetry;
		this.getDocumentTimeout = getDocumentTimeout;
		this.getDocumentSleep = getDocumentSleep;
	}
	
	public Document getDocument(String url) throws IOException, InterruptedException {
    	int attempts = 0;
    	Document doc = null;
    	while (attempts++ < getDocumentRetry) {
        	try {
        	    Connection connection = Jsoup.connect(url);
        	    for (Entry<String, String> cookie : cookies.entrySet()) {
        	        connection.cookie(cookie.getKey(), cookie.getValue());
        	    }
        	    Response response = connection.timeout(getDocumentTimeout).execute();
        	    cookies.putAll(response.cookies());
        	    doc = response.parse();
        		break;
        	}
        	catch (java.net.SocketTimeoutException ex) {
        		LOG.info("Trying " + url + " one more time... " + attempts);
        		Thread.sleep(getDocumentSleep);
        	}
    	}
    	return doc;
	}
	
    public static void print(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }

    public static String trim(String s, int width) {
        if (s.length() > width)
            return s.substring(0, width-1) + ".";
        else
            return s;
    }
    
    public void collectAuthors() throws Exception {
    	authors.clear();
    	removeList.clear();
    	collectAuthorURLS();
    	Collections.sort(authors);
    }
    	
	
    protected abstract void collectAuthorURLS() throws Exception;
    
    protected void addAuthor(Author author) {
    	authors.add(author);
    }
    
    public void removeAuthor(Author author) {
    	removeList.add(author);
    }
    
    public List<Author> getAuthors() {
    	return authors;
    }
        
    public void purgeProcessedAuthors() {
    	authors.removeAll(removeList);
    	removeList.clear();
    }    
    
    public String getAffiliation() {
    	return affiliation;
    }
    
    public String getSiteRoot() {
    	return siteRoot;
    }
    
}
