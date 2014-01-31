package edu.ucsf.crosslink.sitereader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;

import edu.ucsf.crosslink.author.AuthorParser;
import edu.ucsf.crosslink.io.CrosslinkPersistance;

public abstract class SiteReader {
	
	private static final Logger LOG = Logger.getLogger(SiteReader.class.getName());

	private String affiliation;
	private String siteRoot;
	private Map<String, String> cookies = new HashMap<String, String>();

	public SiteReader(String affiliation, String siteRoot) {
		this.affiliation = affiliation;
		this.siteRoot = siteRoot;
	}
	
	public Document getDocument(String url) throws IOException, InterruptedException {
    	int attempts = 0;
    	Document doc = null;
    	while (attempts++ < 10) {
        	try {
        	    Connection connection = Jsoup.connect(url);
        	    for (Entry<String, String> cookie : cookies.entrySet()) {
        	        connection.cookie(cookie.getKey(), cookie.getValue());
        	    }
        	    Response response = connection.timeout(15000).execute();
        	    cookies.putAll(response.cookies());
        	    doc = response.parse();
        		break;
        	}
        	catch (java.net.SocketTimeoutException ex) {
        		LOG.info("Trying " + url + " one more time... " + attempts);
        		Thread.sleep(1000);
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
	
    public abstract void readSite(CrosslinkPersistance store, AuthorParser parser) throws Exception;
    
    public String getAffiliation() {
    	return affiliation;
    }
    
    public String getSiteRoot() {
    	return siteRoot;
    }
}
