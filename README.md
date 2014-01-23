To build this you will first need to install the SitemapParser0.9.jar into your maven repository
>mvn install:install-file -DgroupId=net.sourceforge -DartifactId=SitemapParser -Dversion=0.9 -Dpackaging=jar -Dfile=jar/SitemapParser0.9.jar

Run the main in ProfilesSiteReader.java and this will produce a CSV of the information you need to 
connect coauthors from the target site.  

Needs a version of profiles that has a sitemap with all the authors listed!

Can read HTML or RDF, but the HTML is much faster (two req/response per person ) 
than the RDF (two req/response per person-publication!)

Note that in either HTML or RDF case, RDF is used to parse the authors name.

Examples:
>java edu.ucsf.crosslink.ProfilesSiteReader HTML UCSF http://profiles.ucsf.edu/sitemap.xml

>java edu.ucsf.crosslink.ProfilesSiteReader HTML BU http://profiles.bu.edu/sitemap.xml