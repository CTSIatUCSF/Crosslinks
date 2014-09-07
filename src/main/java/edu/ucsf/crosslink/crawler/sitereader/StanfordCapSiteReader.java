package edu.ucsf.crosslink.crawler.sitereader;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringEscapeUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.inject.Inject;

import edu.ucsf.crosslink.crawler.AffiliationCrawler;
import edu.ucsf.crosslink.crawler.parser.AuthorParser;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;

public class StanfordCapSiteReader extends AffiliationCrawler implements AuthorParser {

	private static final Logger LOG = Logger.getLogger(StanfordCapSiteReader.class.getName());

	@Inject
	public StanfordCapSiteReader(Affiliation affiliation, Mode crawlingMode) {
		super(affiliation, crawlingMode);
	}
	
	protected void collectResearcherURLs() throws IOException, InterruptedException {
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
		    			addResearcher( new Researcher(getAffiliation(), href));
			    	}
		        }
			    LOG.info("Found " + getResearchers().size() + " profile pages so far, onto page " + page);
			}
    	} while(!onLastPage);
	    LOG.info("Found " + getResearchers().size() + " total profile pages");
    }

    public boolean readResearcher(Researcher researcher) throws IOException, InterruptedException {
    	if (researcher.getURI().endsWith("/browse")) {
    		return false;
    	}
    	Document doc = getDocument(researcher.getURI());
    	// read name from title    	
		if (doc != null && (doc.title().contains(" | Stanford"))) {
			String fullName = StringEscapeUtils.escapeHtml4(doc.title().split("\\|")[0].split(",")[0]).trim();
			if (fullName.isEmpty()) {
				LOG.log(Level.WARNING, "no name found for " + researcher + " going to derive one from page");
				fullName = researcher.getURI().substring(getAffiliation().getBaseURL().length() + 1);
			}
			researcher.setLabel(fullName);
			Elements links = doc.select("a[href]");	
			
		    for (Element link : links) {
		    	if (  link.attr("abs:href").contains(PUBMED_SECTION) ) {
		    		researcher.addPubMedPublication(link.attr("abs:href"));
		    	}
	        }

		    for (Element src : doc.select("[src]")) {
	    	   if (src.tagName().equals("img") && src.attr("abs:src").contains("viewImage")) {
	    		   researcher.addImageURL(src.attr("abs:src"));
	    	   }
		    }
		}
    	return doc != null;
    }

    
    public static void main(String[] args) {
    	try {
    		Affiliation stanford = new Affiliation("Stanford", "https://med.stanford.edu/profiles", null);
    		StanfordCapSiteReader reader = new StanfordCapSiteReader(stanford, Mode.FORCED);
    		reader.readResearcher(new Researcher(stanford, "https://med.stanford.edu/profiles/michael-halaas"));
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    }
}
