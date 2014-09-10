package edu.ucsf.crosslink.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;

import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.R2ROntology;

public abstract class R2RResourceObject implements R2RConstants {

	private Resource resource = null;
	private URI uriObj;
	
	protected R2RResourceObject(String uri, String type) throws URISyntaxException {
		this(uri, Arrays.asList(type));
	}
	
	protected R2RResourceObject(String uri, List<String> types) throws URISyntaxException {
		this.uriObj = new URI(uri);
		Model model = R2ROntology.createR2ROntModel();
		resource = model.createResource(uri);			
		for (String type : types) {
			model.add(resource, model.createProperty(RDF_TYPE), model.createResource(type));
		}
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
		return getName();
	}
	
    public void setLabel(String label) {
		setLiteral(RDFS_LABEL, label);
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
		Model model = resource.getModel();
		Property property = model.createProperty(predicate);
		model.removeAll(resource, property, null);
		Resource objectResource = model.createResource(objectURI);
    	model.add(resource, property, objectResource);
    	return objectResource;
	}
	
}
