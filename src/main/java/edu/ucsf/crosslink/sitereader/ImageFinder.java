package edu.ucsf.crosslink.sitereader;

import org.jsoup.nodes.Element;

public interface ImageFinder {
	String getImage(Element src);
}
