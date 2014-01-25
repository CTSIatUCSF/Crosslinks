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

public class LokiSiteReader extends HTMLReader implements SiteReader {

	private static final Logger LOG = Logger.getLogger(LokiSiteReader.class.getName());

	private String affiliation;
	private String siteRoot;

    public void readSite(String affiliation, String siteRoot, AuthorshipPersistance store, AuthorshipParser parser) throws Exception {
    	this.affiliation = affiliation;
    	this.siteRoot = siteRoot;
    	Document doc = getDocument(siteRoot + "/research/browseResearch.jsp");
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").startsWith(siteRoot + "/research/browseResearch.jsp?browse=") && link.attr("abs:href").length() == "https://www.icts.uiowa.edu/Loki/research/browseResearch.jsp?browse=".length() + 1) {
		    		print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
		    		parsePartialSiteMap(link.attr("abs:href"), store);
		    	}
	        }
		}
    }

    private void parsePartialSiteMap(String sitemapUrl, AuthorshipPersistance store) throws Exception {
    	Document doc = getDocument(sitemapUrl);
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").startsWith(siteRoot + "/research/browseResearch.jsp?") && link.attr("abs:href").contains("id=")) {
		    		try {
			    		print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
			    		String[] personName = link.text().split(", ");
		    			LOG.info(personName[0] + ":" + personName[1]);
		    			String url = siteRoot + "/research/browseResearch.jsp?id=" + link.attr("abs:href").split("&id=")[1];
		    			// skip it if we already have it
		    			if (store.containsAuthor(url)) {
			    			LOG.info("Skipping " + personName[0] + ":" + personName[1] + " :" + url);
		    				continue;
		    			}
		    			LOG.info(url);

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
    	Document doc = getDocument(url + "&hitCount=500");
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").startsWith("http://www.ncbi.nlm.nih.gov/pubmed/")) {
		    		authorships.add(new Authorship(affiliation, url, personName[0], personName[1], null, link.attr("abs:href").substring("http://www.ncbi.nlm.nih.gov/pubmed/".length())));
		    	}
	        }
	    	if (personName != null && authorships.isEmpty()) {
	    		// add a blank one just so we know we've processed this person
	        	authorships.add(new Authorship(affiliation, url, personName[0], personName[1], null, null));
	    	}
		}
    	return authorships;
    }

}
