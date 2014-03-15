package edu.ucsf.crosslink.crawler.parser;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.Ontology;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

import edu.ucsf.crosslink.Crosslinks;
import edu.ucsf.crosslink.crawler.AffiliationCrawlerModule;
import edu.ucsf.crosslink.crawler.sitereader.SiteReader;
import edu.ucsf.crosslink.io.IOModule;
import edu.ucsf.crosslink.io.JenaPersistance;
import edu.ucsf.crosslink.io.ThumbnailGenerator;
import edu.ucsf.crosslink.model.Researcher;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class RDFAuthorshipParser implements AuthorParser {

	private static final Logger LOG = Logger.getLogger(RDFAuthorshipParser.class.getName());

	private SiteReader siteReader;
	private JenaPersistance jenaPersistance;
	
    private static void print(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }

    private static String trim(String s, int width) {
        if (s.length() > width)
            return s.substring(0, width-1) + ".";
        else
            return s;
    }
    
    @Inject
    public RDFAuthorshipParser(SiteReader siteReader, JenaPersistance jenaPersistance) {
    	this.siteReader = siteReader;
    	this.jenaPersistance = jenaPersistance;
    }
    
	
    public boolean readResearcher(Researcher researcher) throws IOException, InterruptedException {
    	Document doc = siteReader.getDocument(researcher.getHomePageURL());
    	Resource resource = jenaPersistance.getResourceFromRdfURL(getPersonRDFURLFromHTMLURL(researcher.getHomePageURL(), doc), true);
    	boolean foundResearcherInfo = false;
		if (resource != null && getPersonOnlyFromURL(researcher, doc)) {
			foundResearcherInfo = true;
			StmtIterator rsi = resource.listProperties();
			while (rsi.hasNext()) {
				Statement rs = rsi.next();
				if ("authorInAuthorship".equals(rs.getPredicate().getLocalName())) {
					Resource aia = jenaPersistance.getResource(rs.getObject().asNode().getURI(), false);
					LOG.info("authorInAuthorship : " + aia);
					StmtIterator aiasi = aia.listProperties();
					while (aiasi.hasNext()) {
						Statement aias = aiasi.next();
						if ("linkedInformationResource".equalsIgnoreCase(aias.getPredicate().getLocalName())) {
							Resource lir = jenaPersistance.getResource(aias.getObject().asNode().getURI(), false);
							LOG.info("linkedInformationResource : " + lir);
							String pmid = jenaPersistance.find(lir, "pmid");
							if (pmid != null) {
								researcher.addPubMedPublication(pmid);
							}
							break;
						}
					}
				}
	    	}
	    	//  look for a photo
		    for (Element src : doc.select("[src]")) {
	    	   if (src.tagName().equals("img") && src.attr("class").contains("photo") && !src.attr("title").equals("no image")) {
	    		   researcher.addImageURL(src.attr("abs:src"));
	    	   }
		    }
		}
    	return foundResearcherInfo;
    }
    
    boolean getPersonOnlyFromURL(Researcher researcher, Document doc) {
    	String rdfUrl = getPersonRDFURLFromHTMLURL(researcher.getHomePageURL(), doc);
    	Resource resource = jenaPersistance.getResourceFromRdfURL(rdfUrl, true);
    	return addResearcherDetails(researcher, resource);
    }
    
    private boolean addResearcherDetails(Researcher researcher, Resource resource) {
    	if (resource != null) {
	     	researcher.setURI(resource.getURI());
	    	researcher.setLabel(jenaPersistance.find(resource, "label"));
	    	researcher.addImageURL(jenaPersistance.find(resource, "mainImage"));
	    	researcher.setOrcidId(jenaPersistance.find(resource, "orcidId"));
	    	return true;
    	}
    	return false;
    }
    
    private String getPersonRDFURLFromHTMLURL(String url, Document doc) {
		Elements links = doc.select("a[href]");	
		
		String rdfUrl = null;
	    for (Element link : links) {
	    	if ( link.attr("abs:href").endsWith(".rdf")) {
	    		print(" * a: <%s>  (%s)", link.attr("abs:href"), trim(link.text(), 35));
	    		rdfUrl = link.attr("abs:href");
	    	}
        }
	    if (rdfUrl == null) {
	    	// worth a try
	    	String[] parts = url.split("/");
	    	rdfUrl = url + "/" + parts[parts.length - 1] + ".rdf";
	    }
	    return rdfUrl;
    }
    
    @SuppressWarnings({ "unused", "resource" })
	public static void main(String[] args) {
    	try {
    		if (false) {
	    		String base = "http://www.example.com/ont";
	    		OntModel model = ModelFactory.createOntologyModel();
	    		Ontology ont = model.createOntology("");
	    		ont.addImport(model.createResource("http://test.owl#"));
	    		model.write(System.out, "RDF/XML-ABBREV", base);
	    		model.write(new FileOutputStream("C:\\Development\\Crosslinks\\workspace\\example3.owl"), "RDF/XML-ABBREV", base);
	    		model.close();
    		}
    		
			Properties prop = new Properties();
			prop.load(RDFAuthorshipParser.class.getResourceAsStream(Crosslinks.PROPERTIES_FILE));	
			prop.load(RDFAuthorshipParser.class.getResourceAsStream("/affiliations/WashU.properties"));
			prop.setProperty("rdfBaseDir", "C:\\Development\\R2R\\workspace\\Crosslinks\\testModel");
			Injector injector = Guice.createInjector(new IOModule(prop), new AffiliationCrawlerModule(prop));

			RDFAuthorshipParser parser = injector.getInstance(RDFAuthorshipParser.class);
			//parser.getAuthorFromHTML("http://profiles.ucsf.edu/eric.meeks");
			Researcher reseacher = new Researcher(null, "http://vivo.wustl.edu/individual/hrfact-1358572946");
			parser.readResearcher(reseacher);
			injector.getInstance(ThumbnailGenerator.class).generateThumbnail(reseacher);
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    }
}
