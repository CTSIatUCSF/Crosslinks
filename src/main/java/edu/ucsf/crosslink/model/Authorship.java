package edu.ucsf.crosslink.model;


public class Authorship {

	private String affiliation;
	private String lastName;
	private String firstName;
	private String middleName;
	private String URL;
	private String imageURL;
	private String orcidId;
	private String pmid;  // keep as string so we can handle NULL 
	
	public static String[] ColumnNames = {"Affiliation", "LastName", "FirstName", "MiddleName", "URL", "imageURL", "OrcidID", "PMID"};
	
    Authorship(String affiliation, String lastName, String firstName, String middleName, String url, String imageURL, String orcidId, String pmid) {
    	this.setAffiliation(affiliation);
    	this.setLastName(lastName);
    	this.setFirstName(firstName);
    	this.setMiddleName(middleName);
    	this.setURL(url);
    	this.setImageURL(imageURL);
    	this.setOrcidId(orcidId);
    	this.setPmid(pmid);
    }

	public Authorship(String[] entry) {
		this(entry[0], entry[1], entry[2], entry[3], entry[4], entry[5], entry[6], entry[7]);
	}

    Authorship(Researcher author, String pmid) {
    	this(author.getAffiliationName(), author.getLastName(), author.getFirstName(), author.getMiddleName(), author.getURL(), author.getImageURL(), author.getOrcidId(), pmid);
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
		this.middleName = middleName;
	}
	
	public String getMiddleName() {
		return middleName;
	}
	
	private void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getAffiliation() {
		return affiliation;
	}
	
	private void setAffiliation(String affiliation) {
		this.affiliation = affiliation;
	}
	
	public String getURL() {
		return URL;
	}
	
	private void setURL(String URL) {
		this.URL = URL;
	}
	
	public String getImageURL() {
		return imageURL;
	}
	
	private void setImageURL(String imageURL) {
		this.imageURL = imageURL;
	}

	public String getOrcidId() {
		return orcidId;
	}
	
	private void setOrcidId(String orcidId) {
		this.orcidId = orcidId;
	}

	public String getPmid() {
		return pmid;
	}
	
	private void setPmid(String pmid) {
		this.pmid = pmid;
	}
	
	public String toString() {
		return affiliation + ": " + lastName + ", " + firstName + ": " + pmid;
	}
	
	public String[] toStringArray() {
		String[] retval = {affiliation, lastName, firstName, middleName, URL, imageURL, orcidId, pmid};
		return retval;
	}

}
