package edu.ucsf.crosslink.model;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.joda.time.DateTime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Whitelist;

import edu.ucsf.crosslink.processor.controller.ProcessorController;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.R2ROntology;

public class Researcher extends R2RResourceObject implements Comparable<Researcher>, R2RConstants {
	private static final Logger LOG = Logger.getLogger(Researcher.class.getName());
	
	private Affiliation affiliation;
	private List<String> imageURLs = new ArrayList<String>(); // we allow for grabbing more than one and then test to see if any are valid when saving
	private int readErrorCount = 0;
	
	// display data
	private int externalCoauthorCount;
	private int sharedPublicationCount;	

	public Researcher(String uri) throws URISyntaxException {
		super(uri, FOAF_PERSON);
	}

	public Researcher(String uri, Affiliation affiliation) throws URISyntaxException {
		this(uri);
    	this.setAffiliation(affiliation);
	}

	public Researcher(String uri, Affiliation affiliation, String label) throws URISyntaxException {
		this(uri, affiliation);
		setLabel(label);
	}

	// loading for display only
	public Researcher(String uri, Affiliation affiliation, String label,
			String homePage, String imageURL, String thumbnailURL, 
			int externalCoauthorCount, int sharedPublicationCount) throws URISyntaxException {
		super(uri);
		this.setAffiliation(affiliation);
		this.setLabel(label);
		if (homePage != null) {
			this.setHomepage(homePage);
		}
		this.setConfirmedImgURLs(imageURL, thumbnailURL);
    	this.externalCoauthorCount = externalCoauthorCount;
    	this.sharedPublicationCount = sharedPublicationCount;
    }
    
	public void setFOAFName(String firstName, String lastName) {
		setLiteral(FOAF_FIRST_NAME, firstName);
		setLiteral(FOAF_LAST_NAME, lastName);
	}
	
	public void crawledBy(ProcessorController processorController) {
		Model model = getModel();
		// see if we have already been crawled by this crawler
		StmtIterator si = getStatements(R2R_PROCESSED);
		while (si.hasNext()) {
			Resource crawl = si.next().getResource();
			if (processorController.getURI().equals(crawl.getProperty(model.createProperty(R2R_PROCESSED_BY)).getResource().getURI())) {
				// update the time stamp
				model.removeAll(crawl, model.createProperty(R2R_PROCESSED_ON), null);
				crawl.addLiteral(model.createProperty(R2R_PROCESSED_ON), model.createTypedLiteral(Calendar.getInstance()));
				si.close();
				return;
			}
		}
		si.close();
		// create a blank node
		Resource crawl = model.createResource();
		crawl.addProperty(model.createProperty(R2R_PROCESSED_BY), processorController.getResource());
		crawl.addLiteral(model.createProperty(R2R_PROCESSED_ON), model.createTypedLiteral(Calendar.getInstance()));
		
		model.add(getResource(), model.createProperty(R2R_PROCESSED), crawl);
	}
	
	public Affiliation getAffiliation() {
		return affiliation;
	}
	
	public void setAffiliation(Affiliation affiliation) {
		this.affiliation = affiliation;
		setResource(R2R_HAS_AFFILIATION, affiliation);
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
			Model model = getModel();
			Property propType = model.createProperty(RDF_TYPE);
			Resource foafImage = model.createResource(FOAF_IMAGE);
			Resource imageResource = setResource(FOAF_HAS_IMAGE, imageURL);
			imageResource.addProperty(propType, foafImage);
			if (thumbnailURL != null) {
				Resource thumbnailResource = model.createResource(thumbnailURL);
				thumbnailResource.addProperty(propType, foafImage);
				imageResource.addProperty(model.createProperty(FOAF_THUMBNAIL), thumbnailResource);
				// should we also add thumbnail to the researcher?
			}
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

	public Collection<String> getPublications() {
		Collection<String> publications= new HashSet<String>();
		StmtIterator si = getResource().listProperties(getModel().createProperty(FOAF_PUBLICATIONS));
		while (si.hasNext()) {
			publications.add(si.next().getResource().getURI());
		}
		return publications;		
	}
	
	// can handle it in URL form, or just the pmid
	public void addPublication(String publication) {
		Model model = getModel();
		model.add(getResource(), model.createProperty(FOAF_PUBLICATIONS), model.createResource(publication.replace("https:", "http:")));    			
	}

	public void registerReadException(Exception e) {
		this.readErrorCount++;
		LOG.log(Level.WARNING, "Error reading : " + this.toString(), e);
	}
	
	public int getErrorCount() {
		return readErrorCount;
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
	
	public Calendar getCrawlTime(ProcessorController processorController) {
		Model model = getModel();
		ResIterator ri = getModel().listResourcesWithProperty(model.createProperty(R2R_PROCESSED));
		while (ri.hasNext()) {
			Resource crawl = ri.next();
			if (processorController.getURI().equals(crawl.getProperty(model.createProperty(R2R_PROCESSED_BY)).getResource().getURI())) {
				return ((XSDDateTime)crawl.getProperty(model.createProperty(R2R_PROCESSED_ON)).getLiteral().getValue()).asCalendar();
			}
		}
		return null;
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
			Calendar now = Calendar.getInstance();
			now.setTimeInMillis(new DateTime().minusDays(4).getMillis());
			System.out.println(R2ROntology.createDefaultModel().createTypedLiteral(now).getString());

			String doiLink = "<a href=\"http://psycnet.apa.org/doi/10.1037/a0016478\">10.1037/a0016478</a>";
			System.out.println(Jsoup.isValid(doiLink, Whitelist.basic()));
			Document doc = Jsoup.parseBodyFragment(doiLink);
			System.out.println(doc.text());
			String doi = doc.select("a[href]").get(0).text();
			System.out.println(doi);
			System.out.println(Jsoup.isValid(doi, Whitelist.basic()));

			Researcher foo = new Researcher("http://profiles.ucsf.edu/eric.meeks");
			//foo.addPubMedPublication("1234");
			//foo.addPubMedPublication("https://www.ncbi.nlm.nih.gov/pubmed/?otool=uchsclib&term=17874365");
			//foo.addPubMedPublication("http://www.ncbi.nlm.nih.gov/pubmed/24303259");
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
