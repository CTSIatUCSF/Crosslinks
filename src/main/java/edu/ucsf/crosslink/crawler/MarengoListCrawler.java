package edu.ucsf.crosslink.crawler;

import java.net.URI;
import java.util.logging.Logger;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hp.cache4guice.Cached;
import com.hp.hpl.jena.query.QuerySolution;

import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.jena.SparqlClient;

public class MarengoListCrawler extends SparqlCrawler {

	private static final Logger LOG = Logger.getLogger(MarengoListCrawler.class.getName());

	private static final String RESEARCHERS_SELECT = "SELECT ?s ?r WHERE { " +
			"?s <http://marengo.info-science.uiowa.edu:2020/resource/vocab/Person_URI> ?r . FILTER (!STRENDS(?r, \"Ext\")) }";	
	
	private static final int LIMIT = 1000;
	
	private CrosslinkPersistance store = null;

	// remove harvester as required item
	@Inject
	public MarengoListCrawler(@Named("Name") String name, Mode crawlingMode, CrosslinkPersistance store,  
			@Named("executorThreadCount") Integer threadCount) throws Exception {
		super(name, crawlingMode, store, 
				new SparqlClient("http://marengo.info-science.uiowa.edu:2020/sparql"), threadCount, LIMIT);
		this.store = store;
	}

	@Override
	protected String getSparqlQuery() {
		return RESEARCHERS_SELECT;
	}

	@Override
	protected String getResearcherURI(QuerySolution qs) {
		return qs.getLiteral("?r").getString();
	}

	@Override
	protected boolean avoid(QuerySolution qs) {
		String marengoURI = qs.getResource("?s").getURI();
		return marengoURI.endsWith("Ext");
	}

	@Override
	protected boolean skip(QuerySolution qs) {
		return false;
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
	
	protected QueuedRunnable getResearcherProcessor(String researcherURI) {
		return new SaveResearcherURI(researcherURI);
	}

	private class SaveResearcherURI extends QueuedRunnable {
		
		private SaveResearcherURI(String researcherURI) {
			super(researcherURI);
		}
		
		@Override
		protected Researcher timedRun(String researcherURI) throws Exception {
			Researcher researcher = new Researcher(researcherURI, getAffiliationFor(new URI(researcherURI)));
			researcher.setHarvester(MarengoListCrawler.this);
			update(researcher);
			return researcher;
		}
	}
}
