package edu.ucsf.crosslink;

import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;

public class Authorship {

	private static final Logger LOG = Logger.getLogger(Authorship.class.getName());
	
	private String affiliation;
	private String lastName;
	private String firstName;
	private String middleName;
	private String URL;
	private String pmid;  // keep as string so we can handle NULL 
	
	public static String[] ColumnNames = {"Affiliation", "LastName", "FirstName", "MiddleName", "URL", "PMID"};
	
	public Authorship() {
	}
	
	public Authorship(String[] entry) {
		this.affiliation = entry[0];
		this.lastName = entry[1];
		this.firstName = entry[2];
		this.middleName = entry[3];
		this.URL = entry[4];
		this.pmid = entry[5];
	}

    public Authorship(String url, JSONObject person, String pmid) throws JSONException {
    	this.setFirstName(person.getString("firstName"));
    	try {
    		this.setMiddleName(person.getString("middleName"));
    	}
    	catch (JSONException e) {
    		LOG.info(url + " does not have a middle name");
    	}
    	this.setLastName(person.getString("lastName"));
    	this.setURL(url);
    	this.setPmid(pmid);
    }

    public Authorship(String affiliation, String url, String lastName, String firstName, String middleName, String pmid) throws JSONException {
    	this.setAffiliation(affiliation);
    	this.setLastName(lastName);
    	this.setFirstName(firstName);
    	this.setMiddleName(middleName);
    	this.setURL(url);
    	this.setPmid(pmid);
    }

    public String getLastName() {
		return lastName;
	}
	
	public void setLastName(String lastName) {
		this.lastName = lastName;
	}
	
	public String getFirstName() {
		return firstName;
	}
	
	public void setMiddleName(String middleName) {
		this.middleName = middleName;
	}
	
	public String getMiddleName() {
		return middleName;
	}
	
	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getAffiliation() {
		return affiliation;
	}
	
	public void setAffiliation(String affiliation) {
		this.affiliation = affiliation;
	}
	
	public String getURL() {
		return URL;
	}
	
	public void setURL(String uRL) {
		URL = uRL;
	}
	
	public String getPmid() {
		return pmid;
	}
	
	public void setPmid(String pmid) {
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
