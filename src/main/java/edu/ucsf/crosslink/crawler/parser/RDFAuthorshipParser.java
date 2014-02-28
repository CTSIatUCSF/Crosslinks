package edu.ucsf.crosslink.crawler.parser;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.jena.JenaRDFParser;
import com.github.jsonldjava.utils.JSONUtils;
import com.google.inject.Inject;
import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.ontology.Ontology;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.NsIterator;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.util.FileManager;

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
    	JsonLdProcessor.registerRDFParser(RDFXML, new JenaRDFParser());		    	
    }
    
    public Researcher getAuthorFromHTML(String url) throws IOException, JSONException, InterruptedException, JsonLdError {
    	Researcher author = null;
    	Document doc = siteReader.getDocument(url);
    	JSONObject person = getPersonOnlyFromURL(doc, url);
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
	    	//  look for a photo
		    for (Element src : doc.select("[src]")) {
	    	   if (src.tagName().equals("img") && src.attr("class").contains("photo") && !src.attr("title").equals("no image")) {
	    		   author.addImageURL(src.attr("abs:src"));
	    	   }
		    }
		}
    	return author;
    }
    
    JSONObject getPersonOnlyFromURL(Document doc, String url) throws IOException, InterruptedException, JSONException, JsonLdError {
    	JSONObject person = null;
    	String uri = getPersonRDFURLFromHTMLURL(doc, url);
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
	
    private String getPersonRDFURLFromHTMLURL(Document doc, String url) throws IOException, InterruptedException {
		Elements links = doc.select("a[href]");	
		
		String uri = null;
	    for (Element link : links) {
	    	if ( link.attr("abs:href").endsWith(".rdf")) {
	    		print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
	    		uri = link.attr("abs:href");
	    	}
        }
	    if (uri == null) {
	    	// worth a try
	    	String[] parts = url.split("/");
	    	uri = url + "/" + parts[parts.length - 1] + ".rdf";
	    }
	    return uri;
    }

    private JSONObject getJSONFromURI(String uri) throws JSONException, IOException, JsonLdError { 
        final JsonLdOptions opts = new JsonLdOptions("");
        opts.format = RDFXML;
        opts.outputForm = "compacted";
        Model model = FileManager.get().loadModel(uri);
        Object outobj = JsonLdProcessor.fromRDF(model, opts); 
        String str = JSONUtils.toString(outobj);
        return new JSONObject(str);	
	}
    
    
    private JSONObject getJSONFromURITest(String uri) throws JSONException, IOException, JsonLdError { 
        final JsonLdOptions opts = new JsonLdOptions("http://stage-profiles.ucsf.edu/profiles200");
        opts.format = RDFXML;
        opts.outputForm = "compacted";
        Model model = FileManager.get().loadModel(uri);
        theModel.add(model);
        theModel.add(FileManager.get().loadModel("http://stage-profiles.ucsf.edu/profiles200/profile/368113/368113.rdf"));
// to remove
        NsIterator ns = model.listNameSpaces();
        while (ns.hasNext()) {
        	System.out.println(ns.next());
        }        
        System.out.println();

        ResIterator ni = theModel.listSubjects();
        while (ni.hasNext()) {
        	System.out.println(ni.next());
        }        
        System.out.println();
        
        StmtIterator si = model.listStatements();
        List<Statement> remove = new ArrayList<Statement>();
        while (si.hasNext()) {
        	Statement s = si.next();
        	if (s.getPredicate().getURI().indexOf("orng") != -1) {
        		remove.add(s);
        	}
        	else if (s.getPredicate().getURI().indexOf("/prns") != -1) {
        		remove.add(s);
        	}
        	else {
        		System.out.println(s);
        	}
        }
        System.out.println();
model.remove(remove);        
Resource foo = ResourceFactory.createResource("http://stage-profiles.ucsf.edu/profiles200/profile/foo");
System.out.println(model.contains(foo, null));
foo = ResourceFactory.createResource("http://stage-profiles.ucsf.edu/profiles200/profile/368698");
System.out.println(model.contains(foo, null));
foo = theModel.createResource("http://stage-profiles.ucsf.edu/profiles200/profile/368113");
        Object outobj = JsonLdProcessor.fromRDF(foo, opts);        
System.out.println(JSONUtils.toPrettyString(outobj));
theModel.removeAll(foo, null, null);

outobj = JsonLdProcessor.fromRDF(theModel, opts);        
System.out.println(JSONUtils.toPrettyString(outobj));

        String str = JSONUtils.toString(outobj);
System.out.println(str.length());        
        return new JSONObject(str);	
	}
    
    static Model theModel;
    
    public static void main(String[] args) {
    	try {
    		
    		String base = "http://www.example.com/ont";
    		OntModel model = ModelFactory.createOntologyModel();
    		Ontology ont = model.createOntology("");
    		ont.addImport(model.createResource("http://test.owl#"));
    		model.write(System.out, "RDF/XML-ABBREV", base);
    		model.write(new FileOutputStream("C:\\Development\\Crosslinks\\workspace\\example3.owl"), "RDF/XML-ABBREV", base);
    		
    		
    		  // Make a TDB-backed dataset
    		  String directory = "C:\\Development\\Crosslinks\\workspace\\rdf" ;
    		  Dataset dataset = TDBFactory.createDataset(directory) ;
    		  dataset.begin(ReadWrite.WRITE) ;
    		  theModel = dataset.getDefaultModel() ;
    		  
    		RDFAuthorshipParser parser = new RDFAuthorshipParser(null);
    		JSONObject person = parser.getJSONFromURITest("http://stage-profiles.ucsf.edu/profiles200/profile/368698/368698.rdf");
    		System.out.println(person.getString("@id"));
  		  	dataset.end();
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    }
}
