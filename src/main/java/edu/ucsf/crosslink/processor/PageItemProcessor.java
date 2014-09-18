package edu.ucsf.crosslink.processor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Logger;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

import edu.ucsf.crosslink.crawler.sitereader.SiteReader;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.io.ThumbnailGenerator;
import edu.ucsf.crosslink.model.Affiliated;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;
import edu.ucsf.ctsi.r2r.jena.SparqlPostClient;

public class PageItemProcessor extends SparqlProcessor implements Affiliated, R2RConstants {

	private static final int LIMIT = 50;

	private static final Logger LOG = Logger.getLogger(PageItemProcessor.class.getName());

	// do this only for someone with a work verfied DT
	private static final String RESEARCHERS_SELECT = "SELECT ?r ?ts WHERE { " +
			"?r <" + R2R_HAS_AFFILIATION + "> <%s> . ?r <" + R2R_WORK_VERIFIED_DT + "> ?wts . OPTIONAL {?r <" + 
			R2R_VERIFIED_DT + "> ?ts} }";	
		
	private static final String REMOVE_DERIVED_DATA = "WITH <" + R2R_DERIVED_GRAPH + 
			"> DELETE { <%1$s> ?p ?o } WHERE { <%1$s> ?p ?o }";	

	private static final String ADD_COAUTHORS = "INSERT {GRAPH <" + R2R_DERIVED_GRAPH + 
			"> {<%1$s> <" + FOAF_KNOWS + "> ?r} }  WHERE { <%1$s> <" + FOAF_PUBLICATIONS + 
			"> ?pub . <%1$s> <" + R2R_HAS_AFFILIATION + "> ?a . ?r <" + FOAF_PUBLICATIONS + 
			"> ?pub . ?r <" + R2R_HAS_AFFILIATION + "> ?ea FILTER (?ea != ?a)}";	
	
	private static final String REMOVE_EXISTING_IMAGES = "DELETE {<%1$s> <" + FOAF_HAS_IMAGE + 
			"> ?i } WHERE " + "{  <%1$s> <" + FOAF_HAS_IMAGE + "> ?i }";	

	private static final String ADD_THUMBNAIL = "INSERT DATA { GRAPH <" + R2R_DERIVED_GRAPH + 
			"> {<%s> <" + FOAF_HAS_IMAGE + "> <%s> }}";	

	private static final String GET_COAUTHOR_CNTS = "SELECT (count(distinct ?er) as ?erc) (count(distinct ?cw) as ?cwc) WHERE { {<%1$s> <" +
			FOAF_PUBLICATIONS + "> ?cw} . GRAPH <" + R2R_DERIVED_GRAPH + "> {<%1$s> <" + FOAF_KNOWS + "> ?er } . ?er <" +
			R2R_HAS_AFFILIATION + "> ?ea FILTER (?ea != <%2$s>) . ?er <" + FOAF_PUBLICATIONS + "> ?cw }";

	private static final String ADD_COAUTHOR_CNTS = "INSERT DATA {GRAPH <" + R2R_DERIVED_GRAPH + 
			"> {<%1$s> <" + R2R_EXTERNAL_COAUTHOR_CNT + "> %2$d . <%1$s> <" + R2R_SHARED_PUB_CNT + "> %3$d}}";

	private Integer daysConsideredOld;

	private SiteReader reader = null;
	private Affiliation affiliation = null;
	private SparqlPostClient sparqlClient = null;
	private CrosslinkPersistance store = null;
	private ThumbnailGenerator thumbnailGenerator = null;
	
	// remove harvester as required item
	@Inject
	public PageItemProcessor(@Named("Name") String name, @Named("BaseURL") String baseURL, @Named("Location") String location,
			CrosslinkPersistance store, SiteReader reader,			
			SparqlPostClient sparqlClient, ThumbnailGenerator thumbnailGenerator,
			@Named("daysConsideredOld") Integer daysConsideredOld) throws Exception {
		super(sparqlClient, LIMIT);
		this.affiliation = new Affiliation(name, baseURL, location);
		this.reader = reader;
		this.sparqlClient = sparqlClient;
		this.store = store;
		this.thumbnailGenerator = thumbnailGenerator;
		this.daysConsideredOld = daysConsideredOld;
		store.save(affiliation);	
	}
	
	public Affiliation getAffiliation() {
		return affiliation;
	}
	
	@Override
	protected String getSparqlQuery() {
		return String.format(RESEARCHERS_SELECT, getAffiliation().getURI());
	}
	
	@Override
	protected ResearcherProcessor getResearcherProcessor(QuerySolution qs) {
		return new PageItemResearcherProcessor(qs.getResource("?r").getURI(), 
				qs.get("?ts") != null ? ((XSDDateTime)qs.getLiteral("?ts").getValue()).asCalendar() : null);
	}

	private class PageItemResearcherProcessor extends BasicResearcherProcessor {
		
		private Calendar workVerifiedDT = null;
		private Researcher researcher = null;
		
		private PageItemResearcherProcessor(String researcherURI, Calendar workVerifiedDT) {
			super(researcherURI);
			this.workVerifiedDT = workVerifiedDT;
		}

		private boolean generateThumbnail() {
			return thumbnailGenerator != null ? thumbnailGenerator.generateThumbnail(researcher) : false;
		}
		
		public Action processResearcher() throws Exception {
			if (workVerifiedDT != null && workVerifiedDT.getTimeInMillis() > new DateTime().minusDays(daysConsideredOld).getMillis()) {
				return Action.SKIPPED;
			}
			else {
				researcher = createResearcher();
				researcher.setAffiliation(affiliation);

				reader.getPageItems(researcher);

				List<String> preStatements = new ArrayList<String>();
				preStatements.addAll(Arrays.asList(String.format(REMOVE_DERIVED_DATA, getResearcherURI()), 
						String.format(ADD_COAUTHORS, getResearcherURI())));
				if (generateThumbnail()) {
					// just drop the triple, not the image or thumbnail
					preStatements.addAll(Arrays.asList(String.format(REMOVE_EXISTING_IMAGES, getResearcherURI()), 
						String.format(ADD_THUMBNAIL, getResearcherURI(), researcher.getThumbnailURL())));
				}
				
				store.startTransaction();
				store.execute(preStatements);
				store.update(researcher);
				store.endTransaction();
				
				// we intentionally add this later in a different transaction
				String getCnts = String.format(GET_COAUTHOR_CNTS, getResearcherURI(), researcher.getAffiliation().getURI());								
				sparqlClient.select(getCnts, new ResultSetConsumer() {
					public void useResultSet(ResultSet rs) throws Exception {
						if (rs.hasNext() ) {				
							QuerySolution qs = rs.next();
							if (qs.getLiteral("?erc").getInt() > 0 && qs.getLiteral("?cwc").getInt() > 0) {
								String update = String.format(ADD_COAUTHOR_CNTS, getResearcherURI(), 
									qs.getLiteral("?erc").getInt(), qs.getLiteral("?cwc").getInt());
								sparqlClient.update(update);
							}
						}						
					}	
				});

				return Action.PROCESSED;
			}
		}		
	}

}
