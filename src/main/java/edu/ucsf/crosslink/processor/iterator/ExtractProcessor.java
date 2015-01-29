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

import edu.ucsf.crosslink.model.Affiliated;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.crosslink.processor.BasicResearcherProcessor;
import edu.ucsf.crosslink.processor.ResearcherProcessor;
import edu.ucsf.crosslink.processor.controller.ProcessorController;
import edu.ucsf.crosslink.processor.controller.TypedOutputStats.OutputType;
import edu.ucsf.crosslink.web.FusekiRestMethods;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.R2ROntology;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;
import edu.ucsf.ctsi.r2r.jena.SparqlQueryClient;
import edu.ucsf.ctsi.r2r.jena.SparqlPostClient;

public class ExtractProcessor extends SparqlProcessor implements Affiliated, R2RConstants {

	private static final Logger LOG = Logger.getLogger(ExtractProcessor.class.getName());

	private static final String RESEARCHERS_SELECT_NO_SKIP = "SELECT ?r WHERE { " +
			"?r <" + R2R_HAS_AFFILIATION + "> <%1$s>}";	
		
	private static final String RESEARCHER_DETAIL = "CONSTRUCT {<%1$s> <" + RDF_TYPE + 
			"> <" + FOAF_PERSON + "> . <%1$s> <" + R2R_HAS_AFFILIATION + "> ?a . <%1$s> <" +
			FOAF_PUBLICATIONS + "> ?p } WHERE { <%1$s> <" + R2R_HAS_AFFILIATION + "> ?a . OPTIONAL {<%1$s> <" +
			FOAF_PUBLICATIONS + "> ?p }}";	

	private static final String AFFILIATIONS = "CONSTRUCT {?a ?p ?o } WHERE { ?a <" + RDF_TYPE + 
			"> <" + R2R_AFFILIATION + "> . ?a ?p ?o}";	

	private Affiliation affiliation = null;
	private SparqlPostClient uiSparqlClient = null;
	private List<String> skipList = new ArrayList<String>();
	private ProcessorController processorController = null;
	
	private Model buffer = null;
	private AtomicInteger storeBufferCount = new AtomicInteger();
	private int bufferSize = 25;
	
	// Will copy researchers from a given affiliation into the UI fuseki instance.  
	@Inject
	public ExtractProcessor(Affiliation affiliation,
			@Named("r2r.fusekiUrl") String sparqlQuery, @Named("uiFusekiUrl") String uiFusekiUrl) throws Exception {
		super(new SparqlQueryClient(sparqlQuery + "/query"), 0);
		this.affiliation = affiliation;
		uiSparqlClient = new SparqlPostClient(uiFusekiUrl + "/update", uiFusekiUrl +  "/data?default");
		addToStore(R2ROntology.createR2ROntModel());
		addToStore(getSparqlClient().construct(AFFILIATIONS));
	}
	
	public Affiliation getAffiliation() {
		return affiliation;
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
		uiSparqlClient.add(stream.toByteArray());
		buffer = null;		
	}

	@Override
	protected String getSparqlQuery(int offset, int limit) throws Exception {
		String query = String.format(RESEARCHERS_SELECT_NO_SKIP, getAffiliation().getURI());
		getSparqlClient().select(query, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) throws Exception {
				while (rs.hasNext() ) {				
					QuerySolution qs = rs.next();
					skipList.add(qs.getResource("?r").getURI());
				}						
			}	
		});
		return query;
	}
	
	@Override
	protected ResearcherProcessor getResearcherProcessor(QuerySolution qs) {
		return new ExtractResearcherProcessor(qs.getResource("?r").getURI());
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
				researcher.addFrom(getSparqlClient().construct(String.format(RESEARCHER_DETAIL, getResearcherURI())));
				if (researcher.getPublications().size() > 0) {
					researcher.getModel().add(getSparqlClient().construct(String.format(FusekiRestMethods.COAUTHORS_EXTRACT_CONSTRUCT, getResearcherURI())));
				}
				addToStore(researcher.getModel());
				//Thread.sleep(5000);
				return OutputType.PROCESSED;
			}			
		}
	}

}
