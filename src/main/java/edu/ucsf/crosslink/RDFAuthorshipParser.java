package edu.ucsf.crosslink;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sourceforge.sitemaps.Sitemap;
import net.sourceforge.sitemaps.SitemapParser;
import net.sourceforge.sitemaps.SitemapUrl;

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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class RDFAuthorshipParser implements AuthorshipParser {

	private static final Logger LOG = Logger.getLogger(RDFAuthorshipParser.class.getName());

	private static final String RDFXML = "application/rdf+xml";
	
    private static void print(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }

    private static String trim(String s, int width) {
        if (s.length() > width)
            return s.substring(0, width-1) + ".";
        else
            return s;
    }
    
    public RDFAuthorshipParser() {
    	JSONLD.registerRDFParser(RDFXML, new JenaRDFParser());		    	
    }
    
    public void parseSiteMap(String sitemapUrl, AuthorshipPersistance store) throws Exception {
		SitemapParser parser = new SitemapParser();
		parser.processSitemap(new URL(sitemapUrl));
		Sitemap sitemap = parser.getSitemap();
		
		Collection<SitemapUrl> urls = sitemap.getUrlList();

		for (SitemapUrl url : urls) {
			LOG.info(url.toString());
			if (store.containsAuthor(url.getUrl().toString())) {
				continue;
			}
			Collection<Authorship> authorships = getAuthorshipsFromHTML(url.getUrl().toString());
			for (Authorship authorship : authorships) {
				LOG.info("Authorship -- " + authorship.toString());
				store.saveAuthorship(authorship);
			}
			store.flush();
		}
    }
    
    public Collection<Authorship> getAuthorshipsFromHTML(String url) throws IOException, JSONLDProcessingError, JSONException, InterruptedException {
    	Set<Authorship> authorships = new HashSet<Authorship>();
    	int attempts = 0;
    	String uri = null;
    	while (attempts++ < 10) {
        	try {
        		uri = getPersonRDFURLFromHTMLURL(url);
        		break;
        	}
        	catch (java.net.SocketTimeoutException ex) {
        		LOG.info("Trying " + url + " one more time... " + attempts);
        		Thread.sleep(1000);
        	}
    	}
		if (uri != null) {
	    	JSONObject person = getJSONFromURI(uri);
	    	LOG.info(person.toString());
	    	LOG.info(person.toString());
	        
	    	if ( person.optJSONArray("authorInAuthorship") != null) {
	    		JSONArray authorInAuthorship = person.optJSONArray("authorInAuthorship");
		        for (int i = 0; i < (authorInAuthorship).length(); i++) {
		        	try {
			        	JSONObject authorship = getJSONFromURI(authorInAuthorship.getString(i));
			        	LOG.info(authorship.toString());
			        	JSONObject publication = getJSONFromURI(authorship.getString("linkedInformationResource"));
			        	LOG.info(publication.toString());
			        	LOG.info(publication.getString("pmid"));
			        	
			        	authorships.add(new Authorship(url, person, publication.getString("pmid")));
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
		        	LOG.info(authorship.toString());
		        	JSONObject publication = getJSONFromURI(authorship.getString("linkedInformationResource"));

		        	authorships.add(new Authorship(url, person, publication.getString("pmid")));
	        	}
	        	catch (Exception e) {
	        		LOG.log(Level.WARNING, "Parse failure, moving on...", e);
	        	}
	    	}
	    	if (authorships.isEmpty()) {
	    		// add a blank one just so we know we've processed this person
	        	authorships.add(new Authorship(url, person, null));
	    	}
		}
    	return authorships;
    }
	
    private String getPersonRDFURLFromHTMLURL(String url) throws IOException {
    	LOG.log(Level.INFO, "getRDF :" + url );
		Document doc = Jsoup.connect(url).timeout(10000).get();
		Elements links = doc.select("a[href]");	
		
		String uri = null;
	    for (Element link : links) {
	    	if ( link.attr("abs:href").endsWith(".rdf")) {
	    		print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
	    		uri = link.attr("abs:href");
	    	}
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
}
