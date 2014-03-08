package edu.ucsf.crosslink.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.json.JSONException;
import org.json.JSONObject;

import edu.ucsf.crosslink.crawler.parser.AuthorParser;


public class Researcher implements Comparable<Researcher> {
	private static final Logger LOG = Logger.getLogger(Researcher.class.getName());
	
	private String affiliationName;
	private String label;
	private String homePageURL;
	private String URI;
	private List<String> imageURLs = new ArrayList<String>(); // we allow for grabbing more than one and then test to see if any are valid when saving
	private String thumbnailURL;
	private String orcidId;
	private int externalCoauthorCount;
	private int readErrorCount = 0;
	private Collection<Integer> pmids= new HashSet<Integer>();

	public Researcher(String url) {
		this.setHomePageURL(url);
	}

	// for loading from the DB
	public Researcher(Affiliation affiliation,
			String homePageURL, String uri, String label, String imageURL, String thumbnailURL, 
			String orcidId, int externalCoauthorCount) {		
		this(homePageURL);
    	this.setAffiliationName(affiliation.getName());
    	this.setURI(uri);
    	this.setLabel(label);
    	this.addImageURL(imageURL);
    	this.thumbnailURL = thumbnailURL;
    	this.setOrcidId(orcidId);
    	this.externalCoauthorCount = externalCoauthorCount;
    }

	// TODO make this a URL and URI java object
	public Researcher(String affiliationName, String homePageURL, String uri, String label, String imageURL, String orcidId) {
		this(homePageURL);
    	this.setAffiliationName(affiliationName);
    	this.setURI(uri);
    	this.setLabel(label);
    	this.addImageURL(imageURL);
    	this.setOrcidId(orcidId);
    }

	// need to test this one
    public Researcher(String affiliationName, JSONObject person, String homePageURL) throws JSONException {
    	this(affiliationName, homePageURL, person.getString("@id"), getSuffixMatch(person, "#label"), getSuffixMatch(person, "#mainImage"), getSuffixMatch(person, "#orcidId"));
    }
    
    private static String getSuffixMatch(JSONObject item, String suffix) throws JSONException {
    	@SuppressWarnings("rawtypes")
		Iterator keys = item.keys();
    	while (keys.hasNext()) {
    		String key = "" + keys.next();
    		if (key.endsWith(suffix)) {
    			Object retval = item.get(key);
    			if (retval instanceof String) {
    				return (String)retval;
    			}
    			else if (retval instanceof JSONObject && ((JSONObject)retval).has("@id")) {
    				return ((JSONObject)retval).getString("@id");
    			}
    			if (item.getBoolean(key))
    			return item.getString(key);
    		}
    	}
    	return null;
    }
    
    public void merge(Researcher author) throws Exception {
    	this.setAffiliationName(getMergedValue(this.affiliationName, author.affiliationName));
    	this.setHomePageURL(getMergedValue(this.homePageURL, author.homePageURL));
    	this.setLabel(getMergedValue(this.label, author.label));
    	this.setURI(getMergedValue(this.URI, author.URI));
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
    
    public String getLabel() {
		return label;
	}
	
    private void setLabel(String label) {
		this.label = label;
	}
	
	public String getURI() {
		return URI;
	}
	
	public void setURI(String URI) {
		this.URI = StringUtils.isEmpty(URI) ? null : URI;
	}
	

	public String getAffiliationName() {
		return affiliationName;
	}
	
	private void setAffiliationName(String affiliationName) {
		this.affiliationName = affiliationName;
	}
	
	public String getHomePageURL() {
		return homePageURL;
	}
	
	private void setHomePageURL(String homePageURL) {
		this.homePageURL = homePageURL;
	}
	
	// ugly but it works
	public void setConfirmedImgURLs(String imageURL, String thumbnailURL) {
		this.imageURLs.clear();
		if (imageURL != null) {
			this.imageURLs.add(imageURL);
		}
		this.thumbnailURL = thumbnailURL;
	}
	
	public String getThumbnailURL() {
		return thumbnailURL; 
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
		return label + " : " + homePageURL;
	}

	public int compareTo(Researcher arg0) {
		return this.readErrorCount == arg0.readErrorCount ? 
					this.toString().compareTo(arg0.toString()) : Integer.compare(this.readErrorCount, arg0.readErrorCount);
	}
	
	public String getName() {
		return label;
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
