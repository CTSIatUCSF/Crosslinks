package edu.ucsf.crosslink.author;

import java.util.Collection;
import java.util.HashSet;

import org.json.JSONException;
import org.json.JSONObject;

public class Author {

	private String affiliation;
	private String lastName;
	private String firstName;
	private String middleName;
	private String URL;
	private Collection<Integer> pmids= new HashSet<Integer>();

    public Author(String affiliation, String lastName, String firstName, String middleName, String url) {
    	this.setAffiliation(affiliation);
    	this.setLastName(lastName);
    	this.setFirstName(firstName);
    	this.setMiddleName(middleName);
    	this.setURL(url);
    }

    public Author(String affiliation, JSONObject person, String url) throws JSONException {
    	this(affiliation, person.getString("lastName"), person.getString("firstName"), person.optString("middleName"), url);
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
	
	public Collection<Integer> getPubMedPublications() {
		return pmids;
	}
	
	public void addPubMedPublication(int pmid) {
		pmids.add(pmid);
	}
	
	public void addPubMedPublication(String pmid) {
		addPubMedPublication(Integer.valueOf(pmid));
	}

	Collection<Authorship> getAuthorships() {
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
}
