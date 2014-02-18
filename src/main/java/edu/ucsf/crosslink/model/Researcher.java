package edu.ucsf.crosslink.model;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.coobird.thumbnailator.Thumbnails;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.json.JSONException;
import org.json.JSONObject;

import edu.ucsf.crosslink.crawler.parser.AuthorParser;


public class Researcher implements Comparable<Researcher> {
	private static final Logger LOG = Logger.getLogger(Researcher.class.getName());
	
	private String affiliationName;
	private String lastName;
	private String firstName;
	private String middleName;
	private String URL;
	private List<String> imageURLs = new ArrayList<String>(); // we allow for grabbing more than one and then test to see if any are valid when saving
	private String thumbnailURL;
	private String orcidId;
	private int externalCoauthorCount;
	private int readErrorCount = 0;
	private Collection<Integer> pmids= new HashSet<Integer>();

	public Researcher(String url) {
		this.setURL(url);
	}

	// for loading from the DB
	public Researcher(Affiliation affiliation,
			String lastName, String firstName, String middleName, String url, String imageURL, String thumbnailURL, 
			String orcidId, int externalCoauthorCount) {		
		this(url);
    	this.setAffiliationName(affiliation.getName());
    	this.setLastName(lastName);
    	this.setFirstName(firstName);
    	this.setMiddleName(middleName);
    	this.addImageURL(imageURL);
    	this.thumbnailURL = thumbnailURL;
    	this.setOrcidId(orcidId);
    	this.externalCoauthorCount = externalCoauthorCount;
    }

	public Researcher(String affiliationName, String lastName, String firstName, String middleName, String url, String imageURL, String orcidId) {
		this(url);
    	this.setAffiliationName(affiliationName);
    	this.setLastName(lastName);
    	this.setFirstName(firstName);
    	this.setMiddleName(middleName);
    	this.addImageURL(imageURL);
    	this.setOrcidId(orcidId);
    }

    public Researcher(String affiliationName, JSONObject person, String url) throws JSONException {
    	this(affiliationName, person.getString("lastName"), person.getString("firstName"), person.optString("middleName"), url, person.optString("mainImage"), person.optString("orcidId"));
    }
    
    public void merge(Researcher author) throws Exception {
    	this.setAffiliationName(getMergedValue(this.affiliationName, author.affiliationName));
    	this.setLastName(getMergedValue(this.lastName, author.lastName));
    	this.setFirstName(getMergedValue(this.firstName, author.firstName));
    	this.setMiddleName(getMergedValue(this.middleName, author.middleName));
    	this.setURL(getMergedValue(this.URL, author.URL));
    	this.imageURLs.addAll(author.imageURLs);
    	this.setOrcidId(getMergedValue(this.orcidId, author.orcidId));
    	this.pmids.addAll(author.pmids);
    }
    
    private String getMergedValue(String one, String other) throws Exception {
    	if (one == null) {
    		return other;
    	}
    	else if (other == null) {
    		return one;
    	}
    	else if (one.equalsIgnoreCase(other)) {
        	return one;  
    	}
    	else {
    		throw new Exception("Confilicting values in merge of " + one + " :AND: " + other);
    	}
    }
    
    public String getLastName() {
		return lastName;
	}
	
    private void setLastName(String lastName) {
		this.lastName = lastName;
	}
	
	public String getFirstName() {
		return firstName;
	}
	
	private void setMiddleName(String middleName) {
		this.middleName = StringUtils.isEmpty(middleName) ? null : middleName;
	}
	
	public String getMiddleName() {
		return middleName;
	}
	
	private void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getAffiliationName() {
		return affiliationName;
	}
	
	private void setAffiliationName(String affiliationName) {
		this.affiliationName = affiliationName;
	}
	
	public String getURL() {
		return URL;
	}
	
	private void setURL(String uRL) {
		URL = uRL;
	}
	
	// ugly but it works
	public boolean generateReseacherThumbnail(String thumbnailDir, int thumbnailWidth, int thumbnailHeight, String thumbnailRootURL) {
		if (URL != null && imageURLs.size() > 0 && thumbnailURL == null) {
			int id = URL.toLowerCase().hashCode();
			String loc = getAffiliationName() + "/" + ("" + (100 + (Math.abs(id) % 100))).substring(1) + "/" + id + ".jpg";
			for (String imageURL : imageURLs) {
				try {
					File thumbnail = new File(thumbnailDir + "/" + loc );
					new File(thumbnail.getParent()).mkdirs();
					Thumbnails.of(new URL(imageURL))
			        	.size(thumbnailWidth, thumbnailHeight)
			        	.toFile(thumbnail);
					// if we made it here, we are good
					this.thumbnailURL = thumbnailRootURL + "/" + loc;
					imageURLs.clear();
					imageURLs.add(imageURL);
					return true;
				}
				catch (Exception e) {
					LOG.log(Level.WARNING, e.getMessage(), e);
				}
			}
		}
		// if we get here, they are all bad
		imageURLs.clear();
		return false;
	}	
	
	public String getThumbnailURL() {
		return thumbnailURL; 
	}
	
	public String getImageURL() {
		// If we have more than one, we don't know which is valid so return nothing
		return imageURLs.size() == 1 ? imageURLs.get(0) : null;
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

	public Collection<Authorship> getAuthorships() {
		HashSet<Authorship> authorships = new HashSet<Authorship>();
		for (Integer pmid : pmids) {
			authorships.add(new Authorship(this, String.valueOf(pmid)));
		}
		if (authorships.isEmpty()) {
			// add a blank one
			authorships.add(new Authorship(this, null));			
		}
		return authorships;
	}
	
	public void registerReadException(Exception e) {
		this.readErrorCount++;
		LOG.log(Level.WARNING, "Error reading : " + this.toString(), e);
	}
	
	public int getErrorCount() {
		return readErrorCount;
	}
	
	public String toString() {
		return (lastName != null ? lastName + ", " + firstName + " : " : " ") + URL;
	}

	@Override
	public int compareTo(Researcher arg0) {
		return this.readErrorCount == arg0.readErrorCount ? 
					this.toString().compareTo(arg0.toString()) : Integer.compare(this.readErrorCount, arg0.readErrorCount);
	}
	
	public String getName() {
		return lastName != null ? (lastName + ", " + firstName + (middleName != null ? " " + middleName : "")) : "";
	}
	
	public int getExternalCoauthorCount() {
		return externalCoauthorCount;
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
