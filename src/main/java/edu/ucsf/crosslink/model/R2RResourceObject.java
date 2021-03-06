package edu.ucsf.crosslink.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.Restriction;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;

import edu.ucsf.crosslink.processor.iterator.MarengoListProcessor;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.R2ROntology;

public abstract class R2RResourceObject implements R2RConstants {

	private Resource resource = null;
	private URI uriObj;
	
	// for debugging
	private static final Logger LOG = Logger.getLogger(R2RResourceObject.class.getName());
	private static AtomicLong objectCount = new AtomicLong();
	
	private R2RResourceObject(String uri, boolean ontModel) throws URISyntaxException {
		this.uriObj = new URI(uri);
		Model model = ontModel ? R2ROntology.createR2ROntModel() : R2ROntology.createDefaultModel();
		resource = model.createResource(uri);			
		if (objectCount.incrementAndGet() % 100 == 0) {
			LOG.info("Object count at " + objectCount.get() + ", just added " + uri + " ontModel " + ontModel);
		}
	}

	protected void finalize() throws Throwable {
	     try {
	         objectCount.decrementAndGet();
	     } 
	     finally {
	         super.finalize();
	     }
	}
	
	public static long getObjectCount() {
		return objectCount.get();
	}
	
	protected R2RResourceObject(String uri) throws URISyntaxException {
		this(uri, false);
	}

	protected R2RResourceObject(String uri, String type) throws URISyntaxException {
		this(uri, Arrays.asList(type));
	}
	
	protected R2RResourceObject(String uri, List<String> types) throws URISyntaxException {
		this(uri, true);
		Model model = getModel();
		for (String type : types) {
			model.add(resource, model.createProperty(RDF_TYPE), model.createResource(type));
		}
	}
	
	public void addFrom(Model model) {
		getModel().add(model.listStatements(model.getResource(getURI()), (Property)null, (RDFNode)null));
	}

	public URI getURIObject() {
		return uriObj;
	}
	
	public String getURI() {
		return resource.getURI();
	}
	
    public String getLabel() {
    	return getStringLiteral(RDFS_LABEL);
	}
    
    public String getName() {
    	return getLabel();
    }
	
	@Override
	public String toString() {
		return getURI() + (getName() != null ? ", " + getName() : "");
	}
	
    public void setLabel(String label) {
		setLiteral(RDFS_LABEL, label);
	}
    
    public boolean hasMaxCardinalityRestriction(String predicate) {
    	Model model = getModel();
    	if (model instanceof OntModel) {
    		OntProperty prop = ((OntModel)model).getOntProperty(predicate);
    		if (prop != null) {
    			Iterator<Restriction> i = prop.listReferringRestrictions();
    			while (i.hasNext()) {
    			    Restriction r = i.next();
    			    if (r.isMaxCardinalityRestriction())
    			    	return true;
    			}
    		}    		
    	}
    	return false;
    }
	
	protected Resource getResource() {
		return resource;
	}
	
	// to save in storage
	public List<Resource> getResources() {
		return Arrays.asList(resource);
	}
	
	public Model getModel() {
		return getResource().getModel();
	}

	protected Statement getStatement(String predicate) {
		return getResource().getProperty(getModel().createProperty(predicate));
	}
	
	protected StmtIterator getStatements(String predicate) {
		return getResource().listProperties(getModel().createProperty(predicate));
	}
	
	protected String getStringLiteral(String predicate) {
		Statement stmnt = getStatement(predicate);		
		return stmnt != null ? stmnt.getLiteral().getString() : null;
	}

	protected Calendar getDateTimeLiteral(String predicate) {
		Statement stmnt = getStatement(predicate);		
		return stmnt != null ?  ((XSDDateTime)stmnt.getLiteral().getValue()).asCalendar() : null;
	}

	protected void setLiteral(String predicate, Object literal) {		
		Model model = getModel();
		Property property = model.createProperty(predicate);
		model.removeAll(getResource(), property, null);
		if (literal != null && (!(literal instanceof String && StringUtils.isBlank((String)literal)))) {
	        model.add(getResource(), model.createProperty(predicate), model.createTypedLiteral(literal));
		}
	}
	
	protected String getResourceURI(String predicate) {
		Statement stmnt = getStatement(predicate);		
		return stmnt != null ? stmnt.getResource().getURI() : null;		
	}
	
	protected Resource setResource(String predicate, R2RResourceObject objectObj) {
		return setResource(predicate, objectObj.getResource().getURI());
	}
	
	protected Resource setResource(String predicate, Resource objectResource) {
		return setResource(predicate, objectResource.getURI());		
	}
	
	protected Resource setResource(String predicate, String objectURI) {		
		return addResource(predicate, objectURI, true);
	}
	
	protected Resource addResource(String predicate, R2RResourceObject objectObj) {
		return addResource(predicate, objectObj.getResource().getURI());
	}
	
	protected Resource addResource(String predicate, Resource objectResource) {
		return addResource(predicate, objectResource.getURI());		
	}
	
	protected Resource addResource(String predicate, String objectURI) {	
		return addResource(predicate, objectURI, false);
	}

	private Resource addResource(String predicate, String objectURI, boolean replace) {		
		Model model = resource.getModel();
		Property property = model.createProperty(predicate);
		if (replace) {
			model.removeAll(resource, property, null);
		}
		Resource objectResource = model.createResource(objectURI);
    	model.add(resource, property, objectResource);
    	return objectResource;
	}
}
