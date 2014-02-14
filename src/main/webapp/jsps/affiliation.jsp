<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<html>
<body>
	<a href="../">Home</a><p>	
	<a href = '<c:out value="${affiliation.baseURL}"/>'><c:out value="${affiliation.name}"/> Research Networking Site</a>  
						<c:out value="${affiliation.researcherCount}"/> researchers indexed, <c:out value="${affiliation.pmidCount}"/> PUBMED publications found<p>
	<a href = '<c:out value="${affiliation.name}"/>/researchers'>Indexed researchers from <c:out value="${affiliation.name}"/></a><p> 
	<p>Links to help us clean up our data.  Once our data is all clean, these should not return any results.  Today, they return a bunch of results for most affiliations.<p>				
	List of researchers at other affiliations that we think are also in <c:out value="${affiliation.name}"/>&nbsp;
	<a href = '<c:out value="${affiliation.name}"/>/possibleSamePeople?format=CSV'> (CSV)</a>&nbsp; 
	<a href = '<c:out value="${affiliation.name}"/>/possibleSamePeople?format=JSON'> (JSON)</a><p>
	 
	List of potential disambiguation conflicts for <c:out value="${affiliation.name}"/>&nbsp; 
	<a href = '<c:out value="${affiliation.name}"/>/possibleConflicts?format=CSV''>(CSV)</a>&nbsp;
	<a href = '<c:out value="${affiliation.name}"/>/possibleConflicts?format=JSON''>(JSON)</a><p>

	<p>Note that matches for possibleSamePeople and possibleConflicts are based on name and matching publications.  If a person at one affiliation has the same
	last name and shares overlapping publications with a research at another affiliation with a 
	'similar' first name, they are included in the possibleSamePeople list.  If the first name is  
	'not similar', then we list them in possibleConflicts. <p>
	By 'similar', we mean: <p>
	<code>(len(a1.firstName)< LEN(a2.firstName) AND LEFT(a1.firstName, len(a1.firstName)) = LEFT(a2.firstName, len(a1.firstName))) OR 
		(LEFT(a1.firstName, len(a2.firstName)) = LEFT(a2.firstName, len(a2.firstName))) </code><p>
		By 'not similar', we mean the opposite:<p>
	<code>(len(a1.firstName)< LEN(a2.firstName) AND LEFT(a1.firstName, len(a1.firstName)) != LEFT(a2.firstName, len(a1.firstName))) AND 								  
		(LEFT(a1.firstName, len(a2.firstName)) != LEFT(a2.firstName, len(a2.firstName))) </code><p>
	At some point we want to formally recognize when someone at one affiliation is the same person at another affiliation, and we will  
	make that available when we have that data.<p>
	Please note that the list of coauthors WILL include any possibleSamePeople and possibleConflicts.
</body>
</html>
