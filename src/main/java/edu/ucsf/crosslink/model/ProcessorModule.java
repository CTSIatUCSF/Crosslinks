package edu.ucsf.crosslink.model;

import java.util.Properties;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import edu.ucsf.crosslink.job.quartz.ProcessorControllerJob;
import edu.ucsf.crosslink.processor.ResearcherProcessor;
import edu.ucsf.crosslink.processor.controller.ProcessorController;
import edu.ucsf.crosslink.processor.controller.ProcessorController.Mode;

public class ProcessorModule extends AbstractModule {

	private Properties prop = new Properties();
	
	public ProcessorModule(Properties prop) {
		this.prop = prop;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	protected void configure() {		
		// Crawler items
		// set the name to be based on the researcher processor class name and affiliation name when present
		String processorClassName = prop.getProperty("class").substring(prop.getProperty("class").lastIndexOf('.') + 1);
		if (prop.containsKey("Name")) {
			bind(String.class).annotatedWith(Names.named("crawlerName")).toInstance(prop.getProperty("Name").replaceAll("[^A-Za-z0-9]", "") + "." + processorClassName);
		}
		else {
			bind(String.class).annotatedWith(Names.named("crawlerName")).toInstance(processorClassName);
		}
		bind(ProcessorController.Mode.class).toInstance(ProcessorController.Mode.valueOf(prop.getProperty("executionMode").toUpperCase()));
		bind(Integer.class).annotatedWith(Names.named("errorsToAbort")).toInstance(Integer.parseInt(prop.getProperty("errorsToAbort")));
		bind(Integer.class).annotatedWith(Names.named("pauseOnAbort")).toInstance(Integer.parseInt(prop.getProperty("pauseOnAbort")));
		bind(Integer.class).annotatedWith(Names.named("threadCount")).toInstance(Integer.parseInt(prop.getProperty("threadCount")));
		
		try {
			bind(new TypeLiteral<Iterable<ResearcherProcessor>>(){}).to((Class<? extends Iterable<ResearcherProcessor>>)Class.forName(prop.getProperty("class"))).asEagerSingleton();
			bind(ProcessorController.class).asEagerSingleton();;
		} 
		catch (ClassNotFoundException e) {
			addError(e);
		}

        bind(ProcessorControllerJob.class).asEagerSingleton();;
        
	}

}
