package edu.ucsf.crosslink.web;

import java.util.Properties;

import com.google.inject.name.Names;
import com.sun.jersey.guice.JerseyServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

import edu.ucsf.ctsi.r2r.jena.FusekiClient;
import edu.ucsf.ctsi.r2r.jena.FusekiHttpClient;



public class CrosslinksServletModule extends JerseyServletModule {

	Properties prop;
	
	public CrosslinksServletModule(Properties prop) {
		this.prop = prop;
	}
	
	@Override
	protected void configureServlets() {
		bind(String.class).annotatedWith(Names.named("thumbnailRootURL")).toInstance(prop.getProperty("thumbnailRootURL"));		
		bind(String[].class).annotatedWith(Names.named("administrators")).toInstance(prop.getProperty("administrators").split(","));
		bind(RestMethods.class).asEagerSingleton();
		bind(FusekiClient.class).toInstance(new FusekiHttpClient(prop.getProperty("r2r.fusekiUrl")));
		bind(FusekiRestMethods.class).asEagerSingleton();;
		serve("/*").with(GuiceContainer.class);
		filter("/*").through(CrosslinksServletFilter.class);
	}

}
