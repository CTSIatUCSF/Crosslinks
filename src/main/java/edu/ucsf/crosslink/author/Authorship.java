package edu.ucsf.crosslink.author;

public class Authorship {

	private String affiliation;
	private String lastName;
	private String firstName;
	private String middleName;
	private String URL;
	private String lodURI;
	private String orcidId;
	private String pmid;  // keep as string so we can handle NULL 
	
	public static String[] ColumnNames = {"Affiliation", "LastName", "FirstName", "MiddleName", "URL", "lodURI", "OrcidID", "PMID"};
	
    Authorship(String affiliation, String lastName, String firstName, String middleName, String url, String lodURI, String orcidId, String pmid) {
    	this.setAffiliation(affiliation);
    	this.setLastName(lastName);
    	this.setFirstName(firstName);
    	this.setMiddleName(middleName);
    	this.setURL(url);
    	this.setLodURI(lodURI);
    	this.setOrcidId(orcidId);
    	this.setPmid(pmid);
    }

	public Authorship(String[] entry) {
		this(entry[0], entry[1], entry[2], entry[3], entry[4], entry[5], entry[6], entry[7]);
	}

    Authorship(Author author, String pmid) {
    	this(author.getAffiliation(), author.getLastName(), author.getFirstName(), author.getMiddleName(), author.getURL(), author.getImageURL(), author.getOrcidId(), pmid);
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
	
	public String getLodURI() {
		return lodURI;
	}
	
	private void setLodURI(String lodURI) {
		this.lodURI = lodURI;
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
		String[] retval = {affiliation, lastName, firstName, middleName, URL, lodURI, orcidId, pmid};
		return retval;
	}

}
