package edu.ucsf.crosslink.io.http;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.hp.cache4guice.Cached;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;

public class DOI2PMIDConverter {

	private static final String DOI_TO_PMID = "http://www.ncbi.nlm.nih.gov/pmc/utils/idconv/v1.0/?tool=Crosslinks&email=eric.meeks@ucsf.edu&versions=no&ids=";

	private DocumentBuilder db = null;
	private XPath xpath = null;	
	
	public DOI2PMIDConverter() throws ParserConfigurationException {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		db = dbf.newDocumentBuilder();		
		xpath = XPathFactory.newInstance().newXPath();		
	}

	@Cached
	public String getPMIDFromDOI(String doi) throws MalformedURLException, SAXException, IOException, XPathExpressionException {
		Document doc = db.parse(new URL(DOI_TO_PMID + doi).openStream());
		XPathExpression expr = xpath.compile("//pmcids/record/@pmid");
		return ((NodeList)expr.evaluate(doc, XPathConstants.NODESET)).item(0).getNodeValue();
	}
	
	public static void main(String[] args) {
		try {
			DOI2PMIDConverter c = new DOI2PMIDConverter();
			System.out.println(c.getPMIDFromDOI("10.1093/nar/gks1195"));
			System.out.println(c.getPMIDFromDOI("foo/bar"));
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}
