package edu.ucsf.crosslink.crawler.parser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

import org.json.JSONException;

import com.github.jsonldjava.core.JsonLdError;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

import edu.ucsf.crosslink.Crosslinks;
import edu.ucsf.crosslink.crawler.AffiliationCrawlerModule;
import edu.ucsf.crosslink.crawler.sitereader.SiteReader;
import edu.ucsf.crosslink.io.IOModule;
import edu.ucsf.crosslink.io.JenaPersistance;
import edu.ucsf.crosslink.io.ThumbnailGenerator;
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

	public Researcher getAuthorFromHTML(String htmlUrl) throws IOException, JSONException, InterruptedException, JsonLdError {
    	Researcher researcher = null;
    	Document doc = siteReader.getDocument(htmlUrl);
		if (doc != null) {			
			researcher = rdfParser.getPersonOnlyFromURL(htmlUrl, doc);
		    if (researcher == null) {
		    	return null;
		    }

	    	Elements links = doc.select("a[href]");	
		    for (Element link : links) {
		    	if (link.attr("abs:href").contains(PUBMED_SECTION)) { // this way it works with http and https
		    		researcher.addPubMedPublication(link.attr("abs:href"));
		    	}
		    	else if (link.attr("abs:href").contains(ORCID_SECTION)) { // this way it works with http and https
		    		String orcidId = link.attr("abs:href").split(ORCID_SECTION)[1];
		    		LOG.info("OrcidId = " + orcidId);
		    		researcher.setOrcidId(orcidId);
		    	}
	        }
	    	//  try a few more tricks to look for a photo, this particular method works with VIVO
		    for (Element src : doc.select("[src]")) {
	    	   if (src.tagName().equals("img") && src.className().equals("individual-photo") && !src.attr("abs:src").contains("unknown") ) { 
	    		   researcher.addImageURL(src.attr("abs:src"));
	    	   }
		    }		    
		}
    	return researcher;
    }
	
	public static void main(String[] args) {
		try {
			Properties prop = new Properties();
			prop.load(HTMLAuthorshipParser.class.getResourceAsStream(Crosslinks.PROPERTIES_FILE));	
			prop.load(HTMLAuthorshipParser.class.getResourceAsStream("/affiliations/UCSF.properties"));
			prop.setProperty("rdfBaseDir", "C:\\Development\\R2R\\workspace\\Crosslinks\\testModel");
			Injector injector = Guice.createInjector(new IOModule(prop), new AffiliationCrawlerModule(prop));

			HTMLAuthorshipParser parser = injector.getInstance(HTMLAuthorshipParser.class);
			Researcher reseacher = parser.getAuthorFromHTML("http://profiles.ucsf.edu/eric.meeks");
			injector.getInstance(ThumbnailGenerator.class).generateThumbnail(reseacher);		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
