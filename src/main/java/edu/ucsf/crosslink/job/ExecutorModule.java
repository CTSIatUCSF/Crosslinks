package edu.ucsf.crosslink.job;

import java.util.Properties;




import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.name.Names;

import edu.ucsf.crosslink.processor.controller.ProcessorControllerFactory;
import edu.ucsf.crosslink.web.Stoppable;

public class ExecutorModule extends AbstractModule {

	private Properties prop;
	
	public ExecutorModule(Properties prop) {
		this.prop = prop;
	}


	@Override
	protected void configure() {
		bind(String.class).annotatedWith(Names.named("configurationDirectory")).toInstance(prop.getProperty("configurationDirectory"));
		bind(ProcessorControllerFactory.class).asEagerSingleton();
		
		bind(Integer.class).annotatedWith(Names.named("scanInterval")).toInstance(Integer.parseInt(prop.getProperty("scanInterval")));
		bind(Integer.class).annotatedWith(Names.named("threadCount")).toInstance(Integer.parseInt(prop.getProperty("threadCount")));
				
		bind(Stoppable.class).to(ProcessorControllerExecutor.class).in(Scopes.SINGLETON);

	}

}
