package edu.ucsf.crosslink.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

/**
 * Servlet Filter implementation class CrosslinksServletFilter
 */
@Singleton
public class CrosslinksServletFilter implements Filter {

	private static final String ADMINISTRATOR = "administrator";
	
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
            String email = ((HttpServletRequest)request).getHeader("Shibmail");  
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
		// TODO Auto-generated method stub
	}

	public static boolean isAdministrator(HttpServletRequest request) {
		return request.getAttribute(ADMINISTRATOR) != null;
	}


}
