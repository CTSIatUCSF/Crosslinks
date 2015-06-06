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
