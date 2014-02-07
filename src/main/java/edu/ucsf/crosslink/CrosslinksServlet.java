package edu.ucsf.crosslink;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.inject.Singleton;

import edu.ucsf.crosslink.quartz.AffiliationCrawlerJob;


/**
 * Servlet implementation class CrosslinksServlet
 */
@Singleton
public class CrosslinksServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		for (AffiliationCrawler crawler : AffiliationCrawler.getLiveCrawlers()) {
			response.getOutputStream().println(crawler.toString());
		}
		response.getOutputStream().println();
		for (String history : AffiliationCrawlerJob.getCrawlerJobHistory()) {
			response.getOutputStream().println(history);
		}
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}
	
}
