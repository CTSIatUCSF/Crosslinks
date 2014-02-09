<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<html>
<body>
    <h2>Scheduled and running crawlers</h2>
	<c:forEach var="i" items="${crawlers}">
		<c:if test="${not i.active and i.ok}">
		   <c:out value="${i}"/><p>
		</c:if>
		<c:if test="${i.active}">
		   <span style="color:#008000"><c:out value="${i}"/></span><p>
		</c:if>
		<c:if test="${not i.ok}">
		   <span style="color:#ff0000"><c:out value="${i}"/></span><p>
		</c:if>
	</c:forEach>

    <h2>Completed crawlers</h2>
	<c:forEach var="i" items="${history}">
 	    <c:out value="${i}"/><p>
	</c:forEach>
</body>
</html>
