package edu.ucsf.crosslink.crawler.sitereader;

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

import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;


public abstract class SiteReader {
	
	private static final Logger LOG = Logger.getLogger(SiteReader.class.getName());

	private Affiliation harvester;
	private Affiliation affiliation;
	private List<Researcher> researchers = new ArrayList<Researcher>();
	private List<Researcher> removeList = new ArrayList<Researcher>();
	private Map<String, String> cookies = new HashMap<String, String>();	

	private int getDocumentRetry = 10;
	private int getDocumentTimeout = 15000;
	private int getDocumentSleep = 1000;

	public SiteReader(Affiliation harvester, Affiliation affiliation) {
		this.harvester = harvester;
		this.affiliation = affiliation;
	}

	@Inject
	public SiteReader(Affiliation affiliation) {
		this(affiliation, affiliation);
	}
	
	@Inject
	public void setDocumentReadParameters(@Named("getDocumentRetry") Integer getDocumentRetry, @Named("getDocumentTimeout") Integer getDocumentTimeout, 
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
    
    public void collectResearchers() throws Exception {
    	researchers.clear();
    	removeList.clear();
    	collectResearcherURLs();
    	// dedupe, keep those with a name if you have a choice
    	Map<String, Researcher> rbyU = new HashMap<String, Researcher>();
    	for (Researcher r : researchers) {
    		if (rbyU.containsKey(r.getURI()) && r.getLabel() == null) {
    			continue;
    		}
    		rbyU.put(r.getURI(), r);
    	}
    	researchers.clear();
    	researchers.addAll(rbyU.values());
    	Collections.sort(researchers);
    }
    	
	
    protected abstract void collectResearcherURLs() throws Exception;
    
    protected void addResearcher(Researcher researcher) {
    	researchers.add(researcher);
    }
    
    public void removeResearcher(Researcher researcher) {
    	removeList.add(researcher);
    }
    
    public List<Researcher> getResearchers() {
    	return researchers;
    }
        
    public int getRemainingAuthorsSize() {
    	return researchers.size() - removeList.size();
    }
        
    public void purgeProcessedAuthors() {
    	researchers.removeAll(removeList);
    	removeList.clear();
    }    
    
    public Affiliation getHarvester() {
    	return harvester;
    }

    public Affiliation getAffiliation() {
    	return affiliation;
    }

    public String getSiteRoot() {
    	return affiliation.getBaseURL();
    }
}
