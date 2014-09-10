package edu.ucsf.crosslink.crawler.parser;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.R2ROntology;
import edu.ucsf.ctsi.r2r.jena.FusekiCache;
import edu.ucsf.ctsi.r2r.jena.SparqlPostClient;
import edu.ucsf.ctsi.r2r.jena.LODService;

@Singleton
public class JenaHelper implements R2RConstants {
	private static final Logger LOG = Logger.getLogger(JenaHelper.class.getName());
	
	private LODService lodService;
	private FusekiCache fusekiCache;
	
	@Inject
	public JenaHelper(SparqlPostClient fusekiClient, LODService lodService) throws Exception {
		this.lodService = lodService;
		this.fusekiCache = new FusekiCache(fusekiClient, lodService);
		// make sure we have the latest info
		fusekiClient.add(R2ROntology.createDefaultModel());		
	}

	boolean contains(String uri) {
		return fusekiCache.contains(uri);
	}	

	public Resource getResourceFromRdfURL(String rdfUrl) {
		return getResourceFromRdfURL(rdfUrl, true);
	}
	
	public Resource getResourceFromRdfURL(String rdfUrl, boolean store) {
		// hack
		String uri = rdfUrl;
		if (rdfUrl.endsWith(".rdf")) {
			uri = rdfUrl.substring(0, rdfUrl.lastIndexOf('/'));
		}
		return getResource(rdfUrl, uri, store);
	}

	public Resource getResource(String uri) {
		return getResource(uri, true);
	}

	public Resource getResource(String uri, boolean okToStore) {
		return getResource(uri, uri, okToStore);
	}

	// it is possible that we might have a resource in our fusekiCache even if we don't store it	
	// but we don't bother to check at this point
	private Resource getResource(String rdfUrl, String uri, boolean okToStore) {
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

	public Model getModelFor(Researcher researcher, boolean forWebOutput) throws Exception {
		Model model = ModelFactory.createDefaultModel();
    	String uri = researcher.getURI() != null ? researcher.getURI() : researcher.getURI();
    	if (!contains(uri) || forWebOutput) {
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
        			model.createProperty(FOAF_HAS_IMAGE), 
        			model.createTypedLiteral(researcher.getImageURL()));
        	}
        }
    	// create affiliation.  Should be smart about doing this only when necessary!
    	Resource affiliationResource = model.createResource(researcher.getAffiliation().getURI());
    	Resource researcherResource = model.createResource(uri);
    	
    	model.add(researcherResource,
    			model.createProperty(R2R_HARVESTED_FROM), 
        				affiliationResource);
			
    	// add affiliation to researcher
    	model.add(researcherResource,
    			model.createProperty(R2R_HAS_AFFILIATION), 
        				affiliationResource);
			
    	// homepage
    	model.add(researcherResource, 
    			model.createProperty(FOAF_HOMEPAGE), 
				model.createTypedLiteral(researcher.getHomepage()));        	

    	// thumbnail        	
    	if (researcher.getThumbnailURL() != null) {
    		model.add(researcherResource, 
    				model.createProperty(FOAF_THUMBNAIL), 
    				model.createTypedLiteral(researcher.getThumbnailURL()));
    		    	
    	}
    	
    	// publications
    	if (!researcher.getPubMedPublications().isEmpty()) {
    		for (Long pmid : researcher.getPubMedPublications()) {
                Resource pmidResource = model.createResource("http:" + AuthorParser.PUBMED_SECTION + pmid);
        		// Associate to Researcher
        		model.add(researcherResource, 
        				model.createProperty(R2R_CONTRIBUTED_TO), 
        				pmidResource);    			
    		}
    	}
    	
		return model;
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
						return find(getResourceFromRdfURL(n.asNode().getURI(), false), "downloadLocation", true);
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

	public static void main(String[] args) {
		try {
			JenaHelper jp = new JenaHelper(null, null);
			Resource r = jp.getResourceFromRdfURL(args[0], false);
			System.out.println(r.getURI());
			System.out.println(jp.find(r, "label"));
			System.out.println(jp.find(r, "mainImage"));
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}