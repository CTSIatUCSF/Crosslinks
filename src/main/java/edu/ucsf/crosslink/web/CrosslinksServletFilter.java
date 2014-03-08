package edu.ucsf.crosslink.web;

import java.io.IOException;
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
            String email = servletRequest.getHeader("Shibmail");  
    		if (administrators.isEmpty() || (email != null && administrators.contains(email))) {
    			request.setAttribute(ADMINISTRATOR, email != null ? email : Boolean.TRUE.toString());
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


}