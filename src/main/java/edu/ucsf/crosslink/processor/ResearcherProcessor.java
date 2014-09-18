package edu.ucsf.crosslink.processor;

import edu.ucsf.crosslink.crawler.Crawler;

public interface ResearcherProcessor {
	
	public enum Action {SKIPPED, AVOIDED, PROCESSED};
	
	void setCrawler(Crawler crawler);
	
	Action processResearcher() throws Exception;

}
