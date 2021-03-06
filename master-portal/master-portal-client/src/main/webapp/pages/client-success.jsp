<%--
  User: Jeff Gaynor
  Date: 9/27/11
  Time: 4:58 PM

    NOTE:This page is supplied as an example and under no circumstances should ever be deployed
  on a live server. It is intended to show control flow as simply as possible.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<script type="text/javascript">
function getCookie(cname) {
    var name = cname + "=";
    var ca = document.cookie.split(';');
    for(var i=0; i<ca.length; i++) {
        var c = ca[i];
        while (c.charAt(0)==' ') c = c.substring(1);
        if (c.indexOf(name) == 0) return c.substring(name.length,c.length);
    }
    return "";
}
function redirect() {

    var host = getCookie("voportal");
    host = host.replace(/"+/g,'');

    var username = "${userSubject}"

    if (host) {
        window.location = host + "?username=" + username;
    }
}
window.onload=redirect
</script>
<head>
    <title>Master Portal for OAuth 2 client success page.</title>
    <link rel="stylesheet"
          type="text/css"
          media="all"
          href="static/demo.css"/>
</head>


<style type="text/css">
    .hidden {
        display: none;
    }

    .unhidden {
        display: block;
    }
</style>
<script type="text/javascript">
    function unhide(divID) {
        var item = document.getElementById(divID);
        if (item) {
            item.className = (item.className == 'hidden') ? 'unhidden' : 'hidden';
        }
    }
</script>
<body>

<br clear="all"/>

<div class="main">

    <h1>Success!</h1>

    <p>The username retrieved from the userinfo endpoint is : <b>${userSubject}</b></p><br>

    <p>The subject of the first cert is<br><br> ${certSubject}

    <p>You have successfully requested a certificate from the server.<br>

    <ul>
        <li><a href="javascript:unhide('showCert');">Show/Hide certificates</a></li>
        <div id="showCert" class="hidden">
            <p>
            <pre>${cert}</pre>
        </div>
    </ul>
    <form name="input" action="${action}" method="get"/>
        <input type="submit" value="Return to client"/>
    </form>

    <br>

    <button id="redirect" onclick="redirect()">Return to VO Portal</button>

</div>
</body>
</html>
