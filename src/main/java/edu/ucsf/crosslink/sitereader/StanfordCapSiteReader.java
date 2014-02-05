package edu.ucsf.crosslink.sitereader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.json.JSONException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.github.jsonldjava.core.JSONLDProcessingError;

import edu.ucsf.crosslink.author.Author;
import edu.ucsf.crosslink.author.AuthorParser;

public class StanfordCapSiteReader extends SiteReader implements AuthorParser {

	private static final Logger LOG = Logger.getLogger(StanfordCapSiteReader.class.getName());

	public StanfordCapSiteReader(String affiliation, String siteRoot) {
		super(affiliation, siteRoot);
	}
	
    public List<Author> getAuthors() throws IOException, InterruptedException {
    	List<Author> authors = new ArrayList<Author>();
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
		    			authors.add( new Author(href));
			    	}
		        }
			    LOG.info("Found " + authors.size() + " profile pages so far");
			}
    	} while(!onLastPage);
	    LOG.info("Found " + authors.size() + " total profile pages");
		return authors;
    }

    public Author getAuthorFromHTML(String url) throws IOException, JSONLDProcessingError, JSONException, InterruptedException {
    	if (url.endsWith("/browse")) {
    		return null;
    	}
    	Document doc = getDocument(url);
    	// read name from title    	
    	Author author = null;
		if (doc != null && doc.title().endsWith(" | Stanford Profiles")) {
			String fullName = doc.title().split("\\|")[0].split(",")[0];
			String[] name = fullName.split(" ");	
			author = new Author(getAffiliation(), name[name.length - 1], name[0], name.length > 2 ? name[1] : null, url, null, null);
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").startsWith(getSiteRoot() + "/") && link.attr("abs:href").contains("pubid=")) {
		    		String pmid = getPMIDFromHTML(link.attr("abs:href"));
			    	if (pmid != null) {
			    		author.addPubMedPublication(pmid);
			    	}
		    	}
	        }
		}
    	return author;
    }

    private String getPMIDFromHTML(String url) throws IOException, JSONLDProcessingError, JSONException, InterruptedException {
    	Document doc = getDocument(url);
		if (doc != null) {
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if ( link.attr("abs:href").startsWith("http://www.ncbi.nlm.nih.gov/pubmed/")) {
		    		return link.attr("abs:href").substring("http://www.ncbi.nlm.nih.gov/pubmed/".length());
		    	}
	        }
		}
    	return null;
    }
}
