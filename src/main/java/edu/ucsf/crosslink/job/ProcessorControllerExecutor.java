package edu.ucsf.crosslink.job;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.processor.controller.ProcessorController;
import edu.ucsf.crosslink.processor.controller.ProcessorControllerFactory;
import edu.ucsf.crosslink.web.Stoppable;

@Singleton
public class ProcessorControllerExecutor implements Runnable, Stoppable {
	private static final Logger LOG = Logger.getLogger(ProcessorControllerExecutor.class.getName());
	
	private ProcessorControllerFactory factory;
	ScheduledExecutorService executorService;
	
	@Inject
	public ProcessorControllerExecutor(ProcessorControllerFactory factory, @Named("scanInterval") Integer scanInterval, @Named("threadCount") Integer threadCount) {
		this.factory = factory;
		
		// pass into some scheduled loader
		executorService = Executors.newScheduledThreadPool(threadCount, new ThreadFactory() {
			   public Thread newThread(Runnable runnable) {
			      Thread thread = Executors.defaultThreadFactory().newThread(runnable);
			      thread.setDaemon(true);
			      return thread;
			   }
			});
		
    	executorService.scheduleAtFixedRate(this, 0, scanInterval, TimeUnit.SECONDS);    	
	}

	public void run() {
		try {
		    for (ProcessorController processorController : factory.getCrawlers()) {
	    		executorService.execute(processorController);
		    }
		}
		catch (Exception e) {
			LOG.log(Level.WARNING, e.getMessage(), e);
		}
	}
	
	public void shutdown() {
		executorService.shutdownNow();
	}

}
