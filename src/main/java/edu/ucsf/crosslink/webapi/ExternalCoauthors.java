package edu.ucsf.crosslink.webapi;

import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import au.com.bytecode.opencsv.CSVWriter;

/**
 * Root resource (exposed at "list" path)
 */
@Path("coauthors")
public class ExternalCoauthors {

	private static final Logger LOG = Logger.getLogger(ExternalCoauthors.class.getName());

    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getCoauthors(@QueryParam("authorURL") String authorURL) {
		String sql = "select affiliationName, lastName, firstName, middleName, URL, PMID from vw_ExternalCoauthorList where subjectURL = ? " + 
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
