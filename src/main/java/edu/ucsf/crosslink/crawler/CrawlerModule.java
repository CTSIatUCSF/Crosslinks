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
		// SiteReader items
		bind(Integer.class).annotatedWith(Names.named("getDocumentRetry")).toInstance(Integer.parseInt(prop.getProperty("getDocumentRetry")));
		bind(Integer.class).annotatedWith(Names.named("getDocumentTimeout")).toInstance(Integer.parseInt(prop.getProperty("getDocumentTimeout")));
		bind(Integer.class).annotatedWith(Names.named("getDocumentSleep")).toInstance(Integer.parseInt(prop.getProperty("getDocumentSleep")));

		// Crawler items
		bind(String.class).annotatedWith(Names.named("Name")).toInstance(prop.getProperty("Name"));
		bind(String.class).annotatedWith(Names.named("FileName")).toInstance(prop.getProperty("FileName"));
		if (prop.getProperty("BaseURL") != null) {
			bind(String.class).annotatedWith(Names.named("BaseURL")).toInstance(prop.getProperty("BaseURL"));
		}
		if (prop.getProperty("Location") != null) {
			bind(String.class).annotatedWith(Names.named("Location")).toInstance(prop.getProperty("Location"));
		}
		
		bind(Crawler.Mode.class).toInstance(Crawler.Mode.valueOf(prop.getProperty("crawlingMode").toUpperCase()));
		bind(Integer.class).annotatedWith(Names.named("errorsToAbort")).toInstance(Integer.parseInt(prop.getProperty("errorsToAbort")));
		bind(Integer.class).annotatedWith(Names.named("pauseOnAbort")).toInstance(Integer.parseInt(prop.getProperty("pauseOnAbort")));
		bind(Integer.class).annotatedWith(Names.named("authorReadErrorThreshold")).toInstance(Integer.parseInt(prop.getProperty("authorReadErrorThreshold")));
		
		bind(Integer.class).annotatedWith(Names.named("staleDays")).toInstance(Integer.parseInt(prop.getProperty("staleDays")));
		bind(Integer.class).annotatedWith(Names.named("daysConsideredOld")).toInstance(Integer.parseInt(prop.getProperty("daysConsideredOld")));
		
		if (prop.getProperty("executorThreadCount") != null) {
			bind(Integer.class).annotatedWith(Names.named("executorThreadCount")).toInstance(Integer.parseInt(prop.getProperty("executorThreadCount")));
		}
		
		try {
			bind(new TypeLiteral<Iterable<ResearcherProcessor>>(){}).to((Class<? extends Iterable<ResearcherProcessor>>)Class.forName(prop.getProperty("Processor"))).asEagerSingleton();
			bind(Crawler.class).asEagerSingleton();;
		} 
		catch (ClassNotFoundException e) {
			addError(e);
		}

        bind(CrawlerJob.class).asEagerSingleton();;
        
	}

}
