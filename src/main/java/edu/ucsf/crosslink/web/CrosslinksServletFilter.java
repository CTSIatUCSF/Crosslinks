package edu.ucsf.crosslink.web;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import edu.ucsf.crosslink.model.R2RResourceObject;

/**
 * Servlet Filter implementation class CrosslinksServletFilter
 */
@Singleton
public class CrosslinksServletFilter implements Filter {

	private static final Logger LOG = Logger.getLogger(CrosslinksServletFilter.class.getName());

	private static final String ADMINISTRATOR = "administrator";
	
	private String context;
	private Collection<String> administrators = new ArrayList<String>();

	@Inject
    public CrosslinksServletFilter(@Named("administrators") String[] administrators) {
		for (String administrator : administrators) {
			if (administrator.trim().length() > 0) {
				this.administrators.add(administrator.trim());
			}
		}
    }

	/**
	 * @see Filter#destroy()
	 */
	public void destroy() {
		// TODO Auto-generated method stub
	}

	/**
	 * @see Filter#doFilter(ServletRequest, ServletResponse, FilterChain)
	 */
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {  
        	// remove the trailing '/' if it is there, as it is messing up the links
        	HttpServletRequest servletRequest = (HttpServletRequest)request;
        	if (servletRequest.getRequestURI().endsWith("/")) {
        		String newPath = servletRequest.getRequestURI().substring(0, servletRequest.getRequestURI().length()-1);
        		if (context.equalsIgnoreCase(newPath)) {
        			newPath += "/index";
        		}
        		LOG.info("Redirecting " + servletRequest.getRequestURI() + " to " + newPath);
        		((HttpServletResponse)response).sendRedirect(newPath);
        		return;
        	}
            String eppn = servletRequest.getHeader("eppn");  
    		if (administrators.isEmpty() || (eppn != null && administrators.contains(eppn))) {
    			request.setAttribute(ADMINISTRATOR, eppn != null ? eppn : Boolean.TRUE.toString());
    			request.setAttribute("memoryStats", getMemoryStats());
    		}
        }		
		chain.doFilter(request, response);
	}
	
	/**
	 * @see Filter#init(FilterConfig)
	 */
	public void init(FilterConfig fConfig) throws ServletException {
		this.context = fConfig.getServletContext().getContextPath();
	}

	public static boolean isAdministrator(HttpServletRequest request) {
		return request.getAttribute(ADMINISTRATOR) != null;
	}
	
	public static String getMemoryStats() {
		Runtime runtime = Runtime.getRuntime();

	    NumberFormat format = NumberFormat.getInstance();

	    StringBuilder sb = new StringBuilder();
	    long maxMemory = runtime.maxMemory();
	    long allocatedMemory = runtime.totalMemory();
	    long freeMemory = runtime.freeMemory();

	    sb.append("free memory: " + format.format(freeMemory / 1024) + "<br/>");
	    sb.append("allocated memory: " + format.format(allocatedMemory / 1024) + "<br/>");
	    sb.append("max memory: " + format.format(maxMemory / 1024) + "<br/>");
	    sb.append("total free memory: " + format.format((freeMemory + (maxMemory - allocatedMemory)) / 1024) + "<br/>");
	    sb.append("R2RResourceObject count: " + R2RResourceObject.getObjectCount() + "<br/>");
	    return sb.toString();
	}


}
