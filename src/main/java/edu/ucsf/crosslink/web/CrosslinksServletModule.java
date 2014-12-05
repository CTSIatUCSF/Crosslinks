package edu.ucsf.crosslink.web;

import java.util.Properties;

import com.google.inject.name.Names;
import com.sun.jersey.guice.JerseyServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

import edu.ucsf.ctsi.r2r.jena.SparqlPostClient;

public class CrosslinksServletModule extends JerseyServletModule {

	Properties prop;
	
	public CrosslinksServletModule(Properties prop) {
		this.prop = prop;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	protected void configureServlets() {
		bind(String.class).annotatedWith(Names.named("thumbnailRootURL")).toInstance(prop.getProperty("thumbnailRootURL"));		
		bind(String[].class).annotatedWith(Names.named("administrators")).toInstance(prop.getProperty("administrators").split(","));
		bind(String.class).annotatedWith(Names.named("r2r.fusekiUrl")).toInstance(prop.getProperty("r2r.fusekiUrl"));
		bind(String.class).annotatedWith(Names.named("uiFusekiUrl")).toInstance(prop.getProperty("uiFusekiUrl"));
		bind(FusekiRestMethods.class).asEagerSingleton();
		serve("/*").with(GuiceContainer.class);
		filter("/*").through(CrosslinksServletFilter.class);
	}

}
