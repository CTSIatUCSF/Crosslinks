package edu.ucsf.crosslink.crawler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.Crosslinks;
import edu.ucsf.crosslink.crawler.AffiliationCrawler.DateComparator;

@Singleton
public class AffiliationCrawlerFactory {

	public static final String AFFILIATION = "AFFILIATION";
	
	private Injector guice;
	private String configurationDirectory;
	private Map<String, AffiliationCrawler> liveCrawlers = new HashMap<String, AffiliationCrawler>();
	private Map<String, String> propertyFiles = new HashMap<String, String>(); 
	private Map<String, Injector> injectors = new HashMap<String, Injector>(); 
	
	@Inject
	public AffiliationCrawlerFactory(final Injector guice, @Named("configurationDirectory") String configurationDirectory) {
		this.guice = guice;
		this.configurationDirectory = configurationDirectory;
	}

	private synchronized void loadNewCrawlers() throws FileNotFoundException, IOException {
	    for (String fileName : getConfigurationFiles()) {
	    	// need some way to refresh old ones
	    	if (!propertyFiles.containsValue(fileName)) {
				Properties prop = new Properties();
				prop.load(this.getClass().getResourceAsStream(Crosslinks.PROPERTIES_FILE));	
				prop.load(new FileReader(new File(fileName)));
				Injector injector = guice.createChildInjector(new AffiliationCrawlerModule(prop));
				AffiliationCrawler crawler = injector.getInstance(AffiliationCrawler.class);
				injectors.put(crawler.getAffiliationName(), injector);		
				propertyFiles.put(crawler.getAffiliationName(), fileName);
				liveCrawlers.put(crawler.getAffiliationName(), crawler);				
	    	}
	    }		
	}
	
	public List<AffiliationCrawler> getLiveCrawlers() {
		List<AffiliationCrawler> crawlers = new ArrayList<AffiliationCrawler>();
		crawlers.addAll(liveCrawlers.values());
		Collections.sort(crawlers);
		return crawlers;
	}

	public List<AffiliationCrawler> getOldestCrawlers() throws FileNotFoundException, IOException {
		loadNewCrawlers();
		List<AffiliationCrawler> crawlers = new ArrayList<AffiliationCrawler>();
		crawlers.addAll(liveCrawlers.values());
		Collections.sort(crawlers, new DateComparator());
		return crawlers;
	}

	public AffiliationCrawler getCrawler(String affiliation) {
		return liveCrawlers.get(affiliation);
	}

	public Injector getInjector(String affiliation) throws FileNotFoundException, IOException {
		loadNewCrawlers();
		return injectors.get(affiliation);
	}

	private List<String> getConfigurationFiles() throws IOException  {
		List<String> fileNames = new ArrayList<String>();
		for (File file : new File(configurationDirectory).listFiles()) {
			fileNames.add(file.getAbsolutePath());
		}
		return fileNames;
	}
	
}
