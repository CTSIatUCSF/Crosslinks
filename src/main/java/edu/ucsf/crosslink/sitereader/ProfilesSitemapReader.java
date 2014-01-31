package edu.ucsf.crosslink.sitereader;

import java.net.URL;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.ucsf.crosslink.author.AuthorParser;
import edu.ucsf.crosslink.io.CrosslinkPersistance;

import net.sourceforge.sitemaps.Sitemap;
import net.sourceforge.sitemaps.SitemapParser;
import net.sourceforge.sitemaps.SitemapUrl;

public class ProfilesSitemapReader extends SiteReader {

	private static final Logger LOG = Logger.getLogger(ProfilesSitemapReader.class.getName());
	
	public ProfilesSitemapReader(String affiliation, String siteRoot) {
		super(affiliation, siteRoot);
	}
    
    public void readSite(CrosslinkPersistance store, AuthorParser parser) throws Exception {
		SitemapParser smp = new SitemapParser();
		smp.processSitemap(new URL(getSiteRoot() + "/sitemap.xml"));
		Sitemap sitemap = smp.getSitemap();
		
		Collection<SitemapUrl> urls = sitemap.getUrlList();

		for (SitemapUrl url : urls) {
			LOG.info(url.toString());
			if (store.skipAuthor(url.getUrl().toString())) {
				continue;
			}
			try {
				store.saveAuthor(parser.getAuthorFromHTML(this, url.getUrl().toString()));
			}
			catch (Exception e) {
				LOG.log(Level.WARNING, "Error parsing " + url.getUrl(), e);
			}
		}
    }
}
