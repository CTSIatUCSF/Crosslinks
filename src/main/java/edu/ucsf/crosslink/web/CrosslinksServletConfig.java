package edu.ucsf.crosslink.web;

import java.util.Properties;

import javax.servlet.ServletContextEvent;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;
import com.hp.cache4guice.adapters.ehcache.EhCacheModule;

import edu.ucsf.crosslink.CrosslinksXMLConfiguration;
import edu.ucsf.crosslink.PropertiesModule;
import edu.ucsf.crosslink.io.IOModule;
import edu.ucsf.crosslink.job.quartz.QuartzModule;

public class CrosslinksServletConfig extends GuiceServletContextListener {

	private Stoppable schedulingService;
	private Injector injector;

	@Override
	protected Injector getInjector() {
		try {
			CrosslinksXMLConfiguration config = new CrosslinksXMLConfiguration();
			Properties prop = config.getAsProperties("//Crosslinks/*");
			injector = Guice.createInjector(new PropertiesModule(prop), new EhCacheModule(), new IOModule(prop), new QuartzModule(prop), new CrosslinksServletModule(prop));
			schedulingService = injector.getInstance(Stoppable.class);
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		return injector;
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (schedulingService != null) {
			schedulingService.shutdown();
		}
	}
}
