package edu.ucsf.crosslink.io;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.author.Author;
import edu.ucsf.crosslink.author.Authorship;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class CSVAuthorPersistance implements CrosslinkPersistance {

	private static final Logger LOG = Logger.getLogger(CSVAuthorPersistance.class.getName());
	
	private CSVWriter writer;
	private Set<String> existingEntries;
	
	private String affiliationName;
	private File propertiesFile;
	
	@Inject
	public CSVAuthorPersistance(@Named("Affiliation") String affiliationName) {
		this.affiliationName = affiliationName;
		String filename = affiliationName.replace(' ', '_') + ".csv";
		this.propertiesFile = new File(filename);
	}
	
	public Date dateOfLastCrawl() {
		if (propertiesFile.exists()) {
			return new Date(propertiesFile.lastModified());
		}
		return null;
	}

	
	public void start() throws IOException {
		String filename = affiliationName.replace(' ', '_') + ".csv";
		existingEntries = new HashSet<String>();

		// see if we already have this
		if (propertiesFile.exists()) {
			CSVReader reader = new CSVReader(new FileReader(propertiesFile));
			List<String[]> entries = reader.readAll();
			for (String[] entry : entries) {
				if ("PMID".equalsIgnoreCase(entry[5])) {
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

	
	public void saveAuthor(Author author) throws Exception {
		this.saveAuthorships(author.getAuthorships());
		flush();
	}
	
	private void saveAuthorships(Collection<Authorship> authorships) throws Exception {
		for (Authorship authorship : authorships) {
			writer.writeNext(authorship.toStringArray());
			if (!existingEntries.contains(authorship.getURL())) {
				existingEntries.add(authorship.getURL());
			}
		}
	}

	public boolean skipAuthor(String url) {
		return existingEntries.contains(url);
	}
	
	public void close() throws IOException {
		writer.close();
	}
	
	private void flush() throws IOException {
		writer.flush();
	}

}
