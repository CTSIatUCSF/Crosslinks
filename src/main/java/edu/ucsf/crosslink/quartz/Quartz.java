package edu.ucsf.crosslink.quartz;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;


import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.AffiliationCrawler;
import edu.ucsf.crosslink.Crosslinks;
import edu.ucsf.crosslink.io.DBModule;

public class Quartz {

	private static final Logger LOG = Logger.getLogger(Quartz.class.getName());

	private final Scheduler scheduler;
	private String configurationDirectory;

	public static void main(String[] args) {
		try {
			Properties prop = new Properties();
			prop.load(AffiliationCrawler.class.getResourceAsStream(Crosslinks.PROPERTIES_FILE));			
			Quartz quartz = Guice.createInjector(new DBModule(prop), new QuartzModule(prop)).getInstance(Quartz.class);
			quartz.shutdown();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Inject
	public Quartz(final SchedulerFactory factory,
			final GuiceJobFactory jobFactory, @Named("configurationDirectory") String configurationDirectory,
			@Named("runInterval") Integer runInterval) throws SchedulerException {
		this.configurationDirectory = configurationDirectory;
		scheduler = factory.getScheduler();
		scheduler.setJobFactory(jobFactory);
		
		try {
			for (String fileName : getConfigurationFiles()) {
				// define the job and tie it to our HelloJob class
			    JobDetail job = newJob(AffiliationCrawlerJob.class)
			        .withIdentity(fileName, "group1")
			        .usingJobData("config", fileName)
			        .build();

			    // Trigger the job to run now, and then repeat every 40 seconds
			    Trigger trigger = newTrigger()
			        .withIdentity(fileName, "group1")
			        .startNow()
			        .withSchedule(simpleSchedule()
			        		.withIntervalInMinutes(runInterval)
			                .repeatForever())            
			        .build();

			    scheduler.scheduleJob(job, trigger);			
			}
		} 
		catch (IOException e) {
			throw new SchedulerException(e);
		}
		scheduler.start();
	}

	public final Scheduler getScheduler() {
		return scheduler;
	}

	public void shutdown() {
		try {
			scheduler.shutdown();
		} catch (SchedulerException e) {
			// ... handle it
			LOG.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	private Set<String> getConfigurationFiles() throws IOException  {
		Set<String> fileNames = new HashSet<String>();
		for (File file : new File(configurationDirectory).listFiles()) {
			fileNames.add(file.getAbsolutePath());
		}
		return fileNames;
	}
	

}
