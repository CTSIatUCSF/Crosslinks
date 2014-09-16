package edu.ucsf.crosslink.web;

import java.io.ByteArrayOutputStream;
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

import org.json.JSONException;

import com.github.jsonldjava.core.JsonLdError;
import com.google.inject.Inject;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.sun.jersey.api.view.Viewable;

import edu.ucsf.crosslink.crawler.Affiliated;
import edu.ucsf.crosslink.crawler.Crawler;
import edu.ucsf.crosslink.crawler.CrawlerFactory;
import edu.ucsf.crosslink.job.quartz.MetaCrawlerJob;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.jena.JsonLDService;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;
import edu.ucsf.ctsi.r2r.jena.SparqlUpdateClient;


/**
 * Root resource (exposed at "list" path)
 */
@Path("")
public class FusekiRestMethods implements R2RConstants {

	private static final Logger LOG = Logger.getLogger(FusekiRestMethods.class.getName());
	
	private static final String ALL_AFFILIATIONS_SPARQL = "SELECT ?l ?a ?lat ?lon (count(?r) as ?rc) WHERE { ?a <" +
			RDF_TYPE + "> <" + R2R_AFFILIATION + "> . ?a <" + RDFS_LABEL +  "> ?l . ?a <" + 
			GEO_LATITUDE + "> ?lat . ?a <" + GEO_LONGITUDE + "> ?lon. ?r <" +
			R2R_HAS_AFFILIATION + "> ?a} GROUP BY ?l ?a ?lat ?lon";

	private static final String AFFILIATION_SPARQL = "SELECT ?a ?lat ?lon (count(?r) as ?rc) WHERE { ?a <" +
			RDF_TYPE + "> <" + R2R_AFFILIATION + "> . ?a <" + RDFS_LABEL +  "> ?l . FILTER (?l = \"%s\") . ?a <" + 
			GEO_LATITUDE + "> ?lat . ?a <" + GEO_LONGITUDE + "> ?lon . OPTIONAL {?r <" +
			R2R_HAS_AFFILIATION + "> ?a}} GROUP BY ?a ?lat ?lon";
	
	private static final String RESEARCHERS_SPARQL_SLOW = "SELECT ?r ?l ?hp ?i ?t (count(distinct ?er) as ?erc) (count(distinct ?cw) as ?cwc) WHERE {?r <" + 
			R2R_HAS_AFFILIATION + "> <%1$s>  . ?r <" + RDFS_LABEL + "> ?l . ?r <" + R2R_WORK_VERIFIED_DT + 
			"> ?wvdt . OPTIONAL {?r <" +  FOAF_HOMEPAGE + "> ?hp } . OPTIONAL {?r <" + FOAF_HAS_IMAGE + 
			"> ?i} . OPTIONAL {?i <" + FOAF_THUMBNAIL + "> ?t} . OPTIONAL {?r <" + 
			FOAF_PUBLICATIONS + "> ?cw  . ?er <" + FOAF_PUBLICATIONS + "> ?cw  . ?er <" + 
			R2R_HAS_AFFILIATION + "> ?ea FILTER (?ea != <%1$s>)}} GROUP BY ?r ?l ?hp ?i ?t";
	
	private static final String RESEARCHERS_SPARQL = "SELECT ?r ?l ?hp ?i ?erc ?cwc WHERE {?r <" + 
			R2R_HAS_AFFILIATION + "> <%1$s>  . ?r <" + RDFS_LABEL + "> ?l . ?r <" + R2R_WORK_VERIFIED_DT + 
			"> ?wvdt . OPTIONAL {?r <" +  FOAF_HOMEPAGE + "> ?hp } . OPTIONAL { GRAPH <" + 
			R2R_DERIVED_GRAPH +	"> {?r <" + FOAF_HAS_IMAGE +"> ?i}} . OPTIONAL { GRAPH <" + 
			R2R_DERIVED_GRAPH + "> {?r <" + R2R_EXTERNAL_COAUTHOR_CNT + "> ?erc}} . OPTIONAL { GRAPH <" + 
			R2R_DERIVED_GRAPH + "> {?r <" + R2R_SHARED_PUB_CNT + "> ?cwc}}}";
	
	private static final String COAUTHORS_WHERE = "WHERE {<%1$s> <" + R2R_HAS_AFFILIATION + "> ?a . <%1$s> <" +
			FOAF_PUBLICATIONS + "> ?cw  . ?r <" + FOAF_PUBLICATIONS + "> ?cw  . ?r <" + RDFS_LABEL + 
			"> ?rl . OPTIONAL {?r <" + FOAF_HOMEPAGE + "> ?hp } . OPTIONAL { GRAPH <" + R2R_DERIVED_GRAPH + 
			"> { ?r <" + FOAF_THUMBNAIL + "> ?tn} } . ?r <" + R2R_HAS_AFFILIATION + "> ?ea FILTER (?ea != ?a) . ?ea <" + 
			RDFS_LABEL + "> ?al . ?ea <" + GEO_LATITUDE + "> ?ealat . ?ea <" + GEO_LONGITUDE + "> ?ealon}";
			
	private static final String COAUTHORS_SELECT = "SELECT (?r as ?researcherURI) (?hp as ?researcherHomePage) (?rl as ?researcherLabel) (?cw as ?contributedWork) (?tn as ?thumbnail) (?ea as ?researchNetworkingSite) (?al as ?affiliation)" + COAUTHORS_WHERE;
	
	private static final String COAUTHORS_CONSTRUCT = "CONSTRUCT {?r <" + FOAF_PUBLICATIONS + "> ?cw . ?r <" +
			RDFS_LABEL + "> ?rl . ?r <" + FOAF_HOMEPAGE + "> ?hp . ?r <" + FOAF_HAS_IMAGE + "> ?tn . ?r  <" +
			R2R_HAS_AFFILIATION + "> ?ea . ?ea  <" + RDFS_LABEL + "> ?al . ?ea <" + GEO_LATITUDE + 
			"> ?ealat . ?ea <" + GEO_LONGITUDE + "> ?ealon} " + COAUTHORS_WHERE;

	private CrawlerFactory factory;
	private SparqlUpdateClient sparqlClient;
	private JsonLDService jsonLDService;
	
	@Inject
	public FusekiRestMethods(CrawlerFactory factory, SparqlUpdateClient fusekiClient, JsonLDService jsonLDService) {
		this.factory = factory;
		this.sparqlClient = fusekiClient;
		this.jsonLDService = jsonLDService;
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
		List<Crawler> crawlers = new ArrayList<Crawler>();
		crawlers.addAll(factory.getCurrentCrawlers());
		// sort them so that the active ones show up at the top
		Collections.sort(crawlers);
		request.setAttribute("crawlers", crawlers);
		request.setAttribute("metaHistory", MetaCrawlerJob.getMetaCrawlerHistory());
		return new Viewable("/jsps/status.jsp", null);
	}

	@GET
	@Path("{crawler}/status")
	public Viewable statusDetail(@PathParam("crawler") String crawlerName, @Context HttpServletRequest request,
			@Context HttpServletResponse response, @QueryParam("mode") String mode, @QueryParam("status") String status) throws Exception {
		Crawler crawler = factory.getCrawler(crawlerName);
		if (CrosslinksServletFilter.isAdministrator(request)) {
			if ("PAUSED".equalsIgnoreCase(status)) {
				crawler.pause();
			}
			if (mode != null) {
				crawler.setMode(mode);
			}
		}
		request.setAttribute("crawler", crawler);
		if (crawler instanceof Affiliated) {
			request.setAttribute("affiliation", ((Affiliated)crawler).getAffiliation());			
		}
		return new Viewable("/jsps/statusDetail.jsp", null);
	}

	@GET
    @Path("{affiliation}")
    public Viewable getAffiliationDetail(@PathParam("affiliation") String affiliation, @Context HttpServletRequest request,
			@Context HttpServletResponse response) {
		request.setAttribute("affiliation", getAffiliation(affiliation));
		return new Viewable("/jsps/affiliation.jsp", null);
    }

    @GET
    @Path("{affiliation}/researchers")
    public Viewable getResearchers(@PathParam("affiliation") String affiliationStr, @Context HttpServletRequest request,
			@Context HttpServletResponse response, @QueryParam("clearCache") String clearCache) {
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
    @Path("{affiliation}/possibleConflicts")
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    public String getConflicts(@PathParam("affiliation") String affiliation, @QueryParam("format") String format) {
		String sql = "select * from vw_ConflictList where affiliationName = ? " + 
					 "order by URL, affiliationName2, URL2";
		return "";//getSimpleResults(sql, affiliation, format);
    }

    @GET
    @Path("{affiliation}/possibleSamePeople")
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    public String getSamePeople(@PathParam("affiliation") String affiliation, @QueryParam("format") String format) {
		String sql = "select * from [vw_SamePersonList] where affiliationName = ? " + 
					 "order by URL, affiliationName2, URL2";
		return "";//getSimpleResults(sql, affiliation, format);
    }

    @GET
    @Path("coauthors")
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    public String getCoauthors(@QueryParam("researcherURI") String researcherURI, @QueryParam("format") String format) throws JSONException, JsonLdError {
		if ("JSON-LD".equals(format)) {
			return jsonLDService.getJSONString(sparqlClient.construct(String.format(COAUTHORS_CONSTRUCT, researcherURI)));
		}
		return getFormattedResults(String.format(COAUTHORS_SELECT, researcherURI), format).toString();
    }
    
    private ByteArrayOutputStream getFormattedResults(final String sparql, final String format) {
    	final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    	sparqlClient.select(sparql, new ResultSetConsumer() {
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
    
    private List<Affiliation> getAffiliations() {
    	final List<Affiliation> affiliations = new ArrayList<Affiliation>();
    	sparqlClient.select(ALL_AFFILIATIONS_SPARQL, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					Affiliation affiliationObj;
					try {
						affiliationObj = new Affiliation(qs.getLiteral("?l").getString(), qs.getResource("?a").getURI(),
								qs.getLiteral("?lat").getString() + "," + qs.getLiteral("?lon").getString(),
								qs.getLiteral("?rc").getInt(), 0);
						affiliations.add(affiliationObj);
					} 
					catch (URISyntaxException e) {
						LOG.log(Level.WARNING, e.getMessage(), e);
					}
				}								
			}
		});
        return affiliations;
    }
    
    public Affiliation getAffiliation(final String affiliation) {
    	String sparql = String.format(AFFILIATION_SPARQL, affiliation);
    	final List<Affiliation> affiliations = new ArrayList<Affiliation>();
    	sparqlClient.select(sparql, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				if (rs.hasNext()) {		
					QuerySolution qs = rs.next();
					Affiliation affiliationObj;
					try {
						affiliationObj = new Affiliation(affiliation, qs.getResource("?a").getURI(),
								qs.getLiteral("?lat").getString() + "," + qs.getLiteral("?lon").getString(),
								qs.getLiteral("?rc").getInt(), 0);
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
    
    public List<Researcher> getResearchers(final Affiliation affiliation) {
		String sparql = String.format(RESEARCHERS_SPARQL, affiliation.getURI()); 
    	final List<Researcher> researchers = new ArrayList<Researcher>();
    	sparqlClient.select(sparql, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					if (qs.getResource("?r") != null) {
						try {
							researchers.add(new Researcher(qs.getResource("?r").getURI(), affiliation,
													qs.getLiteral("?l").getString(),
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
}
