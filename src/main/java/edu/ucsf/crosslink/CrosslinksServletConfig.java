package edu.ucsf.crosslink;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletContextEvent;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;

import edu.ucsf.crosslink.io.DBModule;
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
			injector = Guice.createInjector(new DBModule(prop), new QuartzModule(prop), 
					new ServletModule() {
						@Override
						protected void configureServlets() {
							serve("/status").with(CrosslinksServlet.class);
						}
					});
		} catch (IOException e) {
			e.printStackTrace();
		}
		quartz = injector.getInstance(Quartz.class);
		return injector;
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		quartz.shutdown();
	}
}
