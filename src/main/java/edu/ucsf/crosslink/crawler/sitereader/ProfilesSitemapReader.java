package edu.ucsf.crosslink.crawler.sitereader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.logging.Logger;

import com.google.inject.Inject;

import edu.ucsf.crosslink.crawler.AffiliationCrawler;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import net.sourceforge.sitemaps.Sitemap;
import net.sourceforge.sitemaps.SitemapParser;
import net.sourceforge.sitemaps.SitemapUrl;
import net.sourceforge.sitemaps.UnknownFormatException;
import net.sourceforge.sitemaps.http.ProtocolException;

public class ProfilesSitemapReader extends AffiliationCrawler  {

	private static final Logger LOG = Logger.getLogger(ProfilesSitemapReader.class.getName());
	
	@Inject
	public ProfilesSitemapReader(Affiliation affiliation, Mode crawlingMode, CrosslinkPersistance store) throws Exception {
		super(affiliation, crawlingMode, store);
	}

	protected void collectResearcherURLs() throws UnknownHostException, MalformedURLException, UnknownFormatException, IOException, ProtocolException, InterruptedException, URISyntaxException {
		SitemapParser smp = new SitemapParser();
		smp.processSitemap(new URL(getSiteRoot() + "/sitemap.xml"));
		Sitemap sitemap = smp.getSitemap();
		
		Collection<SitemapUrl> urls = sitemap.getUrlList();

		for (SitemapUrl url : urls) {
			addResearcher(new Researcher(getAffiliation(), url.getUrl().toString()));
		}
		LOG.info("Found " + getResearchers().size() + " profile pages");
    }
    
}
