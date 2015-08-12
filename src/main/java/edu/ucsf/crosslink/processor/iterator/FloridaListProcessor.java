package edu.ucsf.crosslink.processor.iterator;

import java.util.logging.Logger;

import org.apache.jena.query.QuerySolution;

import com.google.inject.Inject;

import edu.ucsf.crosslink.io.SparqlPersistance;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.crosslink.processor.BasicResearcherProcessor;
import edu.ucsf.crosslink.processor.ResearcherProcessor;
import edu.ucsf.crosslink.processor.controller.TypedOutputStats.OutputType;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.jena.SparqlQueryClient;

public class FloridaListProcessor extends SparqlProcessor implements R2RConstants {

	private static final Logger LOG = Logger.getLogger(FloridaListProcessor.class.getName());

	// only grab those with ID's
	private static final String RESEARCHERS_SELECT = "SELECT ?r WHERE { ?r <" + 
			RDF_TYPE + "> <" + FOAF_PERSON + "> . ?r <http://vivo.ufl.edu/ontology/vivo-ufl/ufid> ?o}";	
	
	private static final int LIMIT = 1000;
	
	private SparqlPersistance store = null;

	// remove harvester as required item
	@Inject
	public FloridaListProcessor(SparqlPersistance store) throws Exception {
		super(new SparqlQueryClient("http://sparql.vivo.ufl.edu/VIVO/query"), LIMIT);
		this.store = store;
	}

	@Override
	protected String getSparqlQuery(int offset, int limit) {
		return RESEARCHERS_SELECT + 
				(limit > 0 ? String.format(" OFFSET %d LIMIT %d", offset, limit) : "");
	}

	@Override
	protected ResearcherProcessor getResearcherProcessor(QuerySolution qs) {
		return new FloridaListResearcherProcessor(qs.getResource("?r").getURI());
	}

	private class FloridaListResearcherProcessor extends BasicResearcherProcessor {
		
		private String affiliation = null;
		
		public String toString() {
			return super.toString() + (affiliation != null ? " from " + affiliation : "");
		}
		
		private FloridaListResearcherProcessor(String uri) {
			super(uri);
		}

		public OutputType processResearcher() throws Exception {
			Researcher researcher = createResearcher();
			researcher.setAffiliation(store.findAffiliationFor(getResearcherURI()));
			affiliation = researcher.getAffiliation().toString();
			store.update(researcher);				
			return OutputType.PROCESSED;
		}		
	}
}
