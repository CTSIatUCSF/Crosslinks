package edu.ucsf.crosslink.author;

import java.io.IOException;
import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.util.FileManager;

import com.github.jsonldjava.core.JSONLD;
import com.github.jsonldjava.core.JSONLDProcessingError;
import com.github.jsonldjava.core.Options;
import com.github.jsonldjava.impl.JenaRDFParser;
import com.github.jsonldjava.utils.JSONUtils;

import edu.ucsf.crosslink.sitereader.SiteReader;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class HTMLAuthorshipParser implements AuthorParser {

	private static final Logger LOG = Logger.getLogger(HTMLAuthorshipParser.class.getName());

	private static final String RDFXML = "application/rdf+xml";
	private static final String PUBMED_SECTION = "//www.ncbi.nlm.nih.gov/pubmed/";
	
	
    public HTMLAuthorshipParser() {
    	JSONLD.registerRDFParser(RDFXML, new JenaRDFParser());		    	
    }
    
    public Author getAuthorFromHTML(SiteReader siteReader, String url) throws IOException, JSONLDProcessingError, JSONException, InterruptedException {
    	Author author = null;
    	Document doc = siteReader.getDocument(url);
		if (doc != null) {			
			String nodeId = url.substring(url.lastIndexOf('/'));
			JSONObject person = getJSONFromURI(siteReader.getSiteRoot() + "/profile/" + nodeId +"/" + nodeId + ".rdf");
	    	LOG.info("Person = " + person.toString());
			
			Elements links = doc.select("a[href]");	
			
			author = new Author(siteReader.getAffiliation(), person, url);
		    for (Element link : links) {
		    	if (link.attr("abs:href").contains(PUBMED_SECTION)) { // this way it works with http and https
		    		String pmid = link.attr("abs:href").split(PUBMED_SECTION)[1];
		    		LOG.info("PMID = " + pmid);
		    		author.addPubMedPublication(pmid);
		    	}
	        }
		}
    	return author;
    }
	
    private JSONObject getJSONFromURI(String uri) throws JSONLDProcessingError, JSONException { 
        final Options opts = new Options("");
        opts.format = RDFXML;
        opts.outputForm = "compacted";  // [compacted|expanded|flattened]
        Model model = FileManager.get().loadModel(uri);
        Object obj = JSONLD.fromRDF(model, opts);        
        // simplify
        obj = JSONLD.simplify(obj, opts);
        String str = JSONUtils.toString(obj);
        return new JSONObject(str);	
	}
}
