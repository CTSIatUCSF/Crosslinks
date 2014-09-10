package edu.ucsf.crosslink.crawler.sitereader;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.R2RConstants;

public class SiteReader implements R2RConstants {
	
	private static final Logger LOG = Logger.getLogger(SiteReader.class.getName());

	private Map<String, String> cookies = new HashMap<String, String>();	

	private int getDocumentRetry = 10;
	private int getDocumentTimeout = 15000;
	private int getDocumentSleep = 1000;

	@Inject
	public SiteReader(@Named("getDocumentRetry") Integer getDocumentRetry, @Named("getDocumentTimeout") Integer getDocumentTimeout, 
			@Named("getDocumentSleep") Integer getDocumentSleep) {
		this.getDocumentRetry = getDocumentRetry;
		this.getDocumentTimeout = getDocumentTimeout;
		this.getDocumentSleep = getDocumentSleep;
	}
	
	public Document getDocument(String url) throws IOException, InterruptedException  {
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
    
    // this should arguably be the source of the verifiedDT
    public void getPageItems(Researcher researcher) throws IOException, InterruptedException {    	
    	Document doc = getDocument(researcher.getURI());
		researcher.setVerifiedDt(Calendar.getInstance());
    	if (!doc.location().equalsIgnoreCase(researcher.getURI())) {
    		researcher.setHomepage(doc.location());
    	}
		researcher.addImageURL(getImage(doc));
    }
    
    private String getImage(Document doc) {
    	for (Element src : doc.select("[src]")) {
    		if (!src.tagName().equals("img")) {
    			continue;
    		}
    		// from HTML author parser
    		//  try a few more tricks to look for a photo, this particular method works with VIVO
    		if (src.className().equals("individual-photo") && !src.attr("abs:src").contains("unknown") ) { 
    			return src.attr("abs:src");
    		}
    		// from RDF author parser
    		//  look for a photo
    		if (src.attr("class").contains("photo") && !src.attr("title").equals("no image")) {
    			return src.attr("abs:src");
    		}
    		// Profiles
    		if (src.attr("abs:src").contains("PhotoHandler.ashx")) {
    			return src.attr("abs:src");
    		}
    		// from loki
    		if (src.attr("abs:src").contains("displayPhoto")) {
    			return src.attr("abs:src");
    		}
    		// from stanford
    		if (src.attr("abs:src").contains("viewImage")) {
    			return src.attr("abs:src");
    		}
    	}
    	return null;
    }
    
    public static void main(String[] args) {
    	Map<String, String> cookies = new HashMap<String, String>();
    	String url = "http://profiles.ucsf.edu/profile/368698";
    	try {
        	int attempts = 0;
        	Document doc = null;
        	while (attempts++ < 10) {
            	try {
            	    Connection connection = Jsoup.connect(url);
            	    for (Entry<String, String> cookie : cookies.entrySet()) {
            	        connection.cookie(cookie.getKey(), cookie.getValue());
            	    }
            	    Response response = connection.timeout(2000).execute();
            	    cookies.putAll(response.cookies());
            	    doc = response.parse();
            		break;
            	}
            	catch (java.net.SocketTimeoutException ex) {
            		ex.printStackTrace();
            	}
        	}
    		
        	String img = null;
        	for (Element src : doc.select("[src]")) {
        		// from HTML author parser
        		//  try a few more tricks to look for a photo, this particular method works with VIVO
        		if (!src.tagName().equals("img")) {
        			continue;
        		}
        		if (src.tagName().equals("img") && src.className().equals("individual-photo") && !src.attr("abs:src").contains("unknown") ) { 
        			img =  src.attr("abs:src");
        		}
        		// from RDF author parser
        		//  look for a photo
        		if (src.tagName().equals("img") && src.attr("class").contains("photo") && !src.attr("title").equals("no image")) {
        			img =  src.attr("abs:src");
        		}
        		// Profiles
        		if (src.attr("abs:src").contains("PhotoHandler.ashx")) {
        			img =  src.attr("abs:src");
        		}
        		// from loki
        		if (src.tagName().equals("img") && src.attr("abs:src").contains("displayPhoto")) {
        			img =  src.attr("abs:src");
        		}
        		// from stanford
        		if (src.tagName().equals("img") && src.attr("abs:src").contains("viewImage")) {
        			img =  src.attr("abs:src");
        		}
        	}
        	System.out.println(img);
    		
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    }
}
