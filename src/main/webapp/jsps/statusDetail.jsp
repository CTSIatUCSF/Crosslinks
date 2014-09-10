<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<body>
	<a href='.'>Back</a><p>
    <c:out value="${crawler}"/><p>
	<c:if test="${not empty administrator}">
	    <a href='?mode=ENABLED'>ENABLE</a>&nbsp;<a href='?status=PAUSED'>PAUSE</a>&nbsp;<a href='?mode=DISABLED'>HALT</a>&nbsp;<a href='?mode=FORCED'>FORCE</a>&nbsp;<a href='?mode=FORCED_NO_SKIP'>FORCE AND RELOAD EVERYONE</a>
	</c:if><p>    
    Latest Error :
	<c:if test="${not crawler.ok}">
	   &nbsp;<span style="color:#ff0000"><c:out value="${crawler.latestError}"/></span><p>
	</c:if>

	<c:out value="${crawler.counts}"/><p>
	<c:out value="${crawler.dates}"/><p>
	<c:out value="${crawler.rates}"/><p>
	<c:if test="${not empty crawler.lastFoundAuthor}">
 	    Last Found = <c:out value="${crawler.lastFoundAuthor}"/><p>
	</c:if>
	<c:if test="${not empty crawler.lastReadAuthor}">
 	    Last Read = <c:out value="${crawler.lastReadAuthor}"/><p>
	</c:if>
	<c:if test="${not empty crawler.lastSavedAuthor}">
 	    Last Saved = <c:out value="${crawler.lastSavedAuthor}"/><p>
	</c:if>
    <c:out value="${crawler.lastStartStatus}"/><p>
	
    <h2>Researcher Errors</h2>
	<c:forEach var="e" items="${crawler.errors}">
	   <c:out value="${e}"/><p>
	</c:forEach>

    <h2>Researcher Avoids</h2>
	<c:forEach var="a" items="${crawler.avoided}">
	   <c:out value="${a}"/><p>
	</c:forEach>
	Stack trace : <c:out value="${crawler.currentStackTrace}" escapeXml="false"/><p>
</body>
</html>
