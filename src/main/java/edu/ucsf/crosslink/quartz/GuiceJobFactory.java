package edu.ucsf.crosslink.quartz;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import com.google.inject.Inject;
import com.google.inject.Injector;

import edu.ucsf.crosslink.AffiliationCrawler;
import edu.ucsf.crosslink.AffiliationCrawlerModule;
import edu.ucsf.crosslink.Crosslinks;

public class GuiceJobFactory implements JobFactory {

	private final Injector guice;

	@Inject
	public GuiceJobFactory(final Injector guice) throws IOException {
		this.guice = guice;
	}
	
	@Override
	public Job newJob(final TriggerFiredBundle bundle, final Scheduler schedular)
			throws SchedulerException {
		JobDetail jobDetail = bundle.getJobDetail();
		try {
			String config = jobDetail.getJobDataMap().getString("config");
			Properties prop = new Properties();
			prop.load(this.getClass().getResourceAsStream(Crosslinks.PROPERTIES_FILE));	
			prop.load(new FileReader(new File(config)));
			//  AffiliationCrawlerJob is never bound??? 
			return guice.createChildInjector(new AffiliationCrawlerModule(prop)).getInstance(AffiliationCrawlerJob.class);
		} 
		catch (Exception e) {
			throw new SchedulerException(e);
		}
	}
}
