package edu.ucsf.crosslink;

import java.util.Properties;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class PropertiesModule extends AbstractModule {

	private Properties prop = new Properties();
	
	public PropertiesModule(Properties prop) {
		this.prop = prop;
	}

	@Override
	protected void configure() {
		Names.bindProperties(binder(), prop);
	}
}
