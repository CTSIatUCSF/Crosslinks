package edu.ucsf.crosslink.web;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletContextEvent;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;

import edu.ucsf.crosslink.Crosslinks;
import edu.ucsf.crosslink.crawler.AffiliationCrawler;
import edu.ucsf.crosslink.io.IOModule;
import edu.ucsf.crosslink.quartz.Quartz;
import edu.ucsf.crosslink.quartz.QuartzModule;

public class CrosslinksServletConfig extends GuiceServletContextListener {

	private Quartz quartz;
	private Injector injector;

	@Override
	protected Injector getInjector() {
		try {
			Properties prop = new Properties();
			prop.load(AffiliationCrawler.class.getResourceAsStream(Crosslinks.PROPERTIES_FILE));			
			injector = Guice.createInjector(new IOModule(prop), new QuartzModule(prop), new CrosslinksServletModule(prop));
		} catch (IOException e) {
			e.printStackTrace();
		}
		quartz = injector.getInstance(Quartz.class);
		return injector;
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (quartz != null) {
			quartz.shutdown();
		}
	}
}
