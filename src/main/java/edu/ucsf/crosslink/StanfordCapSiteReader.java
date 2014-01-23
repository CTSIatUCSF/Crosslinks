package edu.ucsf.crosslink;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.github.jsonldjava.core.JSONLDProcessingError;

public class StanfordCapSiteReader {

	private static final Logger LOG = Logger.getLogger(StanfordCapSiteReader.class.getName());

	private static String affiliation = "Stanford";
	private static String profilelisturl = "http://med.stanford.edu/profiles/frdActionServlet?choiceId=showFacByName&tab=all";
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {		// TODO Auto-generated method stub		
		try  {
			
			StanfordCapSiteReader psr = new StanfordCapSiteReader();
			AuthorshipPersistance store = new CSVAuthorshipStore(affiliation + ".csv");	;
			String siteMapURL = profilelisturl;
			
			if (psr != null && store != null && siteMapURL != null) {
				psr.parseSiteMap(siteMapURL, store);
				store.close();
			}
			else {
				showUse();
			}
			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	private static void showUse() {
		System.out.println("No arguments needed, just run it!");
	}
	
    private static void print(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }

    private static String trim(String s, int width) {
        if (s.length() > width)
            return s.substring(0, width-1) + ".";
        else
            return s;
    }

    public void parseSiteMap(String sitemapUrl, AuthorshipPersistance store) throws Exception {
    	int attempts = 0;
    	Document doc = null;
    	while (attempts++ < 10) {
        	try {
        		doc = Jsoup.connect(sitemapUrl).timeout(10000).get();
        		break;
        	}
        	catch (java.net.SocketTimeoutException ex) {
        		LOG.info("Tring " + sitemapUrl + " one more time... " + attempts);
        		Thread.sleep(1000);
        	}
    	}
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").startsWith("http://med.stanford.edu/profiles/")) {
		    		try {
			    		print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
			    		String[] personName = link.text().split(", ");
		    			LOG.info(personName[0] + ":" + personName[1]);
		    			String url = link.attr("abs:href");
		    			url = url.contains(";") ? url.split(";")[0] : url;
		    			// skip it if we already have it
		    			if (store.containsAuthor(url)) {
			    			LOG.info("Skipping " + personName[0] + ":" + personName[1] + " :" + url);
		    				continue;
		    			}
		    			
		    			Collection<Authorship> authorships = getAuthorshipsFromHTML(personName, url);
		    			store.saveAuthorships(authorships);
		    		}
		    		catch (Exception e) {
						LOG.log(Level.WARNING, "Error parsing " + link.attr("abs:href"), e);		    			
		    		}
		    	}
	        }
		}
    }

    public Collection<Authorship> getAuthorshipsFromHTML(String[] personName, String url) throws IOException, JSONLDProcessingError, JSONException, InterruptedException {
    	Set<Authorship> authorships = new HashSet<Authorship>();
    	int attempts = 0;
    	Document doc = null;
    	while (attempts++ < 10) {
        	try {
        		doc = Jsoup.connect(url).timeout(10000).get();
        		break;
        	}
        	catch (java.net.SocketTimeoutException ex) {
        		LOG.info("Tring " + url + " one more time... " + attempts);
        		Thread.sleep(1000);
        	}
    	}
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").startsWith("http://med.stanford.edu/profiles/") && link.attr("abs:href").contains("pubid=")) {
		    		print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
		    		String pmid = getPMIDFromHTML(link.attr("abs:href"));
				    //person = getJSONFromURI(link.attr("abs:href"));
			    	LOG.info("PMID = " + pmid);
			    	if (pmid != null) {
			    		authorships.add(new Authorship(affiliation, url, personName[1], personName[0], pmid));
			    	}
		    	}
	        }
	    	if (personName != null && authorships.isEmpty()) {
	    		// add a blank one just so we know we've processed this person
	        	authorships.add(new Authorship(affiliation, url, personName[1], personName[0], null));
	    	}
		}
    	return authorships;
    }

    public String getPMIDFromHTML(String url) throws IOException, JSONLDProcessingError, JSONException, InterruptedException {
    	int attempts = 0;
    	Document doc = null;
    	while (attempts++ < 10) {
        	try {
        		doc = Jsoup.connect(url).timeout(10000).get();
        		break;
        	}
        	catch (java.net.SocketTimeoutException ex) {
        		LOG.info("Tring " + url + " one more time... " + attempts);
        		Thread.sleep(1000);
        	}
    	}
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").startsWith("http://www.ncbi.nlm.nih.gov/pubmed/")) {
		    		return link.attr("abs:href").substring("http://www.ncbi.nlm.nih.gov/pubmed/".length());
		    	}
	        }
		}
    	return null;
    }
}
