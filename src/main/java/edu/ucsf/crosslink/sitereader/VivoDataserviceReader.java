package edu.ucsf.crosslink.sitereader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import edu.ucsf.crosslink.author.AuthorParser;
import edu.ucsf.crosslink.io.CrosslinkPersistance;

public class VivoDataserviceReader extends SiteReader {

	private static final Logger LOG = Logger.getLogger(VivoDataserviceReader.class.getName());
	
	public VivoDataserviceReader(String affiliation, String siteRoot) {
		super(affiliation, siteRoot);
	}

    public void readSite(CrosslinkPersistance store, AuthorParser parser) throws Exception {
		String suffix = "/people";
		Document doc = getDocument(getSiteRoot() + suffix );
    	Set<VIVOPerson> people = new HashSet<VIVOPerson>();
		if (doc != null) {
			Elements links = doc.select("a[href]");	
		    for (Element link : links) {
		    	if (link.attr("abs:href").contains("#") && !link.attr("abs:href").endsWith("#")) {		    		
		    		print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
		    		print(" * a: <%s>  (%s)", link.attr("data-uri"), trim(link.text(), 35));
		    		people.addAll(readPeopleOfType(link.attr("data-uri")));
		    	}
		    }			
		}
		
		// now grab all the individual URI's
		Set<String> uris = new HashSet<String>();
		for (VIVOPerson person : people) {
			uris.add(person.URI);
		}
		LOG.info("Found " + uris.size() + " unique URI's");
		
		for (String uri : uris) {
			LOG.info(uri.toString());
			if (store.skipAuthor(uri)) {
				continue;
			}
			try {
				store.saveAuthor(parser.getAuthorFromHTML(this, uri));
			}
			catch (Exception e) {
				LOG.log(Level.WARNING, "Error parsing " + uri, e);
			}
		}
    }
    
    private Set<VIVOPerson> readPeopleOfType(String type) throws Exception {
    	int page = 1;
    	boolean onLastPage = true;
    	Set<VIVOPerson> people = new HashSet<VIVOPerson>();
    	
    	do {
    		VIVOPage vpage = readPage(type, page);
    		people.addAll(vpage.individuals);
    		onLastPage = vpage.isLastPage(page++);
    		LOG.info("Found " + people.size() + " people of type " + type);
    	}
    	while (false && !onLastPage);
    	
    	return people;
    }

    private VIVOPage readPage(String type, int page) throws Exception {
    	String suffix = "/dataservice?getRenderedSolrIndividualsByVClass=1&vclassId=" + URLEncoder.encode(type, "UTF-8") + "&page=" + page;
    	InputStream input = new URL(getSiteRoot() + suffix).openStream();
    	Reader reader = new InputStreamReader(input, "UTF-8");

    	VIVOPage vpage = new Gson().fromJson(reader, VIVOPage.class);
    	reader.close();
    	input.close();
    	return vpage;
    }
    
    private class VIVOPage {
    	List<VIVOPageNdx> pages;
    	List<VIVOPerson> individuals;
    	
    	boolean isLastPage(int page) {
    		return pages.get(pages.size() - 1).index == page;
    	}
    	
    	public String toString() {
    		String output = "" + individuals.size();
    		for (VIVOPerson person : individuals) {
    			output += person.toString() + ", ";
    		}
    		return output;
    	}

    	public String toStringT() {
    		String output = "" + pages.size();
    		for (VIVOPageNdx p : pages) {
    			output += p.toString() + ", ";
    		}
    		return output;
    	}
    }
    
    private class VIVOPerson {
    	String profileUrl;
		String name;
    	String URI;
    	
    	public String toString() {
    		return profileUrl + " : " + name + " : " + URI;
    	}
    }
    
    private class VIVOPageNdx {
    	int index;
    	String param;
    	
    	public String toString() {
    		return "" + index;
    	}
    }
}
