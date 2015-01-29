package edu.ucsf.crosslink;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class CrosslinksXMLConfiguration {

	private static final String CONFIGURATION_FILE = "/crosslinks.xml";
	
	private Document doc = null;
	private XPath xpath = null;	
	
	public CrosslinksXMLConfiguration() throws SAXException, IOException, ParserConfigurationException, TransformerConfigurationException, XPathExpressionException {		
		InputStream is = CrosslinksXMLConfiguration.class.getResourceAsStream(CONFIGURATION_FILE);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		doc = dBuilder.parse(is);
		//optional, but recommended
		//read this - http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
		doc.getDocumentElement().normalize();

		// there should be one child node in the list called "Defaults" 
		xpath = XPathFactory.newInstance().newXPath();		
	}
	
	public Set<Node> getDefaultedNodes(String expression) throws XPathExpressionException {
		XPathExpression expr = xpath.compile(expression + "/Defaults");
		Node defaults = ((NodeList)expr.evaluate(doc, XPathConstants.NODESET)).item(0);
		
		expr = xpath.compile(expression + "/*");
		NodeList items = (NodeList)expr.evaluate(doc, XPathConstants.NODESET);
		Set<Node> retval = new HashSet<Node>();
		for (int i = 0; i < items.getLength(); i++) {
			Node n = items.item(i);
			if (n.getNodeType() != Node.ELEMENT_NODE || "Defaults".equals(n.getNodeName())) {
				continue;
			}
			retval.add(overlaySecondOntoFirst(defaults, n));
		}
		return retval;
	}
	
	private Node overlaySecondOntoFirst(Node first, Node second) {
		Node retval = first.cloneNode(true);
		NodeList secondItems = second.cloneNode(true).getChildNodes();
		for (int i = 0; i < secondItems.getLength(); i++) {			
			Node newChild = secondItems.item(i);
			if (newChild.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			Node oldChild = getChildOfName(retval, newChild.getNodeName());
			if (oldChild != null) {
				retval.replaceChild(newChild, oldChild);
			}
			else {
				retval.appendChild(newChild);
			}
		}
		return retval;
	}
	
	public Properties getAsProperties(String expression) throws XPathExpressionException {		
		return getAsProperties(evaluate(doc, expression));
	}

	public Properties getChildrenAsProperties(Node n) {
		return getAsProperties(n.getChildNodes());
	}
	
	private Properties getAsProperties(NodeList nl) {
		Properties prop = new Properties();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			// this is weak but works for our XML.  Only add the property style items, not the deeper ones
			if (node.hasChildNodes() && !hasGrandChildren(node)) {
				prop.put(node.getNodeName(), node.getTextContent());
			}
		}
		return prop;		
	}
	
	private boolean hasGrandChildren(Node n) {
		NodeList children = n.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i).hasChildNodes()) {
				return true;
			}
		}
		return false;
	}
	
	public NodeList evaluate(Node n, String expression) throws XPathExpressionException {
		XPathExpression expr = xpath.compile(expression);
		return (NodeList)expr.evaluate(n, XPathConstants.NODESET);		
	}

	public static String printNode(Node n) {
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer;
		ByteArrayOutputStream retval = new ByteArrayOutputStream(); 
		try {
			transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			DOMSource source = new DOMSource(n);
	 
			// Output to console for testing
			StreamResult result = new StreamResult(retval);
			//StreamResult result = new StreamResult(new File(args[0] + "\\affiliations.xml"));
			transformer.transform(source, result);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return retval.toString();
	}
	
	private Node getChildOfName(Node node, String childName) {
		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (childName.equals(children.item(i).getNodeName())) {
				return children.item(i);
			}
		}
		return null;
	}
	
	public static void main(String[] args) {
		try {
			CrosslinksXMLConfiguration config = new CrosslinksXMLConfiguration();
			for (Node n : config.getDefaultedNodes("//Crosslinks/Processors")) {
				System.out.println(CrosslinksXMLConfiguration.printNode(n));			
				Properties prop = config.getChildrenAsProperties(n);
				System.out.println(prop.toString());
			}			
			for (Node n : config.getDefaultedNodes("//Crosslinks/Affiliations")) {
				System.out.println(CrosslinksXMLConfiguration.printNode(n));			
				Properties prop = config.getChildrenAsProperties(n);
				System.out.println(prop.toString());
			}
			System.out.println(config.getAsProperties("//Crosslinks/*"));
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
