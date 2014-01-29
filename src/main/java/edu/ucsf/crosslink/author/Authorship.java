package edu.ucsf.crosslink.author;

import org.json.JSONException;
import org.json.JSONObject;

class Authorship {

	private String affiliation;
	private String lastName;
	private String firstName;
	private String middleName;
	private String URL;
	private String pmid;  // keep as string so we can handle NULL 
	
	public static String[] ColumnNames = {"Affiliation", "LastName", "FirstName", "MiddleName", "URL", "PMID"};
	
	Authorship() {
	}
	
    Authorship(String affiliation, String lastName, String firstName, String middleName, String url, String pmid) {
    	this.setAffiliation(affiliation);
    	this.setLastName(lastName);
    	this.setFirstName(firstName);
    	this.setMiddleName(middleName);
    	this.setURL(url);
    	this.setPmid(pmid);
    }

	Authorship(String[] entry) {
		this(entry[0], entry[1], entry[2], entry[3], entry[4], entry[5]);
	}

    Authorship(Author author, String pmid) {
    	this(author.getAffiliation(), author.getLastName(), author.getFirstName(), author.getMiddleName(), author.getURL(), pmid);
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
	
	private void setURL(String uRL) {
		URL = uRL;
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
		String[] retval = {affiliation, lastName, firstName, middleName, URL, pmid};
		return retval;
	}

}
