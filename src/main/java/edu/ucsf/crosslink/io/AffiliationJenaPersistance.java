package edu.ucsf.crosslink.io;

import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.joda.time.DateTime;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

import edu.ucsf.crosslink.Crosslinks;
import edu.ucsf.crosslink.crawler.AffiliationCrawler;
import edu.ucsf.crosslink.crawler.AffiliationCrawlerFactory;
import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.job.ExecutorModule;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.R2ROntology;
import edu.ucsf.ctsi.r2r.jena.FusekiClient;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;

public class AffiliationJenaPersistance implements CrosslinkPersistance, R2RConstants {

	private static final Logger LOG = Logger.getLogger(AffiliationJenaPersistance.class.getName());

	private Affiliation affiliation;
	private FusekiClient fusekiClient;
	private Integer daysConsideredOld;
	private JenaHelper jenaHelper;
	private ThumbnailGenerator thumbnailGenerator;
	private final Map<String, Long> recentlyProcessedAuthors = new HashMap<String, Long>();
	
	private static final String SKIP_RESEARCHERS_SPARQL = "SELECT ?hp ?ts WHERE {?s <" + R2R_FROM_RN_WEBSITE + "> <%s> . ?s <" +
			R2R_HOMEPAGE_PATH + "> ?hp . ?s <" + R2R_WORK_VERIFIED_DT + "> ?ts . FILTER (?ts > %d)}";
	
	@Inject
	public AffiliationJenaPersistance(Affiliation affiliation, FusekiClient fusekiClient, 
			@Named("daysConsideredOld") Integer daysConsideredOld, JenaHelper jenaHelper) throws Exception {
    	// create affiliation. Should we add latitude and longitude to this?
		this.affiliation = affiliation;
		this.fusekiClient = fusekiClient;
		this.daysConsideredOld = daysConsideredOld;
		this.jenaHelper = jenaHelper;
		
		Model model = R2ROntology.createDefaultModel();
		model.add(fusekiClient.describe(affiliation.getURI()));
		Resource s = model.createResource(affiliation.getURI());
		replace(model, s, model.createProperty(RDFS_LABEL), model.createTypedLiteral(affiliation.getName()));
		replace(model, s, model.createProperty(RDF_TYPE), model.createResource(R2R_RN_WEBSITE));
		replace(model, s, model.createProperty(PRNS_LATITUDE), model.createTypedLiteral(affiliation.getLatitude()));
		replace(model, s, model.createProperty(PRNS_LONGITUDE),	model.createTypedLiteral(affiliation.getLongitude()));
		// remove the old one first.  This will not affect any researchers
		fusekiClient.deleteSubject(affiliation.getURI());
    	fusekiClient.add(model);
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
		fusekiClient.select(sparql, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					recentlyProcessedAuthors.put(qs.getLiteral("?hp").getString(), qs.getLiteral("?ts").getLong());
				}								
			}
		});
		LOG.info("Found " + recentlyProcessedAuthors.size() + " recently processed authors");
	}

	public void finish() throws Exception {
		updateTimestampFieldFor(affiliation.getURI(), R2R_CRAWL_END_DT);
		// sparql out the ones we did not find
		String sparql = "DELETE {?s ?p ?o} WHERE { >" + affiliation.getURI() + "> <" + R2R_CRAWL_START_DT + "> ?cst . " +
			"?s <" + R2R_FROM_RN_WEBSITE + "> <" + affiliation.getURI() + "> . " +
			"?s <" + R2R_VERIFIED_DT + "> ?ta FILTER(?ta < ?cst) ?s ?p ?o}";
		fusekiClient.update(sparql);
		// clear the list
		recentlyProcessedAuthors.clear();
	}

	public void saveResearcher(Researcher researcher) throws Exception {
		if (thumbnailGenerator != null) {
			thumbnailGenerator.generateThumbnail(researcher);
		}		
		delete(researcher, R2R_FROM_RN_WEBSITE);
		delete(researcher, R2R_HOMEPAGE_PATH);
		delete(researcher, R2R_THUMBNAIL);
		delete(researcher, R2R_VERIFIED_DT);
		delete(researcher, R2R_WORK_VERIFIED_DT);
		delete(researcher, R2R_CONTRIBUTED_TO);
		fusekiClient.add(createR2RDataFor(researcher));
		// add to processed list
		recentlyProcessedAuthors.put(researcher.getHomePagePath(), new Date().getTime());
	}

	public Date dateOfLastCrawl() {
		String sparql = "SELECT ?dt WHERE {<" + affiliation.getURI() + "> <" + R2R_CRAWL_END_DT + "> ?dt}";
		DateResultSetConsumer consumer = new DateResultSetConsumer();
		fusekiClient.select(sparql, consumer);
		return consumer.getDate();
	}

	public boolean skip(Researcher researcher) {
		Long ts = recentlyProcessedAuthors.get(researcher.getHomePagePath());
		return ts != null && ts > new DateTime().minusDays(daysConsideredOld).getMillis();
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
	    	fusekiClient.update(sparql);
		}
	}

	private Date updateTimestampFieldFor(String subjectUri, String predicate) throws Exception {
		// delete the old one
		Date now = new Date();
		Model model = R2ROntology.createDefaultModel();
		Property p = model.createProperty(predicate);
    	String sparql = "DELETE WHERE { <" + subjectUri + ">  <" + p.getURI()+ "> ?dt }";	
    	fusekiClient.update(sparql);

		// add the new one
		model.add(model.createResource(subjectUri),
    			p, 
    			model.createTypedLiteral(now.getTime()));
    	fusekiClient.add(model);
    	return now;
	}
	
	private Model createR2RDataFor(Researcher researcher) throws Exception {
		Model model = R2ROntology.createDefaultModel();
    	String uri = researcher.getURI() != null ? researcher.getURI() : researcher.getHomePageURL();
    	if (!jenaHelper.contains(uri)) {
    		// Must be from a site that does not have LOD.  add basic stuff
            Resource researcherResource = model.createResource(uri);
    		
        	// person
            model.add(researcherResource, 
            		model.createProperty(RDF_TYPE), 
            		model.createLiteral(FOAF_PERSON));

            // label
            model.add(researcherResource, 
            		model.createProperty(RDFS_LABEL), 
            		model.createTypedLiteral(researcher.getLabel()));
        	
            // mainImage
        	if (researcher.getImageURL() != null) {
        			model.add(researcherResource, 
        			model.createProperty(PRNS_MAIN_IMAGE), 
        			model.createTypedLiteral(researcher.getImageURL()));
        	}

        	researcher.setURI(uri);
        }
    	// create affiliation.  Should be smart about doing this only when necessary!
    	Resource affiliationResource = model.createResource(researcher.getAffiliation().getURI());
    	Resource researcherResource = model.createResource(uri);
    	
    	// add affiliation to researcher
    	model.add(researcherResource,
    			model.createProperty(R2R_FROM_RN_WEBSITE), 
        				affiliationResource);
			
    	// homepage
    	model.add(researcherResource, 
    			model.createProperty(R2R_HOMEPAGE_PATH), 
				model.createTypedLiteral(researcher.getHomePagePath()));        	

    	// thumbnail        	
    	if (researcher.getThumbnailURL() != null) {
    		model.add(researcherResource, 
    				model.createProperty(R2R_THUMBNAIL), 
    				model.createTypedLiteral(researcher.getThumbnailURL()));    		    	
    	}

    	// timestamps
    	Date now = new Date();
    	model.add(researcherResource, 
    			model.createProperty(R2R_VERIFIED_DT), 
				model.createTypedLiteral(now.getTime()));        	

    	model.add(researcherResource, 
    			model.createProperty(R2R_WORK_VERIFIED_DT), 
				model.createTypedLiteral(now.getTime()));        	
    	
    	// publications
    	if (!researcher.getPubMedPublications().isEmpty()) {
    		for (Integer pmid : researcher.getPubMedPublications()) {
                Resource pmidResource = model.createResource("http:" + AuthorParser.PUBMED_SECTION + pmid);
        		// Associate to Researcher
        		model.add(researcherResource, 
        				model.createProperty(R2R_CONTRIBUTED_TO), 
        				pmidResource);    			
    		}
    	}    	
		return model;
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

	//  this will pull everything out of the DB and put it into fuseki
	public static void main(String[] args) {
		try  {					
			ClassLoader classLoader = AffiliationJenaPersistance.class.getClassLoader();
			URL resource = classLoader.getResource("org/apache/http/message/BasicLineFormatter.class");
			System.out.println(resource);
			
			// get these first
			Properties prop = new Properties();
			prop.load(AffiliationJenaPersistance.class.getResourceAsStream(Crosslinks.PROPERTIES_FILE));	
			Injector injector = Guice.createInjector(new IOModule(prop), new ExecutorModule(prop));
			
			AffiliationCrawlerFactory factory = injector.getInstance(AffiliationCrawlerFactory.class);
			for (AffiliationCrawler crawler : factory.getCrawlers()) {
				System.out.println(crawler.getAffiliation().getName());
				if ("USC".equals(crawler.getAffiliation().getName())) {
					DBResearcherPersistance dbrp = factory.getInjector(crawler.getAffiliation().getName()).getInstance(DBResearcherPersistance.class);

					Collection<Researcher> researchers = dbrp.getResearchers();					
					System.out.println(researchers.size());
					
					AffiliationJenaPersistance ajp = factory.getInjector(crawler.getAffiliation().getName()).getInstance(AffiliationJenaPersistance.class);
					for (Researcher researcher : researchers) {
						System.out.println("Storing " + researcher);
						try {
							ajp.saveResearcher(researcher);
						}
						catch (Exception e) {
							e.printStackTrace(System.out);
						}
					}
				}
			}
						
		}
		catch (Exception e) {
			e.printStackTrace();
		}		
	}}
