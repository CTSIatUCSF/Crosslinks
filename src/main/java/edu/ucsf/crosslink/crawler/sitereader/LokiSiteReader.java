package edu.ucsf.crosslink.crawler.sitereader;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.json.JSONException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.model.Researcher;

public class LokiSiteReader extends SiteReader implements AuthorParser {

	private static final Logger LOG = Logger.getLogger(LokiSiteReader.class.getName());

	@Inject
	public LokiSiteReader(@Named("Affiliation") String affiliation, @Named("BaseURL") String siteRoot) {
		super(affiliation, siteRoot);
	}
	
    protected void collectAuthorURLS() throws IOException, InterruptedException  {
    	Document doc = getDocument(getSiteRoot() + "/research/browseResearch.jsp");
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").startsWith(getSiteRoot() + "/research/browseResearch.jsp?browse=") && link.attr("abs:href").length() == "https://www.icts.uiowa.edu/Loki/research/browseResearch.jsp?browse=".length() + 1) {
		    		parsePartialSiteMap(link.attr("abs:href"));
		    	}
	        }
		}
    }

    private void parsePartialSiteMap(String sitemapUrl) throws IOException, InterruptedException {
    	Document doc = getDocument(sitemapUrl);
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").startsWith(getSiteRoot() + "/research/browseResearch.jsp?") && link.attr("abs:href").contains("id=")) {
		    		try {
			    		String[] personName = link.text().split(", ");
		    			String url = getSiteRoot() + "/research/browseResearch.jsp?id=" + link.attr("abs:href").split("&id=")[1];
		    			addAuthor(new Researcher(getAffiliation(), personName[0], personName[1], null, url, null, null));
		    		}
		    		catch (Exception e) {
						LOG.log(Level.WARNING, "Error parsing " + link.attr("abs:href"), e);		    			
		    		}
		    	}
	        }
		}
    }

    public Researcher getAuthorFromHTML(String url) throws IOException, JSONException, InterruptedException {
    	Researcher author = new Researcher(getAffiliation(), null, null, null, url, null, null);
    	Document doc = getDocument(url + "&hitCount=500");
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").contains(AuthorParser.PUBMED_SECTION)) {
		    		author.addPubMedPublication(link.attr("abs:href"));
		    	}
		    	else if (link.attr("abs:href").contains(AuthorParser.ORCID_SECTION)) { // this way it works with http and https
		    		String orcidId = link.attr("abs:href").split(AuthorParser.ORCID_SECTION)[1];
		    		author.setOrcidId(orcidId);
		    	}
	        }
		    
		    for (Element src : doc.select("[src]")) {
	    	   if (src.tagName().equals("img") && src.attr("abs:src").contains("displayPhoto")) {
	    		   author.addImageURL(src.attr("abs:src"));
	    	   }
		    }
		}
    	return author;
    }

}
