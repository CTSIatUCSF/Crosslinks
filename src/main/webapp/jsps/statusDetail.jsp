<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<body>
	<a href='.'>Back</a><p>
    <c:out value="${crawler}"/><p>
    Mode : <c:out value="${crawler.mode}"/>
	<c:if test="${not empty administrator}">
	    &nbsp;<a href='?mode=ENABLED'>ENABLE</a>&nbsp;<a href='?mode=DISABLED'>DISABLE</a>&nbsp;<a href='?mode=FORCED'>FORCE</a>&nbsp;<a href='?mode=FORCED_NO_SKIP'>FORCE AND RELOAD EVERYONE</a>
	</c:if><p>    
    Status : <c:out value="${crawler.status}"/><p>
    Latest Error : <c:out value="${crawler.latestError}"/><p>
    Current author : <c:out value="${crawler.currentAuthor}"/><p>
	
    <h2>Errors</h2>
	<c:forEach var="e" items="${crawler.errors}">
	   <c:out value="${e}"/><p>
	</c:forEach>

    <h2>Avoids</h2>
	<c:forEach var="a" items="${crawler.avoided}">
	   <c:out value="${a}"/><p>
	</c:forEach>
	Stack trace : <c:out value="${crawler.currentStackTrace}" escapeXml="false"/><p>
</body>
</html>
