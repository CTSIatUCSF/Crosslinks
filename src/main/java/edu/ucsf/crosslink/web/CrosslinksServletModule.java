package edu.ucsf.crosslink.web;

import java.util.Properties;

import com.google.inject.name.Names;
import com.sun.jersey.guice.JerseyServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;



public class CrosslinksServletModule extends JerseyServletModule {

	Properties prop;
	
	public CrosslinksServletModule(Properties prop) {
		this.prop = prop;
	}
	
	@Override
	protected void configureServlets() {
		bind(String.class).annotatedWith(Names.named("thumbnailRootURL")).toInstance(prop.getProperty("thumbnailRootURL"));		
		bind(RestMethods.class);
		serve("/*").with(GuiceContainer.class);
	}

}
