package edu.ucsf.crosslink.processor.iterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hp.cache4guice.Cached;
import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

import edu.ucsf.crosslink.io.SparqlPersistance;
import edu.ucsf.crosslink.io.http.DOI2PMIDConverter;
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

public class MarengoDetailProcessor extends SparqlProcessor implements R2RConstants {

	private static final Logger LOG = Logger.getLogger(MarengoDetailProcessor.class.getName());
	
	public static final String DOI_PREFIX = "http://dx.doi.org/";

	private static final String RESEARCHERS_SELECT_SKIP = "SELECT ?r ?ts WHERE { " +
			"?r <" + RDF_TYPE + "> <" + FOAF_PERSON + "> . OPTIONAL {?r <" + R2R_PROCESSED_BY + "> ?c . ?c <" + RDFS_LABEL + 
			"> \"%1$s\"^^<http://www.w3.org/2001/XMLSchema#string> . ?c <" + R2R_PROCESSED_ON + 
			"> ?ts} FILTER (!bound(?ts) || ?ts < \"%2$s\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)} ORDER BY (?ts)";	

	private static final String RESEARCHERS_SELECT_NO_SKIP = "SELECT ?r ?ts WHERE { " +
			"?r <" + RDF_TYPE + "> <" + FOAF_PERSON + ">}";	
	
	private static final String RESEARCHER_DETAIL = "SELECT ?l ?fn ?ln ?orcid WHERE { " +
			"<%1$s> <" + RDFS_LABEL + "> ?l ." +
			"OPTIONAL {<%1$s> <" + FOAF_FIRST_NAME + "> ?fn } . " +
			"OPTIONAL {<%1$s> <" + FOAF_LAST_NAME + "> ?ln } . " +	
			"OPTIONAL {<%1$s> <" + VIVO_ORCID_ID + "> ?orcid}}";	

	private static final String RESEARCHER_PUBLICATIONS = "SELECT ?lir WHERE { " +
			"?aia <" + VIVO + "linkedAuthor> <%s> . " +
			"?aia <" + VIVO + "linkedInformationResource> ?lir }";	

	private static final String LIR_DETAIL = "SELECT ?pmid ?doi WHERE { OPTIONAL {" +
			"<%1$s> <" + BIBO_PMID + "> ?pmid} . OPTIONAL {<%1$s> <" + BIBO_DOI + "> ?doi}}";

	private static final String REMOVE_EXISTING_PUBLICATIONS = "DELETE {<%1$s> <" + FOAF_PUBLICATIONS + 
			"> ?i } WHERE " + "{  <%1$s> <" + FOAF_PUBLICATIONS + "> ?i }";	
	
	private static final int LIMIT = 0;

	private static final String MARENGO_PREFIX = "http://marengo.info-science.uiowa.edu:2020/resource/";
	
	private Integer daysConsideredOld;

	private SparqlQueryClient marengoSparqlClient = null;
	private SparqlPersistance store = null;
	private ProcessorController processorController = null;
	private DOI2PMIDConverter converter = null;

	private String[] uriAvoids = null;
	
	// remove harvester as required item
	@Inject
	public MarengoDetailProcessor(@Named("r2r.fusekiUrl") String sparqlQuery, SparqlPersistance store, DOI2PMIDConverter converter,
			@Named("daysConsideredOld") Integer daysConsideredOld, @Named("avoids") String avoids) throws Exception {
		super(new SparqlQueryClient(sparqlQuery + "/query"), LIMIT);
		this.marengoSparqlClient = new SparqlQueryClient("http://marengo.info-science.uiowa.edu:2020/sparql", 600000, 600000);
		this.daysConsideredOld = daysConsideredOld;
		this.store = store;
		this.converter = converter;
		this.uriAvoids = avoids != null ? avoids.split(",") : new String[]{};
	}
	
	@Inject
	public void setCrawler(ProcessorController processorController) {
		this.processorController = processorController;
	}
	
	@Override
	protected String getSparqlQuery(int offset, int limit) {
		if (processorController != null && processorController.allowSkip()) {
			Calendar threshold = Calendar.getInstance();
			threshold.setTimeInMillis(new DateTime().minusDays(daysConsideredOld).getMillis());
			return String.format(RESEARCHERS_SELECT_SKIP, processorController.getName(), 
					R2ROntology.createDefaultModel().createTypedLiteral(threshold).getString());
		}
		else {
			return RESEARCHERS_SELECT_NO_SKIP + 
					(limit > 0 ? String.format(" OFFSET %d LIMIT %d", offset, limit) : "");
		}		
	}
	
	private static String getOptionalLiteral(QuerySolution qs, String field) {
		return qs.getLiteral(field) != null ? qs.getLiteral(field).getString() : null;		
	}

	private void readResearcherDetails(final Researcher researcher) throws Exception {
		marengoSparqlClient.select(String.format(RESEARCHER_DETAIL, MARENGO_PREFIX + researcher.getURI()), new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				if (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					String label = qs.getLiteral("?l").getString();
					String firstName = getOptionalLiteral(qs, "?fn");
					String lastName = getOptionalLiteral(qs, "?ln");
					String orcidId = getOptionalLiteral(qs, "?orcid");

					researcher.setLabel(label);
					researcher.setFOAFName(firstName, lastName);
					researcher.setOrcidId(orcidId);
					LOG.info("Read " + label + " : " + firstName + " : " + lastName);
				}								
			}
		});
		
		final List<String> lirs = new ArrayList<String>();
		marengoSparqlClient.select(String.format(RESEARCHER_PUBLICATIONS, MARENGO_PREFIX + researcher.getURI()), new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					String lir = qs.getResource("?lir").getURI();
					lirs.add( lir );
					LOG.info("Publications " + lir);
				}								
			}
		});

		for (String lir : lirs) {
			String publication = getPublication(lir);
			if (publication != null) {
				researcher.addPublication(publication);
			}
		}		
	}
	
	@Cached
	public String getPublication(String lir) throws Exception {
		final StringBuilder publication = new StringBuilder();
		
		marengoSparqlClient.select(String.format(LIR_DETAIL, lir), new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) throws Exception {				
				if (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					// use pmid if they have it AND it works
					if (qs.get("?pmid") != null) {
						try {
							publication.append("http:" + ResearcherProcessor.PUBMED_SECTION + qs.getLiteral("?pmid").getInt());
							return;
						}
						catch (Exception e) {
							LOG.log(Level.WARNING, "Unexepected literal for PMID : " + qs.getLiteral("?pmid").toString(), e);							
							// don't re-throw, hope DOI works out
						}
					}
					if (qs.get("?doi") != null){
						// this handles things like <a href=\"http://psycnet.apa.org/doi/10.1037/a0016478\">10.1037/a0016478</a> 
						// as well as a regular doi
						String doi = qs.getLiteral("?doi").getString();
						if (Jsoup.isValid(doi, Whitelist.basic())) {
							doi = Jsoup.parseBodyFragment(doi).text();
							// see if it can be resolved to a PMID uri
							String doiUri = DOI_PREFIX + doi;
							String pmidUri = null;
							try {
								pmidUri = "http:" + ResearcherProcessor.PUBMED_SECTION + converter.getPMIDFromDOI(doi);
							}
							catch (Exception e) {
								LOG.log(Level.WARNING, "Error converting doi : " + doi + " to PMID", e);
							}
							publication.append(pmidUri != null ? pmidUri: doiUri);							
						}
						else {
							LOG.log(Level.WARNING, "Invalid DOI : " + doi);
						}
					}
					LOG.info("Publication " + publication);
				}	
			}
		});
		return publication.length() > 0 ? publication.toString() : null;
	}
	
	@Override
	protected ResearcherProcessor getResearcherProcessor(QuerySolution qs) {
		return new MarengoDetailResearcherProcessor(qs.getResource("?r").getURI(),
				qs.get("?ts") != null ? ((XSDDateTime)qs.getLiteral("?ts").getValue()).asCalendar() : null);
	}

	private class MarengoDetailResearcherProcessor extends BasicResearcherProcessor {
		
		private Calendar workVerifiedDT = null;
		Researcher researcher = null;
		
		public String toString() {
			return super.toString() + (researcher != null ? " " + researcher.getPublications().size() + " publications" : "");
		}
		
		private MarengoDetailResearcherProcessor(String researcherURI, Calendar workVerifiedDT) {
			super(researcherURI);
			this.workVerifiedDT = workVerifiedDT;
		}

		public OutputType processResearcher() throws Exception {
			if (allowSkip() && workVerifiedDT != null && workVerifiedDT.getTimeInMillis() > new DateTime().minusDays(daysConsideredOld).getMillis()) {
				return OutputType.SKIPPED;
			}
			else if (avoid()) {
				return OutputType.AVOIDED;
			}
			else {
				researcher = createResearcher();
				readResearcherDetails(researcher);
				store.startTransaction();
				store.execute(Arrays.asList(String.format(REMOVE_EXISTING_PUBLICATIONS, getResearcherURI())));
				store.execute(String.format(DELETE_PRIOR_PROCESS_LOG, getResearcherURI(), getCrawler().getName()));
				store.update(researcher);
				store.endTransaction();
				return OutputType.PROCESSED;
			}
		}		
		
		private boolean avoid() {
			for (String prefix : uriAvoids) {
				if (getResearcherURI().startsWith(prefix)) {
					return true;
				}
			}
			return false;
		}
	}

}
