package edu.ucsf.crosslink;

import java.util.Properties;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import edu.ucsf.crosslink.author.AuthorParser;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.sitereader.SiteReader;

public class AffiliationCrawlerModule extends AbstractModule {

	private Properties prop = new Properties();
	
	public AffiliationCrawlerModule(Properties prop) {
		this.prop = prop;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	protected void configure() {
		bind(String.class).annotatedWith(Names.named("Affiliation")).toInstance(prop.getProperty("Affiliation"));
		bind(String.class).annotatedWith(Names.named("BaseURL")).toInstance(prop.getProperty("BaseURL"));
		
		bind(Integer.class).annotatedWith(Names.named("errorsToAbort")).toInstance(Integer.parseInt(prop.getProperty("errorsToAbort")));
		bind(Integer.class).annotatedWith(Names.named("getDocumentRetry")).toInstance(Integer.parseInt(prop.getProperty("getDocumentRetry")));
		bind(Integer.class).annotatedWith(Names.named("getDocumentTimeout")).toInstance(Integer.parseInt(prop.getProperty("getDocumentTimeout")));
		bind(Integer.class).annotatedWith(Names.named("getDocumentSleep")).toInstance(Integer.parseInt(prop.getProperty("getDocumentSleep")));
		
		// should use a real dependency injection framework someday for this
		try {
			bind(CrosslinkPersistance.class).to((Class<? extends CrosslinkPersistance>) Class.forName(prop.getProperty("AuthorshipPersistance")));
			bind(SiteReader.class).to((Class<? extends SiteReader>) Class.forName(prop.getProperty("Reader")));			
			bind(AuthorParser.class).to((Class<? extends AuthorParser>) Class.forName(prop.getProperty("AuthorParser")));
			bind(AffiliationCrawler.class);
		} 
		catch (ClassNotFoundException e) {
			addError(e);
		}

	}

}
