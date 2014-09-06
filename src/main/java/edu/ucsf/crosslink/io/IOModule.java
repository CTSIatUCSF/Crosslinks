package edu.ucsf.crosslink.io;

import java.util.Properties;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import edu.ucsf.ctsi.r2r.jena.LODService;

public class IOModule extends AbstractModule {

	
	private Properties prop;
	
	public IOModule(Properties prop) {
		this.prop = prop;
	}
	
	@Override
	protected void configure() {	
		bind(String.class).annotatedWith(Names.named("thumbnailDir")).toInstance(prop.getProperty("thumbnailDir"));
		bind(String.class).annotatedWith(Names.named("thumbnailRootURL")).toInstance(prop.getProperty("thumbnailRootURL"));
		bind(Integer.class).annotatedWith(Names.named("thumbnailWidth")).toInstance(Integer.parseInt(prop.getProperty("thumbnailWidth")));
		bind(Integer.class).annotatedWith(Names.named("thumbnailHeight")).toInstance(Integer.parseInt(prop.getProperty("thumbnailHeight")));
        bind(ThumbnailGenerator.class).asEagerSingleton();
		
		bind(Integer.class).annotatedWith(Names.named("daysConsideredOld")).toInstance(Integer.parseInt(prop.getProperty("daysConsideredOld")));
	}

}
