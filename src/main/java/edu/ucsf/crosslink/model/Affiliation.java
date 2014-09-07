package edu.ucsf.crosslink.model;

import java.net.URI;
import java.net.URISyntaxException;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class Affiliation {
	
	private String name;
	private URI baseURI;
	private int researcherCount;
	private int pmidCount;
	private String latitude;
	private String longitude;

	@Inject
	public Affiliation(@Named("Name") String affiliationName, @Named("BaseURL") String baseURL, @Named("Location") String location) throws URISyntaxException {
		this.name = affiliationName;
		this.baseURI = new URI(baseURL);
		if (location != null) {
			String [] geoCodes = location.split(",");
			this.latitude = geoCodes[0];
			this.longitude = geoCodes[1];
		}
	}
	
	public Affiliation(String name, String baseURL, String location, int researcherCount, int pmidCount) throws URISyntaxException {
		this(name, baseURL, null);
		this.researcherCount = researcherCount;		
		this.pmidCount = pmidCount;
	}
	
	@Override
	public String toString() {
		return getName();
	}
	
	public URI getURI() {
		return baseURI; 
	}

	public String getBaseURL() {
		return baseURI.toString(); 
	}

	public String getName() {
		return name;
	}
	
	public int getResearcherCount() {
		return researcherCount;
	}
	
	public int getPmidCount() {
		return pmidCount;
	}
	
	public String getLatitude() {
		return latitude;
	}
	
	public String getLongitude() {
		return longitude;
	}
}
