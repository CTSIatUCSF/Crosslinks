package edu.ucsf.crosslink.processor;

import edu.ucsf.crosslink.processor.controller.ProcessorController;
import edu.ucsf.crosslink.processor.controller.TypedOutputStats.OutputType;

public interface ResearcherProcessor {
	
	public static final String PUBMED_SECTION = "//www.ncbi.nlm.nih.gov/pubmed/";
	
	void setCrawler(ProcessorController processorController);
	
	OutputType processResearcher() throws Exception;

}
