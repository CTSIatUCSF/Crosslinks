package edu.ucsf.crosslink;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class CSVAuthorshipStore implements AuthorshipPersistance {

	private static final Logger LOG = Logger.getLogger(CSVAuthorshipStore.class.getName());
	
	private CSVWriter writer;
	private Set<String> existingEntries;
	
	public CSVAuthorshipStore(String filename) throws IOException {
		existingEntries = new HashSet<String>();

		// see if we already have this
		File file = new File(filename);
		if (file.exists()) {
			CSVReader reader = new CSVReader(new FileReader(file));
			List<String[]> entries = reader.readAll();
			for (String[] entry : entries) {
				if ("PMID".equalsIgnoreCase(entry[4])) {
					continue;
				}
				Authorship authorship = new Authorship(entry);
				if (!existingEntries.contains(authorship.getURL())) {
					existingEntries.add(authorship.getURL());
				}
			}
			LOG.info("Found " + existingEntries.size() + " processed authors in " + filename + " out of " + entries.size() + " entries");
			reader.close();
			writer = new CSVWriter(new FileWriter(filename, true));
		}
		else {
			writer = new CSVWriter(new FileWriter(filename));
		    writer.writeNext(Authorship.ColumnNames);
		    flush();
		}
	}
	
	public void saveAuthorship(Authorship authorship) throws Exception {
		writer.writeNext(authorship.toStringArray());
	}
	
	public boolean containsAuthor(String url) {
		return existingEntries.contains(url);
	}
	
	public void close() throws IOException {
		writer.close();
	}
	
	public void flush() throws IOException {
		writer.flush();
	}

}
