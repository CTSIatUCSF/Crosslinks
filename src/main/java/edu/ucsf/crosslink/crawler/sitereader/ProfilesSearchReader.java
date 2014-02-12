package edu.ucsf.crosslink.crawler.sitereader;

import java.io.IOException;
import java.util.logging.Logger;


import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.model.Researcher;


public class ProfilesSearchReader extends SiteReader {

	private static final Logger LOG = Logger.getLogger(ProfilesSearchReader.class.getName());

	@Inject
	public ProfilesSearchReader(@Named("Affiliation") String affiliation, @Named("BaseURL") String siteRoot) {
		super(affiliation, siteRoot);
	}

	protected void collectAuthorURLS() throws IOException, InterruptedException {
		String suffix = "/search/default.aspx?searchtype=people&searchfor=&perpage=100&offset=0&sortby=&sortdirection=&showcolumns=1&page=";
		int page = 1;
		String firstUrlInPriorSet = null;
		boolean findingSamePeople = false;
		String searchRequest = null; // for most instances of profiles, this is needed in the URL to maintain state for pagination
		do {
			Document doc = getDocument(getSiteRoot() + suffix + page++ + (searchRequest != null ? "&searchrequest=" + searchRequest : ""));
			if (doc != null) {
				Elements links = doc.select("input[type=hidden]");	
				boolean firstlink = true;
				
			    for (Element link : links) {
			    	if (searchRequest == null && link.attr("id").equals("txtSearchRequest")) {
			    		searchRequest = link.val();
			    	}
			    	else if ( link.attr("id").startsWith(getSiteRoot() + "/profile/")) {
			    		String url = link.attr("id");
			    		if (firstlink ) {
			    			firstlink = false;
			    			if (!url.equals(firstUrlInPriorSet)) {
			    				firstUrlInPriorSet = url;
			    			}
			    			else {
			    				findingSamePeople = true;
			    				break;
			    			}
			    		}
			    		addAuthor(new Researcher(url));
			    	}
		        }
			}
			LOG.info("Found " + getAuthors().size() + " profile pages so far onto page " + page);		
		}
		while (!findingSamePeople);
		LOG.info("Found " + getAuthors().size() + " profile pages");
    }

}
