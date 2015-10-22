package edu.ucsf.crosslink.model;

import java.io.File;
import java.io.FileReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RiotException;
import org.apache.jena.util.FileManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.io.SparqlPersistance;
import edu.ucsf.crosslink.io.ThumbnailGenerator;
import edu.ucsf.crosslink.io.http.SiteReader;

public class Affiliation extends R2RResourceObject {
	
	private int researcherCount;
	private int publicationCount;
	private RNSType rnsType;
	
	public enum RNSType {PROFILES, VIVO, SCIVAL, LOKI, CAP, UNKNOWN};

	private static final String HAS_ICON = "ASK WHERE {<%s> <" + R2R_HAS_ICON + "> ?i}";
	private static final String HAS_LATITUDE = "ASK WHERE {<%s> <" + GEO_LATITUDE + "> ?i}";

	private static final Logger LOG = Logger.getLogger(Affiliation.class.getName());

	@Inject
	public Affiliation(@Named("BaseURL") String uri, @Named("label") String label) throws URISyntaxException {
		super(uri, Arrays.asList(R2R_AFFILIATION, GEO_SPATIALTHING));
		setLabel(label);				
		rnsType = getRNSType(uri.toLowerCase());
	}
	
	// used for the UI
	public Affiliation(String baseURL, String name, String iconUri, String location, int researcherCount, int publicationCount) throws URISyntaxException {
		this(baseURL, name);
		if (iconUri != null) {
			setIcon(iconUri);
		}
		if (location != null) {
			String [] geoCodes = location.split(",");
			setLiteral(GEO_LATITUDE, geoCodes[0]);
			setLiteral(GEO_LONGITUDE, geoCodes[1]);
		}
		this.researcherCount = researcherCount;		
		this.publicationCount = publicationCount;
	}
	
	@Inject(optional=true)
	public boolean readAndSaveIconFromHost(SiteReader reader, SparqlPersistance store, ThumbnailGenerator thumbnailGenerator) throws Exception {
		// don't do this more than we need to!
		if (store.ask(String.format(HAS_ICON, getURI()))) {
			return true;
		}
		// do not read if it is from an aggregator
		if (getURI().contains("scival.com") || getURI().contains("ctr-in.org")) {
			return false;
		}
		
		// find a good icon, use favicon if present 
		String iconUri = null; 
		if (getURIObject().getHost().indexOf('.') != getURIObject().getHost().lastIndexOf('.')) {
			iconUri = reader.getFavicon("http://" + getURIObject().getHost().substring(getURIObject().getHost().indexOf('.') + 1));
		}
		if (iconUri == null) {
			// we do this second because we don't want the VIVO or Profiles icon
			iconUri = reader.getFavicon(getURI());			
		}
		if (iconUri != null) {
			saveIcon(store, thumbnailGenerator, iconUri);
			return true;
		}
		// still should update triple store
		store.update(this);
		return false;
	}
	
	// this one set it explicitly
	@Inject(optional=true)
	public void readAndSaveIcon(SiteReader reader, SparqlPersistance store, ThumbnailGenerator thumbnailGenerator, @Named("icon") String iconUri) throws Exception {
		saveIcon(store, thumbnailGenerator, iconUri);
	}

	private void saveIcon(SparqlPersistance store, ThumbnailGenerator thumbnailGenerator, String iconUri) throws Exception {
		setIcon(thumbnailGenerator.generateThumbnail(this, iconUri));
		store.update(this);
	}

	private void setIcon(String iconUri) {
		Resource iconResource = setResource(R2R_HAS_ICON, iconUri);
		Property propType = getModel().createProperty(RDF_TYPE);
		Resource foafImage = getModel().createResource(FOAF_IMAGE);
		iconResource.addProperty(propType, foafImage);		
	}
	
	@Inject(optional=true)
	public void readFromDbpedia(SiteReader reader, SparqlPersistance store, ThumbnailGenerator thumbnailGenerator, @Named("DbpediaURI") String dbPediauri) throws Exception {
		// don't do this more than we need to!
		if (store.ask(String.format(HAS_ICON, getURI())) && store.ask(String.format(HAS_LATITUDE, getURI()))) {
			return;
		}
		Model dbPediaModel = null;
		try {
			try {
				dbPediaModel = FileManager.get().loadModel(dbPediauri);			
			}
			catch (RiotException re) {
				LOG.log(Level.WARNING, "Unable to read " + dbPediauri + " for " + getName() + " trying to read RDF/XML directly", re);
				dbPediaModel = FileManager.get().loadModel(dbPediauri.replace("/resource/", "/data/") + ".rdf");			
			}
		}
		catch (Exception e) {
			LOG.log(Level.SEVERE, "Unable to read " + dbPediauri + " for " + getName(), e);
			return;
		}
		Resource resource = dbPediaModel.createResource(dbPediauri);
		
		if (!readAndSaveIconFromHost(reader, store, thumbnailGenerator)) {
			String website = getValue(dbPediaModel, resource, "http://dbpedia.org/property/website");
			String iconUri = null;
			if (website != null) {
				iconUri = reader.getFavicon(website);
			}		
			// check the other pages, favor small ones with www.
			if (iconUri == null) {
				ArrayList<String> values = getValues(dbPediaModel, resource, "http://dbpedia.org/ontology/wikiPageExternalLink");
				Collections.sort(values, new Comparator<String>() {
			            public int compare(String o1, String o2) {
			                return o1.length() - o2.length() + (o1.contains("www.") ? -100 : 0) + (o2.contains("www.") ? 100 : 0);
			            }
			        });
				
				for (String value : values) {
					iconUri = reader.getFavicon(value);
					if (iconUri != null) {
						break;
					}
				}					
			}
			// lastly, resort to thumnail
			if (iconUri == null) {
				iconUri = getValue(dbPediaModel, resource, "http://dbpedia.org/ontology/thumbnail");
			}
			
			if (iconUri != null) {
				saveIcon(store, thumbnailGenerator, iconUri);
			}
		}
		
		// get geocode points
		if (!store.ask(String.format(HAS_LATITUDE, getURI()))) {
			Statement geo = resource.getProperty(dbPediaModel.createProperty("http://www.georss.org/georss/point"));
			if (geo != null && geo.getObject() != null) {
				setLocation(store, thumbnailGenerator, geo.getObject().toString().replace(' ', ','));
			}
		}
	}
	
	private static String getValue(Model model, Resource resource, String property) {
		Statement stmt = resource.getProperty(model.createProperty(property));
		if (stmt != null && stmt.getObject() != null) {
			return stmt.getObject().toString();
		}
		else {
			return null;
		}		
	}

	private static ArrayList<String> getValues(Model model, Resource resource, String property) {
		ArrayList<String> values = new ArrayList<String>();
		StmtIterator stmtIt = resource.listProperties(model.createProperty(property));
		while (stmtIt.hasNext()) {
			values.add(stmtIt.next().getObject().toString());
		}
		return values;
	}

	@Inject(optional=true)
	public void setLocation(SparqlPersistance store, ThumbnailGenerator thumbnailGenerator, @Named("Location") String location) throws Exception {
		String [] geoCodes = location.split(",");
		setLiteral(GEO_LATITUDE, geoCodes[0]);
		setLiteral(GEO_LONGITUDE, geoCodes[1]);

		store.update(this);	
	}

	private static RNSType getRNSType(String baseURL) {
		if (baseURL.contains("stanford.edu")) {
			return RNSType.CAP;
		}
		else if (baseURL.contains("loki")) {
			return RNSType.LOKI;
		}
		else if (baseURL.contains("scival.com") || baseURL.contains("northwestern.edu")) {
			return RNSType.SCIVAL;
		}
		else if (baseURL.contains("vivo") || baseURL.contains("duke.edu") || 
				baseURL.contains("suny.edu") || baseURL.contains("unimelb.edu.au")) {
			return RNSType.VIVO;
		}
		else if (baseURL.contains("profiles")) {
			return RNSType.PROFILES;
		}
		return RNSType.UNKNOWN;
	}
	
	public RNSType getRNSType() {
		return rnsType;
	}
	
	public String getIcon() {
		return getResourceURI(R2R_HAS_ICON);
	}
	
	public int getResearcherCount() {
		return researcherCount;
	}
	
	public int getPublicationCount() {
		return publicationCount;
	}
	
	public String getLatitude() {
		return getStringLiteral(GEO_LATITUDE);
	}
	
	public String getLongitude() {
		return getStringLiteral(GEO_LONGITUDE);
	}
	
	// convert property files to XML
	// cut and paste output to http://www.freeformatter.com/xml-formatter.html for a nice document
	public static void main(String[] args) {
		try {
			
			Model model = FileManager.get().loadModel("http://dbpedia.org/resource/University_of_California,_San_Francisco");
			Resource resource = model.createResource("http://dbpedia.org/resource/University_of_California,_San_Francisco");
			Statement thumbnail = resource.getProperty(model.createProperty("http://dbpedia.org/ontology/thumbnail"));
System.out.println(thumbnail.getObject().isURIResource());			
Statement geo = resource.getProperty(model.createProperty("http://www.georss.org/georss/point"));
System.out.println(geo.toString());
Statement label = resource.getProperty(model.createProperty(RDFS_LABEL));
System.out.println(label.toString());
 if (true) return;
			
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.newDocument();
			Element root = doc.createElement("Affiliations");
			doc.appendChild(root);
			for (File file : new File(args[0]).listFiles()) {				
				Properties prop = new Properties();
				prop.load(new FileReader(file));
				System.out.println(file.getAbsolutePath());
				Element affiliation = doc.createElement("Affiliation");
				root.appendChild(affiliation);
				for (String name : new String[]{"BaseURL", "Name", "Location"} ) {
					if (!prop.containsKey(name)) {
						root.removeChild(affiliation);
						break;
					}
					Element n = doc.createElement(name);
					n.appendChild(doc.createTextNode(prop.getProperty(name)));
					System.out.println(n.toString());
					System.out.println(n.getNodeValue());
					affiliation.appendChild(n);
				}
			}
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			DOMSource source = new DOMSource(doc);
	 
			// Output to console for testing
			StreamResult result = new StreamResult(System.out);
			//StreamResult result = new StreamResult(new File(args[0] + "\\affiliations.xml"));
			transformer.transform(source, result);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
