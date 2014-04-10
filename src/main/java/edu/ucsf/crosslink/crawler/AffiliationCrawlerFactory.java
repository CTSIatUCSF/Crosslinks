package edu.ucsf.crosslink.crawler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.Crosslinks;

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
				// hack!
				if (!prop.containsKey("Location")) {
					prop.put("Location", "0,0");
				}
				Injector injector = guice.createChildInjector(new AffiliationCrawlerModule(prop));
				AffiliationCrawler crawler = injector.getInstance(AffiliationCrawler.class);
				injectors.put(crawler.getAffiliation().getName(), injector);		
				propertyFiles.put(crawler.getAffiliation().getName(), fileName);
				liveCrawlers.put(crawler.getAffiliation().getName(), crawler);				
	    	}
	    }		
	}
	
	public Collection<AffiliationCrawler> getCurrentCrawlers() {
		return liveCrawlers.values();
	}

	public Collection<AffiliationCrawler> getCrawlers() throws FileNotFoundException, IOException {
		loadNewCrawlers();
		return getCurrentCrawlers();
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
