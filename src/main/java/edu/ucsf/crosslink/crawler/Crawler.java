package edu.ucsf.crosslink.crawler;

public interface Crawler extends Runnable, Comparable<Crawler>{

	String getName();
	void setMode(String mode) throws Exception;
}
