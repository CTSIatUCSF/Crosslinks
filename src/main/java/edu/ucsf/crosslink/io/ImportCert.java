package edu.ucsf.crosslink.io;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
//VERY IMPORTANT.  SOME OF THESE EXIST IN MORE THAN ONE PACKAGE!
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

public class ImportCert {
	
	private static final String KEYSTORE = "crosslinks.jks";
	
	public static void loadKeystore() {
		// If running in tomcat, set as system property
		System.setProperty("javax.net.ssl.trustStore", ImportCert.class.getResource("/certs/" + KEYSTORE).getFile());		
	}
	
	public static void main(String[] args) {
		try {
			//Put everything after here in your function.
			KeyStore trustStore  = KeyStore.getInstance(KeyStore.getDefaultType());
			trustStore.load(null);//Make an empty store
			URL dir = ImportCert.class.getResource("/certs/");
			for (int i = 0; i < args.length; i++) {
				String fileNameRoot = args[0];
				InputStream fis = ImportCert.class.getResourceAsStream("/certs/" + fileNameRoot + ".cer");
				BufferedInputStream bis = new BufferedInputStream(fis);
	
				CertificateFactory cf = CertificateFactory.getInstance("X.509");
	
				while (bis.available() > 0) {
				    Certificate cert = cf.generateCertificate(bis);
				    trustStore.setCertificateEntry("R2R "+ fileNameRoot, cert);
				}						    
			    trustStore.store( new FileOutputStream(dir.getFile() + KEYSTORE), "crosslinks".toCharArray());
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
