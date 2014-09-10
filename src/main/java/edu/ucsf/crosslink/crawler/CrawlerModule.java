package edu.ucsf.crosslink.crawler;

import java.util.Properties;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.job.quartz.CrawlerJob;
import edu.ucsf.crosslink.model.Affiliation;

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

		// AffiliationCrawler items
		bind(String.class).annotatedWith(Names.named("Name")).toInstance(prop.getProperty("Name"));
		bind(String.class).annotatedWith(Names.named("BaseURL")).toInstance(prop.getProperty("BaseURL"));
		bind(String.class).annotatedWith(Names.named("Location")).toInstance(prop.getProperty("Location"));
		bind(Affiliation.class).asEagerSingleton();
		
		bind(Crawler.Mode.class).toInstance(Crawler.Mode.valueOf(prop.getProperty("crawlingMode").toUpperCase()));
		bind(Integer.class).annotatedWith(Names.named("errorsToAbort")).toInstance(Integer.parseInt(prop.getProperty("errorsToAbort")));
		bind(Integer.class).annotatedWith(Names.named("pauseOnAbort")).toInstance(Integer.parseInt(prop.getProperty("pauseOnAbort")));
		bind(Integer.class).annotatedWith(Names.named("authorReadErrorThreshold")).toInstance(Integer.parseInt(prop.getProperty("authorReadErrorThreshold")));
		bind(Integer.class).annotatedWith(Names.named("sparqlDetailThreadCount")).toInstance(Integer.parseInt(prop.getProperty("sparqlDetailThreadCount")));
		bind(Integer.class).annotatedWith(Names.named("pageItemThreadCount")).toInstance(Integer.parseInt(prop.getProperty("pageItemThreadCount")));

		bind(Integer.class).annotatedWith(Names.named("staleDays")).toInstance(Integer.parseInt(prop.getProperty("staleDays")));
		
		try {
			bind(Crawler.class).to((Class<? extends Crawler>) Class.forName(prop.getProperty("Crawler"))).asEagerSingleton();
			bind(AuthorParser.class).to((Class<? extends AuthorParser>) Class.forName(prop.getProperty("AuthorParser"))).asEagerSingleton();
		} 
		catch (ClassNotFoundException e) {
			addError(e);
		}

        bind(CrawlerJob.class).asEagerSingleton();;
        
	}

}
