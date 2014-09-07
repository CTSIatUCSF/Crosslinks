<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<html>
<body>
    <a href='.'>Home</a><p>
    <h2>Scheduled and running crawlers</h2>
    Administrator = <c:out value="${administrator}"/><p>
    <c:out value="${memoryStats}" escapeXml="false"/><p>
	<c:forEach var="i" items="${crawlers}">
	   <a href='./<c:out value="${i.name}"/>'><c:out value="${i.name}"/></a>&nbsp;
		<c:if test="${not i.active and i.ok}">
		   <c:out value="${i.state}"/>
		</c:if>
		<c:if test="${i.active}">
		   <span style="color:#008000"><c:out value="${i.state}"/></span>
		</c:if>
		<c:if test="${not i.ok}">
		   <span style="color:#ff0000"><c:out value="${i.state}"/></span><p>
		   &nbsp;<span style="color:#ff0000"><c:out value="${i.latestError}"/></span>
		</c:if>
		&nbsp;<c:out value="${i.counts}"/>&nbsp;<c:out value="${i.dates}"/><p>
 	    <c:out value="${i.currentAuthor}"/><p>
 	    <c:out value="${i.lastStartStatus}"/><p>
 	    <hr>
	</c:forEach>

    <h2>Scheduling history</h2>
	<c:forEach var="i" items="${metaHistory}">
 	    <c:out value="${i}"/><p>
	</c:forEach>
</body>
</html>
