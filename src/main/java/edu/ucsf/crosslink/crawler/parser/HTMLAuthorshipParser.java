package edu.ucsf.crosslink.crawler.parser;

import java.io.IOException;
import java.util.logging.Logger;


import org.json.JSONException;
import org.json.JSONObject;

import com.github.jsonldjava.core.JSONLDProcessingError;
import com.google.inject.Inject;

import edu.ucsf.crosslink.crawler.sitereader.SiteReader;
import edu.ucsf.crosslink.model.Researcher;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class HTMLAuthorshipParser implements AuthorParser {

	private static final Logger LOG = Logger.getLogger(HTMLAuthorshipParser.class.getName());

	private SiteReader siteReader;
	private RDFAuthorshipParser rdfParser;
	
	@Inject
    public HTMLAuthorshipParser(SiteReader siteReader) {
    	this.siteReader = siteReader;
    	this.rdfParser = new RDFAuthorshipParser(siteReader); 		    	
    }

	public Researcher getAuthorFromHTML(String url) throws IOException, JSONLDProcessingError, JSONException, InterruptedException {
    	Researcher author = null;
    	Document doc = siteReader.getDocument(url);
		if (doc != null) {			
	    	JSONObject person = rdfParser.getPersonOnlyFromURL(url);
		    if (person == null) {
		    	return null;
		    }

	    	Elements links = doc.select("a[href]");	
			author = new Researcher(siteReader.getAffiliation(), person, url);
		    for (Element link : links) {
		    	if (link.attr("abs:href").contains(PUBMED_SECTION)) { // this way it works with http and https
		    		String pmid = link.attr("abs:href").split(PUBMED_SECTION)[1];
		    		LOG.info("PMID = " + pmid);
		    		author.addPubMedPublication(pmid);
		    	}
		    	else if (link.attr("abs:href").contains(ORCID_SECTION)) { // this way it works with http and https
		    		String orcidId = link.attr("abs:href").split(ORCID_SECTION)[1];
		    		LOG.info("OrcidId = " + orcidId);
		    		author.setOrcidId(orcidId);
		    	}
	        }
		}
    	return author;
    }
}
