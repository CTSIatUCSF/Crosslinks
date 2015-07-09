package edu.ucsf.crosslink.processor.iterator;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.model.Affiliated;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.crosslink.web.FusekiRestMethods;

public class ExtractAffiliationProcessor extends ExtractProcessor implements Affiliated {

	private static final String RESEARCHERS_SELECT_NO_SKIP = "SELECT ?r WHERE { " +
			"?r <" + R2R_HAS_AFFILIATION + "> <%1$s>}";	

	private static final String COAUTHORS_EXTRACT_WHERE = "WHERE {<%1$s> <" + R2R_HAS_AFFILIATION + "> ?a . <%1$s> <" +
			FOAF_PUBLICATIONS + "> ?cw  . ?r <" + FOAF_PUBLICATIONS + "> ?cw  . ?r <" + RDFS_LABEL + 
			"> ?rl . OPTIONAL {?r <" + FOAF_HOMEPAGE + "> ?hp } . OPTIONAL { GRAPH <" + R2R_DERIVED_GRAPH + 
			"> { ?r <" + FOAF_HAS_IMAGE + "> ?tn} } . ?r <" + R2R_HAS_AFFILIATION + "> ?ea FILTER (?ea != ?a) . ?ea <" + 
			RDFS_LABEL + "> ?al . OPTIONAL {?ea <" + R2R_HAS_ICON + "> ?eaicon} . ?ea <" + GEO_LATITUDE + 
			"> ?ealat . ?ea <" + GEO_LONGITUDE + "> ?ealon}";

	private static final String COAUTHORS_EXTRACT_CONSTRUCT = "CONSTRUCT {?r <" + RDF_TYPE + "> <" + FOAF_PERSON + 
			"> . ?r <" + FOAF_PUBLICATIONS + "> ?cw . ?r <" +
			RDFS_LABEL + "> ?rl . ?r <" + FOAF_HOMEPAGE + "> ?hp . ?r <" + FOAF_HAS_IMAGE + "> ?tn . ?r  <" +
			R2R_HAS_AFFILIATION + "> ?ea} " + COAUTHORS_EXTRACT_WHERE;


	private Affiliation affiliation = null;

	@Inject
	public ExtractAffiliationProcessor(Affiliation affiliation,
			@Named("r2r.fusekiUrl") String sparqlQuery, @Named("uiFusekiUrl") String uiFusekiUrl) throws Exception {
		super(sparqlQuery, uiFusekiUrl);
		this.affiliation = affiliation;
	}
	
	public Affiliation getAffiliation() {
		return affiliation;
	}

	@Override
	protected String getFormattedQueryString(int offset, int limit) {
		return String.format(RESEARCHERS_SELECT_NO_SKIP, getAffiliation().getURI());
	}
	
	@Override
	protected void addDataToResearcherModel(Researcher researcher) {
		super.addDataToResearcherModel(researcher);
		if (researcher.getPublications().size() > 0) {
			researcher.getModel().add(getSparqlClient().construct(String.format(COAUTHORS_EXTRACT_CONSTRUCT, researcher.getURI())));
		}
	}

}
