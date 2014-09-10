package edu.ucsf.crosslink.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.StmtIterator;

import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.ctsi.r2r.R2RConstants;

public class Researcher extends R2RResourceObject implements Comparable<Researcher>, R2RConstants {
	private static final Logger LOG = Logger.getLogger(Researcher.class.getName());
	
	private Affiliation harvester;
	private Affiliation affiliation;
	private List<String> imageURLs = new ArrayList<String>(); // we allow for grabbing more than one and then test to see if any are valid when saving
	private int readErrorCount = 0;
	
	// display data
	private int externalCoauthorCount;
	private int sharedPublicationCount;	

	private Researcher(String uri) throws URISyntaxException {
		super(uri, FOAF_PERSON);
	}

	public Researcher(Affiliation affiliation, String uri) throws URISyntaxException {
		this(uri);
    	this.setAffiliation(affiliation);
	}

	public Researcher(Affiliation affiliation, String uri, String label) throws URISyntaxException {
		this(affiliation, uri);
		this.setLabel(label);
	}

	// for loading from the DB
	public Researcher(Affiliation affiliation,
			String prettyURL, String uri, String label, String imageURL, String thumbnailURL, 
			String orcidId, int externalCoauthorCount, int sharedPublicationCount) throws URISyntaxException {
		this(affiliation, uri, label);
		this.setHomepage(prettyURL);
		this.setConfirmedImgURLs(imageURL, thumbnailURL);
		this.setOrcidId(orcidId);
    	this.externalCoauthorCount = externalCoauthorCount;
    	this.sharedPublicationCount = sharedPublicationCount;
    }
    
	public void setFOAFName(String firstName, String lastName) {
		setLiteral(FOAF_FIRST_NAME, firstName);
		setLiteral(FOAF_LAST_NAME, lastName);
	}
	
	public Affiliation getHarvester() {
		return harvester;
	}
	
	public void setHarvester(Affiliation harvester) {
		this.harvester = harvester;
		setResource(R2R_HARVESTED_FROM, harvester);
	}
	
	public Affiliation getAffiliation() {
		return affiliation;
	}
	
	private void setAffiliation(Affiliation affiliation) {
		setResource(R2R_HAS_AFFILIATION, harvester);
		this.affiliation = affiliation;
	}
	
	public String getHomepage() {
		return getResourceURI(FOAF_HOMEPAGE);
	}
	
	public void setHomepage(String prettyURL) {
		setResource(FOAF_HOMEPAGE, prettyURL);
	}
	
	// ugly but it works
	public void setConfirmedImgURLs(String imageURL, String thumbnailURL) {
		this.imageURLs.clear();
		if (imageURL != null) {
			this.imageURLs.add(imageURL);
		}
		Model model = getModel();
		Property propType = model.createProperty(RDF_TYPE);
		Resource foafImage = model.createResource(FOAF_IMAGE);
		Resource imageResource = setResource(FOAF_HAS_IMAGE, imageURL);
		imageResource.addProperty(propType, foafImage);
		if (thumbnailURL != null) {
			Resource thumbnailResource = model.createResource(thumbnailURL);
			thumbnailResource.addProperty(propType, foafImage);
			imageResource.addProperty(model.createProperty(FOAF_THUMBNAIL), thumbnailResource);
		}
	}
	
	public String getThumbnailURL() {
		Model model = getModel();
		// should be the only one here
		NodeIterator ni = model.listObjectsOfProperty(model.createProperty(FOAF_THUMBNAIL));
		if (ni.hasNext()) {
			return ni.next().asResource().getURI();
		}
		return null;
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
		return getStringLiteral(VIVO_ORCID_ID);
	}
	
	public void setOrcidId(String orcidId) {
		setLiteral(VIVO_ORCID_ID, orcidId);
	}

	public Collection<Long> getPubMedPublications() {
		Collection<Long> pmids= new HashSet<Long>();
		StmtIterator si = getResource().listProperties(getModel().createProperty(R2R_CONTRIBUTED_TO));
		while (si.hasNext()) {
			pmids.add(si.next().getLong());
		}
		return pmids;
	}
	
	public void addPubMedPublication(long pmid) {
		Model model = getModel();
		// Associate to Researcher
		model.add(getResource(), model.createProperty(R2R_CONTRIBUTED_TO), model.createResource("http:" + AuthorParser.PUBMED_SECTION + pmid));    			
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
		return getURI() + (getLabel() != null ? " : " + getLabel() : "") +
					" : " + getPubMedPublications().size() + " publications";
	}

	public int compareTo(Researcher arg0) {
		if (this.readErrorCount != arg0.readErrorCount) {
			return Integer.compare(this.readErrorCount, arg0.readErrorCount);
		}
		else {
			String thisStr = (getLabel() != null ? getLabel().trim() : " ") + getURI();
			String oStr = (arg0.getLabel() != null ? arg0.getLabel().trim() : " ") + arg0.getURI();
			return thisStr.compareTo(oStr);
		}
	}
	
	public int getExternalCoauthorCount() {
		return externalCoauthorCount;
	}    
	
	public int getSharedPublicationCount() {
		return sharedPublicationCount;
	}    
	
	public Calendar getWorkVerifiedDt() {
		return getDateTimeLiteral(R2R_WORK_VERIFIED_DT);
	}

	public void setWorkVerifiedDt(Calendar workVerifiedOn) {
		setLiteral(R2R_WORK_VERIFIED_DT, workVerifiedOn);
	}

	public Calendar getVerifiedDt() {
		return getDateTimeLiteral(R2R_VERIFIED_DT);
	}

	public void setVerifiedDt(Calendar verifiedOn) {
		setLiteral(R2R_VERIFIED_DT, verifiedOn);
	}
	
	@Override
	public List<Resource> getResources() {
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(getResource());
		Property hasImage = getModel().createProperty(FOAF_HAS_IMAGE);
		if (getResource().hasProperty(hasImage)) {
			Resource image = getResource().getPropertyResourceValue(hasImage);
			resources.add(image);
			Property hasThumbnail = getModel().createProperty(FOAF_THUMBNAIL);
			if (image.hasProperty(hasThumbnail)) {
				resources.add(image.getPropertyResourceValue(hasThumbnail));
			}
		}
		return resources;
	}

	public static void main(String[] args) {
		// simple test
		try {
			Researcher foo = new Researcher("http://profiles.ucsf.edu/eric.meeks");
			foo.addPubMedPublication("1234");
			foo.addPubMedPublication("https://www.ncbi.nlm.nih.gov/pubmed/?otool=uchsclib&term=17874365");
			foo.addPubMedPublication("http://www.ncbi.nlm.nih.gov/pubmed/24303259");
			foo.setLiteral(FOAF + "firstName", "Eric");
			foo.setLiteral(FOAF + "lastName", "Meeks");
			foo.setLiteral(FOAF + "firstName", "Joe");
			foo.setLiteral(FOAF_HOMEPAGE, "http://test.one");
			foo.setLiteral(FOAF_HOMEPAGE, "http://test.two");
			foo.setLiteral(FOAF_HOMEPAGE, "http://test.one");
			// force this, add to constructor or such
			foo.setLabel("Eric Meeks");
			StmtIterator si = foo.getResource().listProperties();
			while (si.hasNext()) {
				System.out.println(si.next().toString());
			}
			//RDFDataMgr.write(System.out, foo.getResource().getModel(), RDFFormat.RDFXML);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}
