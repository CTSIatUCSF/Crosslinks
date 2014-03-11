package edu.ucsf.crosslink.model;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class Affiliation {
	
	private String name;
	private String baseURL;
	private int researcherCount;
	private int pmidCount;

	@Inject
	public Affiliation(@Named("Affiliation") String affiliationName, @Named("BaseURL") String baseURL) {
		this.name = affiliationName;
		this.baseURL = baseURL;
	}
	
	public Affiliation(String name, String baseURL, int researcherCount, int pmidCount) {
		this(name, baseURL);
		this.researcherCount = researcherCount;
		this.pmidCount = pmidCount;
	}
	
	@Override
	public String toString() {
		return getName();
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
}
