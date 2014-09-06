package edu.ucsf.crosslink.web;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletContextEvent;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;
import com.hp.cache4guice.adapters.ehcache.EhCacheModule;

import edu.ucsf.crosslink.Crosslinks;
import edu.ucsf.crosslink.crawler.AffiliationCrawler;
import edu.ucsf.crosslink.io.IOModule;
import edu.ucsf.crosslink.job.ExecutorModule;
import edu.ucsf.crosslink.job.quartz.QuartzModule;

public class CrosslinksServletConfig extends GuiceServletContextListener {

	private Stoppable schedulingService;
	private Injector injector;

	@Override
	protected Injector getInjector() {
		try {
			Properties prop = new Properties();
			prop.load(AffiliationCrawler.class.getResourceAsStream(Crosslinks.PROPERTIES_FILE));
			String execution = prop.getProperty("execution");
			if ("quartz".equalsIgnoreCase(execution)) {
				injector = Guice.createInjector(new EhCacheModule(), new IOModule(prop), new QuartzModule(prop), new CrosslinksServletModule(prop));
			}
			else {
				injector = Guice.createInjector(new EhCacheModule(), new IOModule(prop), new ExecutorModule(prop), new CrosslinksServletModule(prop));
			}
			if (execution != null) {
				schedulingService = injector.getInstance(Stoppable.class);
			}
		} catch (IOException e) {
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
