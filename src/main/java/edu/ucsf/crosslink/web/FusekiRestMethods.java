package edu.ucsf.crosslink.web;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

import edu.ucsf.crosslink.crawler.AffiliationCrawler;
import edu.ucsf.crosslink.crawler.AffiliationCrawlerFactory;
import edu.ucsf.crosslink.job.quartz.MetaCrawlerJob;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.DBUtil;
import edu.ucsf.ctsi.r2r.R2RConstants;
import edu.ucsf.ctsi.r2r.jena.FusekiClient;
import edu.ucsf.ctsi.r2r.jena.JsonLDService;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;


/**
 * Root resource (exposed at "list" path)
 */
@Path("")
public class FusekiRestMethods implements R2RConstants {

	private static final Logger LOG = Logger.getLogger(FusekiRestMethods.class.getName());
	
	private static final String ALL_AFFILIATIONS_SPARQL = "SELECT ?l ?a ?lat ?lon (count(?r) as ?rc) WHERE { ?a <" +
			RDF_TYPE + "> <" + R2R_RN_WEBSITE + "> . ?a <" + RDFS_LABEL +  "> ?l . ?a <" + 
			PRNS_LATITUDE + "> ?lat . ?a <" + PRNS_LONGITUDE + "> ?lon. ?r <" +
			R2R_FROM_RN_WEBSITE + "> ?a} GROUP BY ?l ?a ?lat ?lon";

	private static final String AFFILIATION_SPARQL = "SELECT ?a ?lat ?lon (count(?r) as ?rc) WHERE { ?a <" +
			RDF_TYPE + "> <" + R2R_RN_WEBSITE + "> . ?a <" + RDFS_LABEL +  "> ?l . FILTER (?l = \"%s\") . ?a <" + 
			PRNS_LATITUDE + "> ?lat . ?a <" + PRNS_LONGITUDE + "> ?lon . ?r <" +
			R2R_FROM_RN_WEBSITE + "> ?a} GROUP BY ?a ?lat ?lon";
	
	private static final String RESEARCHERS_SPARQL = "SELECT ?hp ?r ?l ?i ?t ?o (count(distinct ?er) as ?erc) (count(distinct ?cw) as ?cwc) WHERE {?r <" + 
			R2R_FROM_RN_WEBSITE + "> <%1$s>  . ?r <" + R2R_HOMEPAGE_PATH + "> ?hp .?r <" + RDFS_LABEL + "> ?l . OPTIONAL {?r <" +
			PRNS_MAIN_IMAGE + "> ?i} . OPTIONAL {?r <" + R2R_THUMBNAIL + "> ?t} . OPTIONAL {?r <" + VIVO_ORCID_ID + "> ?o} . OPTIONAL {?r <" + 
			R2R_CONTRIBUTED_TO + "> ?cw  . ?er <" + R2R_CONTRIBUTED_TO + "> ?cw  . ?er <" + R2R_FROM_RN_WEBSITE + 
			"> ?ea FILTER (?ea != <%1$s>)}} GROUP BY ?hp ?r ?l ?i ?t ?o";
	
	private static final String COAUTHORS_WHERE = "WHERE {<%1$s> <" + R2R_FROM_RN_WEBSITE + "> ?a . <%1$s> <" +
			R2R_CONTRIBUTED_TO + "> ?cw  . ?r <" + R2R_CONTRIBUTED_TO + "> ?cw  . ?r <" + RDFS_LABEL + "> ?rl . OPTIONAL {?r <" +
			PRNS_MAIN_IMAGE + "> ?mi} . OPTIONAL {?r <" + R2R_THUMBNAIL + "> ?tn} . OPTIONAL {?r <" + VIVO_ORCID_ID + "> ?oi} . ?r <" +
			R2R_FROM_RN_WEBSITE + "> ?ea FILTER (?ea != ?a) . ?ea <" + RDFS_LABEL + "> ?al . ?ea <" + PRNS_LATITUDE + 
			"> ?ealat . ?ea <" + PRNS_LONGITUDE + "> ?ealon}";
			
	private static final String COAUTHORS_SELECT = "SELECT (?r as ?researcherURI) (?rl as ?researcherLabel) (?cw as ?contributedWork) (?mi as ?mainImage) (?tn as ?thumbnail) (?oi as ?orchidId) (?ea as ?researchNetworkingSite) (?al as ?affiliation)" + COAUTHORS_WHERE;
	
	private static final String COAUTHORS_CONSTRUCT = "CONSTRUCT {?r <" + R2R_CONTRIBUTED_TO + "> ?cw . ?r <" +
			RDFS_LABEL + "> ?rl . ?r <" + PRNS_MAIN_IMAGE + "> ?mi . ?r <" + R2R_THUMBNAIL + "> ?tn. ?r <" + VIVO_ORCID_ID + "> ?oi . ?r  <" +
			R2R_FROM_RN_WEBSITE + "> ?ea . ?ea  <" + RDFS_LABEL + "> ?al . ?ea <" + PRNS_LATITUDE + 
			"> ?ealat . ?ea <" + PRNS_LONGITUDE + "> ?ealon} " + COAUTHORS_WHERE;

	private AffiliationCrawlerFactory factory;
	private FusekiClient fusekiClient;
	private JsonLDService jsonLDService;
	
	@Inject
	public FusekiRestMethods(DBUtil dbUtil, AffiliationCrawlerFactory factory, FusekiClient fusekiClient, JsonLDService jsonLDService) {
		this.factory = factory;
		this.fusekiClient = fusekiClient;
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
		List<AffiliationCrawler> crawlers = new ArrayList<AffiliationCrawler>();
		crawlers.addAll(factory.getCurrentCrawlers());
		// sort them so that the active ones show up at the top
		Collections.sort(crawlers);
		request.setAttribute("crawlers", crawlers);
		request.setAttribute("metaHistory", MetaCrawlerJob.getMetaCrawlerHistory());
		return new Viewable("/jsps/status.jsp", null);
	}

	@GET
	@Path("{affiliation}/status")
	public Viewable statusDetail(@PathParam("affiliation") String affiliation, @Context HttpServletRequest request,
			@Context HttpServletResponse response, @QueryParam("mode") String mode) throws Exception {
		AffiliationCrawler crawler = factory.getCrawler(affiliation);
		if (CrosslinksServletFilter.isAdministrator(request) && mode != null) {
			crawler.setMode(mode);
		}
		request.setAttribute("crawler", crawler);
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
			return jsonLDService.getJSONString(fusekiClient.construct(String.format(COAUTHORS_CONSTRUCT, researcherURI)));
		}
		return getFormattedResults(String.format(COAUTHORS_SELECT, researcherURI), format).toString();
    }
    
    private ByteArrayOutputStream getFormattedResults(final String sparql, final String format) {
    	final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    	fusekiClient.select(sparql, new ResultSetConsumer() {
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
    	fusekiClient.select(ALL_AFFILIATIONS_SPARQL, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					Affiliation affiliationObj =  new Affiliation(qs.getLiteral("?l").getString(), qs.getResource("?a").getURI(),
							qs.getLiteral("?lat").getString() + "," + qs.getLiteral("?lon").getString(),
							qs.getLiteral("?rc").getInt(), 0);
					affiliations.add(affiliationObj);
				}								
			}
		});
        return affiliations;
    }
    
    public Affiliation getAffiliation(final String affiliation) {
    	String sparql = String.format(AFFILIATION_SPARQL, affiliation);
    	final List<Affiliation> affiliations = new ArrayList<Affiliation>();
    	fusekiClient.select(sparql, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				if (rs.hasNext()) {		
					QuerySolution qs = rs.next();
					Affiliation affiliationObj =  new Affiliation(affiliation, qs.getResource("?a").getURI(),
							qs.getLiteral("?lat").getString() + "," + qs.getLiteral("?lon").getString(),
							qs.getLiteral("?rc").getInt(), 0);
					affiliations.add(affiliationObj);
				}
		    }
		});
    	return affiliations.size() > 0 ? affiliations.get(0) : null;
    }
    
    public List<Researcher> getResearchers(final Affiliation affiliation) {
		String sparql = String.format(RESEARCHERS_SPARQL, affiliation.getURI()); 
    	final List<Researcher> researchers = new ArrayList<Researcher>();
    	fusekiClient.select(sparql, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext()) {				
					QuerySolution qs = rs.next();
					researchers.add(new Researcher(affiliation, qs.getLiteral("?hp").getString(),
												   qs.getResource("?r").getURI(), 
												   qs.getLiteral("?l").getString(),
												   qs.get("?i") != null ? (qs.get("?i").isLiteral() ? qs.getLiteral("?i").getString() : qs.getResource("?i").getURI()): null,
												   qs.get("?t") != null ? qs.getLiteral("?t").getString() : null,
												   qs.get("?o") != null ? qs.getLiteral("?o").getString() : null,
												   qs.getLiteral("?erc").getInt(),
												   qs.getLiteral("?cwc").getInt()));
				}								
			}
		});
		Collections.sort(researchers);
		return researchers;
    }
}
