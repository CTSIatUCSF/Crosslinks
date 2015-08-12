package edu.ucsf.crosslink.processor.iterator;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.util.FileManager;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.inject.Inject;

import edu.ucsf.crosslink.io.SparqlPersistance;
import edu.ucsf.crosslink.io.http.SiteReader;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.crosslink.processor.ResearcherProcessor;
import edu.ucsf.crosslink.processor.controller.ProcessorController;
import edu.ucsf.crosslink.processor.controller.TypedOutputStats.OutputType;
import edu.ucsf.ctsi.r2r.R2RConstants;

public class ProfilesSitemapProcessor implements Iterable<ResearcherProcessor>, R2RConstants {

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
	}

	public Iterator<ResearcherProcessor> iterator() {
		List<ResearcherProcessor> retval = new ArrayList<ResearcherProcessor>();
		try {
			Document doc = siteReader.getDocument(affiliation.getURI() + "/sitemap.xml");
			for (Element loc : doc.select("loc")) {
				System.out.println(loc.html());
				// actually these aren't URI's!!!
				try {
					String url = loc.html();
					retval.add(new ProfilesPageProcessor(url));				
				} 
				catch (Exception e) {
					LOG.log(Level.WARNING, e.getMessage(), e);
				}				
			}
		} 
		catch (Exception e) {
			LOG.log(Level.WARNING, e.getMessage(), e);
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

			// read the RDF for FOAF information
			Model model = FileManager.get().loadModel(rdfUrl);
			Resource resource = model.createResource(researcherURI);
			Statement label = resource.getProperty(model.createProperty(RDFS_LABEL));
			Statement firstName = resource.getProperty(model.createProperty(FOAF_FIRST_NAME));
			Statement lastName = resource.getProperty(model.createProperty(FOAF_LAST_NAME));
			if (firstName == null) {
				//not a person
				return OutputType.AVOIDED;
			}
			researcher.setLabel(label.getString());
			researcher.setFOAFName(firstName.getString(), lastName.getString());

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
			store.execute(String.format(DELETE_PRIOR_PROCESS_LOG, researcherURI, processorController.getURI()));
			store.update(researcher);
			return OutputType.PROCESSED;
		}

		public void setCrawler(ProcessorController processorController) {
			this.processorController = processorController;
		}
		
	}

}
