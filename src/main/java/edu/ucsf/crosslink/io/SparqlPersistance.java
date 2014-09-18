package edu.ucsf.crosslink.io;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
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
import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.StmtIterator;

import edu.ucsf.crosslink.crawler.Crawler;
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
	private Set<Affiliation> knownAffiliations = new HashSet<Affiliation>();
	
	private static final String SKIP_RESEARCHERS_SPARQL = "SELECT ?r ?ts WHERE {?r <" + R2R_HARVESTED_FROM + "> <%s> . " +
			"?r <" + R2R_WORK_VERIFIED_DT + "> ?ts . FILTER (?ts > \"%s\")}";
	
	private static final String LOAD_AFFILIATIONS = "SELECT ?r ?l WHERE  {?r <" + RDF_TYPE + "> <" +
			R2R_AFFILIATION + "> . ?r <" + RDFS_LABEL + "> ?l}";
	
	private enum SaveType {SAVE, UPDATE, ADD}; 

	@Inject
	public SparqlPersistance(SparqlUpdateClient sparqlClient) throws Exception {
		this.sparqlClient = sparqlClient;
		// make sure we have the latest model
		sparqlClient.add(R2ROntology.createR2ROntModel());
		sparqlClient.update("CREATE GRAPH <" + R2R_DERIVED_GRAPH + ">");
		// by loading these now, we make sure that we do not collide with calls to upsertAffiliation
		loadAffiliations();
	}
	
	private void loadAffiliations() throws Exception {
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

	public void save(R2RResourceObject robj) throws Exception {
		saveInternal(robj, SaveType.SAVE);
	}
	
	public void update(R2RResourceObject robj) throws Exception {
		saveInternal(robj, SaveType.UPDATE);
	}
	
	public void add(R2RResourceObject robj) throws Exception {
		saveInternal(robj, SaveType.ADD);
	}

	public void execute(List<String> updates) throws Exception {
		startTransaction();
		sparqlClient.update(updates);
		endTransaction();
	}
	// delete existing one first
	private void saveInternal(R2RResourceObject robj, SaveType saveType) throws Exception {
		startTransaction();
		for (Resource resource : robj.getResources()) {
			if (SaveType.SAVE.equals(saveType)) {
				sparqlClient.deleteSubject(resource.getURI());				
			}
			else if (SaveType.UPDATE.equals(saveType)) {
				// only delete the properties that this resource has maxCardinalityRestrictions on
				// do as set so that we do not do the same thing more than once
				Set<String> deletes = new HashSet<String>();
				StmtIterator si = resource.listProperties();
				while (si.hasNext()) {
					Property prop = si.next().getPredicate();
					if (robj.hasMaxCardinalityRestriction(prop.getURI())) {
						deletes.add("DELETE WHERE { <" + resource.getURI() + ">  <" + prop.getURI() + "> ?o }");
					}
				}
				sparqlClient.update(new ArrayList<String>(deletes));
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

	public Calendar startCrawl(Crawler crawler) throws Exception {
		return updateTimestampFieldFor(crawler.getURI(), R2R_CRAWL_START_DT);
	}

	public Map<String, Long> loadRecentlyHarvestedResearchers(Affiliation affiliation, int daysConsideredOld) throws Exception {
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

	public Calendar finishCrawl(Crawler crawler) throws Exception {
		return updateTimestampFieldFor(crawler.getURI(), R2R_CRAWL_END_DT);
	}

	public void deleteMissingResearchers(Crawler crawler) throws Exception {
		// sparql out the ones we did not find
		String sparql = "DELETE {?s ?p ?o} WHERE { <" + crawler.getURI() + "> <" + R2R_CRAWL_START_DT + "> ?cst . " +
			"?s <" + R2R_HARVESTED_FROM + "> <" + crawler.getURI() + "> . " +
			"?s <" + R2R_VERIFIED_DT + "> ?ta FILTER(?ta < ?cst) ?s ?p ?o}";
		sparqlClient.update(sparql);
	}

	public Calendar dateOfLastCrawl(Crawler crawler) throws Exception {
		String sparql = "SELECT ?dt WHERE {<" + crawler.getURI() + "> <" + R2R_CRAWL_END_DT + "> ?dt}";
		DateResultSetConsumer consumer = new DateResultSetConsumer();
		sparqlClient.select(sparql, consumer);
		return consumer.getCalendar();
	}

	public boolean skip(String researcherURI, String timestampField, int daysConsideredOld) throws Exception {
		final AtomicLong dt = new AtomicLong();
		sparqlClient.select(String.format("SELECT ?ts WHERE {<%1$s> <" + timestampField + "> ?ts}", researcherURI), new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				if (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					dt.set(((XSDDateTime)qs.getLiteral("?ts").getValue()).asCalendar().getTimeInMillis());
				}								
			}
		});
		long threshold = new DateTime().minusDays(daysConsideredOld).getMillis();
		return dt.get() > threshold;
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
