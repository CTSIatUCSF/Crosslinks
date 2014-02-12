<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<html>
<body>
	<h2>List of ${fn:length(researchers)} reseachers indexed from <c:out value="${affiliation.name}"/></h2>
	<ul>
	<c:forEach var="r" items="${researchers}">
		<li>
		<a href = '<c:out value="${r.URL}"/>'><c:out value="${r.name}"/> at <c:out value="${affiliation.name}"/></a>&nbsp;
		<c:if test="${r.orcidId != null}">
			<a href = 'http://orcid.org/<c:out value="${r.orcidId}"/>'>Orcid profile for <c:out value="${r.name}"/></a>&nbsp;
		</c:if>
		<c:if test="${r.externalCoauthorCount > 0}">
			List of <c:out value="${r.externalCoauthorCount}"/> external co-authors and PMID's for <c:out value="${r.name}"/>&nbsp;
			<a href = '../coauthors?authorURL=<c:out value="${r.URL}"/>&format=CSV'> (CSV)</a>&nbsp;
			<a href = '../coauthors?authorURL=<c:out value="${r.URL}"/>&format=JSON'> (JSON)</a>
		</c:if>
		</li>
	</c:forEach>
	</ul>
</body>
</html>
