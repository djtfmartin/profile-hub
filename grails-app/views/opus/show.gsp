<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="${grailsApplication.config.layout}"/>
    <title>Welcome to Grails</title>
</head>

<body>

<h1>${opus.title}</h1>
<g:if test="${!edit}">
    <g:link class="btn btn-mini pull-right" mapping="editOpus"  params="[uuid:opus.uuid]"><i class="icon-edit"></i>&nbsp;Edit</g:link>
</g:if>
<g:else>
    <g:link class="btn btn-mini pull-right" mapping="viewOpus"  params="[uuid:opus.uuid]">Public view</g:link>
</g:else>


<div class="well pull-right">
    <h3>Approved Image sources</h3>
    <ul>
        <g:each in="${opus.imageSources}" var="imageSource">
            <li><a href="http://collections.ala.org.au/public/show/${imageSource}">${dataResources[imageSource]}</a></li>
        </g:each>
    </ul>
    <h3>Approved Specimen/Observation sources</h3>
    <ul>
        <g:each in="${opus.recordSources}" var="recordSource">
            <li><a href="http://collections.ala.org.au/public/show/${recordSource}">${dataResources[recordSource]}</a></li>
        </g:each>
    </ul>
</div>

<div>
<p>
    ${dataResource.pubDescription}
</p>
<p>
    ${dataResource.rights}
</p>
<p>
    ${dataResource.citation}
</p>
</div>






</body>

</html>