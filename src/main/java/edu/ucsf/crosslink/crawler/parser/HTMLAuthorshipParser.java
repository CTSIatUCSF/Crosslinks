package edu.ucsf.crosslink.crawler.parser;

import java.io.IOException;
import java.util.logging.Logger;



import org.json.JSONException;
import org.json.JSONObject;

import com.github.jsonldjava.core.JsonLdError;
import com.google.inject.Inject;

import edu.ucsf.crosslink.crawler.sitereader.SiteReader;
import edu.ucsf.crosslink.io.JenaPersistance;
import edu.ucsf.crosslink.model.Researcher;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class HTMLAuthorshipParser implements AuthorParser {

	private static final Logger LOG = Logger.getLogger(HTMLAuthorshipParser.class.getName());

	private SiteReader siteReader;
	private RDFAuthorshipParser rdfParser;
	
	@Inject
    public HTMLAuthorshipParser(SiteReader siteReader, JenaPersistance jenaPersistance) {
    	this.siteReader = siteReader;
    	this.rdfParser = new RDFAuthorshipParser(siteReader, jenaPersistance); 		    	
    }

	public Researcher getAuthorFromHTML(String url) throws IOException, JSONException, InterruptedException, JsonLdError {
    	Researcher author = null;
    	Document doc = siteReader.getDocument(url);
		if (doc != null) {			
	    	JSONObject person = rdfParser.getPersonOnlyFromURL(doc, url);
		    if (person == null) {
		    	return null;
		    }

	    	Elements links = doc.select("a[href]");	
			author = new Researcher(siteReader.getAffiliation(), person, url);
		    for (Element link : links) {
		    	if (link.attr("abs:href").contains(PUBMED_SECTION)) { // this way it works with http and https
		    		author.addPubMedPublication(link.attr("abs:href"));
		    	}
		    	else if (link.attr("abs:href").contains(ORCID_SECTION)) { // this way it works with http and https
		    		String orcidId = link.attr("abs:href").split(ORCID_SECTION)[1];
		    		LOG.info("OrcidId = " + orcidId);
		    		author.setOrcidId(orcidId);
		    	}
	        }
	    	//  try a few more tricks to look for a photo, this particular method works with VIVO
		    for (Element src : doc.select("[src]")) {
	    	   if (src.tagName().equals("img") && src.className().equals("individual-photo") && !src.attr("abs:src").contains("unknown") ) { 
	    		   author.addImageURL(src.attr("abs:src"));
	    	   }
		    }
		    
		}
    	return author;
    }
}
