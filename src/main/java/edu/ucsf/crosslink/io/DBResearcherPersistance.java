package edu.ucsf.crosslink.io;

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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.thoughtworks.xstream.XStream;

import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;

public class DBResearcherPersistance implements CrosslinkPersistance {
	
	private static final Logger LOG = Logger.getLogger(DBResearcherPersistance.class.getName());
	
	private Affiliation affiliation;
	private int daysConsideredOld;
	private DBUtil dbUtil;
	private Set<String> recentIndexedAuthors = new HashSet<String>();
	
	private ThumbnailGenerator thumbnailGenerator;
	private JenaPersistance jenaPersistance;
		
	@Inject
	public DBResearcherPersistance(Affiliation affiliation, @Named("daysConsideredOld") Integer daysConsideredOld, DBUtil dbUtil) {
		this.affiliation = affiliation;
		this.daysConsideredOld = daysConsideredOld;
		this.dbUtil = dbUtil;
	}
	
	@Inject
	public void setThumbnailGenerator(ThumbnailGenerator thumbnailGenerator) {
		this.thumbnailGenerator = thumbnailGenerator;
	}
	
	@Inject
	public void setJenaPersistance(JenaPersistance jenaPersistance) {
		this.jenaPersistance = jenaPersistance;
	}

	public Date dateOfLastCrawl() {
		Connection conn = dbUtil.getConnection();
		try {
			String sql = "select lastIndexEndDT from Affiliation where affiliationName = ?";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, affiliation.getName());
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
	        cs.setString(1, affiliation.getName());
	        cs.setString(2, affiliation.getBaseURL());
	        cs.setInt(3, daysConsideredOld);
	        ResultSet rs = cs.executeQuery();
	        while (rs.next()) {
	        	recentIndexedAuthors.add(rs.getString(1));
	        }
        	LOG.info("Starting affiliation " + affiliation + " found " + recentIndexedAuthors.size() + " recently indexed authors");
		} 
		finally {
			conn.close();
		}
	}    
    
	public void saveResearcher(Researcher researcher) throws Exception {
		//  clean up image urls
		try {
			if (thumbnailGenerator != null) {
				thumbnailGenerator.generateThumbnail(researcher);
			}
			if (jenaPersistance != null) {
				jenaPersistance.saveResearcher(researcher);
			}
		}
		catch (Exception e) {
			LOG.log(Level.WARNING, "Trying to generate thumbnails and store RDF for " + researcher, e);
		}
		// check DB
		Connection conn = dbUtil.getConnection();
		XStream xstream = new XStream();
		try {
	        CallableStatement cs = conn
			        .prepareCall("{ call [UpsertAuthor](?, ?, ?, ?, ?, ?, ?, ?)}");
	        cs.setString(1, researcher.getAffiliation().getName());
	        cs.setString(2, researcher.getHomePageURL());
	        cs.setString(3, researcher.getURI());
	        cs.setString(4, researcher.getLabel());
	        cs.setString(5, researcher.getImageURL());
	        cs.setString(6, researcher.getThumbnailURL());
	        cs.setString(7, researcher.getOrcidId());
	        cs.setString(8, xstream.toXML(researcher.getPubMedPublications()));
	        
	        ResultSet rs = cs.executeQuery();
	        if (rs.next()) {
	        	LOG.info("Saved authorshipId = " + rs.getInt(1));
	        	recentIndexedAuthors.add(researcher.getHomePageURL());
	        }
		} 
		finally {
			conn.close();
		}
	}

	public boolean skip(String url) {
		return recentIndexedAuthors.contains(url);
	}

	public int touch(String url) throws SQLException {
        Integer authorId = null;
		Connection conn = dbUtil.getConnection();
		try {
	        CallableStatement cs = conn
			        .prepareCall("{ call [TouchAuthor](?, ?)}");
	        cs.setString(1, affiliation.getName());
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
	        cs.setString(1, affiliation.getName());
	        ResultSet rs = cs.executeQuery();
	        Integer affiliationId = null;
	        if (rs.next()) {
	        	affiliationId = rs.getInt(1);
	        	LOG.info("Stopping affiliation " + affiliation + " affiliationId = " + affiliationId);
	        }
	        if (affiliationId == null) {
	        	throw new Exception("Affiliation " + affiliation + " not found in the database, shutting down!");
	        }
	        
		} 
		finally {
			conn.close();
		}
	}
	
	public void close() {
		// no need to do anything, no resources are open
	}
	
	public static void main(String[] args) {
		try {
			XStream xstream = new XStream();
			Collection<Integer> pmids = new ArrayList<Integer>();
			pmids.add(123);
			pmids.add(456);
			System.out.println(xstream.toXML(pmids));
			
			Researcher author = new Researcher(null, null, null, "http://stage-profiles.ucsf.edu/profiles200/nobody.nobody", null, null);
			author.addPubMedPublication(123);
			author.addPubMedPublication(456);
			
			DBResearcherPersistance db = new DBResearcherPersistance(null, 4, null);
			db.saveResearcher(author);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
