package edu.ucsf.crosslink;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public abstract class HTMLReader {

	private static final Logger LOG = Logger.getLogger(HTMLReader.class.getName());
	
	private Map<String, String> cookies = new HashMap<String, String>();

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
	

}
