package edu.ucsf.crosslink.processor;

import edu.ucsf.crosslink.processor.controller.ProcessorController;
import edu.ucsf.crosslink.processor.controller.TypedOutputStats.OutputType;
import edu.ucsf.ctsi.r2r.R2RConstants;

public interface ResearcherProcessor extends R2RConstants {
	
	public static final String PUBMED_SECTION = "//www.ncbi.nlm.nih.gov/pubmed/";
	
	public static final String DELETE_PRIOR_PROCESS_LOG = "DELETE {<%1$s> <" + R2R_PROCESSED + "> ?c . ?c ?p ?o} WHERE { " +
			"<%1$s> <" + R2R_PROCESSED + "> ?c . ?c <" + R2R_PROCESSED_BY + "> <%2$s>}";
	
	void setCrawler(ProcessorController processorController);
	
	OutputType processResearcher() throws Exception;

}
