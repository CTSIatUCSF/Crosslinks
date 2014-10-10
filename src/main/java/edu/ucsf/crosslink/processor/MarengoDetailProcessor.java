package edu.ucsf.crosslink.processor;

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

import edu.ucsf.crosslink.crawler.Crawler;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.R2ROntology;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;
import edu.ucsf.ctsi.r2r.jena.SparqlClient;
import edu.ucsf.ctsi.r2r.jena.SparqlPostClient;

public class MarengoDetailProcessor extends SparqlProcessor implements R2RConstants {

	private static final Logger LOG = Logger.getLogger(MarengoDetailProcessor.class.getName());
	
	public static final String DOI_PREFIX = "http://dx.doi.org/";

	private static final String RESEARCHERS_SELECT_SKIP = "SELECT ?r ?ts WHERE { " +
			"?r <" + RDF_TYPE + "> <" + FOAF_PERSON + "> . OPTIONAL {?r <" + R2R_WORK_VERIFIED_DT + 
			"> ?ts} FILTER (!bound(?ts) || ?ts < \"%s\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)}";	

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
	
	private static final String DOI_TO_PMID = "SELECT ?pmid WHERE { ?pmid <http://www.w3.org/2002/07/owl#sameAs> <%s> }";
	
	private static final int LIMIT = 50;

	private static final String MARENGO_PREFIX = "http://marengo.info-science.uiowa.edu:2020/resource/";
	
	private Integer daysConsideredOld;

	private SparqlClient marengoSparqlClient = null;
	private SparqlClient doiSparqlClient = null;
	private CrosslinkPersistance store = null;
	private Crawler crawler = null;

	// remove harvester as required item
	@Inject
	public MarengoDetailProcessor(SparqlPostClient sparqlClient, CrosslinkPersistance store,
			@Named("daysConsideredOld") Integer daysConsideredOld) throws Exception {
		super(sparqlClient, LIMIT);
		this.marengoSparqlClient = new SparqlClient("http://marengo.info-science.uiowa.edu:2020/sparql");
		this.doiSparqlClient = new SparqlClient("http://www.pmid2doi.org/sparql");
		this.daysConsideredOld = daysConsideredOld;
		this.store = store;
	}
	
	@Inject
	public void setCrawler(Crawler crawler) {
		this.crawler = crawler;
	}
	
	@Override
	protected String getSparqlQuery() {
		if (crawler != null && crawler.allowSkip()) {
			Calendar threshold = Calendar.getInstance();
			threshold.setTimeInMillis(new DateTime().minusDays(daysConsideredOld).getMillis());
			return String.format(RESEARCHERS_SELECT_SKIP, R2ROntology.createDefaultModel().createTypedLiteral(threshold).getString());
		}
		else {
			return RESEARCHERS_SELECT_NO_SKIP;
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
		researcher.setWorkVerifiedDt(Calendar.getInstance());
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
							// see if it can be resolved to a PMID uri
							String doiUri = DOI_PREFIX + Jsoup.parseBodyFragment(doi).text();
							try {
								String pmidUri = getPMIDUriFromDOIUri(doiUri);
								publication.append(pmidUri != null ? pmidUri : doiUri);							
							}
							catch (Exception e) {
								LOG.log(Level.WARNING, "Error converting doi : " + doi + " to PMID", e);
								// just bail, no need to rethrow
							}
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
	
	@Cached
	public String getPMIDUriFromDOIUri(String doiUri) throws Exception {
		final StringBuilder publication = new StringBuilder();
		
		doiSparqlClient.select(String.format(DOI_TO_PMID, doiUri), new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {				
				if (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					if (qs.get("?pmid") != null) {
						publication.append(qs.getResource("?pmid").getURI());
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

		public Action processResearcher() throws Exception {
			if (allowSkip() && workVerifiedDT != null && workVerifiedDT.getTimeInMillis() > new DateTime().minusDays(daysConsideredOld).getMillis()) {
				return Action.SKIPPED;
			}
			else {
				researcher = createResearcher();
				readResearcherDetails(researcher);
				store.startTransaction();
				store.execute(Arrays.asList(String.format(REMOVE_EXISTING_PUBLICATIONS, getResearcherURI())));
				store.update(researcher);
				store.endTransaction();
				return Action.PROCESSED;
			}
		}		
	}

}
