package edu.ucsf.crosslink.quartz;

import java.util.Properties;

import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.name.Names;

public class QuartzModule extends AbstractModule {
	
	private Properties prop;
	
	public QuartzModule(Properties prop) {
		this.prop = prop;
	}

	@Override
	protected void configure() {
		bind(String.class).annotatedWith(Names.named("configurationDirectory")).toInstance(prop.getProperty("configurationDirectory"));
		
		bind(Integer.class).annotatedWith(Names.named("pauseOnAbort")).toInstance(Integer.parseInt(prop.getProperty("pauseOnAbort")));
		bind(Integer.class).annotatedWith(Names.named("staleDays")).toInstance(Integer.parseInt(prop.getProperty("staleDays")));
		bind(Integer.class).annotatedWith(Names.named("runInterval")).toInstance(Integer.parseInt(prop.getProperty("runInterval")));

		bind(SchedulerFactory.class).to(StdSchedulerFactory.class).in(Scopes.SINGLETON);
        bind(GuiceJobFactory.class).in(Scopes.SINGLETON);
        bind(Quartz.class).in(Scopes.SINGLETON);
	}

}
