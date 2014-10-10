<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<html>
<body>
	<a href=".">Home</a><p>	
	<a href = '<c:out value="${affiliation.URI}"/>'><c:out value="${affiliation.name}"/> Research Networking Site</a><p>						
	<a href = '<c:out value="${affiliation.name}"/>/researchers'>Indexed researchers from <c:out value="${affiliation.name}"/></a>&nbsp;
		<c:out value="${affiliation.researcherCount}"/> researchers indexed with <c:out value="${affiliation.publicationCount}"/> publications<p>
	<p>Links to help us clean up our data.<p>				
	<a href = '<c:out value="${affiliation.name}"/>/status'>Status of last crawl for <c:out value="${affiliation.name}"/></a><p> 

	List of researchers at other affiliations that we think are also in <c:out value="${affiliation.name}"/>&nbsp;
	<a href = '<c:out value="${affiliation.name}"/>/possibleSamePeople?format=CSV'> (CSV)</a>&nbsp; 
	<a href = '<c:out value="${affiliation.name}"/>/possibleSamePeople?format=JSON'> (JSON)</a><p>
	 
	<p>Note that matches for possibleSamePeople are based on name and matching publications.  If a person at one affiliation has the same
	last name and shares overlapping publications with a research at another affiliation with a 
	'similar' first name, they are included in the possibleSamePeople list.<p>
	By 'similar first name', we mean that they are exactly the same, or one is just an initial that is a match for the other<p>
	Please note that the list of coauthors WILL include any possibleSamePeople.<p>
</body>
</html>
