package edu.ucsf.crosslink.processor;

import java.net.URISyntaxException;

import edu.ucsf.crosslink.crawler.Crawler;
import edu.ucsf.crosslink.model.Researcher;

public abstract class BasicResearcherProcessor implements ResearcherProcessor {

	private String researcherURI = null;
	private Crawler crawler = null;
	
	protected BasicResearcherProcessor(String researcherURI) {
		this.researcherURI = researcherURI;
	}
	
	public String toString() {
		return researcherURI;
	}
	
	public void setCrawler(Crawler crawler) {
		this.crawler = crawler;
	}
	
	protected String getResearcherURI() {
		return researcherURI;
	}
	
	protected boolean allowSkip() {
		return crawler != null ? crawler.allowSkip() : false;
	}
	
	protected Researcher createResearcher() throws URISyntaxException {
		Researcher researcher = new Researcher(researcherURI);
		if (crawler != null) {
			researcher.setHarvester(crawler);
		}
		return researcher;
	}
}
