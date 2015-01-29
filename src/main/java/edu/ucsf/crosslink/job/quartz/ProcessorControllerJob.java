package edu.ucsf.crosslink.job.quartz;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.UnableToInterruptJobException;

import com.google.inject.Inject;

import edu.ucsf.crosslink.processor.controller.ProcessorController;

@DisallowConcurrentExecution
public class ProcessorControllerJob implements InterruptableJob {
	
	private static final Logger LOG = Logger.getLogger(ProcessorControllerJob.class.getName());

	private ProcessorController processorController;
	private Thread currentExecutionThread;
	
	@Inject
	public ProcessorControllerJob(ProcessorController processorController) {
		this.processorController = processorController;
	}
	
	public void execute(JobExecutionContext context)
			throws JobExecutionException {
		try {
			currentExecutionThread = Thread.currentThread();
			processorController.run();
		} 
		catch (Exception e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
			throw new JobExecutionException(e);
		}
		currentExecutionThread = null;
	}

	public void interrupt() throws UnableToInterruptJobException {
		if (currentExecutionThread != null) {
			currentExecutionThread.interrupt();
		}
		
	}

}
