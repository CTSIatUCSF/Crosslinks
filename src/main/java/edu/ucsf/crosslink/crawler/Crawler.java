package edu.ucsf.crosslink.crawler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Minutes;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.model.Affiliation;
import edu.ucsf.crosslink.model.Researcher;

public abstract class Crawler implements Runnable, Comparable<Crawler> {

	private static final Logger LOG = Logger.getLogger(MarengoSparqlCrawler.class.getName());

	private Mode mode = Mode.ENABLED;
	private Status status = Status.IDLE;
	private Affiliation harvester = null;

	private int pauseOnAbort = 60;
	private int staleDays = 7;

	private Date started = null;
	private Date ended = null;
	private CrawlerStartStatus lastStartStatus;

	private Researcher lastFoundAuthor = null;
	private Researcher lastReadAuthor = null;
	private Researcher lastSavedAuthor = null;

	private String latestError = null;
	private CrosslinkPersistance store;

	private List<Researcher> avoided = new ArrayList<Researcher>();
	private List<Researcher> error = new ArrayList<Researcher>();
	private int foundCnt = 0;
	private int readCnt = 0;
	private int savedCnt = 0;
	private int skipCnt = 0;
	
	private Thread crawlingThread = null;
	private ExecutorService executorService = null;
	private Future<Boolean> currentJob = null;
	
	public Crawler(Mode mode, Affiliation harvester, CrosslinkPersistance store) throws Exception {
		this.mode = mode;
		this.harvester = harvester;
		this.store = store;
		store.save(harvester);		
		executorService = Executors.newSingleThreadExecutor();
	}

	@Inject
	public void setConfiguartion(CrosslinkPersistance store,
			@Named("pauseOnAbort") Integer pauseOnAbort,
			@Named("staleDays") Integer staleDays) {
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
	
	private boolean isForced() {
		return Arrays.asList(Mode.FORCED_NO_SKIP, Mode.FORCED).contains(getMode());		
	}
	
	public String getState() {
		return getMode().toString() + " " + getStatus().toString();
	}
	
	public Mode getMode() {
		return mode;
	}	

	// called from the UI
	public void setMode(String mode) throws Exception {
		setMode(Mode.valueOf(mode));
		if (Mode.DISABLED.equals(getMode()) && currentJob != null) {
			// try and interrupt the job
			currentJob.cancel(true);
		}
		if (isForced() && !isOk()) {
			// clear the bad status
			setStatus(Status.IDLE);
		}
	}
		
	public void setMode(Mode mode) throws Exception {
		this.mode = mode;
	}

	public Status getStatus() {
		return status;
	}
	
	public Date getStartDate() {
		return started;
	}
	
	// to be called from the UI
	public void pause() throws ExecutionException, InterruptedException {
		setStatus(Status.PAUSED);
	}
	
	public boolean isPaused() {
		return Status.PAUSED.equals(status);
	}
	
	private void setStatus(Status status) {
		this.status = status;
	}
	
	protected void setActiveStatus(Status status) throws Exception {
		if (isActive(status)) {
			setStatus(status);
		}
		else {
			throw new Exception("Illegal attempt to set status to " + status);
		}
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

	protected void setLastFoundAuthor(Researcher lastFoundAuthor) {
		this.lastFoundAuthor = lastFoundAuthor;
		foundCnt++;
	}

	public Researcher getLastFoundAuthor() {
		return lastFoundAuthor;
	}

	protected void setLastReadAuthor(Researcher lasteReadAuthor) {
		this.lastReadAuthor = lasteReadAuthor;
		readCnt++;
	}

	public Researcher getLastReadAuthor() {
		return lastReadAuthor;
	}

	public Researcher getLastSavedAuthor() {
		return lastSavedAuthor;
	}

	public String getLatestError() {
		return latestError;
	}

	protected void setLatestError(String latestError, Exception e) {
		this.latestError = latestError;
		LOG.log(Level.SEVERE, latestError, e);
	}
	
	public Affiliation getHarvester() {
		return harvester;
	}
	
	private void clear() {
		avoided.clear();
		error.clear();		
		foundCnt = 0;
		readCnt = 0;
		savedCnt = 0;
		skipCnt = 0;
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
	
	protected void addSkip(Researcher researcher) {
		skipCnt++;
	}

	protected void save(Researcher researcher) throws Exception {
		store.save(researcher);
		savedCnt++;
		lastSavedAuthor = researcher;
		LOG.log(Level.FINE, "Saved " + researcher);
	}
	
	public int getSavedCnt() {
		return savedCnt;
	}

	public Calendar getDateLastCrawled() {
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
			return new CrawlerStartStatus(minutesBetween > pauseOnAbort, "waited " + minutesBetween + " of " + pauseOnAbort + " minutes since " + getStatus());
		}
		else if (isForced()) {
			return new CrawlerStartStatus(true, "isForced");
		}
		else if (getDateLastCrawled() == null) {
			return new CrawlerStartStatus(true, "never finished crawl before");
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
	
	// require the implementing classes to set the status
	public void run() {
		if (!isOkToStart()) {
			return;
		}
		setCrawlingThread(Thread.currentThread());
		try {
			setStatus(Status.RUNNING);
			started = store.startCrawl(getHarvester()).getTime();
			ended = null;
			currentJob = executorService.submit(new Callable<Boolean>() {
		         public Boolean call() throws Exception {
		        	 setCrawlingThread(Thread.currentThread());
		             return crawl();
		         }});
			boolean removeMissing = currentJob.get();
			setCrawlingThread(Thread.currentThread());
			if (!isPaused()) {
				ended = store.finishCrawl(getHarvester()).getTime();
				if (removeMissing) {
					store.deleteMissingResearchers(getHarvester());
				}
				setStatus(Status.FINISHED);
				clear();
			}
			if (isForced()) {
				// don't leave in forced mode
				mode = Mode.ENABLED;
			}
		}
		catch (Exception e) {
			setStatus(Status.ERROR);
			setLatestError(e.getMessage(), e);
		}
		finally {
			if (ended == null) {
				ended = new Date();
			}
			setCrawlingThread(null);
		}
	}
	
	public String toString() {
		return getName() + " : " + getState() + " " + getDates() ; 
	}
		
	public String getCounts() {
		return "Found " + foundCnt + ", Read " + readCnt + ", Saved " + savedCnt + ", Skipped " + skipCnt + ", Error " + getErrors().size() + ", Avoids " + getAvoided().size();
	}

	public String getDates() {
		String retval = "LastFinish " + getDateLastCrawled();
		if (started == null) {
			return retval + ", Not started...";
		}
		else {
			return retval + ", Started " + started + ", Ended " + ended + " " + Minutes.minutesBetween(new DateTime(started), ended != null ? new DateTime(ended) : new DateTime()).getMinutes() + " minutes";
		}
	}

	public String getRates() {
		if (started == null) {
			return "Not started...";
		}
		int saved = Math.max(1, savedCnt);
		return "Saved/person : " +  PeriodFormat.getDefault().print(new Period((new Date().getTime() - getStartDate().getTime())/saved));
	}
	
	public abstract boolean crawl() throws Exception;
}
