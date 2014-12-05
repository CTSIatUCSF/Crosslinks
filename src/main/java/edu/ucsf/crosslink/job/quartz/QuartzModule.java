package edu.ucsf.crosslink.job.quartz;

import java.util.Properties;

import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import edu.ucsf.crosslink.crawler.CrawlerFactory;
import edu.ucsf.crosslink.web.Stoppable;

public class QuartzModule extends AbstractModule {
	
	private Properties prop;
	
	public QuartzModule(Properties prop) {
		this.prop = prop;
	}

	@Override
	protected void configure() {
		bind(CrawlerFactory.class).asEagerSingleton();
		
		bind(Integer.class).annotatedWith(Names.named("scanInterval")).toInstance(Integer.parseInt(prop.getProperty("scanInterval")));
				
		// used by Marengo to set executorService thread size
		bind(Integer.class).annotatedWith(Names.named("threadCount")).toInstance(Integer.parseInt(prop.getProperty("threadCount")));

		try {
			bind(SchedulerFactory.class).toInstance(new StdSchedulerFactory(prop));
		} 
		catch (SchedulerException e) {
			addError(e);
		}
        bind(GuiceJobFactory.class).asEagerSingleton();
        bind(Stoppable.class).to(Quartz.class).asEagerSingleton();
	}

}
