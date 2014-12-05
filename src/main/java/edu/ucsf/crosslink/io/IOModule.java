package edu.ucsf.crosslink.io;

import java.util.Properties;

import com.google.inject.AbstractModule;

import edu.ucsf.ctsi.r2r.jena.SparqlPostClient;

public class IOModule extends AbstractModule {

	
	private Properties prop;
	
	public IOModule(Properties prop) {
		this.prop = prop;
	}
	
	@Override
	protected void configure() {	
        bind(ThumbnailGenerator.class).asEagerSingleton();
        bind(SparqlPostClient.class).toInstance(new SparqlPostClient(prop.getProperty("r2r.fusekiUrl") + "/update", prop.getProperty("r2r.fusekiUrl") + "/data?default"));
		bind(SparqlPersistance.class).asEagerSingleton();
	}

}
