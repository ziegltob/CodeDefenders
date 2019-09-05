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
<%
    final Logger logger = LoggerFactory.getLogger("admin_simulate_game.jsp");
    boolean redirectToGames = false;
    // Get their user id from the session.
    int uid = (Integer) session.getAttribute("uid");
    int gameId = 0;
    try {
        gameId = Integer.parseInt(request.getParameter("id"));
        session.setAttribute("mpGameId", gameId);
    } catch (NumberFormatException e) {
        logger.info("Game ID was not passed in the request " + request.getContextPath() + request.getRequestURI() +". Restoring from session.");
        logger.info("Don't know what game was open...");
        redirectToGames = true;
    } catch (Exception e2){
        logger.error("Exception caught", e2);
        gameId = 0;
        redirectToGames = true;
    }
    MultiplayerGame game = DatabaseAccess.getMultiplayerGame(gameId);
    if (game == null){
        logger.error(String.format("Could not find multiplayer game %d", gameId));
        redirectToGames = true;
    }

    if (redirectToGames){
        response.sendRedirect(request.getContextPath()+"/games/user");
        return;
    }
%>

<% String pageTitle="Simulation of Game"; %>
<%@ page import="org.codedefenders.database.DatabaseAccess" %>
<%@ page import="org.codedefenders.game.GameState" %>
<%@ page import="org.codedefenders.game.Mutant" %>
<%@ page import="org.codedefenders.game.Role" %>
<%@ page import="org.codedefenders.game.Test" %>
<%@ page import="org.codedefenders.game.multiplayer.MultiplayerGame" %>
<%@ page import="org.codedefenders.model.Event" %>
<%@ page import="org.codedefenders.model.EventStatus" %>
<%@ page import="org.codedefenders.model.EventType" %>
<%@ page import="org.codedefenders.model.User" %>
<%@ page import="static org.codedefenders.util.Constants.MUTANT_CANT_BE_CLAIMED_EQUIVALENT_MESSAGE" %>
<%@ page import="org.codedefenders.util.Constants" %>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="java.sql.Timestamp" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%
    int originGameId = DatabaseAccess.getSimulationOriginGame(game.getId());
    boolean renderMutants = true;
    boolean redirect = false;
    String codeDivName = "cut-div"; // used
    Role role = game.getRole(uid);
    HashMap<Integer, ArrayList<Test>> linesCovered = new HashMap<>();

    if ((game.getState().equals(GameState.CREATED) || game.getState().equals(GameState.FINISHED)) && (!role.equals(Role.CREATOR))) {
        response.sendRedirect(request.getContextPath()+"/games/user");
        return;
    }

    List<Test> tests = DatabaseAccess.getTestsForGame(game.getId()); // get stored defenders' tests

    // compute line coverage information
    for (Test t : tests) {
        for (Integer lc : t.getLineCoverage().getLinesCovered()) {
            if (!linesCovered.containsKey(lc)) {
                linesCovered.put(lc, new ArrayList<Test>());
            }
            linesCovered.get(lc).add(t);
        }
    }

%>
<%@ include file="multiplayer/header_game.jsp" %>

<%@ include file="scoring_tooltip.jsp" %>
<%@ include file="playerFeedback.jsp" %>
<%@ include file="multiplayer/game_scoreboard.jsp" %>

<%@ page import="java.util.stream.IntStream" %>
<%@ page import="org.codedefenders.game.singleplayer.automated.defender.AiDefender" %>
<%@ page import="org.codedefenders.game.singleplayer.automated.attacker.AiAttacker" %>
<% { %>

<%-- Set request attributes for the components. --%>
<%
    /* class_viewer */
    final GameClass cut = game.getCUT();
    request.setAttribute("className", cut.getBaseName());
    request.setAttribute("classCode", cut.getAsHTMLEscapedString());
    request.setAttribute("dependencies", cut.getHTMLEscapedDependencyCode());

    /* tests_carousel */
    request.setAttribute("tests", DatabaseAccess.getTestsForGame(game.getId()));
    request.setAttribute("mutants", game.getMutants());

    /* mutants_list */
    request.setAttribute("mutantsAlive", game.getAliveMutants());
    request.setAttribute("mutantsKilled", game.getKilledMutants());
    request.setAttribute("mutantsEquivalent", game.getMutantsMarkedEquivalent());
    request.setAttribute("markEquivalent", false);
    request.setAttribute("markUncoveredEquivalent", false);
    request.setAttribute("viewDiff", true);
    request.setAttribute("gameType", "PARTY");

    /* game_highlighting */
    request.setAttribute("codeDivSelector", "#cut-div");
    // request.setAttribute("tests", game.getTests());
    request.setAttribute("mutants", game.getMutants());
    request.setAttribute("showEquivalenceButton", false);
    // request.setAttribute("gameType", "PARTY");

    /* mutant_explanation */
    request.setAttribute("mutantValidatorLevel", game.getMutantValidatorLevel());
%>

<div class="simulation-panel">
    <h2>Admin Simulation</h2>
    <a class="btn btn-primary btn-game" id="<%="observe-simulated-"+originGameId%>"
       href="<%= request.getContextPath() %>/multiplayer/games?id=<%= originGameId %>">Origin Game <%=originGameId%></a>
    <form id="adminSimulateBtn" action="admin/simulate" method="post" style="display: inline-block;">
        <button type="submit" class="btn btn-primary btn-game" id="simulateGame" form="adminSimulateBtn"
                <% if (game.getState() != GameState.ACTIVE) { %> disabled <% } %>>
            Simulate Game
        </button>
        <input type="hidden" name="formType" value="simulateGame">
        <input type="hidden" name="mpGameID" value="<%= game.getId() %>" />
    </form>
</div>

<div class="row" style="padding: 0px 15px;">
    <div class="col-md-6">
        <div id="mutants-div">
            <h3>Existing Mutants</h3>
            <%@include file="game_components/mutants_list.jsp"%>
        </div>

        <div id="tests-div">
            <h3>JUnit tests </h3>
            <%@include file="game_components/tests_carousel.jsp"%>
        </div>
    </div>

    <div class="col-md-6" id="cut-div">
        <h3>Class Under Test</h3>
        <%@include file="game_components/class_viewer.jsp"%>
        <%@ include file="game_components/game_highlighting.jsp" %>
        <%@include file="game_components/mutant_explanation.jsp"%>
    </div>
</div>
</div>

<% } %>

<%@ include file="game_notifications.jsp"%>
<%@ include file="multiplayer/footer_game.jsp" %>
