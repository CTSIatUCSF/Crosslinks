package edu.ucsf.crosslink.model;


public class Authorship {

	private String affiliation;
	private String homePageURL;
	private String label;
	private String URI;
	private String imageURL;
	private String orcidId;
	private String pmid;  // keep as string so we can handle NULL 
	
	public static String[] ColumnNames = {"Affiliation", "HomePageURL", "URI", "Label", "imageURL", "OrcidID", "PMID"};
	
    Authorship(String affiliation, String homePageURL, String URI, String label, String imageURL, String orcidId, String pmid) {
    	this.setAffiliation(affiliation);
    	this.setHomePageURL(homePageURL);
    	this.setURI(URI);
    	this.setLabel(label);
    	this.setImageURL(imageURL);
    	this.setOrcidId(orcidId);
    	this.setPmid(pmid);
    }

	public Authorship(String[] entry) {
		this(entry[0], entry[1], entry[2], entry[3], entry[4], entry[5], entry[6]);
	}

    Authorship(Researcher author, String pmid) {
    	this(author.getAffiliation().getName(), author.getHomePageURL(), author.getURI(), author.getLabel(), author.getImageURL(), author.getOrcidId(), pmid);
    }

    public String getLabel() {
		return label;
	}
	
    private void setLabel(String label) {
		this.label = label;
	}
	
	public String getHomePageURL() {
		return homePageURL;
	}
	
	private void setHomePageURL(String homePageURL) {
		this.homePageURL = homePageURL;
	}
	
	public String getAffiliation() {
		return affiliation;
	}
	
	private void setAffiliation(String affiliation) {
		this.affiliation = affiliation;
	}
	
	public String getURI() {
		return URI;
	}
	
	private void setURI(String URI) {
		this.URI = URI;
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
		return affiliation + ": " + label + ": " + pmid;
	}
	
	public String[] toStringArray() {
		String[] retval = {affiliation, homePageURL, URI, label, imageURL, orcidId, pmid};
		return retval;
	}

}
