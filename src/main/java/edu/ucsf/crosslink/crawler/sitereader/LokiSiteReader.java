package edu.ucsf.crosslink.crawler.sitereader;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.inject.Inject;

import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;

public class LokiSiteReader extends SiteReader implements AuthorParser {

	private static final Logger LOG = Logger.getLogger(LokiSiteReader.class.getName());

	@Inject
	public LokiSiteReader(Affiliation affiliation) {
		super(affiliation);
	}
	
    protected void collectResearcherURLs() throws IOException, InterruptedException  {
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
		    			String url = getSiteRoot() + "/research/browseResearch.jsp?id=" + link.attr("abs:href").split("&id=")[1];
		    			addResearcher(new Researcher(getAffiliation(), url, link.text()));
		    		}
		    		catch (Exception e) {
						LOG.log(Level.WARNING, "Error parsing " + link.attr("abs:href"), e);		    			
		    		}
		    	}
	        }
		}
    }

    public boolean readResearcher(Researcher researcher) throws IOException, InterruptedException {
    	Document doc = getDocument(researcher.getURI() + "&hitCount=500");
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").contains(AuthorParser.PUBMED_SECTION)) {
		    		researcher.addPubMedPublication(link.attr("abs:href"));
		    	}
		    	else if (link.attr("abs:href").contains(AuthorParser.ORCID_SECTION)) { // this way it works with http and https
		    		String orcidId = link.attr("abs:href").split(AuthorParser.ORCID_SECTION)[1];
		    		researcher.setOrcidId(orcidId);
		    	}
	        }
		    
		    for (Element src : doc.select("[src]")) {
	    	   if (src.tagName().equals("img") && src.attr("abs:src").contains("displayPhoto")) {
	    		   researcher.addImageURL(src.attr("abs:src"));
	    	   }
		    }
		}
    	return doc != null;
    }

}
