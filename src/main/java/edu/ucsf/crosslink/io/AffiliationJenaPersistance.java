package edu.ucsf.crosslink.io;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hp.cache4guice.Cached;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.R2ROntology;
import edu.ucsf.ctsi.r2r.jena.SparqlUpdateClient;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;

public class AffiliationJenaPersistance implements CrosslinkPersistance, R2RConstants {

	private static final Logger LOG = Logger.getLogger(AffiliationJenaPersistance.class.getName());

	private Affiliation affiliation;
	private SparqlUpdateClient sparqlClient;
	private Integer daysConsideredOld;
	private ThumbnailGenerator thumbnailGenerator;
	private final Map<String, Long> recentlyProcessedAuthors = new HashMap<String, Long>();
	
	private List<String> existingSparql = null;
	
	private static final String SKIP_RESEARCHERS_SPARQL = "SELECT ?r ?ts WHERE {?r <" + R2R_HARVESTED_FROM + "> <%s> . " +
			"?r <" + R2R_WORK_VERIFIED_DT + "> ?ts . FILTER (?ts > %d)}";
	
	private static final String SKIP_RESEARCHER_SPARQL = "ASK  {<%s> <" + R2R_WORK_VERIFIED_DT + "> ?ts . " +
			"FILTER (?ts > %d)}";

	@Inject
	public AffiliationJenaPersistance(Affiliation affiliation, SparqlUpdateClient sparqlClient, 
			@Named("daysConsideredOld") Integer daysConsideredOld) throws Exception {
    	// create affiliation. Should we add latitude and longitude to this?
		this.affiliation = affiliation;
		this.sparqlClient = sparqlClient;
		this.daysConsideredOld = daysConsideredOld;
		upsertAffiliation(affiliation);
	}
	
	// ugly
	public void upsertAffiliation(Affiliation affiliation) throws Exception {
		Model model = R2ROntology.createDefaultModel();
		model.add(sparqlClient.describe(affiliation.getURI()));
		Resource resource = model.createResource(affiliation.getURI());
		replace(model, resource, model.createProperty(RDFS_LABEL), model.createTypedLiteral(affiliation.getName()));
		replace(model, resource, model.createProperty(RDF_TYPE), model.createResource(R2R_AFFILIATION));
		replace(model, resource, model.createProperty(PRNS_LATITUDE), model.createTypedLiteral(affiliation.getLatitude()));
		replace(model, resource, model.createProperty(PRNS_LONGITUDE),	model.createTypedLiteral(affiliation.getLongitude()));
		// remove the old one first.  This will not affect any researchers
		sparqlClient.deleteSubject(affiliation.getURI());
		sparqlClient.add(resource);		
	}
	
	private static void replace(Model model, Resource s, Property p, RDFNode n) {
		model.removeAll(s, p, null);
		model.add(s, p, n);
	}
	
	@Inject
	public void setThumbnailGenerator(ThumbnailGenerator thumbnailGenerator) {
		this.thumbnailGenerator = thumbnailGenerator;
	}
		
	public void start() throws Exception {
		Date now = updateTimestampFieldFor(affiliation.getURI(), R2R_CRAWL_START_DT);
		
		String sparql = String.format(SKIP_RESEARCHERS_SPARQL, affiliation.getURI(), new DateTime(now).minusDays(daysConsideredOld).getMillis());
		LOG.info(sparql);
		sparqlClient.select(sparql, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					recentlyProcessedAuthors.put(qs.getResource("?r").getURI(), qs.getLiteral("?ts").getLong());
				}								
			}
		});
		LOG.info("Found " + recentlyProcessedAuthors.size() + " recently processed authors");
	}

	public void finish() throws Exception {
		updateTimestampFieldFor(affiliation.getURI(), R2R_CRAWL_END_DT);
		// sparql out the ones we did not find
		String sparql = "DELETE {?s ?p ?o} WHERE { >" + affiliation.getURI() + "> <" + R2R_CRAWL_START_DT + "> ?cst . " +
			"?s <" + R2R_HARVESTED_FROM + "> <" + affiliation.getURI() + "> . " +
			"?s <" + R2R_VERIFIED_DT + "> ?ta FILTER(?ta < ?cst) ?s ?p ?o}";
		update(sparql);
		// clear the list
		recentlyProcessedAuthors.clear();
	}

	public void saveResearcher(Researcher researcher) throws Exception {
		if (thumbnailGenerator != null) {
			thumbnailGenerator.generateThumbnail(researcher);
		}		
		sparqlClient.startTransaction();
		delete(researcher, R2R_HARVESTED_FROM);
		delete(researcher, R2R_HAS_AFFILIATION);
		delete(researcher, R2R_PRETTY_URL);
		delete(researcher, R2R_THUMBNAIL);
		delete(researcher, R2R_VERIFIED_DT);
		delete(researcher, R2R_WORK_VERIFIED_DT);
		delete(researcher, R2R_CONTRIBUTED_TO);
		// see if ontology handles Foaf name correctly since we do not remove it
		sparqlClient.add(researcher.getResource());
		sparqlClient.endTransaction();
		// add to processed list
		recentlyProcessedAuthors.put(researcher.getURI(), new Date().getTime());
	}

	public Date dateOfLastCrawl() {
		String sparql = "SELECT ?dt WHERE {<" + affiliation.getURI() + "> <" + R2R_CRAWL_END_DT + "> ?dt}";
		DateResultSetConsumer consumer = new DateResultSetConsumer();
		sparqlClient.select(sparql, consumer);
		return consumer.getDate();
	}

	public boolean skip(Researcher researcher) {
		Long ts = recentlyProcessedAuthors.get(researcher.getURI());
		if (ts != null) {
			return ts > new DateTime().minusDays(daysConsideredOld).getMillis();
		}
		else {
//			String sparql = String.format(SKIP_RESEARCHER_SPARQL, researcher.getURI(), new DateTime().minusDays(daysConsideredOld).getMillis());
//			LOG.info(sparql);
//			return fusekiClient.ask(sparql);
			final AtomicLong wvdt = new AtomicLong();
			sparqlClient.select(String.format("SELECT ?ts WHERE {<%s> <" + R2R_WORK_VERIFIED_DT + "> ?ts}", researcher.getURI()), new ResultSetConsumer() {
				public void useResultSet(ResultSet rs) {
					if (rs.hasNext()) {				
						QuerySolution qs = rs.next();
						wvdt.set(qs.getLiteral("?ts").getLong());
					}								
				}
			});
			return wvdt.get() > new DateTime().minusDays(daysConsideredOld).getMillis();
		}
	}
	
	@Cached
	public Long getWorkVerifiedDT(String uri) {
		return recentlyProcessedAuthors.get(uri);		
	}

	// update the researcherVerifiedDT
	public int touch(Researcher researcher) throws Exception {
		String uri = researcher.getURI();
		if (uri != null) {
			updateTimestampFieldFor(researcher.getURI(), R2R_VERIFIED_DT);
		}
    	return 0;
	}
	
	private void delete(Researcher researcher, String predicate) throws Exception {
		if (researcher.getURI() != null) {
			String sparql = "DELETE WHERE { <" + researcher.getURI() + ">  <" + predicate+ "> ?o }";	
	    	update(sparql);
		}
	}

	private Date updateTimestampFieldFor(String subjectUri, String predicate) throws Exception {
		// delete the old one
		Date now = new Date();
		Model model = R2ROntology.createDefaultModel();
		Property p = model.createProperty(predicate);
    	String delete = "DELETE WHERE { <" + subjectUri + ">  <" + p.getURI()+ "> ?dt }";	
    	String insert = "INSERT DATA { <" + subjectUri + ">  <" + p.getURI()+ "> " + now.getTime() + " }";

    	List<String> sparql = new ArrayList<String>();
    	sparql.add(delete);
    	sparql.add(insert);
    	update(sparql);
    	return now;
	}
	

	public void close() {
		// TODO Auto-generated method stub
		
	}
	
	public Collection<Researcher> getResearchers() {
		return null;
	}

	private class DateResultSetConsumer implements ResultSetConsumer {
		private Date dt = null;
		
		public void useResultSet(ResultSet rs) {
			if (rs.hasNext()) {				
				QuerySolution qs = rs.next();
				dt = qs.getLiteral("?dt") != null ? new Date(qs.getLiteral("?dt").getLong()) : null;
			}				
		}	
		
		public Date getDate() {
			return dt;
		}		
	}
	
	private void update(List<String> sparql) throws Exception {
		if (existingSparql == null) {
			sparqlClient.update(sparql);
		}
		else {
			existingSparql.addAll(sparql);
		}
	}

	private void update(String sparql) throws Exception {
		if (existingSparql == null) {
			sparqlClient.update(sparql);
		}
		else {
			existingSparql.add(sparql);
		}
	}

	public synchronized void startTransaction() {
		if (existingSparql == null) {
			existingSparql = new ArrayList<String>();
		}
		
	}

	public synchronized void endTransaction() throws Exception {
		sparqlClient.update(existingSparql);
		existingSparql = null;
	}
}
