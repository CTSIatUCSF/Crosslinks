package edu.ucsf.crosslink.quartz;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import com.google.inject.Inject;
import com.google.inject.Injector;

import edu.ucsf.crosslink.Crosslinks;
import edu.ucsf.crosslink.crawler.AffiliationCrawlerModule;

public class GuiceJobFactory implements JobFactory {

	private static final Logger LOG = Logger.getLogger(GuiceJobFactory.class.getName());

	private final Injector guice;
	private Map<String, Injector> crawlers = new HashMap<String, Injector>(); 

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
			// at some point we should figure out a way to refresh the configuration.  For now, this is good
			// we want crawlers to stick around
			if (!crawlers.containsKey(config)) {
				Properties prop = new Properties();
				prop.load(this.getClass().getResourceAsStream(Crosslinks.PROPERTIES_FILE));	
				prop.load(new FileReader(new File(config)));
				crawlers.put(config,  guice.createChildInjector(new AffiliationCrawlerModule(prop)));
			}
			//  AffiliationCrawlerJob is never bound??? 
			return crawlers.get(config).getInstance(AffiliationCrawlerJob.class);
		} 
		catch (Exception e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
			throw new SchedulerException(e);
		}
	}
}
