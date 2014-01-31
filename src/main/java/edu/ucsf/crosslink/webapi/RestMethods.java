package edu.ucsf.crosslink.webapi;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import edu.ucsf.crosslink.io.DBUtil;

import au.com.bytecode.opencsv.CSVWriter;


/**
 * Root resource (exposed at "list" path)
 */
@Path("")
public class RestMethods {

	private static final Logger LOG = Logger.getLogger(RestMethods.class.getName());
	
	private static final String WEB_ROOT = "/crosslinks/"; // till I find a better way

	private static final String HTML_START = "<html><body>";
	private static final String HTML_END = "</body></html>";
	private static final String NOTES = "<p>Note that matches for possibleSamePeople and possibleConflicts are based on name and matching publications.  If a person at one affiliation has the same" +
								  "last name and shares overlapping publications with a research at another affiliation with a " +  
								  "'similar' first name, they are included in the possibleSamePeople list.  If the first name is " + 
								  "'not similar', then we list them in possibleConflicts. <p>" + 
								  "By 'similar', we mean: <p>" +
								  "<code>(len(a1.firstName)< LEN(a2.firstName) AND LEFT(a1.firstName, len(a1.firstName)) = LEFT(a2.firstName, len(a1.firstName))) OR " +
								  "(LEFT(a1.firstName, len(a2.firstName)) = LEFT(a2.firstName, len(a2.firstName))) </code><p>" +      
								  "By 'not similar', we mean the opposite:<p>" +
								  "<code>(len(a1.firstName)< LEN(a2.firstName) AND LEFT(a1.firstName, len(a1.firstName)) != LEFT(a2.firstName, len(a1.firstName))) AND " +								  
								  "(LEFT(a1.firstName, len(a2.firstName)) != LEFT(a2.firstName, len(a2.firstName))) </code><p>" + 
								  "At some point we want to formally recognize when someone at one affiliation is the same person at another affiliation, and we will " + 
								  "make that available when we have that data.<p>" +
								  "Please note that the list of coauthors WILL include any possibleSamePeople and possibleConflicts.";					     
    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String getAffiliations() {
		String sql = "select affiliation, researcherCount, PMIDCount from vw_AffiliationList";
		Connection conn = DBUtil.getConnection();
		try {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println(HTML_START);
			pw.println("<h2>Research Networking Coauthors Unbound</h2><p>");
			pw.println("<h3>List of indexed affiliations. Click and have fun!</h3><p>");
			PreparedStatement ps = conn.prepareStatement(sql);
			ResultSet rs = ps.executeQuery();
			pw.println("<ul>");
			while (rs.next()) {
				pw.println("<li>");				
				pw.print("<a href = '" + WEB_ROOT + rs.getString(1) + "'>" + rs.getString(1) + "</a>");
				pw.println(" " + rs.getInt(2) + " indexed researchers and " + rs.getInt(3) + " PMID publications");
				pw.println("</li>");				
			}
			pw.println("</ul>");
			pw.println("If you would like to find out more about this, please contact <a href='http://profiles.ucsf.edu/eric.meeks'>Eric Meeks</a><p>");
			pw.println(HTML_END);
			pw.flush();
			return sw.toString();
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

    @GET
    @Path("{affiliation}")
    @Produces(MediaType.TEXT_HTML)
    public String getAffiliationDetail(@PathParam("affiliation") String affiliation) {
		String sql = "select affiliation, baseURL, researcherCount, PMIDCount from vw_AffiliationList where affiliation = ?";
		Connection conn = DBUtil.getConnection();
		try {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println(HTML_START);
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, affiliation);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				pw.println("<a href = '" + WEB_ROOT + rs.getString(2) + "'>" + rs.getString(1) + " Research Networking Site</a> " + 
						rs.getString(3) + " researchers indexed, " + rs.getString(4) + " PUBMED publications found</p>");
				pw.println("<a href = '" + WEB_ROOT + rs.getString(1) + "/researchers'>Indexed researchers from " + rs.getString(1) + "</a><p>"); 
				pw.println("<p>Links to help us clean up our data.  Once our data is all clean, these should not return any results.  Today, they return a bunch of results for most affiliations.<p>");				
				pw.println("<a href = '" + WEB_ROOT + rs.getString(1) + "/possibleSamePeople'>List of researchers at other affiliations that we think are also in " + rs.getString(1) + " (CSV Format)</a><p>"); 
				pw.println("<a href = '" + WEB_ROOT + rs.getString(1) + "/possibleConflicts'>List of potential disambiguation conflicts for " + rs.getString(1) + " (CSV format)</a><p>"); 
			}
			pw.println(NOTES);
			pw.println(HTML_END);
			pw.flush();
			return sw.toString();
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

    @GET
    @Path("{affiliation}/researchers")
    @Produces(MediaType.TEXT_HTML)
    public String getResearchers(@PathParam("affiliation") String affiliation) {
		String sql = "select affiliationName , LastName , FirstName , MiddleName , URL, orcidId, externalCoauthorCount from vw_ResearcherList where affiliationName = ?";
		Connection conn = DBUtil.getConnection();
		try {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println(HTML_START);
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, affiliation);
			ResultSet rs = ps.executeQuery();
			pw.println("<ul>");
			while (rs.next()) {
				pw.println("<li>");				
				String name = rs.getString(2) + ", " + rs.getString(3) + (rs.getString(4) != null ? " " + rs.getString(4) : "");
				pw.println("<a href = '" + rs.getString(5) + "'>" + name + " at " + rs.getString(1) + "</a>&nbsp");
				if (rs.getString(6) != null && !rs.getString(6).trim().isEmpty()) {
					pw.println("<a href = 'http://orcid.org/" + rs.getString(6) + "'>ORCID Profile for " + name + " from " + rs.getString(1) + "</a>&nbsp");
				}
				if (rs.getInt(7) > 0) {
					pw.println("<a href = '" + WEB_ROOT + "coauthors?authorURL=" + rs.getString(5) + "'>List of " + rs.getInt(7) + " external coauthors and PMID's for " + name + " (CSV Format)</a>&nbsp");
				}
				pw.println("</li>");				
			}
			pw.println("</ul>");
			pw.println(HTML_END);
			pw.flush();
			return sw.toString();
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

    @GET
    @Path("{affiliation}/possibleConflicts")
    @Produces(MediaType.TEXT_PLAIN)
    public String getConflicts(@PathParam("affiliation") String affiliation) {
		String sql = "select * from vw_ConflictList where affiliationName = ? " + 
					 "order by URL, affiliationName2, URL2";
		Connection conn = DBUtil.getConnection();
		try {
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, affiliation);
			StringWriter sw = new StringWriter();
			CSVWriter writer = new CSVWriter(sw);
			writer.writeAll(ps.executeQuery(), true);
			writer.close();
			return sw.toString();
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

    @GET
    @Path("{affiliation}/possibleSamePeople")
    @Produces(MediaType.TEXT_PLAIN)
    public String getSamePeople(@PathParam("affiliation") String affiliation) {
		String sql = "select * from [vw_SamePersonList] where affiliationName = ? " + 
					 "order by URL, affiliationName2, URL2";
		Connection conn = DBUtil.getConnection();
		try {
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, affiliation);
			StringWriter sw = new StringWriter();
			CSVWriter writer = new CSVWriter(sw);
			writer.writeAll(ps.executeQuery(), true);
			writer.close();
			return sw.toString();
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

    @GET
    @Path("coauthors")
    @Produces(MediaType.TEXT_PLAIN)
    public String getCoauthors(@QueryParam("authorURL") String authorURL) {
		String sql = "select affiliationName, lastName, firstName, middleName, URL, orcidId, PMID from vw_ExternalCoauthorList where subjectURL = ? " + 
					 "order by affiliationName, URL";
		Connection conn = DBUtil.getConnection();
		try {
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, authorURL);
			StringWriter sw = new StringWriter();
			CSVWriter writer = new CSVWriter(sw);
			writer.writeAll(ps.executeQuery(), true);
			writer.close();
			return sw.toString();
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
}
