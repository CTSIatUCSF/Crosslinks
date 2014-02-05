package edu.ucsf.crosslink;

import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.ucsf.crosslink.author.Author;
import edu.ucsf.crosslink.author.AuthorParser;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.sitereader.SiteReader;

public class Crosslinks {

	private static final Logger LOG = Logger.getLogger(Crosslinks.class.getName());

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
			CrosslinkPersistance store = (CrosslinkPersistance)Class.forName(prop.getProperty("AuthorshipPersistance")).getConstructor().newInstance();
			store.start(affiliation);
			SiteReader reader = (SiteReader)Class.forName(prop.getProperty("Reader")).getConstructor(String.class, String.class).newInstance(affiliation, siteRoot);			
			AuthorParser parser = null;			
			if (prop.getProperty("AuthorParser") != null) {
				parser = (AuthorParser)Class.forName(prop.getProperty("AuthorParser")).getConstructor(SiteReader.class).newInstance(reader);
			}
			else if (reader instanceof AuthorParser) {
				parser = (AuthorParser)reader;
			}
			
			List<Author> authors = reader.getAuthors();
			LOG.info("Found " + authors.size() + " potential Profile pages for " + affiliation);
			for (Author author : authors) {
				if (store.skipAuthor(author.getURL())) {
					LOG.info("Skipping recently processed author :" + author);						
				}
				else {
					try {
						author.merge(parser.getAuthorFromHTML(author.getURL()));
						LOG.info("Saving author :" + author);						
						store.saveAuthor(author);
					}
					catch (Exception e) {
						LOG.log(Level.WARNING, "Error reading page for " + author.getURL(), e);
					}
				}
			}
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
