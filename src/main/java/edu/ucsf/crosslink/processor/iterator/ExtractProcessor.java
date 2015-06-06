package edu.ucsf.crosslink.processor.iterator;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;

import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.crosslink.processor.BasicResearcherProcessor;
import edu.ucsf.crosslink.processor.ResearcherProcessor;
import edu.ucsf.crosslink.processor.controller.ProcessorController;
import edu.ucsf.crosslink.processor.controller.TypedOutputStats.OutputType;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.R2ROntology;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;
import edu.ucsf.ctsi.r2r.jena.SparqlQueryClient;
import edu.ucsf.ctsi.r2r.jena.SparqlPostClient;

public class ExtractProcessor extends SparqlProcessor implements R2RConstants {

	private static final Logger LOG = Logger.getLogger(ExtractProcessor.class.getName());

	private static final String RESEARCHERS_SELECT_NO_SKIP = "SELECT ?r WHERE { " +
			"?r <" + R2R_HAS_AFFILIATION + "> ?a}";	
		
	private static final String RESEARCHER_DETAIL = "CONSTRUCT {<%1$s> ?p ?o} WHERE " +
			"{<%1$s> ?p ?o . FILTER(?p != <http://ucsf.edu/ontology/r2r#processedBy>) }";	

/* Sometime try...
 * CONSTRUCT {<http://profiles.ucsf.edu/profile/368698> ?p ?o . 
<http://profiles.ucsf.edu/profile/368698> <http://xmlns.com/foaf/0.1/img> ?i} 
WHERE {<http://profiles.ucsf.edu/profile/368698> ?p ?o . 
 FILTER(?p != <http://ucsf.edu/ontology/r2r#processedBy>)
. FILTER(?p != <http://xmlns.com/foaf/0.1/img>)
. FILTER(?p != <http://xmlns.com/foaf/0.1/depiction>)
. OPTIONAL 
{ GRAPH <http://ucsf.edu/ontology/r2r#DerivedData> {<http://profiles.ucsf.edu/profile/368698><http://xmlns.com/foaf/0.1/img> ?i} }}

 */
	
	private static final String COAUTHORS_EXTRACT_WHERE = "WHERE {<%1$s> <" + R2R_HAS_AFFILIATION + "> ?a . <%1$s> <" +
			FOAF_PUBLICATIONS + "> ?cw  . ?r <" + FOAF_PUBLICATIONS + "> ?cw  . ?r <" + RDFS_LABEL + 
			"> ?rl . OPTIONAL {?r <" + FOAF_HOMEPAGE + "> ?hp } . OPTIONAL { GRAPH <" + R2R_DERIVED_GRAPH + 
			"> { ?r <" + FOAF_HAS_IMAGE + "> ?tn} } . ?r <" + R2R_HAS_AFFILIATION + "> ?ea FILTER (?ea != ?a) . ?ea <" + 
			RDFS_LABEL + "> ?al . OPTIONAL {?ea <" + R2R_HAS_ICON + "> ?eaicon} . ?ea <" + GEO_LATITUDE + 
			"> ?ealat . ?ea <" + GEO_LONGITUDE + "> ?ealon}";

	protected static final String COAUTHORS_EXTRACT_CONSTRUCT = "CONSTRUCT {?r <" + RDF_TYPE + "> <" + FOAF_PERSON + 
			"> . ?r <" + FOAF_PUBLICATIONS + "> ?cw . ?r <" +
			RDFS_LABEL + "> ?rl . ?r <" + FOAF_HOMEPAGE + "> ?hp . ?r <" + FOAF_HAS_IMAGE + "> ?tn . ?r  <" +
			R2R_HAS_AFFILIATION + "> ?ea} " + COAUTHORS_EXTRACT_WHERE;

	private static final String AFFILIATIONS = "CONSTRUCT {?a ?p ?o } WHERE { ?a <" + RDF_TYPE + 
			"> <" + R2R_AFFILIATION + "> . ?a ?p ?o}";	

	private SparqlPostClient uiSparqlPostClient = null;
	private SparqlQueryClient uiSparqlQueryClient = null;
	private List<String> skipList = new ArrayList<String>();
	private ProcessorController processorController = null;
	
	private Model buffer = null;
	private AtomicInteger storeBufferCount = new AtomicInteger();
	private int bufferSize = 25;
	
	// Will copy researchers from a given affiliation into the UI fuseki instance.  
	@Inject
	public ExtractProcessor(@Named("r2r.fusekiUrl") String sparqlQuery, @Named("uiFusekiUrl") String uiFusekiUrl) throws Exception {
		super(new SparqlQueryClient(sparqlQuery + "/query"), 0);
		uiSparqlPostClient = new SparqlPostClient(uiFusekiUrl + "/update", uiFusekiUrl +  "/data?default");
		uiSparqlQueryClient = new SparqlQueryClient(uiFusekiUrl + "/query");
		addToStore(R2ROntology.createR2ROntModel());
		addToStore(getSparqlClient().construct(AFFILIATIONS));
	}
	
	@Inject
	public void setCrawler(ProcessorController processorController) {
		this.processorController = processorController;
	}
	
	@Override
	protected void shuttingDown() {
		try {
			if (buffer != null) {
				flushBuffer();
			}
		} 
		catch (Exception e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
		}
	}
	
	private synchronized void addToStore(Model model) throws Exception {
		if (buffer == null) {
			buffer = model;
		}
		else {
			buffer.add(model);
		}
		
		if (storeBufferCount.incrementAndGet() % bufferSize == 0) {
			flushBuffer();
		}
	}
	
	private void flushBuffer() throws Exception {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		buffer.write(stream);
		stream.flush();
		stream.close();
		uiSparqlPostClient.add(stream.toByteArray());
		buffer = null;		
	}

	@Override
	protected String getSparqlQuery(int offset, int limit) throws Exception {
		String query = getFormattedQueryString(offset, limit);
		// if they allows skip, then find which ones we already have and add them to the skip list
		if (processorController != null && processorController.allowSkip()) {
			uiSparqlQueryClient.select(query, new ResultSetConsumer() {
				public void useResultSet(ResultSet rs) throws Exception {
					while (rs.hasNext() ) {				
						QuerySolution qs = rs.next();
						skipList.add(qs.getResource("?r").getURI());
					}						
				}	
			});
		}
		return query;
	}
	
	protected String getFormattedQueryString(int offset, int limit) {
		return RESEARCHERS_SELECT_NO_SKIP;
	}
	
	@Override
	protected ResearcherProcessor getResearcherProcessor(QuerySolution qs) {
		return new ExtractResearcherProcessor(qs.getResource("?r").getURI());
	}
	
	protected void addDataToResearcherModel(Researcher researcher) {
		researcher.addFrom(getSparqlClient().construct(String.format(COAUTHORS_EXTRACT_CONSTRUCT, researcher.getURI())));
	}
	
	private class ExtractResearcherProcessor extends BasicResearcherProcessor {
		
		private ExtractResearcherProcessor(String researcherURI) {
			super(researcherURI);
		}

		public OutputType processResearcher() throws Exception {
			if (processorController != null && processorController.allowSkip() && skipList.contains(getResearcherURI())) {
				return OutputType.SKIPPED;
			}
			else {
				Researcher researcher = createResearcher();
				addDataToResearcherModel(researcher);
				addToStore(researcher.getModel());
				//Thread.sleep(5000);
				return OutputType.PROCESSED;
			}			
		}
	}

}
