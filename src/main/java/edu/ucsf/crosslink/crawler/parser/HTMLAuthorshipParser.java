package edu.ucsf.crosslink.crawler.parser;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;




import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

import edu.ucsf.crosslink.Crosslinks;
import edu.ucsf.crosslink.crawler.CrawlerModule;
import edu.ucsf.crosslink.crawler.sitereader.SiteReader;
import edu.ucsf.crosslink.io.IOModule;
import edu.ucsf.crosslink.io.ThumbnailGenerator;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.crosslink.processor.ResearcherProcessor;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@Deprecated
public class HTMLAuthorshipParser implements AuthorParser {

	private static final Logger LOG = Logger.getLogger(HTMLAuthorshipParser.class.getName());

	private SiteReader siteReader;
	private RDFAuthorshipParser rdfParser;
	
	@Inject
    public HTMLAuthorshipParser(SiteReader siteReader, JenaHelper jenaPersistance) {
    	this.siteReader = siteReader;
    	this.rdfParser = new RDFAuthorshipParser(siteReader, jenaPersistance); 		    	
    }

	public boolean readResearcher(Researcher researcher) throws IOException, InterruptedException {
    	Document doc = siteReader.getDocument(researcher.getURI());
    	boolean foundResearcherInfo = false;
		if (doc != null && rdfParser.getPersonDataOnly(researcher, doc)) {	
			foundResearcherInfo = true;
	    	Elements links = doc.select("a[href]");	
		    for (Element link : links) {
		    	if (link.attr("abs:href").contains(ResearcherProcessor.PUBMED_SECTION)) { // this way it works with http and https
		    		researcher.addPublication(link.attr("abs:href"));
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
		// figure out some way to call into rdfauthorship parser if we think it might yeild more info
    	return foundResearcherInfo;
    }
	
	public static void main(String[] args) {
		try {
			Properties prop = new Properties();
			prop.load(HTMLAuthorshipParser.class.getResourceAsStream(Crosslinks.PROPERTIES_FILE));	
			prop.load(HTMLAuthorshipParser.class.getResourceAsStream("/affiliations/UCSF.properties"));
			prop.setProperty("rdfBaseDir", "C:\\Development\\R2R\\workspace\\Crosslinks\\testModel");
			Injector injector = Guice.createInjector(new IOModule(prop), new CrawlerModule(prop));

			HTMLAuthorshipParser parser = injector.getInstance(HTMLAuthorshipParser.class);
			Researcher researcher = new Researcher("http://profiles.ucsf.edu/eric.meeks");
			parser.readResearcher(researcher);
			injector.getInstance(ThumbnailGenerator.class).generateThumbnail(researcher);		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
