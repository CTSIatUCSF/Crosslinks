package edu.ucsf.crosslink.processor;

import java.util.Arrays;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hp.hpl.jena.query.QuerySolution;

import edu.ucsf.crosslink.crawler.Crawler;
import edu.ucsf.crosslink.crawler.TypedOutputStats.OutputType;
import edu.ucsf.crosslink.io.SparqlPersistance;
import edu.ucsf.crosslink.model.Affiliated;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.R2ROntology;
import edu.ucsf.ctsi.r2r.jena.SparqlQueryClient;

public class DeleteProcessor extends SparqlProcessor implements Affiliated, R2RConstants {

	private static final Logger LOG = Logger.getLogger(DeleteProcessor.class.getName());

	private static final String RESEARCHERS_SELECT_SKIP = "SELECT ?r ?ts WHERE { " +
			"?r <" + R2R_HAS_AFFILIATION + "> <%1$s> . OPTIONAL {?r <" + R2R_CRAWLED_BY + "> ?c . ?c <" + RDFS_LABEL + 
			"\"%2$s\" . ?c <" + R2R_CRAWLED_ON + 
			"> ?ts} FILTER (!bound(?ts) || ?ts < \"%3$s\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)} ORDER BY (?ts)";	

	private static final String RESEARCHERS_SELECT_NO_SKIP = "SELECT ?r WHERE { " +
			"?r <" + R2R_HAS_AFFILIATION + "> <%1$s>}";	
		
	private static final String DELETE_RESEARCHER = "DELETE WHERE {<%1$s> ?p ?o }; DELETE WHERE {?s ?p <%1$s>}";	
	private static final String DELETE_RESEARCHER_DERIVED = "DELETE WHERE { GRAPH <" + R2R_DERIVED_GRAPH
			+ "> {<%1$s> ?p ?o }}; DELETE WHERE { GRAPH <" + R2R_DERIVED_GRAPH + "> {?s ?p <%1$s>}}";	

	private Integer daysConsideredOld;

	private Affiliation affiliation = null;
	private SparqlPersistance store = null;
	private Crawler crawler = null;
	private AtomicInteger count = new AtomicInteger();
	
	// remove harvester as required item
	@Inject
	public DeleteProcessor(@Named("Name") String name, @Named("BaseURL") String baseURL, @Named("Location") String location,
			SparqlPersistance store, @Named("r2r.fusekiUrl") String sparqlQuery,
			@Named("daysConsideredOld") Integer daysConsideredOld) throws Exception {
		super(new SparqlQueryClient(sparqlQuery + "/query"), 0);
		this.affiliation = new Affiliation(name, baseURL, location);
		this.store = store;
		this.daysConsideredOld = daysConsideredOld;
		store.save(affiliation);	
	}
	
	@Inject
	public void setCrawler(Crawler crawler) {
		this.crawler = crawler;
	}
	
	public Affiliation getAffiliation() {
		return affiliation;
	}
	
	@Override
	protected String getSparqlQuery(int offset, int limit) {
		if (crawler != null && crawler.allowSkip()) {
			Calendar threshold = Calendar.getInstance();
			threshold.setTimeInMillis(new DateTime().minusDays(daysConsideredOld).getMillis());
			return String.format(RESEARCHERS_SELECT_SKIP, getAffiliation().getURI(), crawler.getName(),
					R2ROntology.createDefaultModel().createTypedLiteral(threshold).getString());
		}
		else {
			return String.format(RESEARCHERS_SELECT_NO_SKIP, getAffiliation().getURI()) + 
					(limit > 0 ? String.format(" OFFSET %d LIMIT %d", offset, limit) : "");
		}
	}
	
	@Override
	protected ResearcherProcessor getResearcherProcessor(QuerySolution qs) {
		return new DeleteResearcherProcessor(qs.getResource("?r").getURI());
	}
	
	@Override
	protected void shuttingDown() {
		try {
			store.endTransaction();
		} 
		catch (Exception e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	private class DeleteResearcherProcessor extends BasicResearcherProcessor {
		
		private String message = null;
		
		private DeleteResearcherProcessor(String researcherURI) {
			super(researcherURI);
		}

		public String toString() {
			return message != null ? message : super.toString();
		}
		
		private void deleteResearcher() throws Exception {
			// should probably have delete be a function in store, but this is OK for now
			if (count.get() == 0) {
				store.startTransaction();
			}
			store.execute(Arrays.asList(String.format(DELETE_RESEARCHER, getResearcherURI())));
			store.execute(Arrays.asList(String.format(DELETE_RESEARCHER_DERIVED, getResearcherURI())));
			if (count.incrementAndGet() % 100 == 0) {
				store.endTransaction();
				store.startTransaction();
			}
		}
		
		public OutputType processResearcher() throws Exception {
			deleteResearcher();
			return OutputType.DELETED;
		}
	}

}
