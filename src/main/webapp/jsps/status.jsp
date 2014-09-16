<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<html>
<body>
    <a href='.'>Home</a><p>
    <h2>Scheduled and running crawlers</h2>
    Administrator = <c:out value="${administrator}"/><p>
    <c:out value="${memoryStats}" escapeXml="false"/><p>
	<c:forEach var="crawler" items="${crawlers}">
	   <a href='./<c:out value="${crawler.name}"/>/status'><c:out value="${crawler.name}"/></a>&nbsp;
		<c:if test="${not crawler.active and crawler.ok}">
		   <c:out value="${crawler.state}"/><p>
		</c:if>
		<c:if test="${crawler.active}">
		   <span style="color:#008000"><c:out value="${crawler.state}"/></span><p>
		</c:if>
		<c:if test="${not crawler.ok}">
		   <span style="color:#ff0000"><c:out value="${crawler.state}"/></span><p>
		   &nbsp;<span style="color:#ff0000"><c:out value="${crawler.latestError}"/></span><p>
		</c:if>
		<c:out value="${crawler.counts}"/>&nbsp;<c:out value="${crawler.dates}"/><p>
 	    <c:out value="${crawler.rates}"/><p>
		<c:if test="${not empty crawler.lastSavedResearcher}">
	 	    Last Saved = <c:out value="${crawler.lastSavedResearcher}"/><p>
		</c:if>
 	    <c:out value="${crawler.lastStartStatus}"/><p>
 	    <hr>
	</c:forEach>

    <h2>Scheduling history</h2>
	<c:forEach var="i" items="${metaHistory}">
 	    <c:out value="${crawler}"/><p>
	</c:forEach>
</body>
</html>
