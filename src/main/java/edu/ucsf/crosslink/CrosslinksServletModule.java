package edu.ucsf.crosslink;

import com.google.inject.Scopes;
import com.sun.jersey.guice.JerseyServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

import edu.ucsf.crosslink.webapi.RestMethods;


public class CrosslinksServletModule extends JerseyServletModule {

	
	@Override
	protected void configureServlets() {
		bind(RestMethods.class);
		bind(CrosslinksServletFilter.class).in(Scopes.SINGLETON);
		serve("/*").with(GuiceContainer.class);
		filter("/*").through(CrosslinksServletFilter.class);
	}

}
