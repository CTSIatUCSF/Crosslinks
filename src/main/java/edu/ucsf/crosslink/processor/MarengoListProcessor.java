package edu.ucsf.crosslink.processor;

import java.net.URI;
import java.util.logging.Logger;

import com.google.inject.Inject;
import com.hp.cache4guice.Cached;
import com.hp.hpl.jena.query.QuerySolution;

import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.jena.SparqlClient;

public class MarengoListProcessor extends SparqlProcessor {

	private static final Logger LOG = Logger.getLogger(MarengoListProcessor.class.getName());

	private static final String RESEARCHERS_SELECT = "SELECT ?s ?r WHERE { " +
			"?s <http://marengo.info-science.uiowa.edu:2020/resource/vocab/Person_URI> ?r . FILTER (!STRENDS(?r, \"Ext\")) }";	
	
	private static final int LIMIT = 1000;
	
	private CrosslinkPersistance store = null;

	// remove harvester as required item
	@Inject
	public MarengoListProcessor(CrosslinkPersistance store) throws Exception {
		super(new SparqlClient("http://marengo.info-science.uiowa.edu:2020/sparql"), LIMIT);
		this.store = store;
	}

	@Override
	protected String getSparqlQuery() {
		return RESEARCHERS_SELECT;
	}

	private Affiliation getAffiliationFor(URI uri) throws Exception {
		Affiliation affiliation = store.findAffiliationFor(uri.toString());
		return affiliation != null ? affiliation : getAffiliation(uri.getScheme(), uri.getHost());		
	}
	
	@Cached
	public Affiliation getAffiliation(String scheme, String host) throws Exception {
		Affiliation affiliation = new Affiliation(host, scheme + "://" + host, "0,0");
		// this really needs to look for an existing one first, but for now this is OK
		store.save(affiliation);
		LOG.info("Saved " + affiliation);
		return affiliation;
	}
	
	@Override
	protected ResearcherProcessor getResearcherProcessor(QuerySolution qs) {
		return new MarengoListResearcherProcessor(qs.getResource("?s").getURI(), qs.getLiteral("?r").getString());
	}

	private class MarengoListResearcherProcessor extends BasicResearcherProcessor {
		
		private String marengoURI = null; 
		private Researcher researcher = null;
		
		public String toString() {
			return super.toString() + (researcher != null ? " from " + researcher.getAffiliation() : "");
		}
		
		private MarengoListResearcherProcessor(String marengoURI, String researcherURI) {
			super(researcherURI);
			this.marengoURI = marengoURI;
		}

		public Action processResearcher() throws Exception {
			if (marengoURI.endsWith("Ext") || getResearcherURI().endsWith("Ext")) {
				return Action.AVOIDED;
			}
			else {
				researcher = createResearcher();
				researcher.setAffiliation(getAffiliationFor(new URI(getResearcherURI())));
				//researcher.setHarvester(MarengoListCrawler.this);
				store.update(researcher);				
				return Action.PROCESSED;
			}
		}		
	}
}
