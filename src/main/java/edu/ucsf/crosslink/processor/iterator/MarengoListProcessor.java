package edu.ucsf.crosslink.processor.iterator;

import java.net.URI;
import java.util.List;
import java.util.logging.Logger;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hp.cache4guice.Cached;
import com.hp.hpl.jena.query.QuerySolution;

import edu.ucsf.crosslink.io.SparqlPersistance;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.crosslink.processor.BasicResearcherProcessor;
import edu.ucsf.crosslink.processor.ResearcherProcessor;
import edu.ucsf.crosslink.processor.controller.TypedOutputStats.OutputType;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.jena.SparqlQueryClient;

public class MarengoListProcessor extends SparqlProcessor implements R2RConstants {

	private static final Logger LOG = Logger.getLogger(MarengoListProcessor.class.getName());

	private static final String RESEARCHERS_SELECT = "SELECT ?s ?r WHERE { " +
			"?s <http://marengo.info-science.uiowa.edu:2020/resource/vocab/Person_URI> ?r . FILTER (!STRENDS(?r, \"Ext\") %s) }";	
	
	private static final String URI_AVOIDS = "&& !STRSTARTS(?r, \"%s\") ";
	
	private static final String DELETE_PRIOR_PROCESS = "DELETE {<%1$s> <" + R2R_PROCESSED_BY + "> ?c . ?c ?p ?o} WHERE { " +
			"<%1$s> <" + R2R_PROCESSED_BY + "> ?c . ?c <" + RDFS_LABEL + "> \"%2$s\"^^<http://www.w3.org/2001/XMLSchema#string>}";
	
	private static final int LIMIT = 1000;
	private static final int RETRY = 5;
	
	private String[] uriAvoids = null;
	private SparqlPersistance store = null;

	// remove harvester as required item
	@Inject
	public MarengoListProcessor(SparqlPersistance store, @Named("avoids") String avoids) throws Exception {
		super(new SparqlQueryClient("http://marengo.info-science.uiowa.edu:2020/sparql", 60000, 60000), LIMIT, RETRY);
		this.store = store;
		this.uriAvoids = avoids != null ? avoids.split(",") : new String[]{};
	}

	@Override
	protected String getSparqlQuery(int offset, int limit) {
		String avoids = "";
		for (String uriAvoid : uriAvoids) {
			avoids += String.format(URI_AVOIDS, uriAvoid);
		}
		
		return String.format(RESEARCHERS_SELECT, avoids) + 
				(limit > 0 ? String.format(" OFFSET %d LIMIT %d", offset, limit) : "");
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

		public OutputType processResearcher() throws Exception {
			// this should not be necessary
			if (marengoURI.endsWith("Ext") || getResearcherURI().endsWith("Ext") || getResearcherURI().startsWith("http://vivo.ufl.edu")) {
				return OutputType.SKIPPED;
			}
			else {
				researcher = createResearcher();
				researcher.setAffiliation(getAffiliationFor(new URI(getResearcherURI())));
				//researcher.setHarvester(MarengoListCrawler.this);
				
				// TODO lame!
//				if (getCrawler() != null) {
//					DELETE {<http://msu.vivo.ctr-in.org/individual/n1793366517> <http://ucsf.edu/ontology/r2r#crawledBy> ?c .
//						?c ?p ?o}
//						WHERE {
//						<http://msu.vivo.ctr-in.org/individual/n1793366517> <http://ucsf.edu/ontology/r2r#crawledBy> ?c .
//						?c <http://www.w3.org/2000/01/rdf-schema#label> "MarengoList"^^<http://www.w3.org/2001/XMLSchema#string>}				}
//				}
				store.startTransaction();
				store.execute(String.format(DELETE_PRIOR_PROCESS, getResearcherURI(), getCrawler().getName()));
				store.update(researcher);
				store.endTransaction();
				return OutputType.PROCESSED;
			}
		}		
	}
}