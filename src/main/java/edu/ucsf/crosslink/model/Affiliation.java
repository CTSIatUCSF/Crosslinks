package edu.ucsf.crosslink.model;

import java.net.URISyntaxException;
import java.util.Arrays;

public class Affiliation extends R2RResourceObject {
	
	private int researcherCount;
	private int publicationCount;
	private RNSType rnsType;
	
	public enum RNSType {PROFILES, VIVO, SCIVAL, LOKI, CAP, UNKNOWN};

	public Affiliation(String affiliationName, String baseURL, String location) throws URISyntaxException {
		super(baseURL, Arrays.asList(R2R_AFFILIATION, GEO_SPATIALTHING));
		setLabel(affiliationName);
		rnsType = getRNSType(baseURL.toLowerCase());
		if (location != null) {
			String [] geoCodes = location.split(",");
			setLiteral(GEO_LATITUDE, geoCodes[0]);
			setLiteral(GEO_LONGITUDE, geoCodes[1]);
		}
	}
	
	public Affiliation(String name, String baseURL, String location, int researcherCount, int publicationCount) throws URISyntaxException {
		this(name, baseURL, null);
		this.researcherCount = researcherCount;		
		this.publicationCount = publicationCount;
	}
	
	private static RNSType getRNSType(String baseURL) {
		if (baseURL.contains("stanford.edu")) {
			return RNSType.CAP;
		}
		else if (baseURL.contains("loki")) {
			return RNSType.LOKI;
		}
		else if (baseURL.contains("scival.com") || baseURL.contains("northwestern.edu")) {
			return RNSType.SCIVAL;
		}
		else if (baseURL.contains("vivo") || baseURL.contains("duke.edu") || 
				baseURL.contains("suny.edu") || baseURL.contains("unimelb.edu.au")) {
			return RNSType.VIVO;
		}
		else if (baseURL.contains("profiles")) {
			return RNSType.PROFILES;
		}
		return RNSType.UNKNOWN;
	}
	
	public RNSType getRNSType() {
		return rnsType;
	}
	
	public int getResearcherCount() {
		return researcherCount;
	}
	
	public int getPublicationCount() {
		return publicationCount;
	}
	
	public String getLatitude() {
		return getStringLiteral(GEO_LATITUDE);
	}
	
	public String getLongitude() {
		return getStringLiteral(GEO_LONGITUDE);
	}
}
