package edu.ucsf.crosslink.processor.iterator;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import net.sourceforge.sitemaps.Sitemap;
import net.sourceforge.sitemaps.SitemapParser;
import net.sourceforge.sitemaps.SitemapUrl;

import com.google.inject.Inject;

import edu.ucsf.crosslink.io.SparqlPersistance;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.crosslink.processor.ResearcherProcessor;
import edu.ucsf.crosslink.processor.controller.ProcessorController;
import edu.ucsf.crosslink.processor.controller.TypedOutputStats.OutputType;
import edu.ucsf.crosslink.sitereader.SiteReader;

public class ProfilesSitemapProcessor implements Iterable<ResearcherProcessor> {

	private static final Logger LOG = Logger.getLogger(ProfilesSitemapProcessor.class.getName());

	private static final String ORCID_SECTION = "//orcid.org/";
	
	private Affiliation affiliation = null;
	private SiteReader siteReader = null;
	private SparqlPersistance store = null;
	
	@Inject
	public ProfilesSitemapProcessor(Affiliation affiliation, SiteReader siteReader, SparqlPersistance store) throws Exception {
		this.affiliation = affiliation;
		this.siteReader = siteReader;
		this.store = store;
		store.save(affiliation);	
	}

	public Iterator<ResearcherProcessor> iterator() {
		SitemapParser smp = new SitemapParser();
		try {
			smp.processSitemap(new URL(affiliation.getURI() + "/sitemap.xml"));
		} 
		catch (Exception e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
		}
		Sitemap sitemap = smp.getSitemap();
		
		Collection<SitemapUrl> urls = sitemap.getUrlList();
		List<ResearcherProcessor> retval = new ArrayList<ResearcherProcessor>();

		for (SitemapUrl urlObj : urls) {
			// actually these aren't URI's!!!
			try {
				String url = urlObj.getUrl().toString();
				retval.add(new ProfilesPageProcessor(url));				
			} 
			catch (Exception e) {
				LOG.log(Level.WARNING, e.getMessage(), e);
			}
			
		}
		return retval.iterator();
	}

    private String getRDFURLFromHTMLURL(String url, Document doc) {
    	// standard links
	    for (Element link : doc.select("a[href]")) {
	    	if ( link.attr("abs:href").endsWith(".rdf")) {
	    		return link.attr("abs:href");
	    	}
        }
	    // other links
	    for (Element link : doc.select("link[href]")) {
	    	if ( link.attr("abs:href").endsWith(".rdf")) {
	    		return link.attr("abs:href");
	    	}
        }
	    return null;
    }
	
	private class ProfilesPageProcessor implements ResearcherProcessor {
		
		private String url = null;
		private String researcherURI = null;
		private ProcessorController processorController = null;

		protected ProfilesPageProcessor(String url) {
			this.url = url;
		}

		public String toString() {
			return url + (researcherURI != null ? " <" + researcherURI + ">" : "");
		}
		
		public OutputType processResearcher() throws Exception {
			Document doc = siteReader.getDocument(url);
			String rdfUrl = getRDFURLFromHTMLURL(url, doc);
			if (rdfUrl == null) {
				return OutputType.AVOIDED;
			}
			researcherURI = rdfUrl.substring(0, rdfUrl.lastIndexOf('/'));
			Researcher researcher = new Researcher(researcherURI, affiliation);
			researcher.setHomepage(url);
			if (processorController != null) {
				researcher.crawledBy(processorController);
			}
			// TODO hunt down old code for figuring out a persons name

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
			store.update(researcher);
			return OutputType.PROCESSED;
		}

		public void setCrawler(ProcessorController processorController) {
			this.processorController = processorController;
		}
		
	}

}
