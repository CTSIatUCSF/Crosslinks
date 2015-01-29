package edu.ucsf.crosslink.model;

import java.io.File;
import java.io.FileReader;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class Affiliation extends R2RResourceObject {
	
	private int researcherCount;
	private int publicationCount;
	private RNSType rnsType;
	
	public enum RNSType {PROFILES, VIVO, SCIVAL, LOKI, CAP, UNKNOWN};

	@Inject
	public Affiliation(@Named("Name") String name, @Named("BaseURL") String baseURL, @Named("Location") String location) throws URISyntaxException {
		super(baseURL, Arrays.asList(R2R_AFFILIATION, GEO_SPATIALTHING));
		setLabel(name);
		rnsType = getRNSType(baseURL.toLowerCase());
		if (location != null) {
			String [] geoCodes = location.split(",");
			setLiteral(GEO_LATITUDE, geoCodes[0]);
			setLiteral(GEO_LONGITUDE, geoCodes[1]);
		}
	}
	
	public Affiliation(String name, String baseURL, String location, int researcherCount, int publicationCount) throws URISyntaxException {
		this(name, baseURL, null);
		this.researcherCount = researcherCount;		
		this.publicationCount = publicationCount;
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
