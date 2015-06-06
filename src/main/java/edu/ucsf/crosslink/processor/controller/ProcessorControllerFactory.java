package edu.ucsf.crosslink.processor.controller;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.processor.ProcessorModule;

@Singleton
public class ProcessorControllerFactory {

	private static final Logger LOG = Logger.getLogger(ProcessorControllerFactory.class.getName());

	private Injector guice;
	private Map<String, ProcessorController> processorControllers = new HashMap<String, ProcessorController>();
	private Map<String, Injector> injectors = new HashMap<String, Injector>(); 
	
	@Inject
	public ProcessorControllerFactory(final Injector guice) throws XPathExpressionException, TransformerConfigurationException, SAXException, IOException, ParserConfigurationException {
		this.guice = guice;		
	}

	public void loadNewCrawlers() throws Exception {
		// first load the ones that do not need an affiliation
		CrosslinksXMLConfiguration config = new CrosslinksXMLConfiguration();

		// first load the ones that do need an affiliation
		for (Node n : config.getDefaultedNodes("//Crosslinks/Affiliations")) {
			Properties affiliationProps = config.getChildrenAsProperties(n);
			NodeList processors = config.evaluate(n, "*/Processor");
			for (int i = 0; i < processors.getLength(); i++) {		
				Node processor = processors.item(i);
				Properties processorProps = config.getChildrenAsProperties(processor);
				// sort of ugly
				processorProps.putAll(affiliationProps);
				Injector injector = guice.createChildInjector(new PropertiesModule(affiliationProps), 
						new ProcessorModule(processorProps));
				ProcessorController processorController = injector.getInstance(ProcessorController.class);
								
				// don't add ones that are already present
				if (!processorControllers.containsKey(processorController.getName())) {
					injectors.put(processorController.getName(), injector);		
					processorControllers.put(processorController.getName(), processorController);
					LOG.info("Loaded controller " + processorController.getName());
					// update it
					processorController.update();
				}
			}			
		}
		LOG.info("Loaded all affiliation level process controllers");
		
		// now load the others
		for (Node n : config.getDefaultedNodes("//Crosslinks/Processors")) {
			Properties prop = config.getChildrenAsProperties(n);
			Injector injector = guice.createChildInjector(new PropertiesModule(prop), new ProcessorModule(prop));
			ProcessorController processorController = injector.getInstance(ProcessorController.class);
			
			// don't add ones that are already present
			if (!processorControllers.containsKey(processorController.getName())) {
				injectors.put(processorController.getName(), injector);		
				processorControllers.put(processorController.getName(), processorController);
				LOG.info("Loaded controller " + processorController.getName());
				// update it
				processorController.update();
			}
		}
		LOG.info("Loaded all process controllers");
	}
	
	public Collection<ProcessorController> getCrawlers() {
		return processorControllers.values();
	}

	public ProcessorController getCrawler(String name) {
		return processorControllers.get(name);
	}

	public Injector getInjector(String name) {
		return injectors.get(name);
	}

}
