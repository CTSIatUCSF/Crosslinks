package edu.ucsf.crosslink.model;

import java.net.URISyntaxException;
import java.util.Arrays;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class Affiliation extends R2RResourceObject {
	
	private int researcherCount;
	private int pmidCount;

	@Inject
	public Affiliation(@Named("Name") String affiliationName, @Named("BaseURL") String baseURL, @Named("Location") String location) throws URISyntaxException {
		super(baseURL, Arrays.asList(R2R_AFFILIATION, GEO_SPATIALTHING));
		setLabel(affiliationName);
		if (location != null) {
			String [] geoCodes = location.split(",");
			setLiteral(GEO_LATITUDE, geoCodes[0]);
			setLiteral(GEO_LONGITUDE, geoCodes[1]);
		}
	}
	
	public Affiliation(String name, String baseURL, String location, int researcherCount, int pmidCount) throws URISyntaxException {
		this(name, baseURL, null);
		this.researcherCount = researcherCount;		
		this.pmidCount = pmidCount;
	}
	
	public int getResearcherCount() {
		return researcherCount;
	}
	
	public int getPmidCount() {
		return pmidCount;
	}
	
	public String getLatitude() {
		return getStringLiteral(GEO_LATITUDE);
	}
	
	public String getLongitude() {
		return getStringLiteral(GEO_LONGITUDE);
	}
}
