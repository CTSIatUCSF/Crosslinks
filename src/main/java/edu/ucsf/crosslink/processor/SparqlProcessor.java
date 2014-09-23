package edu.ucsf.crosslink.processor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;
import edu.ucsf.ctsi.r2r.jena.SparqlClient;

public abstract class SparqlProcessor implements Iterable<ResearcherProcessor> {

	private static final Logger LOG = Logger.getLogger(SparqlProcessor.class.getName());

	private SparqlClient sparqlClient = null;
	
	private int offset = 0;
	private int limit = 0;
	
	private int currentNdx = 0;
	private List<ResearcherProcessor> currentResearcherProcessors = new ArrayList<ResearcherProcessor>();
	
	private String query = null;
	
	// remove harvester as required item
	protected SparqlProcessor(SparqlClient sparqlClient, int limit) {
		this.sparqlClient = sparqlClient;
		this.limit = limit;
	}
	
	public String toString() {
		return "Offset = " + offset + ", Limit = " + limit;
	}
	
	protected SparqlClient getSparqlClient() {
		return sparqlClient;
	}
		
	protected abstract String getSparqlQuery();
	
	protected abstract ResearcherProcessor getResearcherProcessor(QuerySolution qs);
	
	public Iterator<ResearcherProcessor> iterator() {
		query = getSparqlQuery();
		offset = 0;
		return new SparqlProcessorIterator();
    }
	
	private void executeQuery() throws Exception {
		String incrementalQuery = limit > 0 ? String.format(query + " OFFSET %d LIMIT %d", offset, limit) : query;

		final AtomicInteger found = new AtomicInteger();
		getSparqlClient().select(incrementalQuery, new ResultSetConsumer() {
			public void useResultSet(ResultSet rs) {
				while (rs.hasNext()) {				
					currentResearcherProcessors.add(getResearcherProcessor(rs.next()));
					found.incrementAndGet();
				}	
			}
		});
		
		offset += found.get();
	}
	
	private class SparqlProcessorIterator implements Iterator<ResearcherProcessor> {

		public boolean hasNext() {
			if (currentNdx == currentResearcherProcessors.size()) {
				try {
					currentNdx = 0;
					currentResearcherProcessors.clear();
					executeQuery();
				} 
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			return currentNdx < currentResearcherProcessors.size();
		}

		public ResearcherProcessor next() {
			return currentResearcherProcessors.get(currentNdx++);
		}
		
	}
	
}
