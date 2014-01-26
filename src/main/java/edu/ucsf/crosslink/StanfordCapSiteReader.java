package edu.ucsf.crosslink;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.github.jsonldjava.core.JSONLDProcessingError;

public class StanfordCapSiteReader extends SiteReader {

	private static final Logger LOG = Logger.getLogger(StanfordCapSiteReader.class.getName());

	public StanfordCapSiteReader(String affiliation, String siteRoot) {
		super(affiliation, siteRoot);
	}
	
    public void readSite(AuthorshipPersistance store, AuthorshipParser parser) throws Exception {
    	Document doc = getDocument(getSiteRoot() + "/frdActionServlet?choiceId=showFacByName&tab=all");
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").startsWith(getSiteRoot() + "/")) {
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
    	Document doc = getDocument(url);
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").startsWith(getSiteRoot() + "/") && link.attr("abs:href").contains("pubid=")) {
		    		print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
		    		String pmid = getPMIDFromHTML(link.attr("abs:href"));
				    //person = getJSONFromURI(link.attr("abs:href"));
			    	LOG.info("PMID = " + pmid);
			    	if (pmid != null) {
			    		authorships.add(new Authorship(getAffiliation(), url, personName[0], personName[1], null, pmid));
			    	}
		    	}
	        }
	    	if (personName != null && authorships.isEmpty()) {
	    		// add a blank one just so we know we've processed this person
	        	authorships.add(new Authorship(getAffiliation(), url, personName[0], personName[1], null, null));
	    	}
		}
    	return authorships;
    }

    public String getPMIDFromHTML(String url) throws IOException, JSONLDProcessingError, JSONException, InterruptedException {
    	Document doc = getDocument(url);
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
