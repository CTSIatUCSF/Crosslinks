package edu.ucsf.crosslink.processor.iterator;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;

import com.google.inject.Inject;
import com.google.inject.name.Named;

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

	private static final String RESEARCHERS_WITH_EXTERNAL_COAUTHORS = "SELECT ?r WHERE { ?r <" +
			R2R_HAS_AFFILIATION + "> ?a . ?r <" + FOAF_PUBLICATIONS + "> ?cw . ?er <" + FOAF_PUBLICATIONS +
			"> ?cw . ?er <" + R2R_HAS_AFFILIATION + "> ?ea FILTER(?ea != ?a)}";	
	
	private static final String RESEARCHERS_WITH_EXTERNAL_COAUTHORS_CNT = "SELECT ?r WHERE { GRAPH <" +
			R2R_DERIVED_GRAPH + "> { ?r <" + R2R_EXTERNAL_COAUTHOR_CNT + "> ?cw }}";	
	
	private static final int LIMIT = 1000;
	
	private static final String RESEARCHER_CONSTRUCT = "CONSTRUCT {<%1$s> ?p ?o . " + 
			"<%1$s> <" + FOAF_HAS_IMAGE + "> ?i} " + 
			"WHERE {<%1$s> ?p ?o . " +
			"FILTER(?p != <" + R2R_PROCESSED + ">) " +
			". FILTER(?p != <" + FOAF_HAS_IMAGE + ">) " + 
			". FILTER(?p != <" + FOAF + "depiction>) . OPTIONAL " + 
			"{ GRAPH <" + R2R_THUMBNAIL_GRAPH + "> " + 
			"{<%1$s> <" + FOAF_HAS_IMAGE + "> ?i} }}";

 	
	private static final String AFFILIATIONS = "CONSTRUCT {?a ?p ?o } WHERE { ?a <" + RDF_TYPE + 
			"> <" + R2R_AFFILIATION + "> . ?a ?p ?o}";	

	private SparqlPostClient uiSparqlPostClient = null;
	private SparqlQueryClient uiSparqlQueryClient = null;
	private List<String> skipList = new ArrayList<String>();
	private ProcessorController processorController = null;
	
	private Model buffer = null;
	private AtomicInteger storeBufferCount = new AtomicInteger();
	private int bufferSize = 25;
	
	// Will copy all researchers into the UI fuseki instance.  
	@Inject
	public ExtractProcessor(@Named("r2r.fusekiUrl") String sparqlQuery, @Named("uiFusekiUrl") String uiFusekiUrl) throws Exception {
		super(new SparqlQueryClient(sparqlQuery + "/query"), LIMIT);
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
		return RESEARCHERS_WITH_EXTERNAL_COAUTHORS_CNT + 
				(limit > 0 ? String.format(" OFFSET %d LIMIT %d", offset, limit) : "");	}
	
	@Override
	protected ResearcherProcessor getResearcherProcessor(QuerySolution qs) {
		return new ExtractResearcherProcessor(qs.getResource("?r").getURI());
	}
	
	protected void addDataToResearcherModel(Researcher researcher) {
		researcher.addFrom(getSparqlClient().construct(String.format(RESEARCHER_CONSTRUCT, researcher.getURI())));
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
				Researcher researcher =  new Researcher(getResearcherURI());
				// Do not call createResearcher(); because it will add the processor, and we don't want that
				addDataToResearcherModel(researcher);
				addToStore(researcher.getModel());
				//Thread.sleep(5000);
				// add to skip list because the query returns the same people
				skipList.add(getResearcherURI());
				return OutputType.PROCESSED;
			}			
		}
	}

}
