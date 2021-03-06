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
<%@page import="org.codedefenders.util.Constants"%>
<%@page import="org.codedefenders.database.TestDAO"%>
<%@page import="org.codedefenders.database.MutantDAO"%>
<%@ page import="org.codedefenders.database.UserDAO" %>
<%@ page import="org.codedefenders.game.multiplayer.PlayerScore" %>
<%@ page import="org.codedefenders.model.User" %>
<%@ page import="java.util.HashMap" %>
<%

    HashMap mutantScores = game.getMutantScores();


    HashMap testScores = game.getTestScores();

    // Those return the PlayerID not the UserID
    int[] attackers = game.getAttackerIds();
    int[] defenders = game.getDefenderIds();
%>
<div id="scoreboard" class="modal fade" role="dialog" style="z-index: 10000; position: absolute;">
    <div class="modal-dialog">
        <!-- Modal content-->
        <div class="modal-content" style="z-index: 10000; position: absolute; width: 100%; left:0%;">
            <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal">&times;</button>
                <h4 class="modal-title">Scoreboard</h4>
            </div>
            <div class="modal-body">
                <div class="scoreBanner">
                    <span class="attackerTotal"><%
                        int ts = 0;
                        if (mutantScores.containsKey(-1) && mutantScores.get(-1) != null){
                            ts += ((PlayerScore)mutantScores.get(-1)).getTotalScore();
                        }
                        if (testScores.containsKey(-2) && testScores.get(-2) != null){
                            ts += ((PlayerScore)testScores.get(-2)).getTotalScore();
                        } %>
                        <%= ts %>
                    </span><img class="logo" href="<%=request.getContextPath() %>/" src="images/logo.png"/><span class="defenderTotal">
                    <% ts = 0;
                        if (testScores.containsKey(-1) && testScores.get(-1) != null){
                                ts += ((PlayerScore)testScores.get(-1)).getTotalScore(); %>
                        <% } %>
                        <%= ts %>
                </span>
                </div>
                <table class="scoreboard">
                    <tr class="attacker header"><th>Attackers</th><th>Mutants</th><th>Alive / Killed / Equivalent</th><th>Duels Won/Lost/Ongoing</th></th><th>Total Points</th></tr>
                    <%
                    int total = 0;
                    for (int index = 0; index < attackers.length; index++){
                        int i = attackers[index];
                        if (i == -1){
                            continue;
                        }
                        User aUser = UserDAO.getUserForPlayer(i);
                        
                        // Does system attacker submitted any mutant?
                        // TODO #418: we use UserId instead of PlayerID because there's a bug in the logic which initialize the game.
                        // For system generated mutants,  mutant.playerID == userID, which is wrong...
                        if(aUser.getId() == Constants.DUMMY_ATTACKER_USER_ID && MutantDAO.getMutantsByGameAndUser(game.getId(), aUser.getId()).isEmpty() ){
                           continue;
                        }
                        
                        total = 0;
                        int counter = 0;
                        %>
                        <tr class="attacker"><td>
                                <%=aUser.getUsername()%>
                            </td>
                            <td><%
                                if (mutantScores.containsKey(i) && mutantScores.get(i) != null){ %>
                                <%= ((PlayerScore)mutantScores.get(i)).getQuantity() %>
                                <% } else { %>
                                0
                                <% } %></td>
                            <td>
                                <%
                                    if (mutantScores.containsKey(i) && mutantScores.get(i) != null){%>
                                <%= ((PlayerScore)mutantScores.get(i)).getMutantKillInformation() %>
                                <% } else { %>
                                    0 / 0 / 0
                                <% } %>
                            </td>
                            <td>
                                <!-- Equivalence duels -->
                                <%
                                    if (mutantScores.containsKey(i) && mutantScores.get(i) != null){
                                %>
                                <%= ((PlayerScore)mutantScores.get(i)).getDuelInformation() %>
                                <% } else { %>
                                0 / 0 / 0
                                <% } %>
                            </td>
                            <td>
                                <%
                                if (mutantScores.containsKey(i) && mutantScores.get(i) != null){
                                    total += ((PlayerScore)mutantScores.get(i)).getTotalScore(); %>
                            <% }
                                if (testScores.containsKey(i) && testScores.get(i) != null){
                                    total += ((PlayerScore)testScores.get(i)).getTotalScore(); %>
                            <% } %>
                                <%= total %>
                            </td>
                        </tr>
                <%
                    }
                    total = 0;

                    if (attackers.length == 0){
                %><tr class="attacker"><td colspan="4"></td></tr><%
                    }
                %>
                    <tr class="attacker header"><td>
                        Attacking Team
                    </td>
                        <td><%
                            if (mutantScores.containsKey(-1) && mutantScores.get(-1) != null){ %>
                            <%= ((PlayerScore)mutantScores.get(-1)).getQuantity() %>
                            <% } else { %>
                            0
                            <% } %></td>
                        <td>
                            <%
                                if (mutantScores.containsKey(-1) && mutantScores.get(-1) != null){%>
                            <%= ((PlayerScore)mutantScores.get(-1)).getMutantKillInformation() %>
                            <% } else { %>
                            0 / 0 / 0
                            <% } %>
                        </td>
                        <td>
                            <!-- Equivalence duels -->
                            <%
                                if (mutantScores.containsKey(-1) && mutantScores.get(-1) != null){
                            %>
                                <%= ((PlayerScore)mutantScores.get(-1)).getDuelInformation() %>
                            <% } else { %>
                                0 / 0 / 0
                            <% } %>
                        </td>
                        <td>
                            <%
                                if (mutantScores.containsKey(-1) && mutantScores.get(-1) != null){
                                    total += ((PlayerScore)mutantScores.get(-1)).getTotalScore(); %>
                            <% } else { %>
                            0
                            <% }
                                if (testScores.containsKey(-2) && testScores.get(-2) != null){
                                    total += ((PlayerScore)testScores.get(-2)).getTotalScore(); %>
                            <% } %>
                            <%= total %>
                        </td>
                    </tr>
                    <tr class="defender header"><th>Defenders</th><th>Tests</th><th>Mutants Killed</th><th>Duels Won/Lost/Ongoing</th><th>Total Points</th></tr>
                    <%
                        for (int index = 0; index < defenders.length; index++){
                            int i = defenders[index];
                            if (i == -1){
                                continue;
                            }
                            User dUser = UserDAO.getUserForPlayer(i);
                            
                            // XXX: Hardcoded id for system user
                            // TODO #418
                            if(dUser.getId() == Constants.DUMMY_DEFENDER_USER_ID && TestDAO.getTestsForGameAndUser(game.getId(), dUser.getId()).isEmpty() ){
                                continue;
                             }
                            
                            total = 0;
                    %>
                    <tr class="defender"><td>
                        <%=dUser.getUsername()%>
                    </td>
                        <td><%
                            if (testScores.containsKey(i) && testScores.get(i) != null){ %>
                                <%= ((PlayerScore)testScores.get(i)).getQuantity() %>

                            <% } else { %>
                                0 <% } %></td>
                        <td><%
                            if (testScores.containsKey(i) && testScores.get(i) != null){
                                    total += ((PlayerScore)testScores.get(i)).getTotalScore(); %>
                            <%= ((PlayerScore)testScores.get(i)).getMutantKillInformation()%>
                            <% } else { %>
                            0 <% } %>
                        </td>
                        <td>
                            <!-- Equivalence duels -->
                            <%
                                if (testScores.containsKey(i) && testScores.get(i) != null){
                            %>
                            <%= ((PlayerScore)testScores.get(i)).getDuelInformation() %>
                            <% } else { %>
                            0 / 0 / 0
                            <% } %>
                        </td>
                        <td>
                            <%= total %>
                        </td>
                    </tr>
                    <%
                        }
                        total = 0;

                        if (defenders.length == 0){
                            %><tr class="defender"><td colspan="5"></td></tr><%
                        }
                    %>
                    <tr class="defender header"><td>
                        Defending Team
                    </td>
                        <td><%
                            if (testScores.containsKey(-1) && testScores.get(-1) != null){ %>
                            <%= ((PlayerScore)testScores.get(-1)).getQuantity() %>

                            <% } else { %>
                            0 <% } %></td>
                        <td><%
                            if (testScores.containsKey(-1) && testScores.get(-1) != null){
                                total += ((PlayerScore)testScores.get(-1)).getTotalScore(); %>
                            <%= ((PlayerScore)testScores.get(-1)).getMutantKillInformation()%>
                            <% } else { %>
                            0 <% } %>
                        </td>
                        <td>
                            <!-- Equivalence duels -->
                            <%
                                if (testScores.containsKey(-1) && testScores.get(-1) != null){
                            %>
                            <%= ((PlayerScore)testScores.get(-1)).getDuelInformation() %>
                            <% } else { %>
                            0 / 0 / 0
                            <% } %>
                        </td>
                        <td>
                            <%= total %>
                        </td>
                    </tr>
                    </table>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
            </div>
        </div>
    </div>
</div>