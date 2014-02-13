package edu.ucsf.crosslink.web;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
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

import com.google.gson.stream.JsonWriter;
import com.google.inject.Inject;
import com.sun.jersey.api.view.Viewable;

import edu.ucsf.crosslink.crawler.AffiliationCrawler;
import edu.ucsf.crosslink.io.DBUtil;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
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
    public Viewable getResearchers(@PathParam("affiliation") String affiliationStr, @Context HttpServletRequest request,
			@Context HttpServletResponse response) {
    	Affiliation affiliation = getAffiliation(affiliationStr);
		request.setAttribute("affiliation", affiliation);
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
		String sql = "select affiliationName, lastName, firstName, middleName, URL, imageURL, orcidId, PMID from vw_ExternalCoauthorList where subjectURL = ? " + 
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
		     if (rsmd.getColumnType(idx) == Types.INTEGER) {
		    	 writer.value(rs.getInt(idx));	
		     }
		     else {
		    	 writer.value(rs.getString(idx));
		     }
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

    private List<Affiliation> getAffiliations() {
    	List<Affiliation> affiliations = new ArrayList<Affiliation>();
		String sql = "select affiliation, baseURL, researcherCount, PMIDCount from vw_AffiliationList";
		Connection conn = dbUtil.getConnection();
		try {
			PreparedStatement ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				affiliations.add(new Affiliation(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4)));
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
    
    public Affiliation getAffiliation(String affiliation) {
		String sql = "select affiliation, baseURL, researcherCount, PMIDCount from vw_AffiliationList where affiliation = ?";
		Connection conn = dbUtil.getConnection();
		try {
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, affiliation);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				return new Affiliation(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4));
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
		return null;
    }
    
    public List<Researcher> getResearchers(Affiliation affiliation) {
		String sql = "select LastName, FirstName , MiddleName , URL, imageURL, thumbnailURL, orcidId, externalCoauthorCount from vw_ResearcherList where affiliationName = ?";
    	List<Researcher> researchers = new ArrayList<Researcher>();
		Connection conn = dbUtil.getConnection();
		try {
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, affiliation.getName());
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				researchers.add( new Researcher(affiliation, rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getInt(8)) );
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
		Collections.sort(researchers);
		return researchers;
    }
}
