package edu.ucsf.crosslink;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ProfilesSearchReader extends HTMLReader {

	private static final Logger LOG = Logger.getLogger(ProfilesSearchReader.class.getName());

	private String affiliation;
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {		// TODO Auto-generated method stub		
		try  {
			
			AuthorshipParser parser = new HTMLAuthorshipParser();
			ProfilesSearchReader psr = null;
			AuthorshipPersistance store = null;
			String siteRoot = null;
			
			if (args.length == 2) {
			    psr = new ProfilesSearchReader(args[0]);
			    store = new CSVAuthorshipStore(args[0] + ".csv");	
			    siteRoot = args[1];			    		
			}
			else {
				showUse();
			}
			
			if (parser != null && psr != null && store != null && siteRoot != null) {
				psr.parseSearch(siteRoot, store, parser);
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
		System.out.println("affiliation, url");
	}
	
    public ProfilesSearchReader(String affiliation) {
    	this.affiliation = affiliation;
    }
    
    public void parseSearch(String siteRoot, AuthorshipPersistance store, AuthorshipParser parser) throws Exception {
		String suffix = "/search/default.aspx?searchtype=people&searchfor=&perpage=100&offset=0&sortby=&sortdirection=&showcolumns=1&page=";
		int page = 1;
		String firstUrlInPriorSet = null;
		boolean findingSamePeople = false;
		do {
			Document doc = getDocument(siteRoot + suffix + page++);
			if (doc != null) {
				Elements links = doc.select("input[type=hidden]");	
				boolean firstlink = true;
				
			    for (Element link : links) {
			    	if ( link.attr("id").startsWith(siteRoot + "/profile/")) {
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
			    		LOG.info("Person = " + url);
			    		if (store.containsAuthor(url)) {
			    			continue;
			    		}
			    			
						try {
							Collection<Authorship> authorships = parser.getAuthorshipsFromHTML(url);
							for (Authorship authorship : authorships) {
								LOG.info("Authorship -- " + authorship.toString());
								authorship.setAffiliation(affiliation);
								store.saveAuthorship(authorship);
							}
							store.flush();
						}
						catch (Exception e) {
							LOG.log(Level.WARNING, "Error parsing " + url, e);
						}
			    	}
		        }
			}
			
		}
		while (!findingSamePeople);
    }
    
}
