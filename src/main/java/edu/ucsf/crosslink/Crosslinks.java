package edu.ucsf.crosslink;

import java.io.File;
import java.io.FileReader;
import java.util.Properties;

import edu.ucsf.crosslink.author.AuthorParser;
import edu.ucsf.crosslink.author.HTMLAuthorshipParser;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.sitereader.SiteReader;

public class Crosslinks {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try  {
			Properties prop = new Properties();
			File file = new File(args[0]);
			prop.load( new FileReader(file));
			String siteRoot = prop.getProperty("BaseURL");
			String affiliation = prop.getProperty("Affiliation");

			// should use a real dependency injection framework someday for this	
			AuthorParser parser = prop.getProperty("AuthorParser") == null ? new HTMLAuthorshipParser() : (AuthorParser)Class.forName(prop.getProperty("AuthorParser")).getConstructor().newInstance();
			CrosslinkPersistance store = (CrosslinkPersistance)Class.forName(prop.getProperty("AuthorshipPersistance")).getConstructor().newInstance();
			store.start(affiliation);
			SiteReader reader = (SiteReader)Class.forName(prop.getProperty("Reader")).getConstructor(String.class, String.class).newInstance(affiliation, siteRoot);			
			reader.readSite(store, parser);
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
