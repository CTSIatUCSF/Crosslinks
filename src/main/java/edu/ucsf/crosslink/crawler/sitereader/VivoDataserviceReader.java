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

import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.crosslink.processor.AffiliationCrawler;

@Deprecated
public class VivoDataserviceReader extends AffiliationCrawler {

	private static final Logger LOG = Logger.getLogger(VivoDataserviceReader.class.getName());
	
	// hack
	private String[] dataServiceSuffix = {"/dataservice?getRenderedSolrIndividualsByVClass=1&vclassId=", "/dataservice?getSolrIndividualsByVClass=1&vclassId="};
	private int dataServiceSuffixNdx = 0;
	
	@Inject
	public VivoDataserviceReader(@Named("Name") String name, @Named("BaseURL") String baseURL, @Named("Location") String location, 
			CrosslinkPersistance store) throws Exception {
		super(new Affiliation(name, baseURL, location), store);
	}

	protected void collectResearcherURLs() throws Exception {
		String suffix = "/people";
		Document doc = getDocument(getSiteRoot() + suffix );
    	Set<VIVOPerson> people = new HashSet<VIVOPerson>();
    	// remember which types we have read, in case the link shows up multiple times
    	Set<String> processedTypes = new HashSet<String>();
		if (doc != null) {
			Elements links = doc.select("a[href]");	
		    for (Element link : links) {
		    	if (link.attr("abs:href").contains("#") && !link.attr("abs:href").endsWith("#")) {		    		
		    		//print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
		    		//print(" * a: <%s>  (%s)", link.attr("data-uri"), trim(link.text(), 35));
		    		String type = link.attr("data-uri");
		    		if (!processedTypes.contains(type)) {
			    		people.addAll(readPeopleOfType(type));
			    		processedTypes.add(type);
		    		}
		    	}
				LOG.info("Found " + people.size() + " people so far....");
		    }			
		}
		
		// now grab all the individual URI's
		for (VIVOPerson person : people) {
			Researcher r = new Researcher(person.URI,getAffiliation());
			addResearcher(r);
		}
		LOG.info("Found " + getResearchers().size() + " unique URI's");
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
