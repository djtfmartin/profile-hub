<head>
    <base href="${request.contextPath}/">
    <meta http-equiv="X-UA-Compatible" content="IE=edge"/>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta http-equiv="Pragma" content="no-cache">
    <meta http-equiv="Expires" content="-1">
    <meta name="app.version" content="${g.meta(name: 'app.version')}"/>
    <meta name="app.build" content="${g.meta(name: 'app.build')}"/>
    <meta name="description" content="Atlas of Living Australia"/>
    <meta name="author" content="Atlas of Living Australia">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link href="http://www.ala.org.au/wp-content/themes/ala2011/images/favicon.ico" rel="shortcut icon"
          type="image/x-icon"/>
    <title><g:layoutTitle/></title>
    <script>
      var CKEDITOR_BASEPATH = '${request.contextPath}/assets/ckeditor/';
    </script>
    <g:layoutHead/>
    <asset:stylesheet href="application.css" />
    <asset:javascript src="head.js" />
    <style type="text/css">
    #banner-image {
        background-image: url(${bannerUrl ?: grailsApplication.config.images.service.url + '/store/7/4/4/e/a08a52f2-7bbe-40d9-8f1a-fe8acb28e447/original'});
        min-height: ${bannerHeight ?: 100}px;
    }
    </style>
    <!-- HTML5 shim and Respond.js for IE8 support of HTML5 elements and media queries -->
    <!-- WARNING: Respond.js doesn't work if you view the page via file:// -->
    <!--[if lt IE 9]>
        <script src="https://oss.maxcdn.com/html5shiv/3.7.2/html5shiv.min.js"></script>
        <script src="https://oss.maxcdn.com/respond/1.4.2/respond.min.js"></script>
    <![endif]-->
    %{-- Google Analytics --}%
    <script>
      window.ga=window.ga||function(){(ga.q=ga.q||[]).push(arguments)};ga.l=+new Date;
      ga('create', '${grailsApplication.config.googleAnalyticsId}', 'auto');
      ga('send', 'pageview');
    </script>
    <script async src='//www.google-analytics.com/analytics.js'></script>
    %{--End Google Analytics--}%
</head>

<body id="${pageProperty(name: 'body.id')}" onload="${pageProperty(name: 'body.onload')}" ng-app="profileEditor">

<ala:systemMessage/>

<div ng-controller="CustomAlertController" id="timeoutAlert" class="genericAlert">
    <alert ng-repeat="alert in alerts" type="{{alert.type}}" close="closeAlert($index)" ng-cloak>{{alert.msg}}</alert>
</div>

<div ng-controller="StayOnAlertController" id="stayOnAlert" class="genericAlert">
    <alert ng-repeat="alert in alerts" type="{{alert.type}}" close="closeAlert($index)" ng-cloak>{{alert.msg}}</alert>
</div>


<!-- Navbar -->
<nav class="navbar navbar-default" id="primary-nav">
    <div class="container">
        <div class="navbar-header">
            <button type="button" class="navbar-toggle collapsed" data-toggle="collapse"
                    data-target="#bs-example-navbar-collapse-1">
                <span class="sr-only">Toggle navigation</span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
            </button>
        </div>

        <!-- Collect the nav links, forms, and other content for toggling -->
        <div class="collapse navbar-collapse" id="bs-example-navbar-collapse-1">
            <ul class="nav navbar-nav navbar-right">
                <li><delegated-search></delegated-search></li>
                <g:if test="${opusUrl}">
                    <li class="${pageName=='opus'?'active':''}"><a href="${opusUrl}">Home</a></li>
                </g:if>
                <g:if test="${browseUrl}">
                    <li class="${pageName=='browse'?'active':''}"><a href="${browseUrl}">Browse</a></li>
                </g:if>
                <g:if test="${glossaryUrl}">
                    <li class="${pageName=='glossary'?'active':''}"><a href="${glossaryUrl}">Glossary</a></li>
                </g:if>
                <g:if test="${glossaryUrl}">
                    <li class="${pageName=='about'?'active':''}"><a href="${aboutPageUrl}">About&nbsp;&nbsp;</a></li>
                </g:if>
                <g:render template="/layouts/login"/>
                <li><p:help help-id="main"/></li>
            </ul>
        </div>
    </div>
</nav>