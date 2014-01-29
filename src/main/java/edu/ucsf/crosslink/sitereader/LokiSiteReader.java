package edu.ucsf.crosslink.sitereader;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.github.jsonldjava.core.JSONLDProcessingError;

import edu.ucsf.crosslink.author.Author;
import edu.ucsf.crosslink.author.AuthorParser;
import edu.ucsf.crosslink.author.AuthorPersistance;

public class LokiSiteReader extends SiteReader {

	private static final Logger LOG = Logger.getLogger(LokiSiteReader.class.getName());

	public LokiSiteReader(String affiliation, String siteRoot) {
		super(affiliation, siteRoot);
	}

    public void readSite(AuthorPersistance store, AuthorParser parser) throws Exception {
    	Document doc = getDocument(getSiteRoot() + "/research/browseResearch.jsp");
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").startsWith(getSiteRoot() + "/research/browseResearch.jsp?browse=") && link.attr("abs:href").length() == "https://www.icts.uiowa.edu/Loki/research/browseResearch.jsp?browse=".length() + 1) {
		    		print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
		    		parsePartialSiteMap(link.attr("abs:href"), store);
		    	}
	        }
		}
    }

    private void parsePartialSiteMap(String sitemapUrl, AuthorPersistance store) throws Exception {
    	Document doc = getDocument(sitemapUrl);
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").startsWith(getSiteRoot() + "/research/browseResearch.jsp?") && link.attr("abs:href").contains("id=")) {
		    		try {
			    		print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
			    		String[] personName = link.text().split(", ");
		    			LOG.info(personName[0] + ":" + personName[1]);
		    			String url = getSiteRoot() + "/research/browseResearch.jsp?id=" + link.attr("abs:href").split("&id=")[1];
		    			// skip it if we already have it
		    			if (store.containsAuthor(url)) {
			    			LOG.info("Skipping " + personName[0] + ":" + personName[1] + " :" + url);
		    				continue;
		    			}
		    			LOG.info(url);

		    			store.saveAuthor(getAuthorFromHTML(personName, url));
		    		}
		    		catch (Exception e) {
						LOG.log(Level.WARNING, "Error parsing " + link.attr("abs:href"), e);		    			
		    		}
		    	}
	        }
		}
    }

    public Author getAuthorFromHTML(String[] personName, String url) throws IOException, JSONLDProcessingError, JSONException, InterruptedException {
    	Author author = new Author(getAffiliation(), personName[0], personName[1], null, url);
    	Document doc = getDocument(url + "&hitCount=500");
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").startsWith("http://www.ncbi.nlm.nih.gov/pubmed/")) {
		    		author.addPubMedPublication(link.attr("abs:href").substring("http://www.ncbi.nlm.nih.gov/pubmed/".length()));
		    	}
	        }
		}
    	return author;
    }

}
