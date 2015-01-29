package edu.ucsf.crosslink.crawler;

import java.util.Properties;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import edu.ucsf.crosslink.job.quartz.CrawlerJob;
import edu.ucsf.crosslink.processor.ResearcherProcessor;

public class CrawlerModule extends AbstractModule {

	private Properties prop = new Properties();
	
	public CrawlerModule(Properties prop) {
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
		bind(Crawler.Mode.class).toInstance(Crawler.Mode.valueOf(prop.getProperty("crawlingMode").toUpperCase()));
		bind(Integer.class).annotatedWith(Names.named("errorsToAbort")).toInstance(Integer.parseInt(prop.getProperty("errorsToAbort")));
		bind(Integer.class).annotatedWith(Names.named("pauseOnAbort")).toInstance(Integer.parseInt(prop.getProperty("pauseOnAbort")));
		bind(Integer.class).annotatedWith(Names.named("threadCount")).toInstance(Integer.parseInt(prop.getProperty("threadCount")));
		
		try {
			bind(new TypeLiteral<Iterable<ResearcherProcessor>>(){}).to((Class<? extends Iterable<ResearcherProcessor>>)Class.forName(prop.getProperty("class"))).asEagerSingleton();
			bind(Crawler.class).asEagerSingleton();;
		} 
		catch (ClassNotFoundException e) {
			addError(e);
		}

        bind(CrawlerJob.class).asEagerSingleton();;
        
	}

}
