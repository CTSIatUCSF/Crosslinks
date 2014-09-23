<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<body>
	<a href="../status">Status Home</a><p>	
	<c:if test="${not empty affiliation}">
		<a href='<c:out value="${affiliation.URI}"/>'><c:out value="${affiliation.URI}"/></a></p>
		<a href='../<c:out value="${affiliation.name}"/>'>Researchers and information about <c:out value="${affiliation.name}"/></a><p>
	</c:if>
    <c:out value="${crawler}"/><p>
	<c:if test="${not empty administrator}">
	    <a href='?status=PAUSED'>PAUSE</a>&nbsp;<a href='?mode=DISABLED'>DISABLE</a>&nbsp;<a href='?mode=ENABLED'>ENABLE</a>&nbsp;<a href='?mode=FORCED'>FORCE</a>&nbsp;<a href='?mode=FORCED_NO_SKIP'>FORCE AND RELOAD EVERYONE</a>
	</c:if><p>    
	
	<c:out value="${crawler.counts}"/><p>
	<c:out value="${crawler.dates}"/><p>
	<c:out value="${crawler.rates}"/><p>
    <c:out value="${crawler.lastStartStatus}"/><p>

	<c:forEach var="outputStats" items="${crawler.outputStatsList}">
	    <h2><c:out value="${outputStats.name}"/></h2>
		<c:out value="${outputStats}"/><p>
		<c:if test="${not empty outputStats.latest}">
			Latest : <c:out value="${outputStats.latest}"/><p>
		</c:if>
	</c:forEach>
	
	<c:if test="${not empty crawler.latestErrorStackTrace}">
		Latest Exception : <span style="color:#ff0000">
		<c:out value="${crawler.latestErrorStackTrace}" escapeXml="false"/></span><p>
	</c:if>

	Running Stack trace : <c:out value="${crawler.currentStackTrace}" escapeXml="false"/><p>
</body>
</html>
