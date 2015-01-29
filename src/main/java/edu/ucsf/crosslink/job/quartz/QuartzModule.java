package edu.ucsf.crosslink.job.quartz;

import java.util.Properties;

import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import edu.ucsf.crosslink.processor.controller.ProcessorControllerFactory;
import edu.ucsf.crosslink.web.Stoppable;

public class QuartzModule extends AbstractModule {
	
	private Properties prop;
	
	public QuartzModule(Properties prop) {
		this.prop = prop;
	}

	@Override
	protected void configure() {
		bind(ProcessorControllerFactory.class).asEagerSingleton();
		
		bind(Integer.class).annotatedWith(Names.named("scanInterval")).toInstance(Integer.parseInt(prop.getProperty("scanInterval")));
		bind(Integer.class).annotatedWith(Names.named("controllerThreads")).toInstance(Integer.parseInt(prop.getProperty("controllerThreads")));

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
