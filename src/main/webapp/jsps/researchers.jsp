<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<c:set var="rowsPerPage" value="100" />
<c:set var="pageNumber" value="1"/>
<c:set var="a">
    <fmt:formatNumber value="${fn:length(researchers)/rowsPerPage}" maxFractionDigits="0"/>
</c:set>

<c:set var="b" value="${fn:length(researchers)/rowsPerPage}" />

<c:if test="${not empty param.pageNumber}">
	<c:set var="pageNumber" value="${param.pageNumber}"/>
</c:if>
 
<c:choose>
    <c:when test="${a==0}">
        <c:set var="numberOfPages" value="1" scope="session"/>   
    </c:when>
 
    <c:when test="${b>a}">
        <c:set var="xxx" value="${b%a}"/>
        <c:if test="${xxx>0}">
            <c:set var="numberOfPages" value="${b-xxx+1}" scope="session"/>   
        </c:if>
    </c:when>
 
    <c:when test="${a>=b}">
        <c:set var="numberOfPages" value="${a}" scope="session"/>    
    </c:when>
</c:choose>
 
<c:set var="start" value="${pageNumber*rowsPerPage-rowsPerPage}"/>
<c:set var="stop" value="${pageNumber*rowsPerPage-1}"/>

<html>
<body>
	<h2>List of ${fn:length(researchers)} researchers indexed from <c:out value="${affiliation.name}"/></h2>
	<a href="../">Home</a><p>	
	<a href="?clearCache=true">Refresh results from the database</a><p>	

    <%--For displaying Previous link --%>
    <c:if test="${pageNumber gt 1}">
        <a href="?pageNumber=${pageNumber - 1}">Previous</a>
    </c:if>
    <c:forEach begin="1" end="${numberOfPages}" var="i">
        <c:choose>
            <c:when test="${i!=pageNumber}">
                <a href="?pageNumber=<c:out value="${i}"/>"><c:out value="${i}"/></a>
            </c:when>
            <c:otherwise>
                <c:out value="${i}"/>
            </c:otherwise>        
        </c:choose>       
    </c:forEach>  
    <%--For displaying Next link --%>
    <c:if test="${pageNumber lt numberOfPages}">
        <a href="?pageNumber=${pageNumber + 1}">Next</a>
    </c:if>

	<ul>
	<c:forEach var="r" items="${researchers}" begin="${start}" end="${stop}">
		<li>
		<c:if test="${r.thumbnailURL != null}">
			<img src='<c:out value="${r.thumbnailURL}"/>' width='20'/>&nbsp;
		</c:if>
		<a href = '<c:out value="${affiliation.baseURL}"/><c:out value="${r.homePageURL}"/>'><c:out value="${r.name}"/> at <c:out value="${affiliation.name}"/></a>&nbsp;
		<c:if test="${r.orcidId != null}">
			<a href = 'http://orcid.org/<c:out value="${r.orcidId}"/>'>Orcid profile for <c:out value="${r.name}"/></a>&nbsp;
		</c:if>
		<c:if test="${r.externalCoauthorCount > 0}">
			List of <c:out value="${r.externalCoauthorCount}"/> external co-authors and <c:out value="${r.sharedPublicationCount}"/> shared publications for <c:out value="${r.name}"/>&nbsp;
			<a href = '../coauthors?researcherURI=<c:out value="${r.URI}"/>&format=JSON-LD'> (JSON-LD)</a>
			<a href = '../coauthors?researcherURI=<c:out value="${r.URI}"/>&format=CSV'> (CSV)</a>&nbsp;
			<a href = '../coauthors?researcherURI=<c:out value="${r.URI}"/>&format=XML'> (XML)</a>&nbsp;
			<a href = '../coauthors?researcherURI=<c:out value="${r.URI}"/>&format=JSON'> (JSON)</a>&nbsp;
		</c:if>
		</li>
	</c:forEach>
	</ul>
</body>
</html>


