package edu.ucsf.crosslink.processor;

import java.net.URISyntaxException;

import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.crosslink.processor.controller.ProcessorController;

public abstract class BasicResearcherProcessor implements ResearcherProcessor {

	private String researcherURI = null;
	private ProcessorController processorController = null;
	
	protected BasicResearcherProcessor(String researcherURI) {
		this.researcherURI = researcherURI;
	}
	
	public String toString() {
		return researcherURI;
	}
	
	public void setCrawler(ProcessorController processorController) {
		this.processorController = processorController;
	}
	
	public ProcessorController getCrawler() {
		return processorController;
	}
	
	protected String getResearcherURI() {
		return researcherURI;
	}
	
	protected boolean allowSkip() {
		return processorController != null ? processorController.allowSkip() : false;
	}
	
	protected Researcher createResearcher() throws URISyntaxException {
		Researcher researcher = new Researcher(researcherURI);
		if (processorController != null) {
			researcher.crawledBy(processorController);
		}
		return researcher;
	}
}
