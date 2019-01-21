<%--

    Copyright (C) 2016-2018 Code Defenders contributors

    This file is part of Code Defenders.

    Code Defenders is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or (at
    your option) any later version.

    Code Defenders is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
    General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Code Defenders. If not, see <http://www.gnu.org/licenses/>.

--%>
<%@ page import="org.codedefenders.util.Constants" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.codedefenders.database.AdminDAO" %>
<%@ page import="org.codedefenders.servlets.admin.AdminSystemSettings" %>
<%@ page import="org.codedefenders.model.NotificationType" %>
<%@ page import="org.codedefenders.util.Paths" %>

<%@ include file="/jsp/header_base.jsp" %>

<script>
    //If the user is logged in, start receiving notifications
    var updateUserNotifications = function(url) {
        $.getJSON(url, function (r) {

            var notificationCount = 0;

            $(r).each(function (index) {
                $("#userDropDown li:first-child").after(
                    "<li><a " +
                    "href=\"" + "<%=request.getContextPath() + Paths.BATTLEGROUND_GAME%>"
                    + "?gameId=" + r[index].gameId +
                    "\" style=\"width:100%;\">" +
                    r[index].parsedMessage +
                    "</a></li>"
                );

                if (r[index].eventStatus == "NEW"){
                    notificationCount += 1;
                }
            });

            var lastNotifCount = parseInt($("#notificationCount").html());

            var notifCount = notificationCount;

            if (lastNotifCount != null && !isNaN(lastNotifCount)) {
                notifCount += lastNotifCount;
            }

            $("#notificationCount").html(notifCount);
        });
    };

    $(document).ready(function () {
            if ($("#userDropDown").length) {
                //notifications written here:
                // refreshed every 5 seconds
                var interval = 5000;
                setInterval(function () {
                    var url = "<%=request.getContextPath() + Paths.API_NOTIFICATION%>?type=<%=NotificationType.USEREVENT%>&timestamp=" + (new Date().getTime() - interval);
                    updateUserNotifications(url);
                }, interval);

                var url = "<%=request.getContextPath() + Paths.API_NOTIFICATION%>?type=<%=NotificationType.USEREVENT%>&timestamp=0";
                updateUserNotifications(url);
            }
            $('[data-toggle="tooltip"]').tooltip();
        }
    );
</script>

<div class="menu-top bg-light-blue .minus-2 text-white" style="padding: 5px;">
    <div class="full-width">
        <div class="row ws-12 container" style="text-align: right; clear:
        both; width: 100%">
            <a id="site-logo" class="main-title text-white tab-link bg-minus-1"
               href="${pageContext.request.contextPath}/">
                <img class="logo" href="${pageContext.request.contextPath}/"
                     src="images/logo.png" style="float:left; margin-left: 10px; margin-right: 10px"/>
                <div id="home"
                     style="text-align: center; font-size: x-large; padding: 15px 20px 0 0;float: left">
                    Code Defenders
                </div>
            </a>
            <button type="button"
                    class="navbar-toggle tex-white buton tab-link bg-minus-1" data-toggle="collapse"
                    data-target="#bs-example-navbar-collapse-1">
                Menu <span class="glyphicon glyphicon-plus"></span>
            </button>
            <div class="col-md-9">
                <ul
                        class="crow fly no-gutter navbar navbar-nav collapse navbar-collapse"
                        id="bs-example-navbar-collapse-1"
                        style="z-index: 1000; text-align: center; list-style: none;
                 width: 100%; float: right; padding-top: 3px">
                    <li style="float: none" class="dropdown bg-light-blue"><a
                            id="headerGamesDropdown"
                            class="text-white button tab-link bg-minus-1 dropdown-toggle"
                            href="<%=request.getContextPath() + Paths.GAMES_OVERVIEW%>"
                            style="width:100%;" data-toggle="dropdown" href="#"> Multiplayer <span
                            class="glyphicon glyphicon-menu-hamburger" style="float: right;"></span></a>
                        <ul class="dropdown-menu" style="background-color:
                        #FFFFFF; border: 1px solid #000000;">
                            <li><a id="headerUserGames" href="<%=request.getContextPath()  + Paths.GAMES_OVERVIEW%>"
                                   style="width:100%;">Games</a></li>
                            <li><a id="headerGamesHistory" href="<%=request.getContextPath() + Paths.GAMES_HISTORY %>"
                                   style="width:100%;">History</a></li>
                            <li><a id="headerLeaderboardButton"
                                   href="<%= request.getContextPath() + Paths.LEADERBOARD_PAGE%>"
                                   style="width: 100%;">Leaderboard</a></li>
                        </ul>
                    </li>
                    <li style="float: none"><a id="puzzleOverview" class="text-white button tab-link bg-minus-1"
                                               href="<%=request.getContextPath() + Paths.PUZZLE_OVERVIEW%>"
                                               style="width:100%;">Puzzles</a></li>

                    <li style="float: none; white-space: nowrap;" class="dropdown"><a
                            id="headerUserDropdown"
                            class="text-white button tab-link bg-minus-1 dropdown-toggle bg-light-blue"
                            href="<%=request.getContextPath() + Paths.GAMES_OVERVIEW%>"
                            style="width:100%;" data-toggle="dropdown" href="#"><span class="glyphicon glyphicon-user"
                                                                                      aria-hidden="true"></span>
                        <%=request.getSession().getAttribute("username")%>
                        (<span id="notificationCount"></span>)
                        <span class="glyphicon glyphicon-menu-hamburger" style="float: right"></span></a>
                        <ul id="userDropDown" class="dropdown-menu"
                            style="background-color:
                        #FFFFFF; border: 1px solid #000000;">
                            <li><a id="headerHelpButton"
                                   href="<%=request.getContextPath()%>/help" style="width:100%; border-bottom:1px solid">Help</a></li>
                            <li><a
                                    id="headerLogout"
                                    href="<%=request.getContextPath() + Paths.LOGOUT%>"
                                    style="width:100%;border-bottom:1px solid
                               black">Logout
                            </a></li>
                        </ul>
                    </li>
                </ul>
            </div>
        </div>
    </div>
</div>

<form id="logout" action="<%=request.getContextPath()  + Paths.LOGIN%>" method="post">
    <input type="hidden" name="formType" value="logOut">
</form>

<%
    ArrayList<String> messages = (ArrayList<String>) request.getSession().getAttribute("messages");
    request.getSession().removeAttribute("messages");
    if (messages != null && !messages.isEmpty()) {
%>
<div class="alert alert-info" id="messages-div" style="width: 98.9vw">
    <a href="" class="close" data-dismiss="alert" aria-label="close">&times;</a><br/>
    <%
        boolean fadeOut = true;
        for (String m : messages) { %>
    <pre><strong><%=m%></strong></pre>
    <%
            if (m.equals(Constants.MUTANT_UNCOMPILABLE_MESSAGE)
                    || m.equals(Constants.TEST_DID_NOT_PASS_ON_CUT_MESSAGE)
                    || m.equals(Constants.TEST_DID_NOT_COMPILE_MESSAGE)) {
                fadeOut = false;
            }
        }
        if (fadeOut) {
    %>
    <script> $('#messages-div').delay(10000).fadeOut(); </script>
    <% } %>
</div>
<% } %>
