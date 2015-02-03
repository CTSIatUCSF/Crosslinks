package edu.ucsf.crosslink.io.http;

import org.jsoup.nodes.Element;

public interface ImageFinder {
	String getImage(Element src);
}
