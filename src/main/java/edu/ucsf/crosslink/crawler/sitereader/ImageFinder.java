package edu.ucsf.crosslink.crawler.sitereader;

import org.jsoup.nodes.Element;

public interface ImageFinder {
	String getImage(Element src);
}
