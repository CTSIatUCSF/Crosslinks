package edu.ucsf.crosslink.io;

import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;

import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.R2RResourceObject;
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
			"?r <" + R2R_WORK_VERIFIED_DT + "> ?ts . FILTER (?ts > \"%s\")}";
	
	private static final String LOAD_AFFILIATIONS = "SELECT ?r ?l WHERE  {?r <" + RDF_TYPE + "> <" +
			R2R_AFFILIATION + "> . ?r <" + RDFS_LABEL + "> ?l}";

	@Inject
	public SparqlPersistance(SparqlUpdateClient sparqlClient, 
			@Named("daysConsideredOld") Integer daysConsideredOld) throws Exception {
		this.sparqlClient = sparqlClient;
		this.daysConsideredOld = daysConsideredOld;
		// make sure we have the latest model
		sparqlClient.add(R2ROntology.createR2ROntModel());
		// by loading these now, we make sure that we do not collide with calls to upsertAffiliation
		loadAffiliations();
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

	public void save(Affiliation affiliation) throws Exception {
		saveInternal(affiliation, true);
	}
	
	public void save(Researcher researcher) throws Exception {
		if (thumbnailGenerator != null) {
			thumbnailGenerator.generateThumbnail(researcher);
		}		
		saveInternal(researcher, true);
	}

	public void update(Researcher researcher) throws Exception {
		saveInternal(researcher, false);
	}

	// delete existing one first
	private void saveInternal(R2RResourceObject robj, boolean deleteFirst) throws Exception {
		startTransaction();
		for (Resource resource : robj.getResources()) {
			if (deleteFirst) {
				sparqlClient.deleteSubject(resource.getURI());				
			}
			sparqlClient.add(resource);		
		}
		endTransaction();
	}
	
	public Affiliation findAffiliationFor(String uri) {
		for (Affiliation affiliation : knownAffiliations) {
			if (uri.toLowerCase().startsWith(affiliation.getURI().toLowerCase())) {
				return affiliation;
			}
		}
		return null;
	}

	@Inject
	public void setThumbnailGenerator(ThumbnailGenerator thumbnailGenerator) {
		this.thumbnailGenerator = thumbnailGenerator;
	}
		
	public Calendar startCrawl(Affiliation affiliation) throws Exception {
		return updateTimestampFieldFor(affiliation.getURI(), R2R_CRAWL_START_DT);
	}

	public Map<String, Long> loadRecentlyHarvestedResearchers(Affiliation affiliation) throws Exception {
		final Map<String, Long> recentlyProcessedAuthors = new HashMap<String, Long>();
		
		String sparql = String.format(SKIP_RESEARCHERS_SPARQL, affiliation.getURI(), 
				R2ROntology.createDefaultModel().createTypedLiteral(new DateTime().minusDays(daysConsideredOld).toGregorianCalendar()));
		LOG.info(sparql);
		sparqlClient.select(sparql, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					recentlyProcessedAuthors.put(qs.getResource("?r").getURI(), ((XSDDateTime)qs.getLiteral("?ts").getValue()).asCalendar().getTimeInMillis());
				}								
			}
		});
		LOG.info("Found " + recentlyProcessedAuthors.size() + " recently processed authors");
		return recentlyProcessedAuthors;
	}

	public Calendar finishCrawl(Affiliation affiliation) throws Exception {
		return updateTimestampFieldFor(affiliation.getURI(), R2R_CRAWL_END_DT);
	}

	public void deleteMissingResearchers(Affiliation affiliation) throws Exception {
		// sparql out the ones we did not find
		String sparql = "DELETE {?s ?p ?o} WHERE { <" + affiliation.getURI() + "> <" + R2R_CRAWL_START_DT + "> ?cst . " +
			"?s <" + R2R_HARVESTED_FROM + "> <" + affiliation.getURI() + "> . " +
			"?s <" + R2R_VERIFIED_DT + "> ?ta FILTER(?ta < ?cst) ?s ?p ?o}";
		sparqlClient.update(sparql);
	}

	public Calendar dateOfLastCrawl(Affiliation affiliation) {
		String sparql = "SELECT ?dt WHERE {<" + affiliation.getURI() + "> <" + R2R_CRAWL_END_DT + "> ?dt}";
		DateResultSetConsumer consumer = new DateResultSetConsumer();
		sparqlClient.select(sparql, consumer);
		return consumer.getCalendar();
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
					wvdt.set(((XSDDateTime)qs.getLiteral("?ts").getValue()).asCalendar().getTimeInMillis());
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
	
	// TODO clean this up!
	private Calendar updateTimestampFieldFor(String subjectUri, String predicate) throws Exception {
		// delete the old one
		Calendar now = Calendar.getInstance();
		Model model = R2ROntology.createDefaultModel();
		Resource r = model.createResource(subjectUri);
		Property p = model.createProperty(predicate);
		model.addLiteral(r, p,  model.createTypedLiteral(now));		
    	startTransaction();
    	sparqlClient.update( "DELETE WHERE { <" + subjectUri + ">  <" + p.getURI()+ "> ?dt }");
    	sparqlClient.add(model);
    	endTransaction();
    	return now;
	}
	
	public Collection<Researcher> getResearchers() {
		return null;
	}

	private class DateResultSetConsumer implements ResultSetConsumer {
		private Calendar dt = null;
		
		public void useResultSet(ResultSet rs) {
			if (rs.hasNext()) {				
				QuerySolution qs = rs.next();
				dt = qs.getLiteral("?dt") != null ? ((XSDDateTime)qs.getLiteral("?dt").getValue()).asCalendar() : null;
			}				
		}	
		
		public Calendar getCalendar() {
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
