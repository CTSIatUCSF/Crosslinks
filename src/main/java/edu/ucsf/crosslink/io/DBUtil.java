package edu.ucsf.crosslink.io;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

import com.thoughtworks.xstream.XStream;

import edu.ucsf.crosslink.author.Author;

public class DBUtil implements CrosslinkPersistance {
	
	private static String dbUrl = "jdbc:sqlserver://stage-sql-ctsi.ucsf.edu;instanceName=default;portNumber=1433;databaseName=crosslink";
	private static String dbUser = "crosslink";
	private static String dbPassword = "crosslink";

	private static final Logger LOG = Logger.getLogger(DBUtil.class.getName());
	
	private String affiliationName;
	
	static {
		try { 
			Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
    public static Connection getConnection() {
        try {
            Connection conn = DriverManager.getConnection(dbUrl, dbUser,
                    dbPassword);
            return conn;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
    
	public void start(String affiliationName) throws Exception {
		this.affiliationName = affiliationName;
		Connection conn = getConnection();
		try {
	        CallableStatement cs = conn
			        .prepareCall("{ call [StartCrawl](?)}");
	        cs.setString(1, affiliationName);
	        ResultSet rs = cs.executeQuery();
	        Integer affiliationId = null;
	        if (rs.next()) {
	        	affiliationId = rs.getInt(1);
	        	LOG.info("Starting affiliation " + affiliationName + " affiliationId = " + affiliationId);
	        }
	        if (affiliationId == null) {
	        	throw new Exception("Affiliation " + affiliationName + " not found in the database, shutting down!");
	        }
	        
		} 
		finally {
			conn.close();
		}
	}    
    
	public void saveAuthor(Author author) throws Exception {
		// check DB
		Connection conn = getConnection();
		XStream xstream = new XStream();
		try {
	        CallableStatement cs = conn
			        .prepareCall("{ call [UpsertAuthor](?, ?, ?, ?, ?, ?, ?, ?)}");
	        cs.setString(1, author.getAffiliation());
	        cs.setString(2, author.getLastName());
	        cs.setString(3, author.getFirstName());
	        cs.setString(4, author.getMiddleName());
	        cs.setString(5, author.getURL());
	        cs.setString(6, author.getLodURI());
	        cs.setString(7, author.getOrcidId());
	        cs.setString(8, xstream.toXML(author.getPubMedPublications()));
	        ResultSet rs = cs.executeQuery();
	        if (rs.next()) {
	        	LOG.info("Saved authorshipId = " + rs.getInt(1));
	        }
		} 
		finally {
			conn.close();
		}
	}

	public boolean skipAuthor(String url) {
		return false;
	}

	public void close() throws Exception {
		Connection conn = getConnection();
		try {
	        CallableStatement cs = conn
			        .prepareCall("{ call [EndCrawl](?)}");
	        cs.setString(1, affiliationName);
	        ResultSet rs = cs.executeQuery();
	        Integer affiliationId = null;
	        if (rs.next()) {
	        	affiliationId = rs.getInt(1);
	        	LOG.info("Stopping affiliation " + affiliationName + " affiliationId = " + affiliationId);
	        }
	        if (affiliationId == null) {
	        	throw new Exception("Affiliation " + affiliationName + " not found in the database, shutting down!");
	        }
	        
		} 
		finally {
			conn.close();
		}
	}
	
	public static void main(String[] args) {
		try {
			XStream xstream = new XStream();
			Collection<Integer> pmids = new ArrayList<Integer>();
			pmids.add(123);
			pmids.add(456);
			System.out.println(xstream.toXML(pmids));
			
			Author author = new Author("UCSF", "nobody", "nobody", null, "http://stage-profiles.ucsf.edu/profiles200/nobody.nobody", null, null);
			author.addPubMedPublication(123);
			author.addPubMedPublication(456);
			
			DBUtil db = new DBUtil();
			db.saveAuthor(author);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
