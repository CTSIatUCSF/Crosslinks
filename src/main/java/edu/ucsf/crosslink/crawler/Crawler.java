package edu.ucsf.crosslink.crawler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Minutes;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.job.quartz.CrawlerJob;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;

public abstract class Crawler implements Runnable, Comparable<Crawler> {

	private Mode mode = Mode.ENABLED;
	private Status status = Status.IDLE;
	private CrawlerJob job;
	private Thread crawlingThread;

	private int pauseOnAbort = 60;
	private int staleDays = 7;

	private Date started = null;
	private Date ended = null;
	private CrawlerStartStatus lastStartStatus;

	private Researcher currentAuthor = null;
	private String latestError = null;
	private CrosslinkPersistance store;

	private List<Researcher> avoided = new ArrayList<Researcher>();
	private List<Researcher> error = new ArrayList<Researcher>();
	
	public Crawler(Mode mode) {
		this.mode = mode;
	}

	@Inject 
	public void setQuartzItems(CrawlerJob job) {
		this.job = job;
	}		
	
	@Inject 
	public void setPersistance(CrosslinkPersistance store) {
		this.store = store;
	}		

	@Inject
	public void setConfiguartion(CrosslinkPersistance store,
			@Named("pauseOnAbort") Integer pauseOnAbort,
			@Named("staleDays") Integer staleDays) {
		this.store = store;
		this.pauseOnAbort = pauseOnAbort;
		this.staleDays = staleDays;
	}

	public enum Status {
		GATHERING_URLS, READING_RESEARCHERS, VERIFY_PRIOR_RESEARCHERS, ERROR, PAUSED, FINISHED, IDLE, RUNNING, SHUTTING_DOWN;
	}
	
	public enum Mode {
		ENABLED, DISABLED, FORCED, FORCED_NO_SKIP, DEBUG;
	}
	
	public abstract String getName();
	
	public boolean isActive() {
		return isActive(getStatus());
	}
	
	private static boolean isActive(Status status) {
		return Arrays.asList(Status.GATHERING_URLS, Status.VERIFY_PRIOR_RESEARCHERS, Status.READING_RESEARCHERS, Status.RUNNING).contains(status);
	}

	public boolean isOk() {
		return !Arrays.asList(Status.PAUSED, Status.ERROR).contains(getStatus());
	}
	
	protected boolean isForced() {
		return Arrays.asList(Mode.FORCED_NO_SKIP, Mode.FORCED).contains(getMode());		
	}
	
	public String getState() {
		return getMode().toString() + " " + getStatus().toString();
	}
	
	public Mode getMode() {
		return mode;
	}	

	public void setMode(String mode) throws Exception {
		setMode(Mode.valueOf(mode));
		if (Status.GATHERING_URLS.equals(getStatus()) && Mode.DISABLED.equals(getMode()) && job != null) {
			// try and interrupt the job
			job.interrupt();
			setStatus(Status.IDLE);
		}
	}
		
	public void setMode(Mode mode) throws Exception {
		this.mode = mode;
	}

	public Status getStatus() {
		return status;
	}
	
	protected void setStatus(Status status) {
		if (!isActive(this.status) && isActive(status)) {
			started = new Date();
		}
		else if (isActive(this.status) && !isActive(status)) {
			ended = new Date();
		}
		else if (Status.FINISHED.equals(status) && isForced()) {
			// don't leave in forced mode
			mode = Mode.ENABLED;
		}
		this.status = status;
	}

	private void setCrawlingThread(Thread thread) {
		crawlingThread = thread;
	}
	
	public String getCurrentStackTrace() {
		Thread activeThread = crawlingThread;
		StringWriter sw = new StringWriter();
		PrintWriter writer = new PrintWriter(sw);
		if (activeThread != null) {
			int i = 0;
			for(StackTraceElement ste : activeThread.getStackTrace()) {
				writer.println(ste.toString() + "<br/>");
				if (i++ > 30) {
					break;
				}
			}
		}
		writer.flush();
		return sw.toString();
	}

	public String toString() {
		return getName() + " : " + getState() + " " + getCounts() + ", " + getDates() + ", " + getDuration(); 
	}
		
	public String getCounts() {
		return "Errors : " + getErrors().size() + ", Avoids : " + getAvoided().size();		
	}

	public String getDates() {
		return "Started : " + started + ", Ended : " + ended;				
	}

	public String getDuration() {
		if (started == null) {
			return "";
		}
		else {
			return "" +  Minutes.minutesBetween(new DateTime(started), ended != null ? new DateTime(ended) : new DateTime()).getMinutes() + " minutes";
		}
	}
	
	public Researcher getCurrentAuthor() {
		return currentAuthor;
	}

	protected void setCurrentAuthor(Researcher currentAuthor) {
		this.currentAuthor = currentAuthor;
	}

	public String getLatestError() {
		return latestError;
	}

	protected void setLatestError(String latestError) {
		this.latestError = latestError;
	}
	
	public abstract Affiliation getHarvester();
	
	protected void clear() {
		avoided.clear();
		error.clear();		
	}

	public List<Researcher> getErrors() {
		return error;
	}
	
	public List<Researcher> getAvoided() {
		return avoided;
	}
	
	protected void addError(Researcher researcher) {
		error.add(researcher);
	}
	
	protected void addAvoided(Researcher researcher) {
		avoided.add(researcher);
	}
	
	public Date getDateLastCrawled() {
		return store != null ? store.dateOfLastCrawl(getHarvester()) : null;
	}
	
	public CrawlerStartStatus getLastStartStatus() {
		return lastStartStatus;
	}

	private boolean isOkToStart() {
		lastStartStatus = getStartStatus();
		return lastStartStatus.isOkToStart();
	}

	private CrawlerStartStatus getStartStatus() {
		if (Mode.DISABLED.equals(getMode())) {
			return new CrawlerStartStatus(false, "in mode " + getMode().toString());
		}
		else if (isActive()) {
			return new CrawlerStartStatus(false, "currently active");
		}
		else if (!isOk()) {
			int minutesBetween = Minutes.minutesBetween(new DateTime(ended), new DateTime()).getMinutes();
			return new CrawlerStartStatus(minutesBetween > pauseOnAbort, "paused " + minutesBetween + " of " + pauseOnAbort + " minutes");
		}
		else if (isForced()) {
			return new CrawlerStartStatus(true, "isForced");
		}
		else if (getDateLastCrawled() == null) {
			return new CrawlerStartStatus(true, "never crawled before");
		}
		else {
			int daysBetween = Days.daysBetween(new DateTime(getDateLastCrawled()), new DateTime()).getDays();
			return  new CrawlerStartStatus(daysBetween > staleDays, "waited " + daysBetween + " of " + staleDays + " days");
		}
	}

	public int compareTo(Crawler other) {
    	// always put active ones in the front
		if (!(other instanceof AffiliationCrawler)) {
			return 1;
		}
		AffiliationCrawler o = (AffiliationCrawler)other;
    	if (this.isActive() != o.isActive()) {
    		return this.isActive() ? -1 : 1;
    	}	    	

    	CrawlerStartStatus astatus = this.getLastStartStatus();
    	CrawlerStartStatus bstatus = o.getLastStartStatus();	    	
    	if (astatus != null && bstatus != null) {
    		return astatus.compareTo(bstatus);
    	}
    	else if (astatus == null) {
    		return bstatus == null ? 0 : -1;
    	}
    	else {
    		return 1;
    	}
    } 	
	
	// trust the implementing classes to set the status
	public void run() {
		if (!isOkToStart()) {
			return;
		}
		setCrawlingThread(Thread.currentThread());		
		try {
			crawl();
		}
		catch (Exception e) {
			setLatestError(e.getMessage());
		}
		finally {
			setCrawlingThread(null);			
		}
	}
	
	public abstract void crawl() throws Exception;
}
