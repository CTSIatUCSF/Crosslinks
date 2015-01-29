package edu.ucsf.crosslink.crawler;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import edu.ucsf.crosslink.CrosslinksXMLConfiguration;
import edu.ucsf.crosslink.PropertiesModule;

@Singleton
public class CrawlerFactory {

	public static final String AFFILIATION = "AFFILIATION";
	
	private Injector guice;
	private Map<String, Crawler> crawlers = new HashMap<String, Crawler>();
	private Map<String, Injector> injectors = new HashMap<String, Injector>(); 
	
	@Inject
	public CrawlerFactory(final Injector guice) throws XPathExpressionException, TransformerConfigurationException, SAXException, IOException, ParserConfigurationException {
		this.guice = guice;		
		loadCrawlers();
	}

	private void loadCrawlers() throws XPathExpressionException, TransformerConfigurationException, SAXException, IOException, ParserConfigurationException {
		// first load the ones that do not need an affiliation
		CrosslinksXMLConfiguration config = new CrosslinksXMLConfiguration();
		for (Node n : config.getDefaultedNodes("//Crosslinks/Processors")) {
			Properties prop = config.getChildrenAsProperties(n);
			
			Injector injector = guice.createChildInjector(new CrawlerModule(prop));
			Crawler crawler = injector.getInstance(Crawler.class);
			injectors.put(crawler.getName(), injector);		
			crawlers.put(crawler.getName(), crawler);							
		}

		// now load the ones that do need an affiliation
		for (Node n : config.getDefaultedNodes("//Crosslinks/Affiliations")) {
			Properties affiliationProps = config.getChildrenAsProperties(n);
			NodeList processors = config.evaluate(n, "*/Processor");
			for (int i = 0; i < processors.getLength(); i++) {		
				Node processor = processors.item(i);
				Properties processorProps = config.getChildrenAsProperties(processor);
				// sort of ugly
				processorProps.putAll(affiliationProps);
				Injector injector = guice.createChildInjector(new PropertiesModule(affiliationProps), 
						new CrawlerModule(processorProps));
				Crawler crawler = injector.getInstance(Crawler.class);
				injectors.put(crawler.getName(), injector);		
				crawlers.put(crawler.getName(), crawler);							
			}			
		}
	}
	
	public Collection<Crawler> getCrawlers() {
		return crawlers.values();
	}

	public Crawler getCrawler(String name) {
		return crawlers.get(name);
	}

	public Injector getInjector(String name) {
		return injectors.get(name);
	}

}
