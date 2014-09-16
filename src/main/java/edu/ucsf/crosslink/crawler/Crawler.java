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
import edu.ucsf.crosslink.model.R2RResourceObject;
import edu.ucsf.crosslink.model.Researcher;
import edu.ucsf.ctsi.r2r.R2RConstants;

public abstract class Crawler extends R2RResourceObject implements Runnable, Comparable<Crawler>, R2RConstants {

	private static final Logger LOG = Logger.getLogger(MarengoDetailCrawler.class.getName());

	private Mode mode = Mode.ENABLED;
	private Status status = Status.IDLE;

	private int pauseOnAbort = 60;
	private int staleDays = 7;

	private Date started = null;
	private Date ended = null;
	private CrawlerStartStatus lastStartStatus;

	// because it is expensive to create a Researcher object, just use the URI for the found one as we may end up not needing to build the object
	private String lastFoundResearcher = null;
	private Researcher lastSavedResearcher = null;

	private String latestError = null;
	private Exception latestErrorException = null;
	private CrosslinkPersistance store;

	private FixedSizeList<String> avoided = new FixedSizeList<String>(100);
	private FixedSizeList<String> error = new FixedSizeList<String>(100);
	private int avoidedCnt = 0;
	private int errorCnt = 0;
	private int foundCnt = 0;
	private int savedCnt = 0;
	private int skipCnt = 0;
	
	private Thread crawlingThread = null;
	private ExecutorService executorService = null;
	private Future<Boolean> currentJob = null;
	
	public Crawler(String name, Mode mode, CrosslinkPersistance store) throws Exception {
		super(R2R_CRAWLER + "/" + name.replace(' ', '_'), R2R_CRAWLER);
		this.setLabel(name);
		this.mode = mode;
		this.store = store;
		store.update(this);
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
	
	protected CrosslinkPersistance getStore() {
		return store;
	}
	
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
		if (isForced() && !isActive()) {
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

	protected void setLastFoundResearcher(String lastFoundResearcher) {
		this.lastFoundResearcher = lastFoundResearcher;
		foundCnt++;
	}

	public String getlastFoundResearcher() {
		return lastFoundResearcher;
	}

	public Researcher getLastSavedResearcher() {
		return lastSavedResearcher;
	}

	public String getLatestError() {
		return latestError;
	}
	
	public String getLatestErrorStackTrace() {
		if (latestErrorException != null) {
			StringWriter sw = new StringWriter();
			PrintWriter writer = new PrintWriter(sw);
			latestErrorException.printStackTrace(writer);
			writer.flush();
			return sw.toString();
		}
		return null;
	}

	protected void setLatestError(String latestError, Exception e) {
		this.latestError = latestError;
		if (e != null) {
			this.latestErrorException = e;
			LOG.log(Level.SEVERE, latestError, e);
		}
	}
	
	private void clear() {
		avoided.clear();
		error.clear();		
		avoidedCnt = 0;
		errorCnt = 0;
		foundCnt = 0;
		savedCnt = 0;
		skipCnt = 0;
	}

	public List<String> getErrors() {
		return error;
	}
	
	public List<String> getAvoided() {
		return avoided;
	}
	
	protected void addError(String researcherURI) {
		errorCnt++;
		error.push(researcherURI);
	}
	
	protected void addAvoided(String researcherURI) {
		avoidedCnt++;
		avoided.push(researcherURI);
	}
	
	protected void addSkip(String researcherURI) {
		skipCnt++;
	}

	protected void save(Researcher researcher) throws Exception {
		store.save(researcher);
		savedCnt++;
		lastSavedResearcher = researcher;
		LOG.log(Level.FINE, "Saved " + researcher);
	}
	
	protected void update(Researcher researcher) throws Exception {
		update(researcher, null);
	}
	
	protected void update(Researcher researcher, List<String> preStatements) throws Exception {
		store.update(researcher, preStatements);
		savedCnt++;
		lastSavedResearcher = researcher;
		LOG.log(Level.FINE, "Updated " + researcher);
	}

	public int getSavedCnt() {
		return savedCnt;
	}

	// TODO use in memory ended if that will work
	public Calendar getDateLastCrawled() {
		return store != null ? store.dateOfLastCrawl(this) : null;
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
    	if (this.isActive() != other.isActive()) {
    		return this.isActive() ? -1 : 1;
    	}	    	

    	CrawlerStartStatus astatus = this.getLastStartStatus();
    	CrawlerStartStatus bstatus = other.getLastStartStatus();	    	
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
			if (isOk()) {
				// do not clear stats if we are resuming from a pause or error
				clear();				
			}
			setStatus(Status.RUNNING);
			started = store.startCrawl(this).getTime();
			ended = null;
			currentJob = executorService.submit(new Callable<Boolean>() {
		         public Boolean call() throws Exception {
		        	 setCrawlingThread(Thread.currentThread());
		             return crawl();
		         }});
			boolean removeMissing = currentJob.get();
			setCrawlingThread(Thread.currentThread());
			if (!isPaused()) {
				ended = store.finishCrawl(this).getTime();
				if (removeMissing) {
					store.deleteMissingResearchers(this);
				}
				setStatus(Status.FINISHED);
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
		return "Found " + foundCnt + ", Saved " + savedCnt + ", Skipped " + skipCnt + ", Error " + errorCnt + ", Avoids " + avoidedCnt;
	}

	public String getDates() {
		String retval = "";
		if (getDateLastCrawled() != null) {
			retval += "LastFinish " + getDateLastCrawled().getTime() + ", ";			
		}
		if (started == null) {
			return retval + "Not started...";
		}
		else {
			retval += "Started " + started;
		}
		if (ended != null) {
			retval += ", Ended " + ended;
		}
		Date endTime = ended != null ? ended : new Date();
		return retval + ", Duration " + PeriodFormat.getDefault().print(new Period(endTime.getTime() - started.getTime())); 
	}

	public String getRates() {
		if (started == null) {
			return "Not started...";
		}
		int saved = Math.max(1, savedCnt);
		return "Saved/person : " +  PeriodFormat.getDefault().print(new Period((new Date().getTime() - getStartDate().getTime())/saved));
	}
	
	public abstract boolean crawl() throws Exception;
	
	@SuppressWarnings("serial")
	private static class FixedSizeList<T> extends ArrayList<T> {
		private int limit = 100;
		
		private FixedSizeList(int limit) {
			this.limit = limit;
		}
		
		public synchronized void push(T t) {
			add(0, t);
			if (size() > limit) {
				remove(size() - 1);
			}
		}
	}
}
