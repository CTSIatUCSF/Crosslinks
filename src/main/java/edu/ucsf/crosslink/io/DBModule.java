package edu.ucsf.crosslink.io;

import java.util.Properties;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import edu.ucsf.crosslink.io.DBUtil;

public class DBModule extends AbstractModule {

	
	private Properties prop;
	
	public DBModule(Properties prop) {
		this.prop = prop;
	}
	
	@Override
	protected void configure() {
		bind(String.class).annotatedWith(Names.named("dbUrl")).toInstance(prop.getProperty("dbUrl"));
		bind(String.class).annotatedWith(Names.named("dbUser")).toInstance(prop.getProperty("dbUser"));
		bind(String.class).annotatedWith(Names.named("dbPassword")).toInstance(prop.getProperty("dbPassword"));
		bind(DBUtil.class);
	}

}
