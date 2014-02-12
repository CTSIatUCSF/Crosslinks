package edu.ucsf.crosslink.io;

import java.io.File;
import java.net.URL;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.coobird.thumbnailator.Thumbnails;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.thoughtworks.xstream.XStream;

import edu.ucsf.crosslink.author.Author;

public class DBAuthorPersistance implements CrosslinkPersistance {
	
	private static final Logger LOG = Logger.getLogger(DBAuthorPersistance.class.getName());
	
	private String affiliationName;
	private String baseURL;
	private int daysConsideredOld;
	private DBUtil dbUtil;
	private String thumbnailDir;
	private Set<String> recentIndexedAuthors = new HashSet<String>();
	private Set<String> currentIndexedAuthors = new HashSet<String>();
	
	@Inject
	public DBAuthorPersistance(@Named("Affiliation") String affiliationName, @Named("BaseURL") String baseURL, 
			@Named("daysConsideredOld") Integer daysConsideredOld, @Named("thumbnailDir") String thumbnailDir, DBUtil dbUtil) {
		this.affiliationName = affiliationName;
		this.baseURL = baseURL;
		this.daysConsideredOld = daysConsideredOld;
		this.thumbnailDir = thumbnailDir;
		this.dbUtil = dbUtil;
	}
	
	public Date dateOfLastCrawl() {
		Connection conn = dbUtil.getConnection();
		try {
			String sql = "select lastIndexEndDT from Affiliation where affiliationName = ?";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, affiliationName);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				// return the last time finished.  This only works because we assume only ONE CrosslinksRunner is running at a time!
				return rs.getDate(1); 
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
	
	
	public void start() throws Exception {
		Connection conn = dbUtil.getConnection();
		try {
	        CallableStatement cs = conn
			        .prepareCall("{ call [StartCrawl](?, ?, ?)}");
	        cs.setString(1, affiliationName);
	        cs.setString(2, baseURL);
	        cs.setInt(3, daysConsideredOld);
	        ResultSet rs = cs.executeQuery();
	        while (rs.next()) {
	        	recentIndexedAuthors.add(rs.getString(1));
	        }
        	LOG.info("Starting affiliation " + affiliationName + " found " + recentIndexedAuthors.size() + " recently indexed authors");
		} 
		finally {
			conn.close();
		}
	}    
    
	public int saveAuthor(Author author) throws Exception {
		// check DB
		int id = 01;
		Connection conn = dbUtil.getConnection();
		XStream xstream = new XStream();
		try {
	        CallableStatement cs = conn
			        .prepareCall("{ call [UpsertAuthor](?, ?, ?, ?, ?, ?, ?, ?)}");
	        cs.setString(1, author.getAffiliation());
	        cs.setString(2, author.getLastName());
	        cs.setString(3, author.getFirstName());
	        cs.setString(4, author.getMiddleName());
	        cs.setString(5, author.getURL());
	        cs.setString(6, author.getImageURL());
	        cs.setString(7, author.getOrcidId());
	        cs.setString(8, xstream.toXML(author.getPubMedPublications()));
	        ResultSet rs = cs.executeQuery();
	        if (rs.next()) {
	        	id = rs.getInt(1); 
	        	LOG.info("Saved authorshipId = " + id);
	        	currentIndexedAuthors.add(author.getURL());
	        }
		} 
		finally {
			conn.close();
		}
    	// now save a thumbnail 
		// this had better be temporary!
		saveAuthorImage(id, author);
    	return id;
	}

	public boolean skipAuthor(String url) {
		if (recentIndexedAuthors.contains(url)) {
			try {
				return touchAuthor(url) != -1;
			}
			catch (SQLException e) {
				LOG.log(Level.WARNING, e.getMessage(), e);
			}
		}
		return currentIndexedAuthors.contains(url);
	}

	public int touchAuthor(String url) throws SQLException {
        Integer authorId = null;
		Connection conn = dbUtil.getConnection();
		try {
	        CallableStatement cs = conn
			        .prepareCall("{ call [TouchAuthor](?, ?)}");
	        cs.setString(1, affiliationName);
	        cs.setString(2, url);
	        ResultSet rs = cs.executeQuery();
	        if (rs.next()) {
	        	authorId = rs.getInt(1);
	        }
		} 
		finally {
			conn.close();
		}
		return authorId != null ? authorId : -1;
	}

	public void finish() throws Exception {
		Connection conn = dbUtil.getConnection();
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
	
	public void close() {
		// no need to do anything, no resource are open
	}
	
	private void saveAuthorImage(int id, Author author) {
		try {
			if (id > 0 && author.getImageURL() != null) {
				String dir = thumbnailDir + "/" + author.getAffiliation() + "/" + (id % 100);
				File directory = new File(dir);
				directory.mkdirs();
				Thumbnails.of(new URL(author.getImageURL()))
		        .size(100, 100)
		        .toFile(new File(dir + "/" + id + ".jpg"));
			}
		}
		catch (Exception e) {
			e.printStackTrace();
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
			
			DBAuthorPersistance db = new DBAuthorPersistance(null, null, 4, null, null);
			db.saveAuthor(author);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
