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
			prop.load( new FileReader(new File(args[0] + ".properties")));

			AuthorshipParser parser = new HTMLAuthorshipParser(); // RDF Parser is too slow
			String siteRoot = prop.getProperty("BaseURL");
			String affiliation = prop.getProperty("Affiliation"); 
			AuthorshipPersistance store = null;
			
			// should use a real dependency injection framework someday for this
			if ("CVSAuthorshipStore".equals(prop.getProperty("AuthorshipPersistance"))) {
			    store = new CSVAuthorshipStore(args[0] + ".csv");					
			}
			else {
				showUse();
				return;
			}
			
			SiteReader reader = (SiteReader)Class.forName(prop.getProperty("Reader")).getConstructor().newInstance();			
			reader.readSite(affiliation, siteRoot, store, parser);
			store.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	private static void showUse() {
		System.out.println("Pass in the name of the properties file");
	}
	

}
