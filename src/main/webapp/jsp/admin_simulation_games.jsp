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
<%@ page import="org.codedefenders.database.AdminDAO" %>
<%@ page import="org.codedefenders.database.DatabaseAccess" %>
<%@ page import="org.codedefenders.game.GameClass" %>
<%@ page import="org.codedefenders.game.GameState" %>
<%@ page import="org.codedefenders.game.Role" %>
<%@ page import="org.codedefenders.game.multiplayer.MultiplayerGame" %>
<%@ page import="org.codedefenders.servlets.admin.AdminCreateGames" %>
<%@ page import="java.util.List" %>

<% String pageTitle = null; %>
<%@ include file="header_main.jsp" %>
<div class="full-width">
    <% request.setAttribute("adminActivePage", "adminSimulationGames"); %>
    <%@ include file="admin_navigation.jsp" %>

    <form id="games" action="admin/simulation" method="post">
        <%--<input type="hidden" name="formType" value="startStopGame">--%>
        <h3>Simulatable Games</h3>

        <%
            List<MultiplayerGame> insertedGames = AdminDAO.getMultiplayerSimulationGamesCreatedBy( (Integer)request.getSession().getAttribute("uid"));
            if (insertedGames.isEmpty()) {
        %>
        <div class="panel panel-default">
            <div class="panel-body" style="    color: gray;    text-align: center;">There are currently no finished multiplayer games in the Database.
            </div>
        </div>
        <%
        } else {
        %>
        <table id="tableFinishedGames"
               class="table-hover table-striped table-responsive table-paragraphs games-table display table-condensed">
            <thead>
            <tr style="border-bottom: 1px solid black">
                <th><input type="checkbox" id="selectAllGames"
                           onchange="document.getElementById('start_games_btn').disabled = !this.checked;
                           document.getElementById('stop_games_btn').disabled = !this.checked">
                </th>
                <th>ID</th>
                <th></th>
                <th>Class</th>
                <th>Creator</th>
                <th>Attackers</th>
                <th>Defenders</th>
                <th>Original Game</th>
                <th>Starting</th>
                <th>Finishing</th>
                <th>
                    <a id="togglePlayersActive" class="btn btn-sm btn-default" title="Show list of Players for each Game.">
                        <span id = "togglePlayersActiveSpan" class="glyphicon glyphicon-alert"></span>
                    </a>
                </th>
            </tr>
            </thead>
            <tbody>
            <%
                for (MultiplayerGame g : insertedGames) {
                    GameClass cut = g.getCUT();
                    String startStopButtonIcon = g.getState().equals(GameState.ACTIVE) ?
                            "glyphicon glyphicon-stop" : "glyphicon glyphicon-play";
                    String startStopButtonClass = g.getState().equals(GameState.ACTIVE) ?
                            "btn btn-sm btn-danger" : "btn btn-sm btn-primary";
                    String startStopButtonAction = g.getState().equals(GameState.ACTIVE) ?
                            "return confirm('Are you sure you want to stop this Game?');" : "";
                    int gameId = g.getId();
            %>
            <tr style="border-top: 1px solid lightgray; border-bottom: 1px solid lightgray" id="<%="game_row_"+gameId%>">
                <td>
                    <input type="checkbox" name="selectedGames" id="<%="selectedGames_"+gameId%>" value="<%= gameId%>" onchange=
                            "document.getElementById('start_games_btn').disabled = !areAnyChecked('selectedGames');
                            document.getElementById('stop_games_btn').disabled = !areAnyChecked('selectedGames');
                            setSelectAllCheckbox('selectedGames', 'selectAllGames')">
                </td>
                <td><%= gameId %>
                </td>
                <td>
                    <a class="btn btn-sm btn-primary" id="<%="simulate-"+g.getId()%>"
                       href="<%= request.getContextPath() %>/admin/simulate?id=<%= gameId %>">Simulate</a>
                </td>
                <td class="col-sm-2">
                    <a href="#" data-toggle="modal" data-target="#modalCUTFor<%=gameId%>">
                        <%=cut.getAlias()%>
                    </a>
                    <div id="modalCUTFor<%=gameId%>" class="modal fade" role="dialog" style="text-align: left;">
                        <div class="modal-dialog">
                            <div class="modal-content">
                                <div class="modal-header">
                                    <button type="button" class="close" data-dismiss="modal">&times;</button>
                                    <h4 class="modal-title"><%=cut.getAlias()%>
                                    </h4>
                                </div>
                                <div class="modal-body">
                                    <pre class="readonly-pre"><textarea
                                            class="readonly-textarea classPreview"
                                            id="sut<%=gameId%>" name="cut<%=gameId%>" cols="80"
                                            rows="30"><%=cut.getAsHTMLEscapedString()%></textarea></pre>
                                </div>
                                <div class="modal-footer">
                                    <button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
                                </div>
                            </div>
                        </div>
                    </div>
                </td>
                <td class="col-sm-1"><%= DatabaseAccess.getUserForKey("User_ID", g.getCreatorId()).getUsername() %>
                </td>
                <td class="col-sm-1"><%int attackers = g.getAttackerIds().length; %><%=attackers %> of
                        <%=g.getMinAttackers()%>&ndash;<%=g.getAttackerLimit()%>
                </td>
                <td class="col-sm-1"><%int defenders = g.getDefenderIds().length; %><%=defenders %> of
                        <%=g.getMinDefenders()%>&ndash;<%=g.getDefenderLimit()%>
                </td>
                <td><%= g.getOriginGameId() %>
                </td>
                <td class="col-sm-2"><%= g.getStartDateTime() %>
                </td>
                <td class="col-sm-2"><%= g.getFinishDateTime() %>
                </td>
                <td class="col-sm-1" style="padding-top:4px; padding-bottom:4px">
                    <button class="<%=startStopButtonClass%>" type="submit" value="<%=gameId%>" name="start_stop_btn"
                            onclick="<%=startStopButtonAction%>" id="<%="start_stop_"+g.getId()%>">
                        <span class="<%=startStopButtonIcon%>"></span>

                    </button>
                </td>
                    <%List<List<String>> playersInfo = AdminDAO.getPlayersInfo(gameId);
                if(!playersInfo.isEmpty()){%>
            <tr id="playersTableActive" hidden>
                <th colspan="3">Game Score</th>
                <th style="border-bottom: 1px solid black">Name</th>
                <th style="border-bottom: 1px solid black">Submissions</th>
                <th style="border-bottom: 1px solid black">Last Action</th>
                <th style="border-bottom: 1px solid black">Points</th>
                <th style="border-bottom: 1px solid black">Total Score</th>
                <th style="border-bottom: 1px solid black"></th>
            </tr>
            <%
                }
                boolean firstAttacker = true;
                boolean firstDefender = true;

                // Compute the cumulative sum of each role score. Not sure if this is how is done in the scoreboard
                int gameScoreAttack = 0;
                int gameScoreDefense = 0;

                for (List<String> playerInfo : playersInfo) {
                    if( Role.ATTACKER.equals( Role.valueOf(playerInfo.get(2)) ) ){
                        gameScoreAttack = gameScoreAttack + Integer.parseInt(playerInfo.get(4));
                    } else if (Role.DEFENDER.equals( Role.valueOf(playerInfo.get(2)) ) ){
                        gameScoreDefense = gameScoreDefense + Integer.parseInt(playerInfo.get(4));
                    }
                }

                for (List<String> playerInfo : playersInfo) {
                    int playerId = Integer.parseInt(playerInfo.get(0));
                    String userName = playerInfo.get(1);
                    Role role = Role.valueOf(playerInfo.get(2));
                    String ts = playerInfo.get(3);
                    String lastSubmissionTS = AdminDAO.TIMESTAMP_NEVER.equalsIgnoreCase(ts) ? ts : AdminCreateGames.formatTimestamp(ts);
                    int totalScore = Integer.parseInt(playerInfo.get(4));
                    int submissionsCount = Integer.parseInt(playerInfo.get(5));
                    String color = role == Role.ATTACKER ? "#edcece" : "#ced6ed";
                    int gameScore = AdminCreateGames.getPlayerScore(g, playerId);
            %>
            <tr style="height: 3px;" id="playersTableActive" hidden></tr>
            <tr id="playersTableActive" hidden>
                <%	if ( firstAttacker && role.equals( Role.ATTACKER ) ) {%>
                <td colspan = "3"><%= gameScoreAttack %></td>
                <%	firstAttacker = false;
                }
                else if ( firstDefender && role.equals( Role.DEFENDER ) ) {%>
                <td colspan = "3"><%= gameScoreDefense %></td>
                <%  firstDefender = false;
                } else { %>
                <td></td>
                <td></td>
                <td></td>
                <%  } %>
                <td style="background: <%= color %>; border-top-left-radius: 7px;border-bottom-left-radius: 7px;">
                    <%= userName %>
                </td>
                <td style="background: <%= color %>"><%= submissionsCount %>
                </td>
                <td style="background: <%= color %>"><%= lastSubmissionTS %>
                </td>
                <td style="background: <%= color %>"><%= gameScore %>
                </td>
                <td style="background: <%= color %>"><%= totalScore %>
                </td>
                <td style="background: <%= color %>">
                </td>
                <td style="background: <%= color %>; border-top-right-radius: 7px;border-bottom-right-radius: 7px;">

                    <button class="btn btn-sm btn-danger" value="<%=playerId + "-" + gameId%>"
                            onclick="return confirm('This will create a new game without the actions of this player');"
                            id="<%="remove_player_"+playerId+"from_game_"+gameId%>"
                            name="gameUserRemoveButton">Remove
                    </button>
                    <input type="hidden" name="formType" value="removePlayer">
                </td>
            </tr>
            <% } %>
            <% } %>
            </tbody>
        </table>
        <br/>
        <button class="btn btn-md btn-primary" type="submit" name="games_btn" id="start_games_btn"
                disabled value="Start Games">
            Start Games
        </button>
        <button class="btn btn-md btn-danger" type="submit" name="games_btn" id="stop_games_btn"
                onclick="return confirm('Are you sure you want to stop the selected Games?');"
                disabled value="Stop Games">
            Stop Games
        </button>
        <% }
        %>

    </form>


    <script>
        $('#selectAllGames').click(function () {
            $(this.form.elements).filter(':checkbox').prop('checked', this.checked);
        });

        $('#togglePlayersCreated').click(function () {
            localStorage.setItem("showCreatedPlayers", localStorage.getItem("showCreatedPlayers") === "true" ? "false" : "true");
            $("[id=playersTableCreated]").toggle();
            $("[id=playersTableHidden]").toggle();
        });

        $('#togglePlayersActive').click(function () {
            var showPlayers = localStorage.getItem("showActivePlayers") === "true";
            localStorage.setItem("showActivePlayers", showPlayers ? "false" : "true");
            $("[id=playersTableActive]").toggle();
            setActivePlayersSpan()
        });

        function setActivePlayersSpan() {
            var showPlayers = localStorage.getItem("showActivePlayers") === "true";
            var buttonClass = showPlayers ? "glyphicon glyphicon-eye-close" : "glyphicon glyphicon-eye-open";
            document.getElementById("togglePlayersActiveSpan").setAttribute("class", buttonClass);
        }

        function setSelectAllCheckbox(checkboxesName, selectAllCheckboxId) {
            var checkboxes = document.getElementsByName(checkboxesName);
            var allChecked = true;
            checkboxes.forEach(function (element) {
                allChecked = allChecked && element.checked;
            });
            document.getElementById(selectAllCheckboxId).checked = allChecked;
        }

        function areAnyChecked(name) {
            var checkboxes = document.getElementsByName(name);
            var anyChecked = false;
            checkboxes.forEach(function (element) {
                anyChecked = anyChecked || element.checked;
            });
            return anyChecked;
        }


        $(document).ready(function () {
            if (localStorage.getItem("showActivePlayers") === "true") {
                $("[id=playersTableActive]").show();
            }

            if (localStorage.getItem("showCreatedPlayers") === "true") {
                $("[id=playersTableCreated]").show();
                $("[id=playersTableHidden]").hide();
            }

            $('#tableFinishedGames').DataTable();
            $('.dataTables_length').addClass('bs-select');

            setActivePlayersSpan();

        });

        $('.modal').on('shown.bs.modal', function () {
            var codeMirrorContainer = $(this).find(".CodeMirror")[0];
            if (codeMirrorContainer && codeMirrorContainer.CodeMirror) {
                codeMirrorContainer.CodeMirror.refresh();
            } else {
                var editorDiff = CodeMirror.fromTextArea($(this).find('textarea')[0], {
                    lineNumbers: false,
                    readOnly: true,
                    mode: "text/x-java"
                });
                editorDiff.setSize("100%", 500);
            }
        });
    </script>

</div>
<%@ include file="footer.jsp" %>
