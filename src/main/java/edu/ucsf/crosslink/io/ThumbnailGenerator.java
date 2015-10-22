package edu.ucsf.crosslink.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.coobird.thumbnailator.Thumbnails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.model.Affiliation;
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
		if (researcher.getURI() != null && researcher.getImageURLs().size() > 0 && researcher.getThumbnailURL() == null) {
			int id = researcher.getURI().toLowerCase().hashCode();
			URI uri = researcher.getAffiliation().getURIObject();
			String loc = uri.getHost() + "/" + ("" + (100 + (Math.abs(id) % 100))).substring(1) + "/" + id + ".jpg";
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
					// might be a URI
				}
			}
			// if we get here, they are all bad
			researcher.setConfirmedImgURLs(null, null);
		}
		return false;
	}	
	
	public String generateThumbnail(Affiliation affiliation, String imageURL) throws MalformedURLException, IOException {
		URI uri = affiliation.getURIObject();
		
		if (imageURL.endsWith(".ico")) {
			String loc = uri.getHost() + (uri.getPath().length() > 0 ? uri.getPath() + "_favicon.ico" : "/favicon.ico");
			new File(thumbnailDir + "/" + uri.getHost()).mkdirs();
			saveImage(imageURL, thumbnailDir + "/" + loc);
			return thumbnailRootURL + "/" + loc;
		}
		else {
			String loc = uri.getHost() + "/thumbnail.png";
	
			File thumbnail = new File(thumbnailDir + "/" + loc );
			new File(thumbnail.getParent()).mkdirs();
			try {
				Thumbnails.of(new URL(imageURL))
		        	.size(thumbnailWidth, thumbnailHeight)
		        	.toFile(thumbnail);
			}
			catch (IOException e) {
				LOG.log(Level.WARNING, e.getMessage(), e);
				throw e;
			}
			// if we made it here, we are good
			return thumbnailRootURL + "/" + loc;
		}
	}	
	
	private static void saveImage(String imageUrl, String destinationFile) throws IOException {
		URL url = new URL(imageUrl);
		InputStream is = url.openStream();
		OutputStream os = new FileOutputStream(destinationFile);

		byte[] b = new byte[2048];
		int length;

		while ((length = is.read(b)) != -1) {
			os.write(b, 0, length);
		}

		is.close();
		os.close();
	}
	
}
