package edu.ucsf.crosslink.io;

import java.io.File;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.coobird.thumbnailator.Thumbnails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.model.Researcher;

@Singleton
public class ThumbnailGenerator {
	
	private static final Logger LOG = Logger.getLogger(ThumbnailGenerator.class.getName());
		
	private String thumbnailDir;
	private String thumbnailRootURL;
	private int thumbnailWidth = 100;
	private int thumbnailHeight = 100;
	
	@Inject
	public ThumbnailGenerator(@Named("thumbnailDir") String thumbnailDir, @Named("thumbnailRootURL") String thumbnailRootURL, 
			@Named("thumbnailWidth") Integer thumbnailWidth, @Named("thumbnailHeight") Integer thumbnailHeight) {
		this.thumbnailDir = thumbnailDir;
		this.thumbnailRootURL = thumbnailRootURL;
		this.thumbnailWidth = thumbnailWidth;
		this.thumbnailHeight = thumbnailHeight;
		// prove that this works from a user rights perspective
		File directory = new File(thumbnailDir);
		directory.mkdirs();		
		LOG.info("Saving thumbnails in " + thumbnailDir);
	}
		
	public boolean generateThumbnail(Researcher researcher) {
		if (researcher.getHomePageURL() != null && researcher.getImageURLs().size() > 0 && researcher.getThumbnailURL() == null) {
			int id = researcher.getHomePageURL().toLowerCase().hashCode();
			String loc = researcher.getAffiliationName() + "/" + ("" + (100 + (Math.abs(id) % 100))).substring(1) + "/" + id + ".jpg";
			for (String imageURL : researcher.getImageURLs()) {
				try {
					File thumbnail = new File(thumbnailDir + "/" + loc );
					new File(thumbnail.getParent()).mkdirs();
					Thumbnails.of(new URL(imageURL))
			        	.size(thumbnailWidth, thumbnailHeight)
			        	.toFile(thumbnail);
					// if we made it here, we are good
					String thumbnailURL = thumbnailRootURL + "/" + loc;
					researcher.setConfirmedImgURLs(imageURL, thumbnailURL);
					return true;
				}
				catch (Exception e) {
					LOG.log(Level.WARNING, e.getMessage(), e);
				}
			}
		}
		// if we get here, they are all bad
		researcher.setConfirmedImgURLs(null, null);
		return false;
	}	
	
}
