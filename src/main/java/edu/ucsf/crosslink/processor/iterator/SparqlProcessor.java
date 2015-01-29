package edu.ucsf.crosslink.processor.iterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.engine.http.QueryExceptionHTTP;

import edu.ucsf.crosslink.processor.ResearcherProcessor;
import edu.ucsf.ctsi.r2r.jena.ResultSetConsumer;
import edu.ucsf.ctsi.r2r.jena.SparqlQueryClient;

public abstract class SparqlProcessor implements Iterable<ResearcherProcessor> {

	private static final Logger LOG = Logger.getLogger(SparqlProcessor.class.getName());

	private SparqlQueryClient sparqlQueryClient = null;
	
	private int offset = 0;
	private int limit = 0;
	private String query = "";
	private int retry = 0;
	
	private List<ResearcherProcessor> currentResearcherProcessors = new ArrayList<ResearcherProcessor>();
	
	// remove harvester as required item
	protected SparqlProcessor(SparqlQueryClient sparqlQueryClient, int limit) {
		this(sparqlQueryClient, limit, 0);
	}
	
	protected SparqlProcessor(SparqlQueryClient sparqlQueryClient, int limit, int retry) {
		this.sparqlQueryClient = sparqlQueryClient;
		this.limit = limit;
		this.retry = retry;
	}

	public String toString() {
		return "Size = " + currentResearcherProcessors.size() + ", Limit = " + limit + ", Query = " + query;
	}
	
	protected SparqlQueryClient getSparqlClient() {
		return sparqlQueryClient;
	}
		
	protected abstract String getSparqlQuery(int offset, int limit) throws Exception;
	
	protected abstract ResearcherProcessor getResearcherProcessor(QuerySolution qs);
	
	protected void shuttingDown() {
		// allow derived classes to override
	}
	
	public Iterator<ResearcherProcessor> iterator() {
		offset = 0;
		return new SparqlProcessorIterator();
    }
	
	private void executeQuery() throws Exception {
		query = getSparqlQuery(offset, limit);
		final AtomicInteger found = new AtomicInteger();
		Exception currentException = null;
		for (int i = 0; i <= retry; i++) {
			try {
				getSparqlClient().select(query, new ResultSetConsumer() {
					public void useResultSet(ResultSet rs) {
						while (rs.hasNext()) {				
							currentResearcherProcessors.add(getResearcherProcessor(rs.next()));
							found.incrementAndGet();
						}	
					}
				});				
				offset += found.get();
				return;
			}
			catch (QueryExceptionHTTP e) {
				currentException = e;
				LOG.log(Level.WARNING, "Try #" + i, e);			
			}			
		}
		if (currentException != null) {
			throw currentException;
		}
	}
	
	private class SparqlProcessorIterator implements Iterator<ResearcherProcessor> {

		public boolean hasNext() {
			if (currentResearcherProcessors.isEmpty() && (offset == 0 || limit > 0)) {
				try {
					executeQuery();
				} 
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			if (currentResearcherProcessors.isEmpty()) {
				shuttingDown();
			}
			return !currentResearcherProcessors.isEmpty();
		}

		public ResearcherProcessor next() {
			return currentResearcherProcessors.remove(0);
		}
		
	}
	
}
