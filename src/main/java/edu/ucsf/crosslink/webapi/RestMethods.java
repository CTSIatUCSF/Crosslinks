package edu.ucsf.crosslink.webapi;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
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

import com.google.gson.stream.JsonWriter;
import com.google.inject.Inject;
import com.sun.jersey.api.view.Viewable;

import edu.ucsf.crosslink.AffiliationCrawler;
import edu.ucsf.crosslink.io.DBUtil;
import edu.ucsf.crosslink.quartz.AffiliationCrawlerJob;

import au.com.bytecode.opencsv.CSVWriter;


/**
 * Root resource (exposed at "list" path)
 */
@Path("")
public class RestMethods {

	private static final Logger LOG = Logger.getLogger(RestMethods.class.getName());
	
	private DBUtil dbUtil; 
	
	@Inject
	public RestMethods(DBUtil dbUtil) {
		this.dbUtil = dbUtil;
	}

	@GET
	@Path("/")
	public Viewable index(@Context HttpServletRequest request,
			@Context HttpServletResponse response) throws Exception {
		request.setAttribute("affiliations", getAffiliations());
		return new Viewable("/jsps/index.jsp", null);
	}
	
	@GET
	@Path("/status")
	public Viewable status(@Context HttpServletRequest request,
			@Context HttpServletResponse response) throws Exception {
		request.setAttribute("crawlers", AffiliationCrawler.getLiveCrawlers());
		request.setAttribute("history", AffiliationCrawlerJob.getCrawlerJobHistory());
		return new Viewable("/jsps/status.jsp", null);
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
    public Viewable getResearchers(@PathParam("affiliation") String affiliation, @Context HttpServletRequest request,
			@Context HttpServletResponse response) {
		request.setAttribute("affiliation", getAffiliation(affiliation));
		request.setAttribute("researchers", getResearchers(affiliation));
		return new Viewable("/jsps/researchers.jsp", null);
    }

    @GET
    @Path("{affiliation}/possibleConflicts")
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    public String getConflicts(@PathParam("affiliation") String affiliation, @QueryParam("format") String format) {
		String sql = "select * from vw_ConflictList where affiliationName = ? " + 
					 "order by URL, affiliationName2, URL2";
		return getSimpleResults(sql, affiliation, format);
    }

    @GET
    @Path("{affiliation}/possibleSamePeople")
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    public String getSamePeople(@PathParam("affiliation") String affiliation, @QueryParam("format") String format) {
		String sql = "select * from [vw_SamePersonList] where affiliationName = ? " + 
					 "order by URL, affiliationName2, URL2";
		return getSimpleResults(sql, affiliation, format);
    }

    @GET
    @Path("coauthors")
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    public String getCoauthors(@QueryParam("authorURL") String authorURL, @QueryParam("format") String format) {
		String sql = "select affiliationName, lastName, firstName, middleName, URL, orcidId, PMID from vw_ExternalCoauthorList where subjectURL = ? " + 
					 "order by affiliationName, URL";
		return getSimpleResults(sql, authorURL, format);
    }
    
    // expand as needed, this simple two arg is good for now
    private String getSimpleResults(String sql, String param, String format) {
		Connection conn = dbUtil.getConnection();
		try {
			PreparedStatement ps = conn.prepareStatement(sql);
			if (param != null) {
				ps.setString(1, param);
			}
			if ("JSON".equalsIgnoreCase(format)) {
				return getAsJSON(ps.executeQuery());
			}
			else {
				return getAsCSV(ps.executeQuery());
			}
		}
		catch (Exception se) {
	        LOG.log(Level.SEVERE, "Error reading ", se);
		}
		finally {
			try { conn.close(); } catch (SQLException se) {
		        LOG.log(Level.SEVERE, "Error closing connection", se);
			}
		}
        return "Error";
    }
    
    private String getAsJSON(ResultSet rs) throws SQLException, IOException {
		StringWriter sw = new StringWriter();
		JsonWriter writer = new JsonWriter(sw);
		ResultSetMetaData rsmd = rs.getMetaData();
		writer.beginArray();
		while (rs.next()) {
		   writer.beginObject();	
		   // loop rs.getResultSetMetadata columns
		   for (int idx = 1; idx <= rsmd.getColumnCount(); idx++) {
		     writer.name(rsmd.getColumnLabel(idx)); // write key:value pairs
		     writer.value(rs.getString(idx));
		   }
		   writer.endObject();
		}    
		writer.endArray();
		writer.flush();
		return sw.toString();
    }

    private String getAsCSV(ResultSet rs) throws SQLException, IOException  {
		StringWriter sw = new StringWriter();
		CSVWriter writer = new CSVWriter(sw);
		writer.writeAll(rs, true);
		writer.close();
		return sw.toString();
    }

    private List<AffiliationData> getAffiliations() {
    	List<AffiliationData> affiliations = new ArrayList<AffiliationData>();
		String sql = "select affiliation, baseURL, researcherCount, PMIDCount from vw_AffiliationList";
		Connection conn = dbUtil.getConnection();
		try {
			PreparedStatement ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				AffiliationData data = new AffiliationData();
				data.affiliationName = rs.getString(1);
				data.baseURL = rs.getString(2);
				data.researcherCount = rs.getInt(3);
				data.pmidCount = rs.getInt(4);
				affiliations.add(data);
			}
		}
		catch (Exception se) {
	        LOG.log(Level.SEVERE, "Error reading ", se);
		}
		finally {
			try { conn.close(); } catch (SQLException se) {
		        LOG.log(Level.SEVERE, "Error closing connection", se);
			}
		}
        return affiliations;
    }
    
    public AffiliationData getAffiliation(String affiliation) {
		String sql = "select affiliation, baseURL, researcherCount, PMIDCount from vw_AffiliationList where affiliation = ?";
		AffiliationData data = new AffiliationData();
		Connection conn = dbUtil.getConnection();
		try {
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, affiliation);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				data.affiliationName = rs.getString(1);
				data.baseURL = rs.getString(2);
				data.researcherCount = rs.getInt(3);
				data.pmidCount = rs.getInt(4);
			}
		}
		catch (Exception se) {
	        LOG.log(Level.SEVERE, "Error reading ", se);
		}
		finally {
			try { conn.close(); } catch (SQLException se) {
		        LOG.log(Level.SEVERE, "Error closing connection", se);
			}
		}
		return data;
    }
    
    public List<ResearcherData> getResearchers(String affiliation) {
		String sql = "select LastName , FirstName , MiddleName , URL, orcidId, externalCoauthorCount from vw_ResearcherList where affiliationName = ?";
    	List<ResearcherData> researchers = new ArrayList<ResearcherData>();
		Connection conn = dbUtil.getConnection();
		try {
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, affiliation);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				ResearcherData data = new ResearcherData();
				data.lastName = rs.getString(1);
				data.firstName = rs.getString(2);
				data.middleName = rs.getString(3);
				data.URL = rs.getString(4);
				data.orcidId = rs.getString(5);
				data.externalCoauthorCount = rs.getInt(6);
				researchers.add(data);
			}
		}
		catch (Exception se) {
	        LOG.log(Level.SEVERE, "Error reading ", se);
		}
		finally {
			try { conn.close(); } catch (SQLException se) {
		        LOG.log(Level.SEVERE, "Error closing connection", se);
			}
		}
		return researchers;
    }

    public class AffiliationData {

    	String affiliationName;
    	String baseURL;
    	int researcherCount;
    	int pmidCount;

    	public String getAffiliationName() {
    		return affiliationName;
    	}
    	
    	public String getBaseURL() {
    		return baseURL;
    	}

    	public int getResearcherCount() {
    		return researcherCount;
    	}
    	
    	public int getPmidCount() {
    		return pmidCount;
    	}
    }
    
    public class ResearcherData {
    	String lastName;
    	String firstName;
    	String middleName;
    	String URL;
    	String orcidId;
    	int externalCoauthorCount;
    	
		public String getName() {
			return lastName + ", " + firstName + (middleName != null ? " " + middleName : "");
		}
		
		public String getLastName() {
			return lastName;
		}
		
		public String getFirstName() {
			return firstName;
		}
		
		public String getMiddleName() {
			return middleName;
		}
		
		public String getURL() {
			return URL;
		}
		
		public String getOrcidId() {
			return orcidId;
		}
		
		public int getExternalCoauthorCount() {
			return externalCoauthorCount;
		}    	    	
    }
}
