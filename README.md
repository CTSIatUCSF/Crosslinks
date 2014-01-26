To build this you will first need to install the SitemapParser0.9.jar into your maven repository
>mvn install:install-file -DgroupId=net.sourceforge -DartifactId=SitemapParser -Dversion=0.9 -Dpackaging=jar -Dfile=jar/SitemapParser0.9.jar

Run the main in Crosslinks.java and this will produce a CSV of the information you need to 
connect coauthors from the target site.  

Pass in one argument, and that will be used to read a file of the properties file
Examples:
>java edu.ucsf.crosslink.Crosslinks config/Stanford.properties

>java edu.ucsf.crosslink.Crosslinks config/Iowa.properties