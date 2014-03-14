package edu.ucsf.crosslink.io;

import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.jena.FusekiCache;
import edu.ucsf.ctsi.r2r.jena.FusekiService;
import edu.ucsf.ctsi.r2r.jena.HttpClientFusekiService;
import edu.ucsf.ctsi.r2r.jena.LODService;

@Singleton
public class JenaPersistance implements CrosslinkPersistance, R2RConstants {
	private static final Logger LOG = Logger.getLogger(JenaPersistance.class.getName());
	
	private LODService lodService;
	private FusekiService fusekiService;
	private FusekiCache fusekiCache;
	private ThumbnailGenerator thumbnailGenerator;
	
	@Inject
	public JenaPersistance(@Named("r2r.fusekiUrl") String fusekiUrl, LODService lodService, @Named("daysConsideredOld") Integer daysConsideredOld) {
		this.lodService = lodService;
		this.fusekiService = new HttpClientFusekiService(fusekiUrl);
		this.fusekiCache = new FusekiCache(fusekiService, lodService, "" + daysConsideredOld*24);

		// load the ontology into the model
//		if (this.fusekiCache != null) {
//			Dataset dataSet = getDataset(ReadWrite.WRITE);
//    		Model model = dataSet.getDefaultModel();
//    		model.add(JenaPersistance.getR2ROntModel());
//        	dataSet.commit();
//    		dataSet.end();
//		}
	}

	@Inject
	public void setThumbnailGenerator(ThumbnailGenerator thumbnailGenerator) {
		this.thumbnailGenerator = thumbnailGenerator;
	}
		
	private boolean contains(String uri) {
		return fusekiCache.contains(uri);
	}	
	
	public Resource getFreshResource(String rdfUrl, boolean store) {
		// hack
		String uri = rdfUrl;
		if (rdfUrl.endsWith(".rdf")) {
			uri = rdfUrl.substring(0, rdfUrl.lastIndexOf('/'));
		}
		return getResource(uri, rdfUrl, true, true, store);
	}
	
	public Resource getResource(String uri, boolean okToUseLocal, boolean okToUseWeb, boolean okToStore) {
		return getResource(uri, uri, okToUseLocal, okToUseWeb, okToStore);
	}
		
	public Resource getResource(String uri, String rdfUrl, boolean okToUseLocal, boolean okToUseWeb, boolean okToStore) {
		Resource resource = null;
		try {
			if (okToStore) {
				resource = fusekiCache.getResource(rdfUrl, uri);				
			}
			else {
				resource = lodService.getModel(rdfUrl).createResource(uri);
			}
		}
		catch (Exception e) {
			LOG.log(Level.WARNING, "Error reading " + uri, e);
		}
		return resource;
	}

	public void start() throws Exception {
	}

	public void saveResearcher(Researcher researcher) throws Exception {
		if (thumbnailGenerator != null) {
			thumbnailGenerator.generateThumbnail(researcher);
		}
		
    	Model writeModel = ModelFactory.createDefaultModel();
    	writeModel.setNsPrefix(FOAF_PREFIX, FOAF);
    	
    	String uri = researcher.getURI() != null ? researcher.getURI() : researcher.getHomePageURL();
    	if (!contains(uri)) {
    		// Must be from a site that does not have LOD.  add basic stuff
            Resource resource = writeModel.createResource(uri);
    		
        	// person
        	writeModel.add(resource, 
    				writeModel.createProperty(TYPE), 
    				writeModel.createLiteral("http://xmlns.com/foaf/0.1/Person"));

            // label
        	writeModel.add(resource, 
        				writeModel.createProperty(LABEL), 
        				writeModel.createTypedLiteral(researcher.getLabel()));
        	
        	researcher.setURI(uri);
        }
    	Resource resource = writeModel.createResource(uri);
			
    	// homepage
    	writeModel.add(resource, 
				writeModel.createProperty(HOMEPAGE), 
				writeModel.createTypedLiteral(researcher.getHomePageURL()));        	

    	// image        	
    	if (researcher.getThumbnailURL() != null) {
        	writeModel.add(resource, 
    				writeModel.createProperty(IMAGE), 
    				writeModel.createTypedLiteral(researcher.getThumbnailURL()));
    	}
    	
    	// now store in our fuseki store
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		    		writeModel.write(stream);
		    		stream.flush();
		    		stream.close();
		fusekiService.add(stream.toByteArray());			
	}
	
	public String find(Resource resource, String name) {
		return find(resource, name, false);
	}
	
	private String find(Resource resource, String name, boolean force) {
		StmtIterator si = resource.listProperties();
		while (si.hasNext()) {
			Statement s = si.next();
			if (name.equalsIgnoreCase(s.getPredicate().getLocalName())) {
				RDFNode n = s.getObject();
				if (force) {
					return n.toString();
				}
				else if (n.isLiteral()) {
					return "" + n.asLiteral().getValue();
				}
				else if (n.isURIResource() && name.toLowerCase().contains("image")) {
					String possibleReturnValue = n.asNode().getURI(); 
					if (possibleReturnValue.toLowerCase().contains("photohandler.ashx")) {
						// this is profiles, and this is not a LOD uri, we are done
						return possibleReturnValue;
					}
					try {
						return find(getFreshResource(n.asNode().getURI(), false), "downloadLocation", true);
					}
					catch (Exception e) {
						LOG.info(e.getMessage());
					}
					return possibleReturnValue;
				}
			}
		}	
		return null;
	}

	public Date dateOfLastCrawl() {
		return null;
	}

	public boolean skip(String url) {
		return false;
	}

	public int touch(String url) throws Exception {
		return 0;
	}

	public void close() throws Exception {
	}

	public void finish() throws Exception {
	}
	
	public static void main(String[] args) {
		try {
			JenaPersistance jp = new JenaPersistance(null, null, 6);
			Resource r = jp.getFreshResource(args[0], false);
			System.out.println(r.getURI());
			System.out.println(jp.find(r, "label"));
			System.out.println(jp.find(r, "mainImage"));
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
