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
	private String lodURI;
	private String orcidId;
	private Collection<Integer> pmids= new HashSet<Integer>();

	public Author(String url) {
		this.setURL(url);
	}
	
	public Author(String affiliation, String lastName, String firstName, String middleName, String url, String lodURI, String orcidId) {
		this(url);
    	this.setAffiliation(affiliation);
    	this.setLastName(lastName);
    	this.setFirstName(firstName);
    	this.setMiddleName(middleName);
    	this.setLodURI(lodURI);
    	this.setOrcidId(orcidId);
    }

    public Author(String affiliation, JSONObject person, String url) throws JSONException {
    	this(affiliation, person.getString("lastName"), person.getString("firstName"), person.optString("middleName"), url, person.getString("@id"), person.optString("orcidId"));
    }
    
    public void merge(Author author) throws Exception {
    	this.setAffiliation(getMergedValue(this.affiliation, author.affiliation));
    	this.setLastName(getMergedValue(this.lastName, author.lastName));
    	this.setFirstName(getMergedValue(this.firstName, author.firstName));
    	this.setMiddleName(getMergedValue(this.middleName, author.middleName));
    	this.setURL(getMergedValue(this.URL, author.URL));
    	this.setLodURI(getMergedValue(this.lodURI, author.lodURI));
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
    
    public void setPersonInfo(JSONObject person) throws JSONException {
    	 this.setLastName(person.getString("lastName"));
    	 this.setFirstName(person.getString("firstName"));
    	 this.setMiddleName(person.optString("middleName"));
    	 this.setLodURI(person.getString("@id"));
    	 this.setOrcidId(person.optString("orcidId"));
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
	
	public String getLodURI() {
		return lodURI;
	}
	
	private void setLodURI(String lodURI) {
		this.lodURI = lodURI;
	}

	public String getOrcidId() {
		return orcidId;
	}
	
	public void setOrcidId(String orcidId) {
		this.orcidId = orcidId;
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
	
	public String toString() {
		return "" + lastName + ", " + firstName + " : " + URL;
	}
}
