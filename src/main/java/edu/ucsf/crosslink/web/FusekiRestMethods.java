package edu.ucsf.crosslink.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.w3c.dom.DOMException;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.sun.jersey.api.view.Viewable;

import edu.ucsf.crosslink.job.quartz.Quartz;
import edu.ucsf.crosslink.model.Affiliated;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.crosslink.processor.controller.ProcessorController;
import edu.ucsf.crosslink.processor.controller.ProcessorControllerFactory;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.jena.JsonLDService;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;
import edu.ucsf.ctsi.r2r.jena.SparqlQueryClient;

public class FusekiRestMethods implements R2RConstants {

	private static final Logger LOG = Logger.getLogger(FusekiRestMethods.class.getName());
	
	private static final String ALL_AFFILIATIONS_SPARQL = "SELECT ?l ?a ?icon ?lat ?lon (count(?r) as ?rc) WHERE { ?a <" +
			RDF_TYPE + "> <" + R2R_AFFILIATION + "> . ?a <" + RDFS_LABEL +  "> ?l . OPTIONAL {?a <" + R2R_HAS_ICON + "> ?icon} . OPTIONAL {?a <" + 
			GEO_LATITUDE + "> ?lat . ?a <" + GEO_LONGITUDE + "> ?lon} . OPTIONAL {?r <" +
			R2R_HAS_AFFILIATION + "> ?a} } GROUP BY ?l ?a ?icon ?lat ?lon ORDER BY ?l";

	private static final String AFFILIATION_SPARQL = "SELECT ?a ?icon ?lat ?lon (count(distinct(?r)) as ?rc) (count(distinct(?p)) as ?pc) WHERE { ?a <" +
			RDF_TYPE + "> <" + R2R_AFFILIATION + "> . ?a <" + RDFS_LABEL +  "> ?l . FILTER (?l = \"%s\") . OPTIONAL {?a <" + R2R_HAS_ICON + "> ?icon} . OPTIONAL {?a <" + 
			GEO_LATITUDE + "> ?lat . ?a <" + GEO_LONGITUDE + "> ?lon} . OPTIONAL {?r <" +
			R2R_HAS_AFFILIATION + "> ?a . OPTIONAL {?r <" + FOAF_PUBLICATIONS + "> ?p} } } GROUP BY ?a ?icon ?lat ?lon";
	
//	private static final String RESEARCHERS_SPARQL_SLOW = "SELECT ?r ?l ?hp ?i ?t (count(distinct ?er) as ?erc) (count(distinct ?cw) as ?cwc) WHERE {?r <" + 
//			R2R_HAS_AFFILIATION + "> <%1$s>  . ?r <" + RDFS_LABEL + "> ?l . ?r <" + R2R_WORK_VERIFIED_DT + 
//			"> ?wvdt . OPTIONAL {?r <" +  FOAF_HOMEPAGE + "> ?hp } . OPTIONAL {?r <" + FOAF_HAS_IMAGE + 
//			"> ?i} . OPTIONAL {?i <" + FOAF_THUMBNAIL + "> ?t} . OPTIONAL {?r <" + 
//			FOAF_PUBLICATIONS + "> ?cw  . ?er <" + FOAF_PUBLICATIONS + "> ?cw  . ?er <" + 
//			R2R_HAS_AFFILIATION + "> ?ea FILTER (?ea != <%1$s>)}} GROUP BY ?r ?l ?hp ?i ?t";
//	
	private static final String RESEARCHERS_SPARQL = "SELECT DISTINCT ?r ?l ?hp ?i ?erc ?cwc WHERE {?r <" + 
			R2R_HAS_AFFILIATION + "> <%1$s>  . OPTIONAL {?r <" + RDFS_LABEL + "> ?l} . OPTIONAL {?r <" + 
			FOAF_HOMEPAGE +	"> ?hp } . OPTIONAL { GRAPH <" + 
			R2R_THUMBNAIL_GRAPH +	"> {?r <" + FOAF_HAS_IMAGE +"> ?i}} . OPTIONAL { GRAPH <" + 
			R2R_DERIVED_GRAPH + "> {?r <" + R2R_EXTERNAL_COAUTHOR_CNT + "> ?erc}} . OPTIONAL { GRAPH <" + 
			R2R_DERIVED_GRAPH + "> {?r <" + R2R_SHARED_PUB_CNT + "> ?cwc}}}";
	
	private static final String COAUTHORS_WHERE = "WHERE {<%1$s> <" + R2R_HAS_AFFILIATION + "> ?a . <%1$s> <" +
			FOAF_PUBLICATIONS + "> ?cw  . ?r <" + FOAF_PUBLICATIONS + "> ?cw  . ?r <" + RDFS_LABEL + 
			"> ?rl . OPTIONAL {?r <" + FOAF_HOMEPAGE + "> ?hp } . OPTIONAL {?r <" + FOAF_HAS_IMAGE + "> ?tn} . ?r <" + 
			R2R_HAS_AFFILIATION + "> ?ea FILTER (?ea != ?a) . ?ea <" + RDFS_LABEL + 
			"> ?al . OPTIONAL {?ea <" + R2R_HAS_ICON + "> ?eaicon} . ?ea <" + GEO_LATITUDE + "> ?ealat . ?ea <" + GEO_LONGITUDE + "> ?ealon}";
			
	private static final String COAUTHORS_SELECT = "SELECT (?r as ?researcherURI) (?hp as ?researcherHomePage) (?rl as ?researcherLabel) (?cw as ?contributedWork) (?tn as ?thumbnail) " +
			"(?ea as ?researchNetworkingSite) (?al as ?affiliation) (?eaicon as ?icon) (?ealat as ?lat) (?ealon as ?lon) " + COAUTHORS_WHERE;
	
	private static final String COAUTHORS_CONSTRUCT = "CONSTRUCT {?r <" + FOAF_PUBLICATIONS + "> ?cw . ?r <" +
			RDFS_LABEL + "> ?rl . ?r <" + FOAF_HOMEPAGE + "> ?hp . ?r <" + FOAF_HAS_IMAGE + "> ?tn . ?r  <" +
			R2R_HAS_AFFILIATION + "> ?ea . ?ea  <" + RDFS_LABEL + "> ?al . ?ea <" + R2R_HAS_ICON + 
			"> ?eaicon . ?ea <" + GEO_LATITUDE + "> ?ealat . ?ea <" + GEO_LONGITUDE + "> ?ealon} " + COAUTHORS_WHERE;

	private static final String COAUTHORS_SAMEAS = "SELECT (?r as ?researcherURI) (?fn as ?firstName) " + 
			"(?ln as ?lastName) (?er as ?otherResearcherURI) (?efn as ?otherReserarcherFirstName) (?ln as ?otherResearcherLastName) " +
			"WHERE {?r <" +	R2R_HAS_AFFILIATION + "> <%1$s> . ?r <" + FOAF_LAST_NAME + "> ?ln . ?r <" + FOAF_FIRST_NAME +
			"> ?fn . ?r <" + FOAF_PUBLICATIONS + "> ?cw . ?er <" + FOAF_PUBLICATIONS + "> ?cw . ?er <" + 
			R2R_HAS_AFFILIATION + "> ?ea . ?er <" + FOAF_LAST_NAME + "> ?ln . ?er <" + FOAF_FIRST_NAME + "> ?efn " +
			"FILTER (?ea != <%1$s>) && (FILTER ((LCASE(?efn) = LCASE(?fn)) || " + 
			"(STRLEN(?efn) = 1 && STRSTARTS(LCASE(?fn), LCASE(?efn))) || " + 
			"(STRLEN(?fn) = 1 && STRSTARTS(LCASE(?efn), LCASE(?fn))))) }";

	private ProcessorControllerFactory factory;
	private JsonLDService jsonLDService;
	private SparqlQueryClient uiSparqlClient;
	
	// XML bs
	private DocumentBuilderFactory docFactory;
	private DocumentBuilder docBuilder;
    private Transformer transformer;

	@Inject
	public FusekiRestMethods(JsonLDService jsonLDService,
			ProcessorControllerFactory factory, @Named("uiFusekiUrl") String uiFusekiUrl) throws ParserConfigurationException, TransformerConfigurationException, TransformerFactoryConfigurationError {
		this.uiSparqlClient = new SparqlQueryClient(uiFusekiUrl + "/sparql", 10000, 20000);
		this.factory = factory;
		this.jsonLDService = jsonLDService;

		// XML bs
		docFactory = DocumentBuilderFactory.newInstance();
		docBuilder = docFactory.newDocumentBuilder();
		transformer = TransformerFactory.newInstance().newTransformer();
	}

	@GET
	@Path("/index")
	public Viewable index(@Context HttpServletRequest request,
			@Context HttpServletResponse response) throws Exception {
		request.setAttribute("affiliations", getAffiliations());
		return new Viewable("/jsps/index.jsp", null);
	}
	
	@GET
	@Path("/status")
	public Viewable status(@Context HttpServletRequest request,
			@Context HttpServletResponse response) throws Exception {
		List<ProcessorController> processorControllers = new ArrayList<ProcessorController>();
		processorControllers.addAll(factory.getCrawlers());
		// sort them so that the active ones show up at the top
		Collections.sort(processorControllers);
		request.setAttribute("crawlers", processorControllers);
		request.setAttribute("metaHistory", Quartz.getMetaControllerHistory());
		return new Viewable("/jsps/status.jsp", null);
	}

	@GET
	@Path("{crawler}/status")
	public Viewable statusDetail(@PathParam("crawler") String crawlerName, @Context HttpServletRequest request,
			@Context HttpServletResponse response, @QueryParam("mode") String mode, @QueryParam("status") String status) throws Exception {
		ProcessorController processorController = factory.getCrawler(crawlerName);
		if (CrosslinksServletFilter.isAdministrator(request)) {
			if ("PAUSED".equalsIgnoreCase(status)) {
				processorController.pause();
			}
			if (mode != null) {
				processorController.setMode(mode);
			}
		}
		request.setAttribute("crawler", processorController);
		if (processorController.getIterable() instanceof Affiliated) {
			request.setAttribute("affiliation", ((Affiliated)processorController.getIterable()).getAffiliation());			
		}
		return new Viewable("/jsps/statusDetail.jsp", null);
	}

	@GET
    @Path("{affiliation}")
    public Viewable getAffiliationDetail(@PathParam("affiliation") String affiliation, @Context HttpServletRequest request,
			@Context HttpServletResponse response) throws Exception {
		request.setAttribute("affiliation", getAffiliation(affiliation));
		return new Viewable("/jsps/affiliation.jsp", null);
    }

    @GET
    @Path("{affiliation}/researchers")
    public Viewable getResearchers(@PathParam("affiliation") String affiliationStr, @Context HttpServletRequest request,
			@Context HttpServletResponse response, @QueryParam("clearCache") String clearCache) throws Exception {
    	Affiliation affiliation = getAffiliation(affiliationStr);
		request.setAttribute("affiliation", affiliation);
		if ("true".equalsIgnoreCase(clearCache)) {
			request.getSession().removeAttribute(affiliation.getName() + "reseachers");
		}
		@SuppressWarnings("unchecked")
		List<Researcher> researchers = (List<Researcher>)request.getSession().getAttribute(affiliation.getName() + "reseachers");
		if (researchers == null) {
			researchers = getResearchers(affiliation);
			request.getSession().setAttribute(affiliation.getName() + "reseachers", researchers);
		}
		
		request.setAttribute("researchers", researchers);
		return new Viewable("/jsps/researchers.jsp", null);
    }

    @GET
    @Path("{affiliation}/possibleSamePeople")
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    public String getSamePeople(@PathParam("affiliation") String affiliation, @QueryParam("format") String format) throws Exception {
		return getFormattedResults(uiSparqlClient, String.format(COAUTHORS_SAMEAS, getAffiliation(affiliation).getURI()), format).toString();
    }

    @GET
    @Path("coauthors")
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    public String getCoauthors(@QueryParam("researcherURI") String researcherURI, @QueryParam("format") String format) throws Exception {
		if ("JSON-LD".equals(format)) {
			return jsonLDService.getJSONString(uiSparqlClient.construct(String.format(COAUTHORS_CONSTRUCT, researcherURI)));
		}
		return getFormattedResults(uiSparqlClient, String.format(COAUTHORS_SELECT, researcherURI), format).toString();
    }
    
    @GET
    @Path("ctsaSearchCoauthors")
    @Produces(MediaType.APPLICATION_XML)
    public String ctsaSearchCoauthors(@QueryParam("PMID") String pmid) throws IOException, DOMException, ParserConfigurationException, TransformerFactoryConfigurationError, TransformerException  {
		Document doc = Jsoup.connect("http://research.icts.uiowa.edu/polyglot/service/person_by_pub.jsp")
			    .data("mode", "PMID")
			    .data("sort", "rank")
			    .data("value", pmid)
			    .post();    	
		//System.out.println(doc.select("th").first().parent().parent());  

		// root elements
		org.w3c.dom.Document document = docBuilder.newDocument();
		org.w3c.dom.Element rootElement = document.createElement("coauthors");
		document.appendChild(rootElement);
		
		for (Element tr : doc.select("th").first().parent().parent().select("tr")) {
			//System.out.println(tr);
			rootElement.appendChild(getAuthor(document, tr));
		}		
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.getBuffer().toString().replaceAll("\n|\r", "");
    }

    private static ByteArrayOutputStream getFormattedResults(final SparqlQueryClient client, final String sparql, final String format) throws Exception {
    	final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    	client.select(sparql, new ResultSetConsumer() {
			public void useResultSet(ResultSet resultSet) {
				if ("CSV".equals(format)) {
					ResultSetFormatter.outputAsCSV(outStream, resultSet);
				}
				else if ("XML".equals(format)) {
					ResultSetFormatter.outputAsXML(outStream, resultSet);					
				}
				else if ("JSON".equals(format)) {
					ResultSetFormatter.outputAsJSON(outStream, resultSet);
				}
			}
		});
    	return outStream;
    }
    
    private List<Affiliation> getAffiliations() throws Exception {
    	final List<Affiliation> affiliations = new ArrayList<Affiliation>();
    	uiSparqlClient.select(ALL_AFFILIATIONS_SPARQL, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					Affiliation affiliationObj;
					try {
						// because we have an aggregate clause, we can return one row even if no data is present. This if prevents that use case from throwing a NPE..
						if (qs.contains("?a")) {
							affiliationObj = new Affiliation(qs.getResource("?a").getURI(), qs.getLiteral("?l").getString(),
									(qs.contains("?icon") ? qs.getResource("?icon").getURI() : null),
									(qs.contains("?lat") ? qs.getLiteral("?lat").getString() + "," + qs.getLiteral("?lon").getString() : "0,0"),
									qs.getLiteral("?rc").getInt(), 0);
							affiliations.add(affiliationObj);
						}
					} 
					catch (URISyntaxException e) {
						LOG.log(Level.WARNING, e.getMessage(), e);
					}
				}								
			}
		});
        return affiliations;
    }
    
    public Affiliation getAffiliation(final String affiliation) throws Exception {
    	String sparql = String.format(AFFILIATION_SPARQL, affiliation);
    	final List<Affiliation> affiliations = new ArrayList<Affiliation>();
    	uiSparqlClient.select(sparql, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				if (rs.hasNext()) {		
					QuerySolution qs = rs.next();
					Affiliation affiliationObj;
					try {
						affiliationObj = new Affiliation(qs.getResource("?a").getURI(), affiliation,
								(qs.contains("?icon") ? qs.getResource("?icon").getURI() : null),
								(qs.contains("?lat") ? qs.getLiteral("?lat").getString() + "," + qs.getLiteral("?lon").getString() : "0,0"),
								qs.getLiteral("?rc").getInt(), qs.getLiteral("?pc").getInt());
						affiliations.add(affiliationObj);
					} 
					catch (URISyntaxException e) {
						LOG.log(Level.WARNING, e.getMessage(), e);
					}
				}
		    }
		});
    	return affiliations.size() > 0 ? affiliations.get(0) : null;
    }
    
    public List<Researcher> getResearchers(final Affiliation affiliation) throws Exception {
		String sparql = String.format(RESEARCHERS_SPARQL, affiliation.getURI()); 
    	final List<Researcher> researchers = new ArrayList<Researcher>();
    	uiSparqlClient.select(sparql, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					if (qs.getResource("?r") != null) {
						try {
							researchers.add(new Researcher(qs.getResource("?r").getURI(), affiliation,
													qs.get("?l") != null ? qs.getLiteral("?l").getString() : qs.getResource("?r").getURI(),
													qs.get("?hp") != null ? qs.getResource("?hp").getURI(): null,
													qs.get("?i") != null ? qs.getResource("?i").getURI(): null,
													qs.get("?t") != null ? qs.getResource("?t").getURI() : null,
													qs.get("?erc") != null ? qs.getLiteral("?erc").getInt() : 0,
													qs.get("?cwc") != null ? qs.getLiteral("?cwc").getInt() : 0));
						}
						catch (URISyntaxException e) {
							LOG.log(Level.WARNING, e.getMessage(), e);
						}
					} 
				}								
			}
		});
		Collections.sort(researchers);
		return researchers;
    }
    
    @Path("raw")
    public static class RawFusekiRestMethods extends FusekiRestMethods {

    	@Inject
    	public RawFusekiRestMethods(JsonLDService jsonLDService,
    			ProcessorControllerFactory factory, @Named("r2r.fusekiUrl") String fusekiUrl) throws ParserConfigurationException, TransformerConfigurationException, TransformerFactoryConfigurationError {
    		super(jsonLDService, factory, fusekiUrl);
    	}
    }

    @Path("")
    public static class UiFusekiRestMethods extends FusekiRestMethods {

    	@Inject
    	public UiFusekiRestMethods(JsonLDService jsonLDService,
    			ProcessorControllerFactory factory, @Named("uiFusekiUrl") String fusekiUrl) throws ParserConfigurationException, TransformerConfigurationException, TransformerFactoryConfigurationError {
    		super(jsonLDService, factory, fusekiUrl);
    	}
    }
    
    private org.w3c.dom.Element getAuthor(org.w3c.dom.Document doc, Element tr) throws ParserConfigurationException, TransformerFactoryConfigurationError, TransformerException {
		org.w3c.dom.Element rootElement = doc.createElement("coauthor");
		
		if (!tr.select("a[href]").isEmpty()) {
			// position elements
			org.w3c.dom.Element position = doc.createElement("position");
			position.appendChild(doc.createTextNode(tr.select("td").get(0).text()));
			rootElement.appendChild(position);

			// firstname elements
			org.w3c.dom.Element firstname = doc.createElement("firstname");
			firstname.appendChild(doc.createTextNode(tr.select("td").get(2).text()));
			rootElement.appendChild(firstname);
			
			// lastname elements
			org.w3c.dom.Element lastname = doc.createElement("lastname");
			lastname.appendChild(doc.createTextNode(tr.select("td").get(1).text()));
			rootElement.appendChild(lastname);

			// URI
			org.w3c.dom.Element uri = doc.createElement("uri");
			uri.appendChild(doc.createTextNode(tr.select("td").get(3).text()));
			rootElement.appendChild(uri);
			//rootElement.appendChild(doc.createElement("firstname").appendChild(doc.createTextNode(tr.select(":nth-child(3)").first().text())));	
			//rootElement.appendChild(doc.createElement("lastname").appendChild(doc.createTextNode(tr.select(":nth-child(2)").first().text())));
			//rootElement.appendChild(doc.createElement("URI").appendChild(doc.createTextNode(tr.select("a[href]").first().attr("abs:href"))));
    	}

		return rootElement;
    }
    
    
    
}
