/**
 * Copyright (C) 2016-2018 Code Defenders contributors
 *
 * This file is part of Code Defenders.
 *
 * Code Defenders is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Code Defenders is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Code Defenders. If not, see <http://www.gnu.org/licenses/>.
 */
package org.codedefenders.servlets.admin;

import org.codedefenders.database.AdminDAO;
import org.codedefenders.database.DatabaseAccess;
import org.codedefenders.game.*;
import org.codedefenders.game.multiplayer.MultiplayerGame;
import org.codedefenders.game.singleplayer.automated.attacker.AiAttacker;
import org.codedefenders.game.singleplayer.automated.defender.AiDefender;
import org.codedefenders.servlets.util.Redirect;
import org.codedefenders.util.Constants;

import java.io.IOException;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.crypto.Data;

import static org.codedefenders.util.Constants.DUMMY_DEFENDER_USER_ID;

public class AdminModifyGames extends HttpServlet {

    private MultiplayerGame multiplayerGame;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        request.getRequestDispatcher(Constants.ADMIN_MODIFY_JSP).forward(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        HttpSession session = request.getSession();
        ArrayList<String> messages = new ArrayList<String>();
        session.setAttribute("messages", messages);

        switch (request.getParameter("formType")) {

            // For now removePlayer actually replaces the player with an AI-Player
            case "removePlayer":
                removePlayer(request, response, messages);
                break;
            // the admin add aidef/atk won't be needed. TODO other solution on one page!!!
            case "adminModifyAiDefender":
                String gameToAddAiDefender = request.getParameter("adminModifyAiDefender");
                multiplayerGame.setId(Integer.parseInt(gameToAddAiDefender));
                multiplayerGame = DatabaseAccess.getMultiplayerGame(multiplayerGame.getId());
                multiplayerGame.addPlayer(AiDefender.ID, Role.DEFENDER);
                // only set the game as simulation game when a bot is added
                multiplayerGame.setSimulationGame(true);
                multiplayerGame.update();
                break;
            case "adminModifyAiAttacker":
                String gameToAddAiAttacker = request.getParameter("adminModifyAiAttacker");
                multiplayerGame.setId(Integer.parseInt(gameToAddAiAttacker));
                multiplayerGame = DatabaseAccess.getMultiplayerGame(multiplayerGame.getId());
                multiplayerGame.addPlayer(AiAttacker.ID, Role.ATTACKER);
                // only set the game as simulation game when a bot is added
                multiplayerGame.setSimulationGame(true);
                multiplayerGame.update();
                break;
            default:
                System.err.println("Action not recognised");
                Redirect.redirectBack(request, response);
                break;
        }
    }

    /**
     * This creates a new multiplayer game with the selected player removed from the game.
     * So actually the game is being copied with the player missing.
     * @param request
     * @param response
     * @param messages
     * @throws IOException
     */
    private void removePlayer (HttpServletRequest request, HttpServletResponse response, ArrayList<String> messages) throws IOException {
        String gameAndPlayerId = request.getParameter("gameUserRemoveButton");
        int gameToModifyId = Integer.parseInt(gameAndPlayerId.split("-")[1]);
        int playerIdNotToCopy = Integer.parseInt(gameAndPlayerId.split("-")[0]);
        Role playersRole = DatabaseAccess.getRoleOfPlayer(playerIdNotToCopy);
        System.out.println(gameToModifyId + "okokk "+ playerIdNotToCopy);
        multiplayerGame = DatabaseAccess.getMultiplayerGame(gameToModifyId);
        // setting start and end date for the newly created game
        Date startDate = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        multiplayerGame.setStartDateTime(startDate);
        calendar.add(Calendar.DATE, 2);
        multiplayerGame.setFinishDateTime(calendar.getTime());
        multiplayerGame.setState(GameState.ACTIVE);
        // do not set this here when the structure of adding ai players to a finnished game is changed
        multiplayerGame.setSimulationGame(true);

        /* maybe take other players instead of the regular ones because this will fake statistics
        this is already done but not working 100%
        Arrays.stream(multiplayerGame.getUserIds(Role.DEFENDER))
                .forEach(id -> multiplayerGame.addPlayer(id, Role.DEFENDER));
        Arrays.stream(multiplayerGame.getUserIds(Role.ATTACKER))
                .forEach(id -> multiplayerGame.addPlayer(id, Role.ATTACKER)); */

        multiplayerGame.insert();

        List<Test> testsInGame = new ArrayList<>();
        List<Mutant> mutantsInGame = new ArrayList<>();
        if (playersRole == Role.ATTACKER) {
            testsInGame = DatabaseAccess.getTestsForGame(gameToModifyId);
            mutantsInGame = DatabaseAccess.getMutantsForGameWithoutOnePlayer(gameToModifyId, playerIdNotToCopy);
            multiplayerGame.addPlayer(AiAttacker.ID, Role.ATTACKER);
        } else if (playersRole == Role.DEFENDER) {
            testsInGame = DatabaseAccess.getTestsForGameWithoutOnePlayer(gameToModifyId, playerIdNotToCopy);
            mutantsInGame = DatabaseAccess.getMutantsForGame(gameToModifyId);
            multiplayerGame.addPlayer(AiDefender.ID, Role.DEFENDER);
        }

        addTestsMutantsPlayersToGame(gameToModifyId, playerIdNotToCopy, testsInGame, mutantsInGame);
        response.sendRedirect(request.getContextPath() + "/admin/modify");
    }

    private void addTestsMutantsPlayersToGame(int originGameId, int playerIdToRemove, List<Test> testsToAdd, List<Mutant> mutantsToAdd) {
        // The gameId has to change to the new game for each test and mutant
        System.out.println("sizes: " + testsToAdd.size() + "-" +  mutantsToAdd.size());
        MultiplayerGame originGame = DatabaseAccess.getMultiplayerGame(originGameId);
        int userIdNotToAdd = DatabaseAccess.getUserFromPlayer(playerIdToRemove).getId();
        Integer[] attackersPlayerIds = Arrays.stream(originGame.getAttackerIds()).boxed().toArray(Integer[]::new);
        Integer[] defendersPlayerIds = Arrays.stream(originGame.getDefenderIds()).boxed().toArray(Integer[]::new);
        int[] attackersUserIds = originGame.getUserIds(Role.ATTACKER);
        int[] defendersUserIds = originGame.getUserIds(Role.DEFENDER);
        Map<Integer, Integer> defenderIdBelongsToDummyId = new HashMap<>();
        Map<Integer, Integer> attackerIdBelongsToDummyId = new HashMap<>();
        System.out.println(Arrays.toString(attackersUserIds) +"||"+ Arrays.toString(defendersUserIds));
        for (int i = 0; i < attackersUserIds.length; ++i) {
            // There are only 5 simulation players.
            // When there are more than 5 players in one finished game this will break the simulation game
            if (Constants.SIMULATION_ATTACKER_IDS.length > i && attackersUserIds[i] != userIdNotToAdd) {
                attackerIdBelongsToDummyId.put(attackersUserIds[i], Constants.SIMULATION_ATTACKER_IDS[i]);
                multiplayerGame.addPlayer(Constants.SIMULATION_ATTACKER_IDS[i], Role.ATTACKER);
            }
        }
        for (int i = 0; i < defendersUserIds.length; ++i) {
            // There are only 5 simulation players.
            // When there are more than 5 players in one finished game this will break the simulation game
            if (Constants.SIMULATION_DEFENDER_IDS.length > i && defendersUserIds[i] != userIdNotToAdd) {
                defenderIdBelongsToDummyId.put(defendersUserIds[i], Constants.SIMULATION_DEFENDER_IDS[i]);
                multiplayerGame.addPlayer(Constants.SIMULATION_DEFENDER_IDS[i], Role.DEFENDER);
            }
        }

        // in dem new attacker player ids sind auch die hinzugefügten bots schon drin die müssen ignoriert werden!!!!!
        Integer[] newAttackerPlayerIds = Arrays.stream(multiplayerGame.getAttackerIds()).boxed().toArray(Integer[]::new);
        Integer[] newDefenderPlayerIds = Arrays.stream(multiplayerGame.getDefenderIds()).boxed().toArray(Integer[]::new);
        // the playerId != playerIdToRemove is a double check that no tests of the removed player get in the game
        for (Test test : testsToAdd) {
            if (test.getPlayerId() != playerIdToRemove) {
                test.setGameId(multiplayerGame.getId());
                test.setPlayerId(newDefenderPlayerIds[Arrays.asList(defendersPlayerIds).indexOf(test.getPlayerId())]);
                test.insert(false);
                // multiplayerGame.update();
            }
        }
        for (Mutant mutant : mutantsToAdd) {
            if (mutant.getPlayerId() != playerIdToRemove) {
                mutant.setGameId(multiplayerGame.getId());
                mutant.setPlayerId(newAttackerPlayerIds[Arrays.asList(attackersPlayerIds).indexOf(mutant.getPlayerId())]);
                mutant.insert(false);
                // multiplayerGame.update();
            }
        }
    }
}