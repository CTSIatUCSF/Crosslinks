package edu.ucsf.crosslink.processor.iterator;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.io.SparqlPersistance;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.crosslink.processor.BasicResearcherProcessor;
import edu.ucsf.crosslink.processor.ResearcherProcessor;
import edu.ucsf.crosslink.processor.controller.TypedOutputStats.OutputType;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;
import edu.ucsf.ctsi.r2r.jena.SparqlQueryClient;

public class CalculateCoauthorsProcessor extends SparqlProcessor implements R2RConstants {

	private static final Logger LOG = Logger.getLogger(CalculateCoauthorsProcessor.class.getName());

	public static final String RESEARCHERS_SELECT_NO_SKIP = "SELECT ?r ?a WHERE { " +
			"?r <" + R2R_HAS_AFFILIATION + "> ?a}";	
		
	private static final String REMOVE_DERIVED_DATA = "WITH <" + R2R_DERIVED_GRAPH + 
			"> DELETE { <%1$s> ?p ?o } WHERE { <%1$s> ?p ?o }";	

	private static final String GET_COAUTHOR_CNTS = "SELECT (count(distinct ?er) as ?erc) (count(distinct ?cw) as ?cwc) WHERE {<%1$s> <" +
			FOAF_PUBLICATIONS + "> ?cw . ?er <" + FOAF_PUBLICATIONS + "> ?cw . ?er <" +
			R2R_HAS_AFFILIATION + "> ?ea FILTER (?ea != <%2$s>)}";

	private static final String ADD_COAUTHOR_CNTS = "INSERT DATA {GRAPH <" + R2R_DERIVED_GRAPH + 
			"> {<%1$s> <" + R2R_EXTERNAL_COAUTHOR_CNT + "> %2$d . <%1$s> <" + R2R_SHARED_PUB_CNT + "> %3$d}}";

	private static final int LIMIT = 1000;

	private SparqlPersistance store = null;
	
	@Inject
	public CalculateCoauthorsProcessor(@Named("r2r.fusekiUrl") String sparqlQuery, SparqlPersistance store) throws Exception {
		super(new SparqlQueryClient(sparqlQuery + "/query"), LIMIT);
		this.store = store;
	}
	
	@Override
	protected String getSparqlQuery(int offset, int limit) throws Exception {
		return RESEARCHERS_SELECT_NO_SKIP + 
				(limit > 0 ? String.format(" OFFSET %d LIMIT %d", offset, limit) : "");	
	}
	
	@Override
	protected ResearcherProcessor getResearcherProcessor(QuerySolution qs) {
		return new CalculateCoauthorsResearcherProcessor(qs.getResource("?r").getURI(), qs.getResource("?a").getURI());
	}
	
	private class CalculateCoauthorsResearcherProcessor extends BasicResearcherProcessor {
		
		private Affiliation affiliation = null; 
		
		private CalculateCoauthorsResearcherProcessor(String researcherURI, String affiliationURI) {
			super(researcherURI);
			try {
				affiliation = store.findAffiliationFor(affiliationURI);
			}
			catch (Exception e) {
				LOG.log(Level.SEVERE, "Exception finding affiliation" + affiliationURI, e);
			}
		}

		public OutputType processResearcher() throws Exception {
			Researcher researcher = createResearcher();
			researcher.setAffiliation(affiliation);

			final List<String> preStatements = new ArrayList<String>();
			preStatements.add(String.format(REMOVE_DERIVED_DATA, getResearcherURI()));

			String getCnts = String.format(GET_COAUTHOR_CNTS, getResearcherURI(), researcher.getAffiliation().getURI());								
			getSparqlClient().select(getCnts, new ResultSetConsumer() {
				public void useResultSet(ResultSet rs) throws Exception {
					if (rs.hasNext() ) {				
						QuerySolution qs = rs.next();
						if (qs.getLiteral("?erc").getInt() > 0 && qs.getLiteral("?cwc").getInt() > 0) {
							String update = String.format(ADD_COAUTHOR_CNTS, getResearcherURI(), 
								qs.getLiteral("?erc").getInt(), qs.getLiteral("?cwc").getInt());
							preStatements.add(update);
						}
					}						
				}	
			});

			// new
			store.startTransaction();
			store.execute(preStatements);
			store.execute(String.format(DELETE_PRIOR_PROCESS_LOG, getResearcherURI(), getCrawler().getURI()));
			store.update(researcher);
			store.endTransaction();
			// new
			
			
			return OutputType.PROCESSED;
		}
	}

}
