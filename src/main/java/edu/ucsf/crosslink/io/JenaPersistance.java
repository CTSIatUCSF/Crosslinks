package edu.ucsf.crosslink.io;

import java.io.FileWriter;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hp.hpl.jena.ontology.DatatypeProperty;
import com.hp.hpl.jena.ontology.ObjectProperty;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.Ontology;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.vocabulary.XSD;

import edu.ucsf.crosslink.model.Researcher;

@Singleton
public class JenaPersistance implements CrosslinkPersistance {
	private static final Logger LOG = Logger.getLogger(JenaPersistance.class.getName());
	
	private static String SOURCE = "http://ucsf.edu/ontology/R2R";
	private static String NS = SOURCE + "#";			
	private static String ADDED_TO_CACHE = NS + "addedToCacheOn";
	
	private static String LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
	private static String TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
	
	private static String FOAF = "http://xmlns.com/foaf/0.1/";
	private static String HOMEPAGE = FOAF + "homepage";			
	private static String IMAGE = FOAF + "img";			
	
	private String tdbBaseDir;
	private ThumbnailGenerator thumbnailGenerator;
	
	@Inject
	public JenaPersistance(@Named("rdfBaseDir")String tdbBaseDir) {
		this.tdbBaseDir = tdbBaseDir;
		
		// load the ontology into the model
		if (this.tdbBaseDir != null) {
			Dataset dataSet = getDataset(ReadWrite.WRITE);
    		Model model = dataSet.getDefaultModel();
    		model.add(getR2ROntModel());
        	dataSet.commit();
    		dataSet.end();
		}
	}

	@Inject
	public void setThumbnailGenerator(ThumbnailGenerator thumbnailGenerator) {
		this.thumbnailGenerator = thumbnailGenerator;
	}
		
	private Dataset getDataset(ReadWrite readWrite) {
		Dataset dataset = TDBFactory.createDataset(tdbBaseDir);
		dataset.begin(readWrite);
		return dataset;
	}
	
	public boolean contains(String uri) {
		if (tdbBaseDir == null) {
			return false;
		}
    	Dataset dataSet = getDataset(ReadWrite.READ);
    	try {
    		Model model = dataSet.getDefaultModel();
	    	return model.contains(ResourceFactory.createResource(uri), null);
        }	
	    finally {
	    	dataSet.end();
	    }
	}
	
	public Resource getFreshResource(String rdfUrl, boolean store) {
		Dataset dataSet = null;
		Resource resource = null;
		// hack
		String uri = rdfUrl;
		if (rdfUrl.endsWith(".rdf")) {
			uri = rdfUrl.substring(0, rdfUrl.lastIndexOf('/'));
		}
		try {
			if (tdbBaseDir == null || !store) {
				resource = FileManager.get().loadModel(rdfUrl).createResource(uri);
			}
			else if (!contains(uri)) {
	        	dataSet = getDataset(ReadWrite.WRITE);
	        	Model writeModel = dataSet.getDefaultModel();
	            writeModel.removeAll(resource, null, null);
	            writeModel.add(FileManager.get().loadModel(rdfUrl));
	            resource = writeModel.createResource(uri);
	        	dataSet.commit();
			}
			else {
		    	dataSet = getDataset(ReadWrite.READ);
	            Model readModel = dataSet.getDefaultModel();
	            resource = readModel.createResource(uri);
			}
		}
		catch (Exception e) {
			LOG.log(Level.WARNING, e.getMessage(), e);
		}
		finally {
	    	dataSet.end();
		}
		return resource;
	}
		

	public void start() throws Exception {
	}

	public void saveResearcher(Researcher researcher) throws Exception {
		if (thumbnailGenerator != null) {
			thumbnailGenerator.generateThumbnail(researcher);
		}
		
    	Dataset dataSet = getDataset(ReadWrite.WRITE);
    	try {
	    	Model writeModel = dataSet.getDefaultModel();
	    	
	    	String uri = researcher.getURI() != null ? researcher.getURI() : researcher.getHomePageURL();
	    	if (!contains(uri)) {
	    		// Must be from a site that does not have LOD.  add basic stuff
	            Resource resource = writeModel.createResource(uri);
	    		
	        	// person
	        	writeModel.add(resource, 
        				writeModel.createProperty(TYPE), 
        				writeModel.createLiteral("http://xmlns.com/foaf/0.1/Person"));

	            // label
	        	writeModel.add(resource, 
	        				writeModel.createProperty(LABEL), 
	        				writeModel.createTypedLiteral(researcher.getLabel()));
	        	
	        	researcher.setURI(uri);
	        }
	    	Resource resource = writeModel.createResource(uri);
    			
            // added
        	writeModel.add(resource, 
        				writeModel.createProperty(ADDED_TO_CACHE), 
        				writeModel.createTypedLiteral(new DateTime().getMillis()));  
        	
        	// homepage
        	writeModel.add(resource, 
    				writeModel.createProperty(HOMEPAGE), 
    				writeModel.createTypedLiteral(researcher.getHomePageURL()));        	

        	// image        	
        	if (researcher.getThumbnailURL() != null) {
	        	writeModel.add(resource, 
	    				writeModel.createProperty(IMAGE), 
	    				writeModel.createTypedLiteral(researcher.getThumbnailURL()));
        	}
		}
		catch (Exception e) {
			LOG.log(Level.WARNING, e.getMessage(), e);
		}
		finally {
        	dataSet.commit();
	    	dataSet.end();
		}
	}

	public Date dateOfLastCrawl() {
		return null;
	}

	public boolean skip(String url) {
		return false;
	}

	public int touch(String url) throws Exception {
		return 0;
	}

	public void close() throws Exception {
	}

	public void finish() throws Exception {
	}
	
	public static OntModel getR2ROntModel() {
		OntModel m = ModelFactory.createOntologyModel();
		//m.read("http://xmlns.com/foaf/spec/", "RDF/XML");
		Ontology ont = m.createOntology( SOURCE );
		ont.addImport( m.createResource( "http://xmlns.com/foaf/0.1/" ) );
		m.createClass(NS + "ColloaboartiveWork");
	
		DatatypeProperty pmid = m.createDatatypeProperty( NS + "PMID" );
		pmid.addDomain( m.getOntClass( NS + "ColloaboartiveWork" ) );
		pmid.addRange( XSD.xint );			

		DatatypeProperty pmcid = m.createDatatypeProperty( NS + "PMCID" );
		pmcid.addDomain( m.getOntClass( NS + "ColloaboartiveWork" ) );
		pmcid.addRange( XSD.xint );			
		
		DatatypeProperty doi = m.createDatatypeProperty( NS + "doi" );
		doi.addDomain( m.getOntClass( NS + "ColloaboartiveWork" ) );
		doi.addRange( XSD.xstring );	
				
		// FOAF extensions
		OntModel foaf = ModelFactory.createOntologyModel();
		foaf.read("http://xmlns.com/foaf/spec/", "RDF/XML");
		
		ObjectProperty ab = m.createObjectProperty( NS + "collaboratedOn" );
		ab.addDomain( foaf.getOntClass( "http://xmlns.com/foaf/0.1/Person" ) );
		ab.addRange( m.getOntClass( NS + "ColloaboartiveWork" ) );		
		
		DatatypeProperty ts = m.createDatatypeProperty( ADDED_TO_CACHE );
		ts.addDomain( foaf.getOntClass( "http://xmlns.com/foaf/0.1/Person" ) );
		ts.addRange( XSD.xlong );			

		return m;
	}
	
	public static void main(String[] args) {
		try {
			OntModel m = getR2ROntModel();
			//			/http://xmlns.com/foaf/0.1/Person
			//TDBCacheResourceService service = new TDBCacheResourceService("C:\\Development\\R2R\\workspace\\datastores");
//			OntModel m = ModelFactory.createOntologyModel();
			//m.read( "http://xmlns.com/foaf/0.1/" );
//			m.read("http://xmlns.com/foaf/spec/", "RDF/XML");
				
			FileWriter out = null;
			try {
			  // XML format - long and verbose
			  out = new FileWriter( "mymodel.xml" );
			  m.write( out, "RDF/XML-ABBREV" );

			  // OR Turtle format - compact and more readable
			  // use this variant if you're not sure which to use!
			  out = new FileWriter( "mymodel.ttl" );
			  m.write( out, "Turtle" );
			}
			finally {
			  if (out != null) {
				  out.close();
			  }
			}		
			
			new JenaPersistance("C:\\Development\\R2R\\workspace\\Crosslinks\\testModel");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}}
