package edu.ucsf.crosslink.model;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class Affiliation {
	
	private String name;
	private String baseURL;
	private int researcherCount;
	private int pmidCount;
	private String latitude;
	private String longitude;

	@Inject
	public Affiliation(@Named("Affiliation") String affiliationName, @Named("BaseURL") String baseURL, @Named("Location") String location) {
		this.name = affiliationName;
		this.baseURL = baseURL;
		if (location != null) {
			String [] geoCodes = location.split(",");
			this.latitude = geoCodes[0];
			this.longitude = geoCodes[1];
		}
	}
	
	public Affiliation(String name, String baseURL, String location, int researcherCount, int pmidCount) {
		this(name, baseURL, null);
		this.researcherCount = researcherCount;		
		this.pmidCount = pmidCount;
	}
	
	@Override
	public String toString() {
		return getName();
	}
	
	public String getURI() {
		return getBaseURL(); 
	}

	public String getName() {
		return name;
	}
	
	public String getBaseURL() {
		return baseURL;
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
