package edu.ucsf.crosslink.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;


public class Researcher implements Comparable<Researcher> {
	private static final Logger LOG = Logger.getLogger(Researcher.class.getName());
	
	private String affiliationName;
	private String lastName;
	private String firstName;
	private String middleName;
	private String URL;
	private String imageURL;
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
    	this.setImageURL(imageURL);
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
    	this.setImageURL(imageURL);
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
    	this.setImageURL(getMergedValue(this.imageURL, author.imageURL));
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
	
	public String generateThumbnailURLSuffix() {
		if (URL != null && imageURL != null) {
			int id = URL.toLowerCase().hashCode();
			return getAffiliationName() + "/" + ("" + (100 + (Math.abs(id) % 100))).substring(1) + "/" + id + ".jpg";
		}
		return null;
	}
	
	public String getThumbnailURL() {
		return thumbnailURL; 
	}
	
	public String getImageURL() {
		return imageURL;
	}
	
	public void setImageURL(String imageURL) {
		this.imageURL = StringUtils.isEmpty(imageURL) ? null : imageURL;
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
	
	public void addPubMedPublication(String pmid) {
		addPubMedPublication(Integer.valueOf(pmid));
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
}
