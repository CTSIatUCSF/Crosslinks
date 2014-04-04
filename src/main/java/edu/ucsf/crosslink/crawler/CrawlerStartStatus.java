package edu.ucsf.crosslink.crawler;

import java.text.DateFormat;
import java.util.Date;

public class CrawlerStartStatus implements Comparable<CrawlerStartStatus> {
	
	private final boolean okToStart;
	private final String reason;
	private final Date when;
	
	public CrawlerStartStatus(boolean okToStart, String reason) {
		super();
		this.okToStart = okToStart;
		this.reason = reason;
		this.when = new Date();
	}

	public boolean isOkToStart() {
		return okToStart;
	}

	public String getReason() {
		return reason;
	}
	
	public String toString() {
		return DateFormat.getDateTimeInstance().format(when) + ": Starting = " + okToStart + " because " + reason; 				
	}

	public int compareTo(CrawlerStartStatus o) {
		if (isOkToStart() != o.isOkToStart()) {
			return isOkToStart() ? -1 : 1;
		}
		else {
			return this.when.compareTo(o.when);
		}
	}
	
}
