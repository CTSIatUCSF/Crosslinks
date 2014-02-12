package edu.ucsf.crosslink.crawler.parser;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.util.FileManager;

import com.github.jsonldjava.core.JSONLD;
import com.github.jsonldjava.core.JSONLDProcessingError;
import com.github.jsonldjava.core.Options;
import com.github.jsonldjava.impl.JenaRDFParser;
import com.github.jsonldjava.utils.JSONUtils;
import com.google.inject.Inject;

import edu.ucsf.crosslink.crawler.sitereader.SiteReader;
import edu.ucsf.crosslink.model.Researcher;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class RDFAuthorshipParser implements AuthorParser {

	private static final Logger LOG = Logger.getLogger(RDFAuthorshipParser.class.getName());

	private static final String RDFXML = "application/rdf+xml";
	
	private SiteReader siteReader;
	
    private static void print(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }

    private static String trim(String s, int width) {
        if (s.length() > width)
            return s.substring(0, width-1) + ".";
        else
            return s;
    }
    
    @Inject
    public RDFAuthorshipParser(SiteReader siteReader) {
    	this.siteReader = siteReader;
    	JSONLD.registerRDFParser(RDFXML, new JenaRDFParser());		    	
    }
    
    public Researcher getAuthorFromHTML(String url) throws IOException, JSONLDProcessingError, JSONException, InterruptedException {
    	Researcher author = null;
    	JSONObject person = getPersonOnlyFromURL(url);
		if (person != null) {
	    	author = new Researcher(siteReader.getAffiliation(), person, url);
	    	if ( person.optJSONArray("authorInAuthorship") != null) {
	    		JSONArray authorInAuthorship = person.optJSONArray("authorInAuthorship");
		        for (int i = 0; i < (authorInAuthorship).length(); i++) {
		        	try {
			        	JSONObject authorship = getJSONFromURI(authorInAuthorship.getString(i));
			        	authorship = findDataItem(authorship, "linkedInformationResource");
			        	LOG.info(authorship.toString());
			        	JSONObject publication = getJSONFromURI(authorship.getString("linkedInformationResource"));
			        	publication = findDataItem(publication, "pmid");
			        	LOG.info(publication.toString());
			        	if (!publication.optString("pmid").isEmpty()) {
			        		author.addPubMedPublication(publication.getString("pmid"));
			        	}
		        	}
		        	catch (Exception e) {
		        		LOG.log(Level.WARNING, "Parse failure, moving on...", e);
		        	}
		        	
		        }
	    	}
	    	else if (!StringUtils.isEmpty(person.optString("authorInAuthorship"))) {
	    		try {
		 	    	//  not an array for someone like ME
		    		JSONObject authorship = getJSONFromURI(person.optString("authorInAuthorship"));
		        	authorship = findDataItem(authorship, "linkedInformationResource");
		        	LOG.info(authorship.toString());
		        	JSONObject publication = getJSONFromURI(authorship.getString("linkedInformationResource"));
		        	publication = findDataItem(publication, "pmid");
		        	LOG.info(publication.toString());
		        	if (!publication.optString("pmid").isEmpty()) {
		        		author.addPubMedPublication(publication.getString("pmid"));
		        	}
	        	}
	        	catch (Exception e) {
	        		LOG.log(Level.WARNING, "Parse failure, moving on...", e);
	        	}
	    	}
		}
    	return author;
    }
    
    JSONObject getPersonOnlyFromURL(String url) throws IOException, InterruptedException, JSONException, JSONLDProcessingError {
    	JSONObject person = null;
    	String uri = getPersonRDFURLFromHTMLURL(url);
		if (uri != null) {
	    	person = getJSONFromURI(uri);
	    	LOG.info(person.toString());
	    	// ugly but necessary
	    	person = findDataItem(person, "lastName");
		}
		return person;
    }
    
    // sometimes the RDF is in this weird @graph item
    private JSONObject findDataItem(JSONObject container, String hint) throws JSONException {
    	if (container.optJSONObject("@graph") != null) {
    		return container.getJSONObject("@graph");	
    	}
    	else if (container.optJSONArray("@graph") != null) {	    	  
	    	// so ugly
	    	for (int i = 0; i < container.optJSONArray("@graph").length(); i++) {
	    		JSONObject item = container.optJSONArray("@graph").getJSONObject(i);
	    		if (!item.optString(hint).isEmpty()) {
	    			return item;
	    		}
	    	}
    	}
		return container;
    }
	
    private String getPersonRDFURLFromHTMLURL(String url) throws IOException, InterruptedException {
    	Document doc = siteReader.getDocument(url);
		Elements links = doc.select("a[href]");	
		
		String uri = null;
	    for (Element link : links) {
	    	if ( link.attr("abs:href").endsWith(".rdf")) {
	    		print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
	    		uri = link.attr("abs:href");
	    	}
        }
	    if (uri == null && url.indexOf('.') == -1) {
	    	// worth a try
	    	String[] parts = url.split("/");
	    	uri = url + "/" + parts[parts.length - 1] + ".rdf";
	    }
	    return uri;
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
    
    public static void main(String[] args) {
    	try {
    		RDFAuthorshipParser parser = new RDFAuthorshipParser(null);
    		JSONObject person = parser.getJSONFromURI(args[0]);
    		System.out.println(person.getString("@id"));
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    }
}
