package edu.ucsf.crosslink.io;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
import com.hp.hpl.jena.rdf.model.StmtIterator;

import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.R2RResourceObject;
import edu.ucsf.crosslink.processor.controller.ProcessorController;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.R2ROntology;
import edu.ucsf.ctsi.r2r.jena.SparqlPostClient;
import edu.ucsf.ctsi.r2r.jena.SparqlQueryClient;
import edu.ucsf.ctsi.r2r.jena.SparqlUpdateClient;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.jena.riot.web.HttpOp;


public class SparqlPersistance implements R2RConstants {

	private static final Logger LOG = Logger.getLogger(SparqlPersistance.class.getName());

	private SparqlQueryClient sparqlQuery;
	private SparqlPostClient sparqlClient;
	private List<Affiliation> knownAffiliations = new ArrayList<Affiliation>();
	
	private static final String SKIP_RESEARCHERS_SPARQL = "SELECT ?r ?ts WHERE {?r <" + R2R_PROCESSED_BY + "> ?c . ?c <" + 
			RDFS_LABEL + "> \"%s\" . ?c <" + R2R_PROCESSED_ON + "> ?ts . FILTER (?ts > \"%s\")}";
	
	private static final String LOAD_AFFILIATIONS = "SELECT ?r ?l WHERE  {?r <" + RDF_TYPE + "> <" +
			R2R_AFFILIATION + "> . ?r <" + RDFS_LABEL + "> ?l}";
	
	private enum SaveType {SAVE, UPDATE, ADD}; 

	@Inject
	public SparqlPersistance(@Named("r2r.fusekiUrl") String sparqlQuery, SparqlPostClient sparqlClient) throws Exception {
		this.sparqlQuery = new SparqlQueryClient(sparqlQuery + "/query");
		this.sparqlClient = sparqlClient;
		
		// putting this here for now 
		HttpParams params = new BasicHttpParams();		
		HttpConnectionParams.setConnectionTimeout(params, 10000);
		HttpConnectionParams.setSoTimeout(params, 40000);
		PoolingClientConnectionManager cm = new PoolingClientConnectionManager();
		cm.setDefaultMaxPerRoute(20);
		cm.setMaxTotal(200);
		HttpOp.setDefaultHttpClient(new DefaultHttpClient(cm, params));

		// make sure we have the latest model
		sparqlClient.add(R2ROntology.createR2ROntModel());
		sparqlClient.update("CREATE GRAPH <" + R2R_DERIVED_GRAPH + ">");
		// by loading these now, we make sure that we do not collide with calls to upsertAffiliation
		loadAffiliations();
	}
	
	private void loadAffiliations() throws Exception {
		sparqlQuery.select(LOAD_AFFILIATIONS, new ResultSetConsumer() {
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
		// start with longer URI first so that we match http://profiles.somewhere.edu/profile before http://profiles.somewhere.edu
		Collections.sort(knownAffiliations, new Comparator<Affiliation>() {
			public int compare(Affiliation o1, Affiliation o2) {
				return o2.getURI().length() - o1.getURI().length();
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

	public void execute(String updates) throws Exception {
		startTransaction();
		sparqlClient.update(updates);
		endTransaction();
	}

	public void execute(List<String> updates) throws Exception {
		startTransaction();
		sparqlClient.update(updates);
		endTransaction();
	}

	// delete existing one first
	private void saveInternal(R2RResourceObject robj, SaveType saveType) throws Exception {
		startTransaction();
		LOG.log(Level.INFO, "Saving " + robj + ", Type " + saveType);
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

	public Calendar startCrawl(ProcessorController processorController) throws Exception {
		return updateTimestampFieldFor(processorController.getURI(), R2R_PROCESSOR_START_DT);
	}

	public Calendar finishCrawl(ProcessorController processorController) throws Exception {
		return updateTimestampFieldFor(processorController.getURI(), R2R_PROCESSOR_END_DT);
	}

	public Calendar dateOfLastCrawl(ProcessorController processorController) throws Exception {
		String sparql = "SELECT ?dt WHERE {<" + processorController.getURI() + "> <" + R2R_PROCESSOR_END_DT + "> ?dt}";
		DateResultSetConsumer consumer = new DateResultSetConsumer();
		sparqlQuery.select(sparql, consumer);
		return consumer.getCalendar();
	}

	public boolean skip(String researcherURI, String timestampField, int daysConsideredOld) throws Exception {
		final AtomicLong dt = new AtomicLong();
		sparqlQuery.select(String.format("SELECT ?ts WHERE {<%1$s> <" + timestampField + "> ?ts}", researcherURI), new ResultSetConsumer() {
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
