package edu.ucsf.crosslink.crawler.sitereader;

import java.io.IOException;
import java.util.logging.Logger;


import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.github.jsonldjava.core.JSONLDProcessingError;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.model.Researcher;

public class StanfordCapSiteReader extends SiteReader implements AuthorParser {

	private static final Logger LOG = Logger.getLogger(StanfordCapSiteReader.class.getName());

	@Inject
	public StanfordCapSiteReader(@Named("Affiliation") String affiliation, @Named("BaseURL") String siteRoot) {
		super(affiliation, siteRoot);
	}
	
	protected void collectAuthorURLS() throws IOException, InterruptedException {
    	int page = 1;
    	boolean onLastPage = true;// until we know otherwise
    	do {    		
	    	Document doc = getDocument(getSiteRoot() + "/browse?p=" + page++ );
	    	onLastPage = true;
			if (doc != null) {
				Elements links = doc.select("a[href]");					
			    for (Element link : links) {
			    	String href = link.attr("abs:href");
			    	if ( href.startsWith(getSiteRoot() + "/browse?p=" + page)) {
			    		onLastPage = false;
			    	}
			    	else if ( href.startsWith(getSiteRoot() + "/") && href.length() > getSiteRoot().length() + 1 && !href.contains("?")) {
		    			addAuthor( new Researcher(href));
			    	}
		        }
			    LOG.info("Found " + getAuthors().size() + " profile pages so far, onto page " + page);
			}
    	} while(!onLastPage);
	    LOG.info("Found " + getAuthors().size() + " total profile pages");
    }

    public Researcher getAuthorFromHTML(String url) throws IOException, JSONLDProcessingError, JSONException, InterruptedException {
    	if (url.endsWith("/browse")) {
    		return null;
    	}
    	Document doc = getDocument(url);
    	// read name from title    	
    	Researcher author = null;
		if (doc != null && doc.title().endsWith(" | Stanford Profiles")) {
			String fullName = StringEscapeUtils.escapeHtml4(doc.title().split("\\|")[0].split(",")[0]);
			String[] name = fullName.split(" ");	
			author = new Researcher(getAffiliation(), name[name.length - 1], name[0], name.length > 2 ? name[1] : null, url, null, null);
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if (  link.attr("abs:href").contains(PUBMED_SECTION) ) {
		    		author.addPubMedPublication(link.attr("abs:href"));
		    	}
	        }

		    for (Element src : doc.select("[src]")) {
		    	   if (src.tagName().equals("img") && src.attr("abs:src").contains("viewImage")) {
		    		   author.addImageURL(src.attr("abs:src"));
		    	   }
			    }
		}
    	return author;
    }

    
    public static void main(String[] args) {
    	try {
    		StanfordCapSiteReader reader = new StanfordCapSiteReader("TEST", "https://med.stanford.edu/profiles");
    		reader.getAuthorFromHTML(args[0]);
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    }
}
