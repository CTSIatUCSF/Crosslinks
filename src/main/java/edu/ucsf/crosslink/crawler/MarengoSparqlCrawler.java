package edu.ucsf.crosslink.crawler;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.time.StopWatch;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hp.cache4guice.Cached;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.crawler.sitereader.SiteReader;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;
import edu.ucsf.ctsi.r2r.jena.SparqlClient;
import edu.ucsf.ctsi.r2r.R2RConstants;
import net.sourceforge.sitemaps.UnknownFormatException;
import net.sourceforge.sitemaps.http.ProtocolException;

public class MarengoSparqlCrawler extends Crawler implements AuthorParser, R2RConstants {

	private static final Logger LOG = Logger.getLogger(MarengoSparqlCrawler.class.getName());

	private static final String RESEARCHERS_SELECT = "SELECT ?s ?o WHERE { " +
			"?s <http://marengo.info-science.uiowa.edu:2020/resource/vocab/Person_URI> ?o } OFFSET %d LIMIT %d";	
	
	private static final String RESEARCHER_DETAIL = "SELECT ?l ?fn ?ln ?orcid WHERE { " +
			"<%1$s> <" + RDFS_LABEL + "> ?l ." +
			"OPTIONAL {<%1$s> <" + FOAF + "firstName> ?fn } . " +
			"OPTIONAL {<%1$s> <" + FOAF + "lastName> ?ln } . " +	
			"OPTIONAL {<%1$s> <" + VIVO_ORCID_ID + "> ?orcid}}";	

	private static final String RESEARCHER_PUBLICATIONS = "SELECT ?lir WHERE { " +
			"?aia <http://vivoweb.org/ontology/core#linkedAuthor> <%s> . " +
			"?aia <" + VIVO + "linkedInformationResource> ?lir }";	

	private static final String LIR_DETAIL = "SELECT ?pmid WHERE { " +
			"<%s> <" + BIBO_PMID + "> ?pmid}";
	
	private static final int LIMIT = 50;

	private SparqlClient sparqlClient = null;
	private static final String MARENGO_PREFIX = "http://marengo.info-science.uiowa.edu:2020/resource/";
	
	private Affiliation harvester;
	private SiteReader reader;
	private CrosslinkPersistance store;

	private ExecutorService readResearcherExecutiveService;
	private ExecutorService pageItemExecutiveService;	
	
	private long readListTime = 0;
	private long readResearcherTime = 0;
	private long pageItemsTime = 0;
	
	private int readQueueSize = 0;
	private int pageItemQueueSize = 0;
	private int offset = 0;
	private int limit = LIMIT;
	
	// remove harvester as required item
	@Inject
	public MarengoSparqlCrawler(Affiliation harvester, SiteReader reader, 
			CrosslinkPersistance store, Mode crawlingMode, 
			@Named("sparqlDetailThreadCount") Integer sparqlDetailThreadCount, @Named("pageItemThreadCount") Integer pageItemThreadCount) throws Exception {
		super(crawlingMode, harvester, store);
		this.harvester = harvester;
		this.sparqlClient = new SparqlClient("http://marengo.info-science.uiowa.edu:2020/sparql");
		this.reader = reader;
		this.store = store;

		readResearcherExecutiveService = sparqlDetailThreadCount > 0 ? Executors.newFixedThreadPool(sparqlDetailThreadCount) : Executors.newCachedThreadPool();
		pageItemExecutiveService = pageItemThreadCount > 0 ? Executors.newFixedThreadPool(pageItemThreadCount) : Executors.newCachedThreadPool();
	}
	
	public Affiliation getHarvester() {
		return harvester;
	}
	
	private int collectResearcherURLs() throws UnknownHostException, MalformedURLException, UnknownFormatException, IOException, ProtocolException, InterruptedException {		
		final List<Researcher> added = new ArrayList<Researcher>();
		StopWatch sw = new StopWatch();
		sw.start();
		sparqlClient.select(String.format(RESEARCHERS_SELECT, offset, limit), new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext() && !isPaused()) {				
					QuerySolution qs = rs.next();
					String marengoURI = qs.getResource("?s").getURI();
					String personURI = qs.getLiteral("?o").getString();
					
					try {
						URI uri = new URI(personURI);
						Researcher researcher = new Researcher(getAffiliationFor(uri), personURI);
						researcher.setHarvester(getHarvester());
						if (marengoURI.endsWith("Ext")) {
							LOG.info("Avoiding " + marengoURI + " because it appears to be a stub profile page");
							addAvoided(researcher);
							continue;
						}
						if (!store.skip(researcher)) {
							readAndSaveResearcher(researcher);
						}
						else {
							addSkip(researcher);
						}
						setLastFoundAuthor(researcher);
						added.add(researcher);
					}
					catch (Exception e) {
						setLatestError("Error collecting " + personURI + " " + e.getMessage(), e);
						LOG.log(Level.WARNING, "Error collecting " + personURI, e);
					}
				}	
			}
		});
		sw.stop();
		readListTime += sw.getTime();
		// if we are still finding researchers, then return true so we can some back for more
		return added.size();
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
			Integer pmid = getPMID(lir);
			if (pmid != null) {
				researcher.addPubMedPublication(pmid);
			}
		}		
		researcher.setWorkVerifiedDt(Calendar.getInstance());
		return true;
	}
	
	@Cached
	public Integer getPMID(String lir) {
		final AtomicInteger pmid = new AtomicInteger();
		
		sparqlClient.select(String.format(LIR_DETAIL, lir), new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {				
				if (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					pmid.set(qs.getLiteral("?pmid").getInt());
					LOG.info("PMID " + pmid);
				}	
			}
		});
		return pmid.get() > 0 ? pmid.get() : null;
	}
	
	private void readAndSaveResearcher(Researcher researcher) {
		readQueueSize++;
		readResearcherExecutiveService.submit(new ReadResearcherAndPublications(researcher));
	}
	
	private Affiliation getAffiliationFor(URI uri) throws Exception {
		Affiliation affiliation = store.findAffiliationFor(uri.toString());
		return affiliation != null ? affiliation : getAffiliation(uri.getScheme(), uri.getHost());		
	}
	
	@Cached
	public Affiliation getAffiliation(String scheme, String host) throws Exception {
		Affiliation affiliation = new Affiliation(host, scheme + "://" + host, "0,0");
		// this really needs to look for an existing one first, but for now this is OK
		store.save(affiliation);
		LOG.info("Saved " + affiliation);
		return affiliation;
	}
	
	public boolean crawl() throws InterruptedException, UnknownHostException, MalformedURLException, UnknownFormatException, IOException, ProtocolException {
		if (Mode.FORCED_NO_SKIP.equals(getMode())) {
			offset = 0;
		}

		int newlyFound = 0;
		do {
			LOG.info(getCounts());
			LOG.info(getRates());
			if (readQueueSize > limit || pageItemQueueSize > limit) {
				Thread.sleep(10000);
			}
			else {
				newlyFound = collectResearcherURLs();
				offset += limit;
			}
		}
		while (newlyFound > 0 && !isPaused());
		
		readResearcherExecutiveService.shutdown();
		readResearcherExecutiveService.awaitTermination(10, TimeUnit.MINUTES);
		pageItemExecutiveService.shutdown();
		pageItemExecutiveService.awaitTermination(10, TimeUnit.MINUTES);
		offset = 0;
		return false;
	}
	
	private class ReadResearcherAndPublications implements Runnable {
		private Researcher researcher = null;
		
		private ReadResearcherAndPublications(Researcher researcher) {
			this.researcher = researcher;
		}
		
		public void run() {
			StopWatch rrsw = new StopWatch();
			rrsw.start();
			try {
				if (readResearcher(researcher)) {
					setLastReadAuthor(researcher);
					pageItemQueueSize++;
					pageItemExecutiveService.submit(new ReadPageItemsAndSave(researcher));
				}
			}
			catch (Exception e) {
				setLatestError("Error reading details for " + researcher + " " + e.getMessage(), e);
				addError(researcher);
			}
			finally {
				readQueueSize--;
				rrsw.stop();
				readResearcherTime += rrsw.getTime();						
			}
		}
	}

	private class ReadPageItemsAndSave implements Runnable {
		private Researcher researcher = null;
		
		private ReadPageItemsAndSave(Researcher researcher) {
			this.researcher = researcher;
		}
		
		public void run() {
			try {
				StopWatch pisw = new StopWatch();
				pisw.start();
				try {
					reader.getPageItems(researcher);
				}
				catch (Exception e) {
					setLatestError("Error reading page items for " + researcher + " " + e.getMessage(), e);
					addError(researcher);
				}
				finally {
					pisw.stop();
					pageItemsTime += pisw.getTime();						
				}
				save(researcher);
			}
			catch (Exception e) {
				setLatestError("Error with " + researcher + " " + e.getMessage(), e);
				addError(researcher);
			}
			finally {
				pageItemQueueSize--;
			}
		}
	}

	public String getName() {
		return getHarvester().getName();
	}

	@Override
	public String getCounts() {
		return super.getCounts() + ", ReadQueue " + readQueueSize + ", PageItemQueue " + pageItemQueueSize + ", Limit = " + limit;
	}
	
	@Override
	public String getRates() {
		int saved = Math.max(1, getSavedCnt());
		return super.getRates() + 
				", Query/person : " + PeriodFormat.getDefault().print(new Period(readListTime/saved)) +
				", Read/person : " + PeriodFormat.getDefault().print(new Period(readResearcherTime/saved)) + 
				", PageItems/person : " +  PeriodFormat.getDefault().print(new Period(pageItemsTime/saved));
	}
}
