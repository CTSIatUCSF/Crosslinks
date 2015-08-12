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

public class CopyProcessor extends SparqlProcessor implements R2RConstants {

	private static final Logger LOG = Logger.getLogger(CopyProcessor.class.getName());

	private static final String AFFILIATIONS = "CONSTRUCT {?a ?p ?o } WHERE { ?a <" + RDF_TYPE + 
			"> <" + R2R_AFFILIATION + "> . ?a ?p ?o}";	

	public static final String RESEARCHERS = "SELECT ?r WHERE { ?r <" +
			R2R_HAS_AFFILIATION + "> ?a}";	
	
	private static final int LIMIT = 1000;
	
	private static final String RESEARCHER_CONSTRUCT = "CONSTRUCT {<%1$s> ?p ?o . ?o ?op ?oo} WHERE {<%1$s> ?p ?o FILTER(?p != <" +
			R2R_PROCESSED_BY + ">) . OPTIONAL {?o ?op ?oo}}";	

	private static final String GET_THUMBNAIL = "SELECT ?t WHERE { GRAPH <" + R2R_THUMBNAIL_GRAPH + 
			"> {<%s> <" + FOAF_HAS_IMAGE + "> ?t }}";	

	private static final String ADD_THUMBNAIL = "INSERT DATA { GRAPH <" + R2R_THUMBNAIL_GRAPH + 
			"> {<%s> <" + FOAF_HAS_IMAGE + "> <%s> }}";	
	
	private SparqlPostClient uiSparqlPostClient = null;
	private SparqlQueryClient uiSparqlQueryClient = null;
	private List<String> skipList = new ArrayList<String>();
	private ProcessorController processorController = null;
	
	private Model buffer = null;
	private AtomicInteger storeBufferCount = new AtomicInteger();
	private int bufferSize = 25;
	
	// Will copy all researchers into the UI fuseki instance.  
	@Inject
	public CopyProcessor(@Named("r2r.fusekiUrl") String sparqlQuery, @Named("uiFusekiUrl") String uiFusekiUrl) throws Exception {
		super(new SparqlQueryClient(sparqlQuery + "/query"), LIMIT);
		uiSparqlPostClient = new SparqlPostClient(uiFusekiUrl + "/update", uiFusekiUrl +  "/data?default");
		uiSparqlQueryClient = new SparqlQueryClient(uiFusekiUrl + "/query");
		addToUIStore(R2ROntology.createR2ROntModel());
		addToUIStore(getSparqlClient().construct(AFFILIATIONS));
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
	
	private synchronized void addToUIStore(Model model) throws Exception {
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
		return RESEARCHERS + 
				(limit > 0 ? String.format(" OFFSET %d LIMIT %d", offset, limit) : "");	}
	
	@Override
	protected ResearcherProcessor getResearcherProcessor(QuerySolution qs) {
		return new CopyResearcherProcessor(qs.getResource("?r").getURI());
	}
	
	protected void addDataToResearcherModel(Researcher researcher) {
		researcher.addFrom(getSparqlClient().construct(String.format(RESEARCHER_CONSTRUCT, researcher.getURI())));
	}
	
	private class CopyResearcherProcessor extends BasicResearcherProcessor {
		
		private CopyResearcherProcessor(String researcherURI) {
			super(researcherURI);
		}

		public OutputType processResearcher() throws Exception {
			if (processorController != null && processorController.allowSkip() && skipList.contains(getResearcherURI())) {
				return OutputType.SKIPPED;
			}
			else {
				Researcher researcher =  createResearcher();
				addDataToResearcherModel(researcher);
				addToUIStore(researcher.getModel());
				//Thread.sleep(5000);
				// add thumbnail if it is present
				getSparqlClient().select(String.format(GET_THUMBNAIL, getResearcherURI()), new ResultSetConsumer() {
					public void useResultSet(ResultSet rs) throws Exception {
						if (rs.hasNext() ) {				
							QuerySolution qs = rs.next();
							uiSparqlPostClient.update(String.format(ADD_THUMBNAIL, getResearcherURI(), qs.getResource("?t").getURI()));
						}						
					}	
				});
				return OutputType.PROCESSED;
			}			
		}
	}

}
