<%--

    Copyright (C) 2016-2019 Code Defenders contributors

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
	request.setAttribute("tests", game.getTests());
	request.setAttribute("mutants", game.getMutants());

	/* mutants_list */
	request.setAttribute("mutantsAlive", game.getAliveMutants());
	request.setAttribute("mutantsKilled", game.getKilledMutants());
	request.setAttribute("mutantsEquivalent", game.getMutantsMarkedEquivalent());
	request.setAttribute("mutantsMarkedEquivalent", game.getMutantsMarkedEquivalentPending());
	request.setAttribute("markEquivalent", false);
	request.setAttribute("markUncoveredEquivalent", false);
	request.setAttribute("viewDiff", true);
	request.setAttribute("gameType", GameMode.PARTY);
	request.setAttribute("gameId", game.getId());

	/* game_highlighting */
	request.setAttribute("codeDivSelector", "#cut-div");
	// request.setAttribute("tests", game.getTests());
	request.setAttribute("mutants", game.getMutants());
	request.setAttribute("showEquivalenceButton", false);
	// request.setAttribute("gameType", GameMode.PARTY);
//    request.setAttribute("gameId", game.getId());

	/* mutant_explanation */
	request.setAttribute("mutantValidatorLevel", game.getMutantValidatorLevel());
%>

</div> <%-- TODO move the whole div here after changing the header --%>

<div class="admin-panel col-md-12">
	<form id="adminEndBtn" action="<%=request.getContextPath() + Paths.BATTLEGROUND_SELECTION%>" method="post" style="display: inline-block;">
		<button type="submit" class="btn btn-primary btn-game btn-left" id="endGame" form="adminEndBtn"
				<% if (game.getState() != GameState.ACTIVE) { %> disabled <% } %>>
			End Game
		</button>
		<input type="hidden" name="formType" value="endGame">
		<input type="hidden" name="gameId" value="<%= game.getId() %>" />
	</form>
	<form id="adminStartBtn" action="<%=request.getContextPath() + Paths.BATTLEGROUND_SELECTION%>" method="post" style="display: inline-block;">
		<button type="submit" class="btn btn-primary btn-game" id="startGame" form="adminStartBtn"
				<% if (game.getState() != GameState.CREATED) { %> disabled <% } %>>
			Start Game
		</button>
		<input type="hidden" name="formType" value="startGame">
		<input type="hidden" name="gameId" value="<%= game.getId() %>" />
	</form>
	<form id="adminAddDefender"
		  action="<%=request.getContextPath() + "/" + game.getClass().getSimpleName().toLowerCase()%>" method="post"
		  style="display: inline-block;">
		<% int aiDefPlayerId = IntStream.of(game.getDefenderIds()).filter(id -> DatabaseAccess.getUserFromPlayer(id).getId() == AiDefender.ID).findAny().orElse(0);
			boolean isAiDefActive = DatabaseAccess.getPlayerIsActive(aiDefPlayerId);
			boolean aiDefJoinedGame = DatabaseAccess.getJoinedMultiplayerGamesForUser(AiDefender.ID).stream()
					.filter(joinedGames -> joinedGames.getId() == game.getId())
					.findFirst().isPresent();
			if (!aiDefJoinedGame) { %>
		<button type="submit" class="btn btn-primary btn-game" id="addDefender" form="adminAddDefender">
			Add AI-Defender
		</button>
		<input type="hidden" name="formType" value="addDefender">
		<% } else if (!isAiDefActive && aiDefJoinedGame) { %>
		<button type="submit" class="btn btn-primary btn-game" id="activateDefender" form="adminAddDefender">
			Activate AI-Defender
		</button>
		<input type="hidden" name="formType" value="addDefender">
		<% } else if (isAiDefActive && aiDefJoinedGame) { %>
		<button type="submit" class="btn btn-primary btn-game" id="pauseAiDefender" form="adminAddDefender">
			Pause AI-Defender
		</button>
		<input type="hidden" name="formType" value="pauseDefender">
		<% } %>
		<input type="hidden" name="mpGameID" value="<%= game.getId() %>"/>
	</form>
	<form id="adminAddAttacker"
		  action="<%=request.getContextPath() + "/" + game.getClass().getSimpleName().toLowerCase()%>" method="post"
		  style="display: inline-block;">
		<% int aiAttPlayerId = IntStream.of(game.getAttackerIds()).filter(id -> DatabaseAccess.getUserFromPlayer(id).getId() == AiAttacker.ID).findAny().orElse(0);
			boolean isAiAttActive = DatabaseAccess.getPlayerIsActive(aiAttPlayerId);
			boolean aiAttJoinedGame = DatabaseAccess.getJoinedMultiplayerGamesForUser(AiAttacker.ID).stream()
					.filter(joinedGames -> joinedGames.getId() == game.getId())
					.findFirst().isPresent();
			if (!aiAttJoinedGame) { %>
		<button type="submit" class="btn btn-primary btn-game" id="addAttacker" form="adminAddAttacker">
			Add AI-Attacker
		</button>
		<input type="hidden" name="formType" value="addAttacker">
		<% } else if (!isAiAttActive && aiAttJoinedGame) { %>
		<button type="submit" class="btn btn-primary btn-game" id="activateAttacker" form="adminAddAttacker">
			Activate AI-Attacker
		</button>
		<input type="hidden" name="formType" value="addAttacker">
		<% } else if (isAiAttActive && aiAttJoinedGame) { %>
		<button type="submit" class="btn btn-primary btn-game" id="pauseAiAttacker" form="adminAddAttacker">
			Pause AI-Attacker
		</button>
		<input type="hidden" name="formType" value="pauseAttacker">
		<% } %>
		<input type="hidden" name="mpGameID" value="<%= game.getId() %>"/>
	</form>
</div>

<div class="row" style="padding: 0px 15px;">
	<div class="col-md-6">
		<div id="mutants-div">
			<h3>Existing Mutants</h3>
			<%@include file="../game_components/mutants_list.jsp"%>
		</div>

		<div id="tests-div">
			<h3>JUnit tests </h3>
			<%@include file="../game_components/tests_carousel.jsp"%>
		</div>
	</div>

	<div class="col-md-6" id="cut-div">
		<h3>Class Under Test</h3>
		<%@include file="../game_components/class_viewer.jsp"%>
		<%@ include file="../game_components/game_highlighting.jsp" %>
		<%@include file="../game_components/mutant_explanation.jsp"%>
	</div>
</div>

<% } %>
