package edu.ucsf.crosslink.crawler;

import java.util.ArrayList;
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

import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.R2ROntology;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;
import edu.ucsf.ctsi.r2r.jena.SparqlClient;
import edu.ucsf.ctsi.r2r.jena.SparqlPostClient;

public class MarengoDetailCrawler extends SparqlCrawler {

	private static final Logger LOG = Logger.getLogger(MarengoDetailCrawler.class.getName());
	
	public static final String DOI_PREFIX = "http://dx.doi.org/";

	// TODO add order by in this so that we get old ones first and make this less fragile with name issue!
	private static final String RESEARCHERS_SELECT = "SELECT ?r ?ts WHERE { " +
			"?r <" + R2R_HARVESTED_FROM + "> ?h . OPTIONAL {?r <" + R2R_WORK_VERIFIED_DT + 
			"> ?ts} FILTER (!bound(?ts) || ?ts < \"%s\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)}";	
	
	private static final String RESEARCHER_DETAIL = "SELECT ?l ?fn ?ln ?orcid WHERE { " +
			"<%1$s> <" + RDFS_LABEL + "> ?l ." +
			"OPTIONAL {<%1$s> <" + FOAF + "firstName> ?fn } . " +
			"OPTIONAL {<%1$s> <" + FOAF + "lastName> ?ln } . " +	
			"OPTIONAL {<%1$s> <" + VIVO_ORCID_ID + "> ?orcid}}";	

	private static final String RESEARCHER_PUBLICATIONS = "SELECT ?lir WHERE { " +
			"?aia <" + VIVO + "linkedAuthor> <%s> . " +
			"?aia <" + VIVO + "linkedInformationResource> ?lir }";	

	private static final String LIR_DETAIL = "SELECT ?pmid ?doi WHERE { OPTIONAL {" +
			"<%1$s> <" + BIBO_PMID + "> ?pmid} . OPTIONAL {<%1$s> <" + BIBO_DOI + "> ?doi}}";
	
	private static final int LIMIT = 50;

	private static final String MARENGO_PREFIX = "http://marengo.info-science.uiowa.edu:2020/resource/";
	
	private Integer daysConsideredOld;

	SparqlClient sparqlClient = null;

	// remove harvester as required item
	@Inject
	public MarengoDetailCrawler(@Named("Name") String name, 
			CrosslinkPersistance store, Mode crawlingMode, 
			SparqlPostClient sparqlPostClient,
			@Named("executorThreadCount") Integer threadCount, 
			@Named("daysConsideredOld") Integer daysConsideredOld) throws Exception {
		super(name, crawlingMode, store, sparqlPostClient, threadCount, LIMIT);
		this.sparqlClient = new SparqlClient("http://marengo.info-science.uiowa.edu:2020/sparql");
		this.daysConsideredOld = daysConsideredOld;
	}
	
	@Override
	protected String getSparqlQuery() {
		Calendar threshold = Calendar.getInstance();
		threshold.setTimeInMillis(new DateTime(getStartDate()).minusDays(daysConsideredOld).getMillis());
		return String.format(RESEARCHERS_SELECT, R2ROntology.createDefaultModel().createTypedLiteral(threshold).getString());
	}
	
	@Override
	protected String getResearcherURI(QuerySolution qs) {
		return qs.getResource("?r").getURI();
	}

	@Override
	protected boolean avoid(QuerySolution qs) {
		return false;
	}

	@Override
	protected boolean skip(QuerySolution qs) {
		if (Mode.FORCED_NO_SKIP.equals(getMode())) {
			return false;
		}
		else if (qs.getLiteral("?ts") != null) {
			Calendar vd = ((XSDDateTime)qs.getLiteral("?ts").getValue()).asCalendar();
			return vd.getTimeInMillis() > new DateTime().minusDays(daysConsideredOld).getMillis();
		}
		return false;
	}

	private static String getOptionalLiteral(QuerySolution qs, String field) {
		return qs.getLiteral(field) != null ? qs.getLiteral(field).getString() : null;		
	}

	public boolean readResearcher(final Researcher researcher) throws Exception {
		if (isPaused()) {
			return false;
		}

		sparqlClient.select(String.format(RESEARCHER_DETAIL, MARENGO_PREFIX + researcher.getURI()), new ResultSetConsumer() {
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
		
		if (isPaused()) {
			return false;
		}

		final List<String> lirs = new ArrayList<String>();
		sparqlClient.select(String.format(RESEARCHER_PUBLICATIONS, MARENGO_PREFIX + researcher.getURI()), new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext() && !isPaused()) {				
					QuerySolution qs = rs.next();
					String lir = qs.getResource("?lir").getURI();
					lirs.add( lir );
					LOG.info("Publications " + lir);
				}								
			}
		});

		if (isPaused()) {
			return false;
		}

		for (String lir : lirs) {
			String publication = getPublication(lir);
			if (publication != null) {
				researcher.addPublication(publication);
			}
		}		
		researcher.setWorkVerifiedDt(Calendar.getInstance());
		return true;
	}
	
	@Cached
	public String getPublication(String lir) {
		final StringBuilder publication = new StringBuilder();
		
		sparqlClient.select(String.format(LIR_DETAIL, lir), new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {				
				if (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					// use pmid if they have it
					if (qs.get("?pmid") != null) {
						publication.append("http:" + AuthorParser.PUBMED_SECTION + qs.getLiteral("?pmid").getInt());
					}
					else if (qs.get("?doi") != null){
						// this handles things like <a href=\"http://psycnet.apa.org/doi/10.1037/a0016478\">10.1037/a0016478</a> 
						// as well as a regular doi
						String doi = qs.getLiteral("?doi").getString();
						if (Jsoup.isValid(doi, Whitelist.basic())) {
							publication.append(DOI_PREFIX + Jsoup.parseBodyFragment(doi).text());							
						}
						else {
							setLatestError("Invalid DOI : " + doi, null);
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
	protected QueuedRunnable getResearcherProcessor(String researcherURI) {
		return new ReadResearcherAndPublications(researcherURI);
	}

	private class ReadResearcherAndPublications extends QueuedRunnable {
		
		private ReadResearcherAndPublications(String researcherURI) {
			super(researcherURI);
		}
		
		@Override
		protected Researcher timedRun(String researcherURI) throws Exception {
			Researcher researcher = new Researcher(researcherURI);
			if (readResearcher(researcher)) {
				update(researcher);
			}
			return researcher;
		}
	}

}
