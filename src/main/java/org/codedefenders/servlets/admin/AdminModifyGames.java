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
import org.codedefenders.servlets.util.Redirect;
import org.codedefenders.util.Constants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.crypto.Data;

public class AdminModifyGames extends HttpServlet {

    private MultiplayerGame multiplayerGame;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        request.getRequestDispatcher(Constants.ADMIN_MODIFY_JSP).forward(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        HttpSession session = request.getSession();
        // Get their user id from the session.
        ArrayList<String> messages = new ArrayList<String>();
        session.setAttribute("messages", messages);

        switch (request.getParameter("formType")) {

            case "removePlayer":
                removePlayer(request, response, messages);
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
        int gameId = Integer.parseInt(gameAndPlayerId.split("-")[1]);
        int playerIdNotToCopy = Integer.parseInt(gameAndPlayerId.split("-")[0]);
        Role playersRole = DatabaseAccess.getRoleOfPlayer(playerIdNotToCopy);
        System.out.println(gameId + "okokk "+ playerIdNotToCopy);
        multiplayerGame = DatabaseAccess.getMultiplayerGame(gameId);
        // setting start and end date for the newly created game
        Date startDate = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        multiplayerGame.setStartDateTime(startDate);
        calendar.add(Calendar.DATE, 2);
        multiplayerGame.setFinishDateTime(calendar.getTime());
        multiplayerGame.setState(GameState.ACTIVE);
        // do this only when a bot is added
        multiplayerGame.setSimulationGame(true);
        multiplayerGame.insert();
        List<Test> testsInGame = new ArrayList<>();
        List<Mutant> mutantsInGame = new ArrayList<>();
        if (playersRole == Role.ATTACKER) {
            testsInGame = DatabaseAccess.getTestsForGame(gameId);
            mutantsInGame = DatabaseAccess.getMutantsForGameWithoutOnePlayer(gameId, playerIdNotToCopy);
        } else if (playersRole == Role.DEFENDER) {
            testsInGame = DatabaseAccess.getTestsForGameWithoutOnePlayer(gameId, playerIdNotToCopy);
            mutantsInGame = DatabaseAccess.getMutantsForGame(gameId);
        }

        addTestsAndMutantsToGame(multiplayerGame.getId(), testsInGame, mutantsInGame);
        response.sendRedirect(request.getContextPath() + "/admin/modify");
    }

    private void addTestsAndMutantsToGame(int gameId, List<Test> testsToAdd, List<Mutant> mutantsToAdd) {
        // The gameId has to change to the new game for each test and mutant
        System.out.println("sizes: " + testsToAdd.size() + "-" +  mutantsToAdd.size());
        for (Test test : testsToAdd) {
            test.setGameId(gameId);
            test.insert(false);
            multiplayerGame.update();
        }
        for (Mutant mutant : mutantsToAdd) {
            mutant.setGameId(gameId);
            mutant.insert(false);
            multiplayerGame.update();
        }
    }


    private void startStopGame(HttpServletRequest request, HttpServletResponse response, ArrayList<String> messages) throws IOException {
        String playerToRemoveIdGameIdString = request.getParameter("activeGameUserRemoveButton");
        String playerToSwitchIdGameIdString = request.getParameter("activeGameUserSwitchButton");
        boolean switchUser = playerToSwitchIdGameIdString != null;
        if (playerToRemoveIdGameIdString != null || playerToSwitchIdGameIdString != null) { // admin is removing user from temp game
            int playerToRemoveId = Integer.parseInt((switchUser ? playerToSwitchIdGameIdString : playerToRemoveIdGameIdString).split("-")[0]);
            int gameToRemoveFromId = Integer.parseInt((switchUser ? playerToSwitchIdGameIdString : playerToRemoveIdGameIdString).split("-")[1]);
            int userId = DatabaseAccess.getUserFromPlayer(playerToRemoveId).getId();
            if (!deletePlayer(playerToRemoveId, gameToRemoveFromId))
                messages.add("Deleting player " + playerToRemoveId + " failed! \n Please check the logs!");
            else if (switchUser) {
                Role newRole = Role.valueOf(playerToSwitchIdGameIdString.split("-")[2]).equals(Role.ATTACKER)
                        ? Role.DEFENDER : Role.ATTACKER;
                multiplayerGame = DatabaseAccess.getMultiplayerGame(gameToRemoveFromId);
                if (!multiplayerGame.addPlayerForce(userId, newRole))
                    messages.add("Inserting user " + userId + " failed! \n Please check the logs!");
            }

        } else {  // admin is starting or stopping selected games
            String[] selectedGames = request.getParameterValues("selectedGames");

            if (selectedGames == null) {
                // admin is starting or stopping a single game
                int gameId = -1;
                // Get the identifying information required to create a game from the submitted form.

                try {
                    gameId = Integer.parseInt(request.getParameter("start_stop_btn"));
                } catch (Exception e) {
                    messages.add("There was a problem with the form.");
                    response.sendRedirect(request.getContextPath() + "/admin");
                    return;
                }


                String errorMessage = "ERROR trying to start or stop game " + String.valueOf(gameId)
                        + ".\nIf this problem persists, contact your administrator.";

                multiplayerGame = DatabaseAccess.getMultiplayerGame(gameId);

                if (multiplayerGame == null) {
                    messages.add(errorMessage);
                } else {
                    GameState newState = multiplayerGame.getState() == GameState.ACTIVE ? GameState.FINISHED : GameState.ACTIVE;
                    multiplayerGame.setState(newState);
                    if (!multiplayerGame.update()) {
                        messages.add(errorMessage);
                    }
                }
            } else {
                GameState newState = request.getParameter("games_btn").equals("Start Games") ? GameState.ACTIVE : GameState.FINISHED;
                for (String gameId : selectedGames) {
                    multiplayerGame = DatabaseAccess.getMultiplayerGame(Integer.parseInt(gameId));
                    multiplayerGame.setState(newState);
                    if (!multiplayerGame.update()) {
                        messages.add("ERROR trying to start or stop game " + String.valueOf(gameId));
                    }
                }
            }
        }
        response.sendRedirect(request.getContextPath() + "/admin/modify");
    }

    private static boolean deletePlayer(int pid, int gid) {
        for (Test t : DatabaseAccess.getTestsForGame(gid)) {
            if (t.getPlayerId() == pid)
                AdminDAO.deleteTestTargetExecutions(t.getId());
        }
        for (Mutant m : DatabaseAccess.getMutantsForGame(gid)) {
            if (m.getPlayerId() == pid)
                AdminDAO.deleteMutantTargetExecutions(m.getId());
        }
        DatabaseAccess.removePlayerEventsForGame(gid, pid);
        AdminDAO.deleteAttackerEquivalences(pid);
        AdminDAO.deleteDefenderEquivalences(pid);
        AdminDAO.deletePlayerTest(pid);
        AdminDAO.deletePlayerMutants(pid);
        return AdminDAO.deletePlayer(pid);
    }

}