package edu.ucsf.crosslink.author;

import java.io.IOException;
import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import com.github.jsonldjava.core.JSONLDProcessingError;

import edu.ucsf.crosslink.sitereader.SiteReader;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class HTMLAuthorshipParser implements AuthorParser {

	private static final Logger LOG = Logger.getLogger(HTMLAuthorshipParser.class.getName());

	private SiteReader siteReader;
	private RDFAuthorshipParser rdfParser;
	
    public HTMLAuthorshipParser(SiteReader siteReader) {
    	this.siteReader = siteReader;
    	this.rdfParser = new RDFAuthorshipParser(siteReader); 		    	
    }
    
    public void getMoreInformation(Author author) throws JSONException, IOException, InterruptedException, JSONLDProcessingError {
    	Document doc = siteReader.getDocument(author.getURL());
		if (doc != null) {			
	    	JSONObject person = rdfParser.getPersonOnlyFromURL(author.getURL());
		    if (person != null) {
		    	author.setPersonInfo(person);
		    	Elements links = doc.select("a[href]");	
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
		}    	
    }
    
    public Author getAuthorFromHTML(String url) throws IOException, JSONLDProcessingError, JSONException, InterruptedException {
    	Author author = null;
    	Document doc = siteReader.getDocument(url);
		if (doc != null) {			
	    	JSONObject person = rdfParser.getPersonOnlyFromURL(url);
		    if (person == null) {
		    	return null;
		    }

	    	Elements links = doc.select("a[href]");	
			author = new Author(siteReader.getAffiliation(), person, url);
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
