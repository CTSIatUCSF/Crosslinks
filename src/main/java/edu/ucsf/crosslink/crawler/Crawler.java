package edu.ucsf.crosslink.crawler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.time.StopWatch;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Minutes;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.crawler.TypedOutputStats.OutputType;
import edu.ucsf.crosslink.io.CrosslinkPersistance;
import edu.ucsf.crosslink.model.R2RResourceObject;
import edu.ucsf.crosslink.processor.MarengoDetailProcessor;
import edu.ucsf.crosslink.processor.ResearcherProcessor;
import edu.ucsf.ctsi.r2r.R2RConstants;

public final class Crawler extends R2RResourceObject implements Runnable, Comparable<Crawler>, R2RConstants {

	private static final Logger LOG = Logger.getLogger(MarengoDetailProcessor.class.getName());
	
	private int MAX_QUEUE_SIZE = 100;
	private long SLEEP_TIME_MILLIS = 10000;

	private Mode mode = Mode.ENABLED;
	private Status status = Status.IDLE;

	private int errorsToAbort = 5;
	private int pauseOnAbort = 60;
	private int staleDays = 7;
	private AtomicInteger consecutiveErrorCnt = new AtomicInteger();

	private Date started = null;
	private Date ended = null;
	private CrawlerStartStatus lastStartStatus;

	private Exception latestErrorException = null;
	private CrosslinkPersistance store;
	private Iterable<ResearcherProcessor> researcherIterable = null;	
	private Iterator<ResearcherProcessor> currentIterator = null;	

	private Map<OutputType, TypedOutputStats> stats = new HashMap<OutputType, TypedOutputStats>();
	private AtomicInteger queueSize = new AtomicInteger();
	
	private Thread crawlingThread = null;
	private ExecutorService executorService = null;
	private Future<Boolean> currentJob = null;
	
	@Inject
	public Crawler(@Named("FileName") String name, Mode mode, CrosslinkPersistance store, 
			Iterable<ResearcherProcessor> researcherIterable, @Named("executorThreadCount") Integer threadCount) throws Exception {
		super(R2R_CRAWLER + "/" + name, R2R_CRAWLER);
		this.setLabel(name);
		this.mode = mode;	
		this.store = store;
		this.researcherIterable = researcherIterable;
		store.update(this);
		if (threadCount >= 0) {
			executorService = threadCount > 0 ? Executors.newFixedThreadPool(threadCount) : Executors.newCachedThreadPool();
		}
		clear();
	}
	
	@Inject
	public void setConfiguartion(@Named("errorsToAbort") Integer errorsToAbort,
			@Named("pauseOnAbort") Integer pauseOnAbort,
			@Named("staleDays") Integer staleDays) {
		this.errorsToAbort = errorsToAbort;
		this.pauseOnAbort = pauseOnAbort;
		this.staleDays = staleDays;
	}

	public enum Status {
		ERROR, PAUSED, FINISHED, IDLE, RUNNING, SHUTTING_DOWN;
	}
	
	public enum Mode {
		ENABLED, DISABLED, FORCED, FORCED_NO_SKIP, DEBUG;
	}
	
	public boolean isActive() {
		return isActive(getStatus());
	}
	
	private static boolean isActive(Status status) {
		return Arrays.asList(Status.SHUTTING_DOWN, Status.RUNNING).contains(status);
	}

	public boolean isOk() {
		return !Arrays.asList(Status.PAUSED, Status.ERROR).contains(getStatus());
	}
	
	private boolean isForced() {
		return Arrays.asList(Mode.FORCED_NO_SKIP, Mode.FORCED).contains(getMode());		
	}
	
	public boolean allowSkip() {
		return !Mode.FORCED_NO_SKIP.equals(getMode());
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

	// called from the UI
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

	private void addOutput(OutputType type, Object message) {
		addOutput(type, message.toString(), 0);
	}

	private void addOutput(OutputType type, Object message, long time) {
		stats.get(type).push(message.toString(), time);
	}

	private void addUnhandledException(Object message, Exception e) {
		addOutput(OutputType.ERROR, message);
		consecutiveErrorCnt.incrementAndGet();
		if (consecutiveErrorCnt.get() > errorsToAbort) {
			setStatus(Status.ERROR);
		}
		if (e != null) {
			this.latestErrorException = e;
			LOG.log(Level.WARNING, message.toString(), e);
		}
	}
	
	public OutputType[] getOutputTypes() {
		return OutputType.values();
	}
	
	public TypedOutputStats getOutputStats(OutputType type) {
		return stats.get(type);
	}
	
	public Collection<TypedOutputStats> getOutputStatsList() {
		return stats.values();
	}
	
	public String getLatestErrorStackTrace() {
		if (latestErrorException != null) {
			if (latestErrorException.getCause() != null) {
				return showThrowable(latestErrorException) + "<br/><br/>Inner Throwable<br/>" + 
						showThrowable(latestErrorException.getCause());
			}
			else {
				return showThrowable(latestErrorException);
			}
		}
		return null;
	}

	private static String showThrowable(Throwable t) {
		StringWriter sw = new StringWriter();
		PrintWriter writer = new PrintWriter(sw);
		writer.println(t.getMessage() + "<br/>");
		for (StackTraceElement ste : t.getStackTrace()) {
			writer.println(ste.toString() + "<br/>");
		}
		t.printStackTrace(writer);
		writer.flush();
		return sw.toString();
	}

	private void clear() {
		// clear the stats and the curent iterator
		currentIterator = null;
		for (OutputType type : OutputType.values()) {
			stats.put(type, new TypedOutputStats(type, 100));
		}
	}

	// TODO use in memory ended if that will work
	public Calendar getDateLastCrawled() {
		try {
			return store.dateOfLastCrawl(this);
		} 
		catch (Exception e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
		}
		return null;
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
    	// always put active ones in the front, then ones in trouble
    	if (this.isActive() != other.isActive()) {
    		return this.isActive() ? -1 : 1;
    	}	    	
    	else if (this.isOk() != other.isOk()) {
    		return this.isOk() ? 1 : -1;
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
		if (isOk()) {
			// do not clear stats if we are resuming from a pause or error
			clear();				
		}
		setStatus(Status.RUNNING);
		try {
			started = store.startCrawl(this).getTime();
			ended = null;
			// restart old iterator if you can, otherwise grab a fresh one
			currentIterator = currentIterator != null ? currentIterator : researcherIterable.iterator();
			while (isOk() && currentIterator.hasNext()) {
				// don't let the queue get too big!!!
				if (executorService != null && queueSize.get() > MAX_QUEUE_SIZE) {
					Thread.sleep(SLEEP_TIME_MILLIS);
					continue;
				}
				ResearcherProcessor rp = currentIterator.next();
				rp.setCrawler(this);
				addOutput(OutputType.FOUND, rp);
				QueuedRunnable qr = new QueuedRunnable(rp);
				if (executorService != null) {
					executorService.submit(qr);
				}
				else {
					// run in line
					qr.run();
				}
			}
			if (isOk()) {
				setStatus(Status.SHUTTING_DOWN);
				if (executorService != null) {
					executorService.shutdown();
					executorService.awaitTermination(10, TimeUnit.MINUTES);
				}
				ended = store.finishCrawl(this).getTime();
				setStatus(Status.FINISHED);
			}
		}
		catch (Exception e) {			
			addUnhandledException("Error while iterating over researchers", e);
			setStatus(Status.ERROR);
		}
		if (isForced()) {
			// don't leave in forced mode
			mode = Mode.ENABLED;
		}
		if (null == ended) {
			ended = new Date();
		}
		setCrawlingThread(null);
	}
	
	public Iterable<ResearcherProcessor> getIterable() {
		return researcherIterable;
	}
	
	public String toString() {
		return getName() + " : " + getState() + " " + getDates()  + " Iterable : " + researcherIterable.toString(); 
	}
		
	public String getCounts() {
		String retval = "Queue = " + queueSize.get();
		for (TypedOutputStats output : getOutputStatsList()) {
			retval += ", " + output.toString();
		}
		return retval;
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
		if (stats.get(OutputType.PROCESSED).getCount() == 0) {
			return "None yet...";
		}
		else {
			int processed = stats.get(OutputType.PROCESSED).getCount();
			return "Throughput processed/person : " +  PeriodFormat.getDefault().print(new Period((new Date().getTime() - getStartDate().getTime())/processed));			
		}
	}
		
	private final class QueuedRunnable implements Runnable {
		private ResearcherProcessor researcherProcessor = null;
		
		private QueuedRunnable(ResearcherProcessor researcherProcessor) {
			queueSize.incrementAndGet();
			this.researcherProcessor = researcherProcessor;
		}
		
		public void run() {
			try {
				StopWatch sw = new StopWatch();
				sw.start();
				ResearcherProcessor.Action action = researcherProcessor.processResearcher();
				sw.stop();
				consecutiveErrorCnt.set(0);
				if (ResearcherProcessor.Action.AVOIDED.equals(action)) {
					addOutput(OutputType.AVOIDED, researcherProcessor);
				}
				else if (ResearcherProcessor.Action.SKIPPED.equals(action)) {
					addOutput(OutputType.SKIPPED, researcherProcessor);
				}
				else if (ResearcherProcessor.Action.PROCESSED.equals(action)) {
					addOutput(OutputType.PROCESSED, researcherProcessor, sw.getTime());					
				}
				else if (ResearcherProcessor.Action.ERROR.equals(action)) {
					addOutput(OutputType.ERROR, researcherProcessor);					
				}
			}
			catch (Exception e) {
				addUnhandledException(researcherProcessor, e);
			}
			finally {
				queueSize.decrementAndGet();
			}
		}
	}
	
}
