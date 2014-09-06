package edu.ucsf.crosslink.crawler.sitereader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

import com.google.inject.Inject;
import com.hp.cache4guice.Cached;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;

import edu.ucsf.crosslink.crawler.AffiliationCrawler.Status;
import edu.ucsf.crosslink.crawler.Crawler;
import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;
import edu.ucsf.ctsi.r2r.jena.SparqlClient;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.R2ROntology;
import net.sourceforge.sitemaps.Sitemap;
import net.sourceforge.sitemaps.SitemapParser;
import net.sourceforge.sitemaps.SitemapUrl;
import net.sourceforge.sitemaps.UnknownFormatException;
import net.sourceforge.sitemaps.http.ProtocolException;

public class MarengoSparqlReader extends SiteReader implements Crawler, AuthorParser, R2RConstants {

	private static final Logger LOG = Logger.getLogger(MarengoSparqlReader.class.getName());

	private static final String RESEARCHERS_SELECT = "SELECT ?s ?o WHERE { " +
			"?s <http://marengo.info-science.uiowa.edu:2020/resource/vocab/Person_URI> ?o } OFFSET %d LIMIT %d";	
	
	private static final String RESEARCHER_DETAIL = "SELECT ?l ?fn ?ln WHERE { " +
			"<%1$s> <" + RDFS_LABEL + "> ?l ." +
			"<%1$s> <" + FOAF + "firstName> ?fn ." +
			"<%1$s> <" + FOAF + "lastName> ?ln}";	

	private static final String RESEARCHER_PUBLICATIONS = "SELECT ?lir WHERE { " +
			"?aia <http://vivoweb.org/ontology/core#linkedAuthor> <%s> . " +
			"?aia <" + VIVO + "linkedInformationResource> ?lir  . OPTIONAL { " +
			"}}";	

	private static final String LIR_DETAIL = "SELECT ?pmid WHERE { " +
			"<%s> <" + BIBO_PMID + "> ?pmid}";

	private SparqlClient sparqlClient = null;
	private static final String MARENGO_PREFIX = "http://marengo.info-science.uiowa.edu:2020/resource/";
	
	private CrosslinkPersistance store;
	private ExecutorService executorService;
	private int savedCnt = 0;
	private int queueSize = 0;
	private boolean running = false;
	
	// remove harvester as required item
	@Inject
	public MarengoSparqlReader(Affiliation harvester, CrosslinkPersistance store) {
		super(harvester);
		this.sparqlClient = new SparqlClient("http://marengo.info-science.uiowa.edu:2020/sparql");
		this.store = store;
		executorService = Executors.newCachedThreadPool();
	}
	
	protected void collectResearcherURLs() throws UnknownHostException, MalformedURLException, UnknownFormatException, IOException, ProtocolException, InterruptedException {
		collectResearcherURLs(0, 1000);
	}

	private int collectResearcherURLs(int offset, int limit) throws UnknownHostException, MalformedURLException, UnknownFormatException, IOException, ProtocolException, InterruptedException {		
		final List<Researcher> added = new ArrayList<Researcher>();
		sparqlClient.select(String.format(RESEARCHERS_SELECT, offset, limit), new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					String marengoURI = qs.getResource("?s").getURI();
					String personURI = qs.getLiteral("?o").getString();
					
					try {
						URI uri = new URI(personURI);
						Researcher researcher = new Researcher(getAffiliation(uri.getScheme(), uri.getHost()), personURI);
						researcher.setHarvester(getHarvester());
						//addResearcher(researcher);
						if (!store.skip(researcher)) {
							readAndSaveResearcher(researcher);
							LOG.info("Added " + personURI);
						}
						else {
							LOG.info("Skipping " + researcher);
						}
						added.add(researcher);
					}
					catch (Exception e) {
						LOG.log(Level.WARNING, "Error with " + personURI, e.getMessage());
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
					researcher.setLabel(label);
					if (StringUtils.isNotBlank(firstName)) {
						researcher.addLiteral(FOAF + "firstName", firstName);
					}
					if (StringUtils.isNotBlank(firstName)) {
						researcher.addLiteral(FOAF + "lastName", lastName);
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
	
	@Cached
	public Affiliation getAffiliation(String scheme, String host) throws Exception {
		Affiliation affiliation = new Affiliation(host, scheme + "://" + host, "0,0");
		// this really needs to look for an existing one first, but for now this is OK
		store.startTransaction();
		store.upsertAffiliation(affiliation);
		store.endTransaction();
		LOG.info("Upserted " + affiliation);
		return affiliation;
	}
	
	public void run() {
		// load runners
		running = true;
		int found = 0;
		int offset = 0;
		int limit = 50;
		try {
			do {
				LOG.info("found, offset, limit, saved, queue " + found + ", " + offset + ", " + limit + ", " + savedCnt + ", " + queueSize);
				if (queueSize > limit) {
					Thread.sleep(10000);
				}
				else {
					int newlyFound = collectResearcherURLs(offset, limit);
					if (newlyFound > 0) {
						found += newlyFound;
					}
					else {
						running = false;
					}
					offset += limit;
				}
			}
			while(running);
			executorService.shutdown();
			executorService.awaitTermination(10, TimeUnit.MINUTES);
		}
		catch (Exception e) {
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
					store.saveResearcher(researcher);
					savedCnt++;
					LOG.info("Saved " + researcher);
				}
			}
			catch (Exception e) {
				LOG.log(Level.SEVERE, e.getMessage(), e);				
			}
			finally {
				queueSize--;
			}
		}
	}

	public int compareTo(Crawler o) {
		// TODO Auto-generated method stub
		return 0;
	}

	public String getName() {
		return getHarvester().getName();
	}

	public void setMode(String mode) throws Exception {
		// TODO Auto-generated method stub
		
	}
	
}
