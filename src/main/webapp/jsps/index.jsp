<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<body>
    <h2>Research Networking Coauthors Unbound</h2><p>
	<h3>List of indexed affiliations. Click and have fun!</h3><p>
	<ul>
	<c:forEach var="i" items="${affiliations}">
		<li>
		<a href = '<c:out value="${i.name}"/>'><c:out value="${i.name}"/></a>
		<c:out value="${i.researcherCount}"/> indexed researchers and <c:out value="${i.pmidCount}"/> PMID publications
		</li>
	</c:forEach>
	</ul>
	If you would like to find out more about this, please contact <a href="http://profiles.ucsf.edu/eric.meeks">Eric Meeks</a>				     

</body>
</html>
