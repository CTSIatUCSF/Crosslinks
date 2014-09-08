package edu.ucsf.crosslink.crawler;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

import com.google.inject.Inject;
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
			"?aia <" + VIVO + "linkedInformationResource> ?lir  . OPTIONAL { " +
			"}}";	

	private static final String LIR_DETAIL = "SELECT ?pmid WHERE { " +
			"<%s> <" + BIBO_PMID + "> ?pmid}";
	
	private static final int LIMIT = 50;

	private SparqlClient sparqlClient = null;
	private static final String MARENGO_PREFIX = "http://marengo.info-science.uiowa.edu:2020/resource/";
	
	private Affiliation harvester;
	private SiteReader reader;
	private CrosslinkPersistance store;
	private ExecutorService executorService;
	
	private int savedCnt = 0;
	private int queueSize = 0;
	private int found = 0;
	private int offset = 0;
	private int limit = LIMIT;
	
	// remove harvester as required item
	@Inject
	public MarengoSparqlCrawler(Affiliation harvester, SiteReader reader, CrosslinkPersistance store, Mode crawlingMode) throws Exception {
		super(crawlingMode);
		this.harvester = harvester;
		this.sparqlClient = new SparqlClient("http://marengo.info-science.uiowa.edu:2020/sparql");
		this.reader = reader;
		this.store = store;
		this.store.upsertAffiliation(harvester);
		executorService = Executors.newCachedThreadPool();
	}
	
	public Affiliation getHarvester() {
		return harvester;
	}
	
	private int collectResearcherURLs() throws UnknownHostException, MalformedURLException, UnknownFormatException, IOException, ProtocolException, InterruptedException {		
		final List<Researcher> added = new ArrayList<Researcher>();
		sparqlClient.select(String.format(RESEARCHERS_SELECT, offset, limit), new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext()) {				
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
							LOG.info("Added " + personURI);
						}
						else {
							LOG.info("Skipping " + researcher);
						}
						setCurrentAuthor(researcher);
						added.add(researcher);
					}
					catch (Exception e) {
						setLatestError("Error collecting " + personURI + " " + e.getMessage());
						LOG.log(Level.WARNING, "Error collecting " + personURI, e);
					}
				}								
			}
		});
		// if we are still finding researchers, then return true so we can some back for more
		return added.size();
    }

	public boolean readResearcher(final Researcher researcher) throws Exception {
		sparqlClient.select(String.format(RESEARCHER_DETAIL, MARENGO_PREFIX + researcher.getURI()), new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					String label = qs.getLiteral("?l").getString();
					String firstName = qs.getLiteral("?fn").getString();
					String lastName = qs.getLiteral("?ln").getString();
					String orcidId = qs.getLiteral("?orcid").getString();
					researcher.setLabel(label);
					if (StringUtils.isNotBlank(firstName)) {
						researcher.setLiteral(FOAF + "firstName", firstName);
					}
					if (StringUtils.isNotBlank(firstName)) {
						researcher.setLiteral(FOAF + "lastName", lastName);
					}
					if (StringUtils.isNotBlank(orcidId)) {
						researcher.setOrcidId(orcidId);
					}
					LOG.info("Read " + label + " : " + firstName + " : " + lastName);
				}								
			}
		});

		final List<String> lirs = new ArrayList<String>();
		sparqlClient.select(String.format(RESEARCHER_PUBLICATIONS, MARENGO_PREFIX + researcher.getURI()), new ResultSetConsumer() {
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
			Integer pmid = getPMID(lir);
			if (pmid != null) {
				researcher.addPubMedPublication(pmid);
			}
		}		
		researcher.setWorkVerifiedDt(new Date());
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
		executorService.submit(new ReadAndSaveResearcher(researcher));
		queueSize++;
	}
	
	private Affiliation getAffiliationFor(URI uri) throws Exception {
		Affiliation affiliation = store.findAffiliationFor(uri.toString());
		return affiliation != null ? affiliation : getAffiliation(uri.getScheme(), uri.getHost());		
	}
	
	@Cached
	public Affiliation getAffiliation(String scheme, String host) throws Exception {
		Affiliation affiliation = new Affiliation(host, scheme + "://" + host, "0,0");
		// this really needs to look for an existing one first, but for now this is OK
		store.upsertAffiliation(affiliation);
		LOG.info("Upserted " + affiliation);
		return affiliation;
	}
	
	public void crawl() {
		if (Mode.FORCED_NO_SKIP.equals(getMode())) {
			offset = 0;
		}
		setStatus(Status.RUNNING);
		try {
			do {
				LOG.info(getCounts());
				if (queueSize > limit) {
					Thread.sleep(10000);
				}
				else {
					int newlyFound = collectResearcherURLs();
					if (newlyFound > 0) {
						found += newlyFound;
					}
					else {
						setStatus(Status.SHUTTING_DOWN);
					}
					offset += limit;
				}
			}
			while(Status.RUNNING.equals(getStatus()));
			executorService.shutdown();
			executorService.awaitTermination(10, TimeUnit.MINUTES);
			offset = 0;
			clear();
			setStatus(Status.FINISHED);
		}
		catch (Exception e) {
			setStatus(Status.PAUSED);
			setLatestError("Error while running " + e.getMessage());
			LOG.log(Level.SEVERE, e.getMessage(), e);
		}
	}
	
	private class ReadAndSaveResearcher implements Runnable {
		private Researcher researcher = null;
		
		private ReadAndSaveResearcher(Researcher researcher) {
			this.researcher = researcher;
		}
		
		public void run() {
			try {
				if (readResearcher(researcher)) {
					try {
						reader.getPageItems(researcher);
					}
					catch (Exception e) {
						setLatestError("Error reading page items for " + researcher + " " + e.getMessage());
						addError(researcher);
						LOG.log(Level.SEVERE, e.getMessage(), e);										
					}
					store.saveResearcher(researcher);
					savedCnt++;
					LOG.info("Saved " + researcher);
				}
			}
			catch (Exception e) {
				setLatestError("Error with " + researcher + " " + e.getMessage());
				addError(researcher);
				LOG.log(Level.SEVERE, e.getMessage(), e);				
			}
			finally {
				queueSize--;
			}
		}
	}

	public String getName() {
		return getHarvester().getName();
	}

	@Override
	public String getCounts() {
		return super.getCounts() + ", Found : " + found + ", Offset : " + offset + ", Limit : " + limit + ", Saved : " + savedCnt + ", Queue : " + queueSize;
	}

}
