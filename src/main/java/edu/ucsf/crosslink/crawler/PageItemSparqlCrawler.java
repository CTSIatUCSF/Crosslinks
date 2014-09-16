package edu.ucsf.crosslink.crawler;

import java.util.Arrays;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

import edu.ucsf.crosslink.crawler.sitereader.SiteReader;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;
import edu.ucsf.ctsi.r2r.jena.SparqlPostClient;

public class PageItemSparqlCrawler extends SparqlCrawler implements Affiliated {

	private static final Logger LOG = Logger.getLogger(PageItemSparqlCrawler.class.getName());

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
	
	private static final String GET_COAUTHOR_CNTS = "SELECT (count(distinct ?er) as ?erc) (count(distinct ?cw) as ?cwc) WHERE { {<%1$s> <" +
			FOAF_PUBLICATIONS + "> ?cw} . GRAPH <" + R2R_DERIVED_GRAPH + "> {<%1$s> <" + FOAF_KNOWS + "> ?er } . ?er <" +
			R2R_HAS_AFFILIATION + "> ?ea FILTER (?ea != <%2$s>) . ?er <" + FOAF_PUBLICATIONS + "> ?cw }";

	private static final String ADD_COAUTHOR_CNTS = "INSERT DATA {GRAPH <" + R2R_DERIVED_GRAPH + 
			"> {<%1$s> <" + R2R_EXTERNAL_COAUTHOR_CNT + "> %2$d . <%1$s> <" + R2R_SHARED_PUB_CNT + "> %3$d}}";

	private Integer daysConsideredOld;

	private SiteReader reader;
	private Affiliation affiliation;
	private SparqlPostClient sparqlClient;
	
	// remove harvester as required item
	@Inject
	public PageItemSparqlCrawler(@Named("Name") String name, @Named("BaseURL") String baseURL, @Named("Location") String location,
			Mode crawlingMode, CrosslinkPersistance store,
			SiteReader reader,			
			SparqlPostClient sparqlClient,
			@Named("executorThreadCount") Integer threadCount, 
			@Named("daysConsideredOld") Integer daysConsideredOld) throws Exception {
		super(name, crawlingMode, store, sparqlClient, threadCount, 50);
		this.affiliation = new Affiliation(name, baseURL, location);
		this.reader = reader;
		this.sparqlClient = sparqlClient;
		this.daysConsideredOld = daysConsideredOld;
		store.save(affiliation);		
	}
	
	public Affiliation getAffiliation() {
		return affiliation;
	}
	
	@Override
	protected QueuedRunnable getResearcherProcessor(String researcherURI) {
		return new ReadPageItemsAndSave(researcherURI);
	}
	
	private class ReadPageItemsAndSave extends QueuedRunnable {

		private ReadPageItemsAndSave(String researcherURI) {
			super(researcherURI);
		}
		
		@Override
		protected Researcher timedRun(final String researcherURI) throws Exception {
			Researcher researcher = new Researcher(researcherURI, affiliation);
			researcher.setHarvester(PageItemSparqlCrawler.this);
			reader.getPageItems(researcher);
			update(researcher, Arrays.asList(String.format(REMOVE_DERIVED_DATA, researcherURI), 
					String.format(ADD_COAUTHORS, researcherURI)));
			
			String getCnts = String.format(GET_COAUTHOR_CNTS, researcherURI, researcher.getAffiliation().getURI());
			sparqlClient.select(getCnts, new ResultSetConsumer() {
				public void useResultSet(ResultSet rs) {
					if (rs.hasNext() ) {				
						QuerySolution qs = rs.next();
						if (qs.getLiteral("?erc").getInt() > 0 && qs.getLiteral("?cwc").getInt() > 0)
						{
						String update = String.format(ADD_COAUTHOR_CNTS, researcherURI, 
								qs.getLiteral("?erc").getInt(), qs.getLiteral("?cwc").getInt());
							try {
								sparqlClient.update(update);
							}
							catch (Exception e) {
								addError(researcherURI);
								setLatestError(researcherURI, e);
							}
						}
					}						
				}	
			});
			return researcher;
		}
	}
	
	@Override
	protected String getSparqlQuery() {
		return String.format(RESEARCHERS_SELECT, getAffiliation().getURI());
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
}
