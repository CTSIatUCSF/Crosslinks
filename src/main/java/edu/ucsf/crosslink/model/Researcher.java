package edu.ucsf.crosslink.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;

import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.R2ROntology;


public class Researcher implements Comparable<Researcher>, R2RConstants {
	private static final Logger LOG = Logger.getLogger(Researcher.class.getName());
	
	private Affiliation harvester;
	private Affiliation affiliation;
	private String label;
	private String prettyURL;
	private String URI;
	private List<String> imageURLs = new ArrayList<String>(); // we allow for grabbing more than one and then test to see if any are valid when saving
	private String thumbnailURL;
	private String orcidId;
	private int readErrorCount = 0;
	private Collection<Integer> pmids= new HashSet<Integer>();
	private Resource resource;
	
	// display data
	private int externalCoauthorCount;
	private int sharedPublicationCount;	

	private Researcher(String uri) {
		this.URI = uri;
		Model model = R2ROntology.createDefaultModel();
		resource = model.createResource(uri);		
	}

	public Researcher(Affiliation affiliation, String uri) {
		this(uri);
    	this.setAffiliation(affiliation);
	}

	public Researcher(Affiliation affiliation, String uri, String label) {
		this(affiliation, uri);
		this.setLabel(label);
	}

	// for loading from the DB
	public Researcher(Affiliation affiliation,
			String prettyURL, String uri, String label, String imageURL, String thumbnailURL, 
			String orcidId, int externalCoauthorCount, int sharedPublicationCount) {
		this(affiliation, uri, label);
		this.setPrettyURL(prettyURL);
		this.addImageURL(imageURL);
		this.setOrcidId(orcidId);
    	this.thumbnailURL = thumbnailURL;
    	this.externalCoauthorCount = externalCoauthorCount;
    	this.sharedPublicationCount = sharedPublicationCount;
    }
    
    public String getLabel() {
		return label;
	}
	
    public void setLabel(String label) {
		this.label = label;
	}
	
	public String getURI() {
		return URI;
	}
	
	public Affiliation getHarvester() {
		return harvester;
	}
	
	public void setHarvester(Affiliation harvester) {
		this.harvester = harvester;
	}
	
	public Affiliation getAffiliation() {
		return affiliation;
	}
	
	private void setAffiliation(Affiliation affiliation) {
		this.affiliation = affiliation;
	}
	
	public String getPrettyURL() {
		return prettyURL;
	}
	
	public void setPrettyURL(String prettyURL) {
		this.prettyURL = prettyURL;
	}
	
	// ugly but it works
	public void setConfirmedImgURLs(String imageURL, String thumbnailURL) {
		this.imageURLs.clear();
		if (imageURL != null) {
			this.imageURLs.add(imageURL);
		}
		this.thumbnailURL = thumbnailURL;
	}
	
	public String getThumbnailURL() {
		return thumbnailURL; 
	}
	
	public String getImageURL() {
		// If we have more than one, we don't know which is valid so return nothing
		return imageURLs.size() == 1 ? imageURLs.get(0) : null;
	}
	
	public List<String> getImageURLs() {
		return imageURLs;
	}

	public void addImageURL(String imageURL) {
		if (!StringUtils.isEmpty(imageURL)) {
			imageURLs.add(imageURL);
		}
	}

	public String getOrcidId() {
		return orcidId;
	}
	
	public void setOrcidId(String orcidId) {
		this.orcidId = StringUtils.isEmpty(orcidId) ? null : orcidId;
	}

	public Collection<Integer> getPubMedPublications() {
		return pmids;
	}
	
	public void addPubMedPublication(int pmid) {
		pmids.add(pmid);
	}
	
	// can handle it in URL form, or just the pmid
	public void addPubMedPublication(String publication) {
		Integer pmid = null;
		if (StringUtils.isNumeric(publication)) {
			pmid = Integer.valueOf(publication);
		}
		else if (publication.contains(AuthorParser.PUBMED_SECTION) && StringUtils.isNumeric(publication.split(AuthorParser.PUBMED_SECTION)[1])) {
			pmid = Integer.valueOf(publication.split(AuthorParser.PUBMED_SECTION)[1]);
		}
		else {
		    List<NameValuePair> params;
			try {
				params = URLEncodedUtils.parse(new URI(publication), "UTF-8");
			    for (NameValuePair param : params) {
			    	if ("term".equalsIgnoreCase(param.getName())) {
						pmid = Integer.valueOf(param.getValue());		    		
			    	}
			    }
			} 
			catch (URISyntaxException e) {
				LOG.log(Level.WARNING, e.getMessage(), e);
				e.printStackTrace();
			}		    
		}
		if (pmid != null) {
			LOG.info("PMID = " + pmid);
			addPubMedPublication(pmid);
		}
		else {
			LOG.log(Level.WARNING, "Could not extract PMID from " + publication);			
		}
	}

	public void registerReadException(Exception e) {
		this.readErrorCount++;
		LOG.log(Level.WARNING, "Error reading : " + this.toString(), e);
	}
	
	public int getErrorCount() {
		return readErrorCount;
	}
	
	public String toString() {
		return URI + (label != null ? " : " + label : "") +
					" : " + pmids.size() + " publications";
	}

	public int compareTo(Researcher arg0) {
		if (this.readErrorCount != arg0.readErrorCount) {
			return Integer.compare(this.readErrorCount, arg0.readErrorCount);
		}
		else {
			String thisStr = (label != null ? label.trim() : " ") + URI;
			String oStr = (arg0.label != null ? arg0.label.trim() : " ") + arg0.URI;
			return thisStr.compareTo(oStr);
		}
	}
	
	public String getName() {
		return label;
	}
	
	public int getExternalCoauthorCount() {
		return externalCoauthorCount;
	}    
	
	public int getSharedPublicationCount() {
		return sharedPublicationCount;
	}    
	
	public Resource getResource() throws Exception {
		Model model = resource.getModel();
		
    	// person
        model.add(resource, 
        		model.createProperty(RDF_TYPE), 
        		model.createLiteral(FOAF_PERSON));

        // label
        model.add(resource, 
        		model.createProperty(RDFS_LABEL), 
        		model.createTypedLiteral(getLabel()));
    	
        // mainImage
    	if (getImageURL() != null) {
    			model.add(resource, 
    			model.createProperty(PRNS_MAIN_IMAGE), 
    			model.createTypedLiteral(getImageURL()));
    	}

    	if (getHarvester() != null) {
    		Resource affiliationResource = model.createResource(getHarvester().getURI());
        	// add affiliation to researcher
        	model.add(resource,
        			model.createProperty(R2R_HARVESTED_FROM), 
            				affiliationResource);    			
    	}

    	// create affiliation.  Should be smart about doing this only when necessary!
    	if (getAffiliation() != null) {
    		Resource affiliationResource = model.createResource(getAffiliation().getURI());
        	// add affiliation to researcher
        	model.add(resource,
        			model.createProperty(R2R_HAS_AFFILIATION), 
            				affiliationResource);
    			
    	}

    	// homepage
    	if (getPrettyURL() != null) {
	    	model.add(resource, 
	    			model.createProperty(R2R_PRETTY_URL), 
					model.createTypedLiteral(getPrettyURL()));
    	}

    	// thumbnail        	
    	if (getThumbnailURL() != null) {
    		model.add(resource, 
    				model.createProperty(R2R_THUMBNAIL), 
    				model.createTypedLiteral(getThumbnailURL()));    		    	
    	}

    	// timestamps
    	Date now = new Date();
    	model.add(resource, 
    			model.createProperty(R2R_VERIFIED_DT), 
				model.createTypedLiteral(now.getTime()));        	

    	model.add(resource, 
    			model.createProperty(R2R_WORK_VERIFIED_DT), 
				model.createTypedLiteral(now.getTime()));        	
    	
    	// publications
    	if (!getPubMedPublications().isEmpty()) {
    		for (Integer pmid : getPubMedPublications()) {
                Resource pmidResource = model.createResource("http:" + AuthorParser.PUBMED_SECTION + pmid);
        		// Associate to Researcher
        		model.add(resource, 
        				model.createProperty(R2R_CONTRIBUTED_TO), 
        				pmidResource);    			
    		}
    	}    	
		return resource;
	}

	public void addLiteral(String predicate, String literal) {
		Model model = resource.getModel();
        model.add(resource, 
        		model.createProperty(predicate), 
        		model.createTypedLiteral(literal));		
	}
	
	public static void main(String[] args) {
		// simple test
		try {
			Researcher foo = new Researcher("http://profiles.ucsf.edu/eric.meeks");
			foo.addPubMedPublication("1234");
			foo.addPubMedPublication("https://www.ncbi.nlm.nih.gov/pubmed/?otool=uchsclib&term=17874365");
			foo.addPubMedPublication("http://www.ncbi.nlm.nih.gov/pubmed/24303259");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
