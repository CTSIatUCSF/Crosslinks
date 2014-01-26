package edu.ucsf.crosslink;

import java.net.URL;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sourceforge.sitemaps.Sitemap;
import net.sourceforge.sitemaps.SitemapParser;
import net.sourceforge.sitemaps.SitemapUrl;

public class ProfilesSitemapReader extends SiteReader {

	private static final Logger LOG = Logger.getLogger(ProfilesSitemapReader.class.getName());
	
	public ProfilesSitemapReader(String affiliation, String siteRoot) {
		super(affiliation, siteRoot);
	}
    
    public void readSite(AuthorshipPersistance store, AuthorshipParser parser) throws Exception {
		SitemapParser smp = new SitemapParser();
		smp.processSitemap(new URL(getSiteRoot() + "/sitemap.xml"));
		Sitemap sitemap = smp.getSitemap();
		
		Collection<SitemapUrl> urls = sitemap.getUrlList();

		for (SitemapUrl url : urls) {
			LOG.info(url.toString());
			if (store.containsAuthor(url.getUrl().toString())) {
				continue;
			}
			try {
				Collection<Authorship> authorships = parser.getAuthorshipsFromHTML(this, url.getUrl().toString());
				for (Authorship authorship : authorships) {
					LOG.info("Authorship -- " + authorship.toString());
					authorship.setAffiliation(getAffiliation());
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
