package edu.ucsf.crosslink.io;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.R2ROntology;
import edu.ucsf.ctsi.r2r.jena.SparqlUpdateClient;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;

public class SparqlPersistance implements CrosslinkPersistance, R2RConstants {

	private static final Logger LOG = Logger.getLogger(SparqlPersistance.class.getName());

	private SparqlUpdateClient sparqlClient;
	private Integer daysConsideredOld;
	private ThumbnailGenerator thumbnailGenerator;
	private Set<Affiliation> knownAffiliations = new HashSet<Affiliation>();
	
	private static final String SKIP_RESEARCHERS_SPARQL = "SELECT ?r ?ts WHERE {?r <" + R2R_HARVESTED_FROM + "> <%s> . " +
			"?r <" + R2R_WORK_VERIFIED_DT + "> ?ts . FILTER (?ts > %d)}";
	
	private static final String LOAD_AFFILIATIONS = "SELECT ?r ?l WHERE  {?r <" + RDF_TYPE + "> <" +
			R2R_AFFILIATION + "> . ?r <" + RDFS_LABEL + "> ?l}";

	private static final String SKIP_RESEARCHER_SPARQL = "ASK  {<%s> <" + R2R_WORK_VERIFIED_DT + "> ?ts . " +
			"FILTER (?ts > %d)}";

	@Inject
	public SparqlPersistance(SparqlUpdateClient sparqlClient, 
			@Named("daysConsideredOld") Integer daysConsideredOld) throws Exception {
		this.sparqlClient = sparqlClient;
		this.daysConsideredOld = daysConsideredOld;
		loadAffiliations();
	}
	
	public void upsertAffiliation(Affiliation affiliation) throws Exception {
		sparqlClient.startTransaction();
		Model model = R2ROntology.createR2ROntModel();
		model.add(sparqlClient.describe(affiliation.getBaseURL()));
		Resource resource = model.createResource(affiliation.getBaseURL());
		replace(model, resource, model.createProperty(RDFS_LABEL), model.createTypedLiteral(affiliation.getName()));
		replace(model, resource, model.createProperty(RDF_TYPE), model.createResource(R2R_AFFILIATION));
		replace(model, resource, model.createProperty(PRNS_LATITUDE), model.createTypedLiteral(affiliation.getLatitude()));
		replace(model, resource, model.createProperty(PRNS_LONGITUDE),	model.createTypedLiteral(affiliation.getLongitude()));
		// remove the old one first.  This will not affect any researchers
		sparqlClient.deleteSubject(affiliation.getBaseURL());
		sparqlClient.add(resource);		
		sparqlClient.endTransaction();
	}
	
	private void loadAffiliations() {
		sparqlClient.select(LOAD_AFFILIATIONS, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					try {
						knownAffiliations.add(new Affiliation(qs.getLiteral("?l").getString(), 
								qs.getResource("?r").getURI(), null));
					} 
					catch (URISyntaxException e) {
						LOG.log(Level.SEVERE, e.getMessage(), e);
					}
				}								
			}
		});		
	}

	public Affiliation findAffiliationFor(String uri) {
		for (Affiliation affiliation : knownAffiliations) {
			if (uri.toLowerCase().startsWith(affiliation.getBaseURL().toLowerCase())) {
				return affiliation;
			}
		}
		return null;
	}

	private static void replace(Model model, Resource s, Property p, RDFNode n) {
		model.removeAll(s, p, null);
		model.add(s, p, n);
	}
	
	@Inject
	public void setThumbnailGenerator(ThumbnailGenerator thumbnailGenerator) {
		this.thumbnailGenerator = thumbnailGenerator;
	}
		
	public Map<String, Long> startCrawl(Affiliation affiliation) throws Exception {
		final Map<String, Long> recentlyProcessedAuthors = new HashMap<String, Long>();
		Date now = updateTimestampFieldFor(affiliation.getBaseURL(), R2R_CRAWL_START_DT);
		
		String sparql = String.format(SKIP_RESEARCHERS_SPARQL, affiliation.getBaseURL(), new DateTime(now).minusDays(daysConsideredOld).getMillis());
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
		return recentlyProcessedAuthors;
	}

	public void finishCrawl(Affiliation affiliation) throws Exception {
		updateTimestampFieldFor(affiliation.getBaseURL(), R2R_CRAWL_END_DT);
		// sparql out the ones we did not find
		String sparql = "DELETE {?s ?p ?o} WHERE { <" + affiliation.getBaseURL() + "> <" + R2R_CRAWL_START_DT + "> ?cst . " +
			"?s <" + R2R_HARVESTED_FROM + "> <" + affiliation.getBaseURL() + "> . " +
			"?s <" + R2R_VERIFIED_DT + "> ?ta FILTER(?ta < ?cst) ?s ?p ?o}";
		sparqlClient.update(sparql);
	}

	public void saveResearcher(Researcher researcher) throws Exception {
		if (thumbnailGenerator != null) {
			thumbnailGenerator.generateThumbnail(researcher);
		}		
		startTransaction();
		
		delete(researcher, RDFS_LABEL);
		delete(researcher, R2R_HARVESTED_FROM);
		delete(researcher, R2R_HAS_AFFILIATION);
		delete(researcher, R2R_CONTRIBUTED_TO);
		delete(researcher, R2R_WORK_VERIFIED_DT);
		delete(researcher, VIVO_ORCID_ID);
		delete(researcher, FOAF + "firstName");
		delete(researcher, FOAF + "lastName");

		// only remove these if we have new ones
		if (researcher.getVerifiedDt() != null) {
			delete(researcher, R2R_VERIFIED_DT);
		}
		if (researcher.getPrettyURL() != null) {
			delete(researcher, R2R_PRETTY_URL);
		}
		if (researcher.getImageURL() != null) {
			delete(researcher, PRNS_MAIN_IMAGE);
		}
		if (researcher.getThumbnailURL() != null) {
			delete(researcher, R2R_THUMBNAIL);
		}
		// see if ontology handles Foaf name correctly since we do not remove it
		sparqlClient.add(researcher.getResource());
		endTransaction();
	}

	public Date dateOfLastCrawl(Affiliation affiliation) {
		String sparql = "SELECT ?dt WHERE {<" + affiliation.getBaseURL() + "> <" + R2R_CRAWL_END_DT + "> ?dt}";
		DateResultSetConsumer consumer = new DateResultSetConsumer();
		sparqlClient.select(sparql, consumer);
		return consumer.getDate();
	}

	public boolean skip(Researcher researcher) {
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
	
	// update the researcherVerifiedDT
	public int touch(Researcher researcher) throws Exception {
		String uri = researcher.getURI();
		if (uri != null) {
			updateTimestampFieldFor(researcher.getURI(), R2R_WORK_VERIFIED_DT);
		}
    	return 0;
	}
	
	private void delete(Researcher researcher, String predicate) throws Exception {
		if (researcher.getURI() != null) {
			String sparql = "DELETE WHERE { <" + researcher.getURI() + ">  <" + predicate+ "> ?o }";	
			sparqlClient.update(sparql);
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
    	sparqlClient.update(sparql);
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
	
	public void startTransaction() {
		sparqlClient.startTransaction();
	}

	public void endTransaction() throws Exception {
		sparqlClient.endTransaction();
	}
}
