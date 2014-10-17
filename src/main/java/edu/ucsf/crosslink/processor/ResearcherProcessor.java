package edu.ucsf.crosslink.processor;

import edu.ucsf.crosslink.crawler.Crawler;
import edu.ucsf.crosslink.crawler.TypedOutputStats.OutputType;

public interface ResearcherProcessor {
	
	public static final String PUBMED_SECTION = "//www.ncbi.nlm.nih.gov/pubmed/";
	
	void setCrawler(Crawler crawler);
	
	OutputType processResearcher() throws Exception;

}
