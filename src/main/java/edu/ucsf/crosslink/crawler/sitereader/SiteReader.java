package edu.ucsf.crosslink.crawler.sitereader;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.io.ImportCert;
import edu.ucsf.crosslink.model.Affiliation.RNSType;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.R2RConstants;

public class SiteReader implements R2RConstants {
	
	private static final Logger LOG = Logger.getLogger(SiteReader.class.getName());

	private Map<String, String> cookies = new HashMap<String, String>();	

	private int getDocumentRetry = 10;
	private int getDocumentTimeout = 15000;
	private int getDocumentSleep = 1000;
	
	private static Map<RNSType, ImageFinder> imageFinders = new HashMap<RNSType, ImageFinder>();
	private static List<ImageFinder> baseImageFinders = null;
	
	static {
		imageFinders.put(RNSType.PROFILES, new ImageFinder() {
			public String getImage(Element src) {
	    		// Profiles
	    		if (src.attr("abs:src").contains("PhotoHandler.ashx")) {
	    			try {
	    				URIBuilder uri = new URIBuilder(src.attr("abs:src"));
	    				List<NameValuePair> params = uri.getQueryParams();
	    				uri.removeQuery();
	        			for (NameValuePair param : params) {
	        				if ("cachekey".equalsIgnoreCase(param.getName())) {
	        					continue;
	        				}
	        				else if ("Thumbnail".equalsIgnoreCase(param.getName())) {
	        					// this is not an image of the person!
	        					uri = null;
	        					break;
	        				}        	
	        				uri.addParameter(param.getName(), param.getValue());
	        			}
	        			if (uri != null) {
	        				return uri.toString();
	        			}
	        		}
	    			catch (Exception e) {
	    				LOG.log(Level.WARNING, e.getMessage(), e);
	    			}
	    		}
				return null;
			}
		});
	
		imageFinders.put(RNSType.VIVO, new ImageFinder() {
			public String getImage(Element src) {
	    		// from RDF author parser
	    		//  look for a photo
	    		if (src.attr("class").contains("photo") && !src.attr("title").equals("no image")) {
	    			return src.attr("abs:src");
	    		}
	    		// from HTML author parser
	    		//  try a few more tricks to look for a photo, this particular method works with VIVO
	    		if (src.className().equals("individual-photo") && !src.attr("abs:src").contains("unknown") ) { 
	    			return src.attr("abs:src");
	    		}
				return null;
			}
		});

		imageFinders.put(RNSType.LOKI, new ImageFinder() {
			public String getImage(Element src) {
	    		// from loki
	    		if (src.attr("abs:src").contains("displayPhoto")) {
	    			return src.attr("abs:src");
	    		}
				return null;
			}
		});

		imageFinders.put(RNSType.CAP, new ImageFinder() {
			public String getImage(Element src) {
	    		// from stanford
	    		if (src.attr("abs:src").contains("viewImage")) {
	    			return src.attr("abs:src");
	    		}
				return null;
			}
		});
		
		baseImageFinders = Arrays.asList(imageFinders.get(RNSType.PROFILES),
				imageFinders.get(RNSType.VIVO),
				imageFinders.get(RNSType.CAP),
				imageFinders.get(RNSType.LOKI));

		imageFinders.put(RNSType.SCIVAL, imageFinders.get(RNSType.VIVO));

		imageFinders.put(RNSType.UNKNOWN, new ImageFinder() {
			public String getImage(Element src) {
				String img = null;
				for (ImageFinder finder : baseImageFinders) {
					img = finder.getImage(src);
					if (img != null) {
						return img;
					}
	    		}
				return null;
			}
		});		
	}	
	
	@Inject
	public SiteReader(@Named("getDocumentRetry") Integer getDocumentRetry, @Named("getDocumentTimeout") Integer getDocumentTimeout, 
			@Named("getDocumentSleep") Integer getDocumentSleep) {
		this.getDocumentRetry = getDocumentRetry;
		this.getDocumentTimeout = getDocumentTimeout;
		this.getDocumentSleep = getDocumentSleep;
	}
	
	public Document getDocument(String url) throws IOException, InterruptedException  {
    	int attempts = 0;
    	Document doc = null;
    	while (attempts++ < getDocumentRetry) {
        	try {
        	    Connection connection = Jsoup.connect(url);
        	    for (Entry<String, String> cookie : cookies.entrySet()) {
        	        connection.cookie(cookie.getKey(), cookie.getValue());
        	    }
        	    Response response = connection.timeout(getDocumentTimeout).execute();
        	    cookies.putAll(response.cookies());
        	    doc = response.parse();
        		break;
        	}
        	catch (java.net.SocketTimeoutException ex) {
        		LOG.info("Trying " + url + " one more time... " + attempts);
        		Thread.sleep(getDocumentSleep);
        	}
    	}
    	return doc;
	}
	
    public static void print(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }

    public static String trim(String s, int width) {
        if (s.length() > width)
            return s.substring(0, width-1) + ".";
        else
            return s;
    }
    
    // this should arguably be the source of the verifiedDT
    public void getPageItems(Researcher researcher) throws IOException, InterruptedException {    	
    	Document doc = getDocument(researcher.getURI());
		researcher.setVerifiedDt(Calendar.getInstance());
		researcher.setHomepage(doc.location());
		researcher.addImageURL(getImage(doc, researcher.getAffiliation().getRNSType()));
    }
           
    private String getImage(Document doc, RNSType type) {
    	for (Element src : doc.select("[src]")) {
    		String img = null;
    		if (!src.tagName().equals("img")) {
    			continue;
    		}
    		else {
    			img = imageFinders.get(type).getImage(src);
    			if (img != null) {
    				return img;
    			}
    		}
    	}
    	return null;
    }
    
    public static void main(String[] args) {
    	// this only works when running from the command line.  Setting this is tomcat does not work for some reason
		System.out.println(System.getProperty("javax.net.ssl.trustStore"));
    	ImportCert ic = new ImportCert();
		System.out.println(System.getProperty("javax.net.ssl.trustStore"));
    	Map<String, String> cookies = new HashMap<String, String>();
    	String url = "http://vivo.brown.edu/individual/aacidera";
    	try {
        	int attempts = 0;
        	@SuppressWarnings("unused")
			Document doc = null;
        	while (attempts++ < 10) {
            	try {
            	    Connection connection = Jsoup.connect(url);
            	    for (Entry<String, String> cookie : cookies.entrySet()) {
            	        connection.cookie(cookie.getKey(), cookie.getValue());
            	    }
            	    Response response = connection.timeout(2000).execute();
            	    cookies.putAll(response.cookies());
            	    doc = response.parse();
            		break;
            	}
            	catch (java.net.SocketTimeoutException ex) {
            		ex.printStackTrace();
            	}
        	}
    		    		
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    }
}
