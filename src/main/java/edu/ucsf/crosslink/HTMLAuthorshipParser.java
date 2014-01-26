package edu.ucsf.crosslink;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
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

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class HTMLAuthorshipParser implements AuthorshipParser {

	private static final Logger LOG = Logger.getLogger(HTMLAuthorshipParser.class.getName());

	private static final String RDFXML = "application/rdf+xml";
	private static final String PUBMED_PREFIX = "http://www.ncbi.nlm.nih.gov/pubmed/";
	
	
    public HTMLAuthorshipParser() {
    	JSONLD.registerRDFParser(RDFXML, new JenaRDFParser());		    	
    }
    
    public Collection<Authorship> getAuthorshipsFromHTML(SiteReader siteReader, String url) throws IOException, JSONLDProcessingError, JSONException, InterruptedException {
    	Set<Authorship> authorships = new HashSet<Authorship>();
    	Document doc = siteReader.getDocument(url);
		if (doc != null) {			
			String nodeId = url.substring(url.lastIndexOf('/'));
			JSONObject person = getJSONFromURI(siteReader.getSiteRoot() + "/profile/" + nodeId +"/" + nodeId + ".rdf");
	    	LOG.info("Person = " + person.toString());
			
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if (link.attr("abs:href").startsWith(PUBMED_PREFIX)) {
		    		String pmid = link.attr("abs:href").substring(PUBMED_PREFIX.length());
		    		LOG.info("PMID = " + pmid);
		        	authorships.add(new Authorship(url, person, pmid));
		    	}
	        }
	    	if (person != null && authorships.isEmpty()) {
	    		// add a blank one just so we know we've processed this person
	        	authorships.add(new Authorship(url, person, null));
	    	}
		}
    	return authorships;
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
