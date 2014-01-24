package edu.ucsf.crosslink;

import java.net.URL;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sourceforge.sitemaps.Sitemap;
import net.sourceforge.sitemaps.SitemapParser;
import net.sourceforge.sitemaps.SitemapUrl;

public class ProfilesSitemapReader {

	private static final Logger LOG = Logger.getLogger(ProfilesSitemapReader.class.getName());

	private String affiliation;
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {		// TODO Auto-generated method stub		
		try  {
			
			AuthorshipParser parser = null;
			ProfilesSitemapReader psr = null;
			AuthorshipPersistance store = null;
			String siteMapURL = null;
			
			if ("RDF".equalsIgnoreCase(args[0])) {
				parser = new RDFAuthorshipParser();
			}
			else if ("HTML".equalsIgnoreCase(args[0])) {
				parser = new HTMLAuthorshipParser();
			}
			
			if (args.length == 3 ) {
			    psr = new ProfilesSitemapReader(args[1]);
			    store = new CSVAuthorshipStore(args[1] + ".csv");	
			    siteMapURL = args[2];			    		
			}
			else {
				showUse();
			}
			
			if (parser != null && psr != null && store != null && siteMapURL != null) {
				psr.parseSiteMap(siteMapURL, store, parser);
				store.close();
			}
			else {
				showUse();
			}
			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	private static void showUse() {
		System.out.println("HTML | RDF, affiliation, url");
	}
	
    public ProfilesSitemapReader(String affiliation) {
    	this.affiliation = affiliation;
    }
    
    public void parseSiteMap(String sitemapUrl, AuthorshipPersistance store, AuthorshipParser parser) throws Exception {
		SitemapParser smp = new SitemapParser();
		smp.processSitemap(new URL(sitemapUrl));
		Sitemap sitemap = smp.getSitemap();
		
		Collection<SitemapUrl> urls = sitemap.getUrlList();

		for (SitemapUrl url : urls) {
			LOG.info(url.toString());
			if (store.containsAuthor(url.getUrl().toString())) {
				continue;
			}
			try {
				Collection<Authorship> authorships = parser.getAuthorshipsFromHTML(url.getUrl().toString());
				for (Authorship authorship : authorships) {
					LOG.info("Authorship -- " + authorship.toString());
					authorship.setAffiliation(affiliation);
					store.saveAuthorship(authorship);
				}
				store.flush();
			}
			catch (Exception e) {
				LOG.log(Level.WARNING, "Error parsing " + url.getUrl(), e);
			}
		}
    }
    
}
