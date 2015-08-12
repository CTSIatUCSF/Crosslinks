package edu.ucsf.crosslink.processor.iterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Logger;

import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.query.QuerySolution;
import org.joda.time.DateTime;
import org.jsoup.HttpStatusException;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.io.SparqlPersistance;
import edu.ucsf.crosslink.io.ThumbnailGenerator;
import edu.ucsf.crosslink.io.http.SiteReader;
import edu.ucsf.crosslink.model.Affiliated;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.crosslink.processor.BasicResearcherProcessor;
import edu.ucsf.crosslink.processor.ResearcherProcessor;
import edu.ucsf.crosslink.processor.controller.ProcessorController;
import edu.ucsf.crosslink.processor.controller.TypedOutputStats.OutputType;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.R2ROntology;
import edu.ucsf.ctsi.r2r.jena.SparqlPostClient;
import edu.ucsf.ctsi.r2r.jena.SparqlQueryClient;

public class PageItemProcessor extends SparqlProcessor implements Affiliated, R2RConstants {

	private static final int LIMIT = 0;

	private static final Logger LOG = Logger.getLogger(PageItemProcessor.class.getName());

	private static final String RESEARCHERS_SELECT_SKIP = "SELECT ?r ?ts WHERE { " +
			"?r <" + R2R_HAS_AFFILIATION + "> <%1$s> . OPTIONAL {?r <" + R2R_PROCESSED + "> ?c . ?c <" + 
			R2R_PROCESSED_BY + "> <%2$s> . ?c <" + R2R_PROCESSED_ON +  
			"> ?ts} FILTER (!bound(?ts) || ?ts < \"%3$s\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)} ORDER BY (?ts)";	

	private static final String RESEARCHERS_SELECT_NO_SKIP = "SELECT ?r WHERE { " +
			"?r <" + R2R_HAS_AFFILIATION + "> <%1$s>}";	
		
	private static final String REMOVE_EXISTING_IMAGES = "DELETE {<%1$s> <" + FOAF_HAS_IMAGE + 
			"> ?i } WHERE " + "{  <%1$s> <" + FOAF_HAS_IMAGE + "> ?i }";	

	private static final String ADD_THUMBNAIL = "INSERT DATA { GRAPH <" + R2R_THUMBNAIL_GRAPH + 
			"> {<%s> <" + FOAF_HAS_IMAGE + "> <%s> }}";	

	private static final String DELETE_RESEARCHER = "DELETE WHERE {<%1$s> ?p ?o }; DELETE WHERE {?s ?p <%1$s>}; WITH <" + 
			R2R_THUMBNAIL_GRAPH + "> DELETE { <%1$s> ?p ?o } WHERE { <%1$s> ?p ?o }; WITH " +
			R2R_DERIVED_GRAPH + "> DELETE { <%1$s> ?p ?o } WHERE { <%1$s> ?p ?o };";	
	private static final String DELETE_RESEARCHER_THUMBNAIL = "WITH <" + R2R_THUMBNAIL_GRAPH + 
			"> DELETE { <%1$s> ?p ?o } WHERE { <%1$s> ?p ?o }";	

	private Integer daysConsideredOld;

	private SiteReader reader = null;
	private Affiliation affiliation = null;
	private SparqlPersistance store = null;
	private ThumbnailGenerator thumbnailGenerator = null;
	private ProcessorController processorController = null;
	
	// remove harvester as required item
	@Inject
	public PageItemProcessor(Affiliation affiliation,
			SparqlPersistance store, SiteReader reader,	@Named("r2r.fusekiUrl") String sparqlQuery,		
			SparqlPostClient sparqlClient, ThumbnailGenerator thumbnailGenerator,
			@Named("daysConsideredOld") Integer daysConsideredOld) throws Exception {
		super(new SparqlQueryClient(sparqlQuery + "/query"), LIMIT);
		this.affiliation = affiliation;
		this.reader = reader;
		this.store = store;
		this.thumbnailGenerator = thumbnailGenerator;
		this.daysConsideredOld = daysConsideredOld;
	}
	
	@Inject
	public void setCrawler(ProcessorController processorController) {
		this.processorController = processorController;
	}
	
	public Affiliation getAffiliation() {
		return affiliation;
	}
	
	@Override
	protected String getSparqlQuery(int offset, int limit) {
		if (processorController != null && processorController.allowSkip()) {
			Calendar threshold = Calendar.getInstance();
			threshold.setTimeInMillis(new DateTime().minusDays(daysConsideredOld).getMillis());
			return String.format(RESEARCHERS_SELECT_SKIP, getAffiliation().getURI(), processorController.getURI(),
					R2ROntology.createDefaultModel().createTypedLiteral(threshold).getString());
		}
		else {			
			return String.format(RESEARCHERS_SELECT_NO_SKIP, getAffiliation().getURI()) + 
					(limit > 0 ? String.format(" OFFSET %d LIMIT %d", offset, limit) : "");
		}
	}
	
	@Override
	protected ResearcherProcessor getResearcherProcessor(QuerySolution qs) {
		return new PageItemResearcherProcessor(qs.getResource("?r").getURI(), 
				qs.get("?ts") != null ? ((XSDDateTime)qs.getLiteral("?ts").getValue()).asCalendar() : null);
	}

	private class PageItemResearcherProcessor extends BasicResearcherProcessor {
		
		private Calendar workVerifiedDT = null;
		private String message = null;
		
		private PageItemResearcherProcessor(String researcherURI, Calendar workVerifiedDT) {
			super(researcherURI);
			this.workVerifiedDT = workVerifiedDT;
		}

		private boolean generateThumbnail(Researcher researcher) {
			return thumbnailGenerator != null ? thumbnailGenerator.generateThumbnail(researcher) : false;
		}
		
		public String toString() {
			return message != null ? message : super.toString();
		}
		
		private void deleteResearcher() throws Exception {
			// should probably have delete be a function in store, but this is OK for now
			store.startTransaction();
			store.execute(Arrays.asList(String.format(DELETE_RESEARCHER, getResearcherURI())));
			store.endTransaction();
		}
		
		public OutputType processResearcher() throws Exception {
			if (allowSkip() && workVerifiedDT != null && workVerifiedDT.getTimeInMillis() > new DateTime().minusDays(daysConsideredOld).getMillis()) {
				return OutputType.SKIPPED;
			}
			else {
				Researcher researcher = createResearcher();
				researcher.setAffiliation(affiliation);

				try {
					reader.getPageItems(researcher);
				}
				catch (HttpStatusException e) {
					if (404 == e.getStatusCode()) {
						message = e.toString();
						deleteResearcher();
						return OutputType.DELETED;
					}
					else {
						throw e;
					}
				}

				List<String> preStatements = new ArrayList<String>();
				preStatements.add(String.format(DELETE_RESEARCHER_THUMBNAIL, getResearcherURI()));
				if (generateThumbnail(researcher)) {
					// just drop the triple, not the image or thumbnail
					preStatements.addAll(Arrays.asList(String.format(REMOVE_EXISTING_IMAGES, getResearcherURI()), 
						String.format(ADD_THUMBNAIL, getResearcherURI(), researcher.getThumbnailURL())));
				}
				
				store.startTransaction();
				store.execute(preStatements);
				store.execute(String.format(DELETE_PRIOR_PROCESS_LOG, getResearcherURI(), getCrawler().getURI()));
				store.update(researcher);
				store.endTransaction();
				
				return OutputType.PROCESSED;
			}
		}		
	}

}
