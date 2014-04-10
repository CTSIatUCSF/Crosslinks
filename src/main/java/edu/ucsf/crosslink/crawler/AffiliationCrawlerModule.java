package edu.ucsf.crosslink.crawler;

import java.util.Properties;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.crawler.sitereader.SiteReader;
import edu.ucsf.crosslink.io.AffiliationJenaPersistance;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.job.quartz.AffiliationCrawlerJob;
import edu.ucsf.crosslink.model.Affiliation;

public class AffiliationCrawlerModule extends AbstractModule {

	private Properties prop = new Properties();
	
	public AffiliationCrawlerModule(Properties prop) {
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
		bind(String.class).annotatedWith(Names.named("Affiliation")).toInstance(prop.getProperty("Affiliation"));
		bind(String.class).annotatedWith(Names.named("BaseURL")).toInstance(prop.getProperty("BaseURL"));
		bind(String.class).annotatedWith(Names.named("Location")).toInstance(prop.getProperty("Location"));
		bind(Affiliation.class).asEagerSingleton();;
		
		bind(AffiliationCrawler.Mode.class).toInstance(AffiliationCrawler.Mode.valueOf(prop.getProperty("crawlingMode").toUpperCase()));
		bind(Integer.class).annotatedWith(Names.named("errorsToAbort")).toInstance(Integer.parseInt(prop.getProperty("errorsToAbort")));
		bind(Integer.class).annotatedWith(Names.named("pauseOnAbort")).toInstance(Integer.parseInt(prop.getProperty("pauseOnAbort")));
		bind(Integer.class).annotatedWith(Names.named("authorReadErrorThreshold")).toInstance(Integer.parseInt(prop.getProperty("authorReadErrorThreshold")));

		bind(Integer.class).annotatedWith(Names.named("staleDays")).toInstance(Integer.parseInt(prop.getProperty("staleDays")));
        // just for now...
        bind(AffiliationJenaPersistance.class).asEagerSingleton();;
		try {
			bind(CrosslinkPersistance.class).to((Class<? extends CrosslinkPersistance>) Class.forName(prop.getProperty("AuthorshipPersistance"))).asEagerSingleton();;
			bind(SiteReader.class).to((Class<? extends SiteReader>) Class.forName(prop.getProperty("Reader"))).asEagerSingleton();;			
			bind(AuthorParser.class).to((Class<? extends AuthorParser>) Class.forName(prop.getProperty("AuthorParser"))).asEagerSingleton();;
		} 
		catch (ClassNotFoundException e) {
			addError(e);
		}

		bind(AffiliationCrawler.class).asEagerSingleton();;
        bind(AffiliationCrawlerJob.class).asEagerSingleton();;
        
	}

}
