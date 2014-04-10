package edu.ucsf.crosslink.job.quartz;

import java.util.Properties;

import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import edu.ucsf.crosslink.crawler.AffiliationCrawlerFactory;
import edu.ucsf.crosslink.web.Stoppable;

public class QuartzModule extends AbstractModule {
	
	private Properties prop;
	
	public QuartzModule(Properties prop) {
		this.prop = prop;
	}

	@Override
	protected void configure() {
		bind(String.class).annotatedWith(Names.named("configurationDirectory")).toInstance(prop.getProperty("configurationDirectory"));
		bind(AffiliationCrawlerFactory.class).asEagerSingleton();
		
		bind(Integer.class).annotatedWith(Names.named("scanInterval")).toInstance(Integer.parseInt(prop.getProperty("scanInterval")));
				
		bind(SchedulerFactory.class).to(StdSchedulerFactory.class).asEagerSingleton();
        bind(GuiceJobFactory.class).asEagerSingleton();
        bind(Stoppable.class).to(Quartz.class).asEagerSingleton();
	}

}
