package edu.ucsf.crosslink;

import java.io.File;
import java.io.FileReader;
import java.util.Properties;

public class Crosslinks {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try  {
			Properties prop = new Properties();
			File file = new File(args[0]);
			prop.load( new FileReader(file));
			AuthorshipParser parser = new HTMLAuthorshipParser(); // RDF Parser is too slow
			String siteRoot = prop.getProperty("BaseURL");
			String affiliation = prop.getProperty("Affiliation");

			// should use a real dependency injection framework someday for this			
			AuthorshipPersistance store = (AuthorshipPersistance)Class.forName(prop.getProperty("AuthorshipPersistance")).getConstructor(String.class).newInstance(file.getName().split("\\.")[0] + ".csv");
			SiteReader reader = (SiteReader)Class.forName(prop.getProperty("Reader")).getConstructor().newInstance();			
			reader.readSite(affiliation, siteRoot, store, parser);
			store.close();
		}
		catch (Exception e) {
			e.printStackTrace();
			showUse();
		}		
	}
	
	private static void showUse() {
		System.out.println("Pass in the name of the properties file");
	}
	

}
