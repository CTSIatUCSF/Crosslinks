package edu.ucsf.crosslink.crawler.sitereader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;


import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.model.Researcher;


public class VivoDataserviceReader extends SiteReader {

	private static final Logger LOG = Logger.getLogger(VivoDataserviceReader.class.getName());
	
	// hack
	private String[] dataServiceSuffix = {"/dataservice?getRenderedSolrIndividualsByVClass=1&vclassId=", "/dataservice?getSolrIndividualsByVClass=1&vclassId="};
	private int dataServiceSuffixNdx = 0;
	
	@Inject
	public VivoDataserviceReader(@Named("Affiliation") String affiliation, @Named("BaseURL") String siteRoot) {
		super(affiliation, siteRoot);
	}

	protected void collectAuthorURLS() throws Exception {
		String suffix = "/people";
		Document doc = getDocument(getSiteRoot() + suffix );
    	Set<VIVOPerson> people = new HashSet<VIVOPerson>();
		if (doc != null) {
			Elements links = doc.select("a[href]");	
		    for (Element link : links) {
		    	if (link.attr("abs:href").contains("#") && !link.attr("abs:href").endsWith("#")) {		    		
		    		//print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
		    		//print(" * a: <%s>  (%s)", link.attr("data-uri"), trim(link.text(), 35));
		    		people.addAll(readPeopleOfType(link.attr("data-uri")));
		    	}
				LOG.info("Found " + people.size() + " people so far....");
		    }			
		}
		
		// now grab all the individual URI's
		for (VIVOPerson person : people) {
			addAuthor(new Researcher(person.URI));
		}
		LOG.info("Found " + getAuthors().size() + " unique URI's");
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
    	while (!onLastPage);
    	
    	return people;
    }

    private VIVOPage readPage(String type, int page) throws Exception {
    	String suffix = dataServiceSuffix[dataServiceSuffixNdx] + URLEncoder.encode(type, "UTF-8") + "&page=" + page;
    	InputStream input = new URL(getSiteRoot() + suffix).openStream();
    	Reader reader = new InputStreamReader(input, "UTF-8");

    	VIVOPage vpage = new Gson().fromJson(reader, VIVOPage.class);
    	// hack to try other method, works for Washingon University
    	if (page == 1 && vpage == null && dataServiceSuffixNdx == 0) {
        	reader.close();
        	input.close();
        	dataServiceSuffixNdx++;
    		return readPage(type, page);
    	}
    	reader.close();
    	input.close();
    	return vpage;
    }
    
    private class VIVOPage {
    	List<VIVOPageNdx> pages;
    	List<VIVOPerson> individuals;
    	
    	boolean isLastPage(int page) {
    		return pages == null || pages.isEmpty() || pages.get(pages.size() - 1).index == page;
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
