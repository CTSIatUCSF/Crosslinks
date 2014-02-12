package edu.ucsf.crosslink.model;

public class Affiliation {
	
	private String name;
	private String baseURL;
	private int researcherCount;
	private int pmidCount;
	
	public Affiliation(String name, String baseURL, int researcherCount, int pmidCount) {
		this.name = name;
		this.baseURL = baseURL;
		this.researcherCount = researcherCount;
		this.pmidCount = pmidCount;
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
