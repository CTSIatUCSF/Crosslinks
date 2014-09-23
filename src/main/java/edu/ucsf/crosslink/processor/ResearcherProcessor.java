package edu.ucsf.crosslink.processor;

import edu.ucsf.crosslink.crawler.Crawler;

public interface ResearcherProcessor {
	
	public enum Action {SKIPPED, AVOIDED, PROCESSED, ERROR};

	public static final String PUBMED_SECTION = "//www.ncbi.nlm.nih.gov/pubmed/";
	
	void setCrawler(Crawler crawler);
	
	Action processResearcher() throws Exception;

}
