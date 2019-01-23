/**
 * Copyright (C) 2016-2018 Code Defenders contributors
 * <p>
 * This file is part of Code Defenders.
 * <p>
 * Code Defenders is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 * <p>
 * Code Defenders is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with Code Defenders. If not, see <http://www.gnu.org/licenses/>.
 */
package org.codedefenders.servlets.admin;

import org.codedefenders.database.DatabaseAccess;
import org.codedefenders.game.GameState;
import org.codedefenders.game.Mutant;
import org.codedefenders.game.Test;
import org.codedefenders.game.multiplayer.MultiplayerGame;
import org.codedefenders.servlets.util.Redirect;
import org.codedefenders.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class AdminSimulateGame extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(AdminSimulateGame.class);

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        System.out.println("doget simulate");
        request.getRequestDispatcher(Constants.ADMIN_SIMULATE_JSP).forward(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        System.out.println("do post simulate game");
        final ArrayList<String> messages = new ArrayList<>();
        final HttpSession session = request.getSession();
        session.setAttribute("messages", messages);
        final int uid = (Integer) session.getAttribute("uid");
        final int gameId;
        if (request.getParameter("mpGameID") != null) {
            gameId = Integer.parseInt(request.getParameter("mpGameID"));
            session.setAttribute("mpGameId", gameId);
        } else if (session.getAttribute("mpGameId") != null) {
            gameId = (Integer) session.getAttribute("mpGameId");
        } else {
            // TODO Not sure this is 100% right
            logger.error("Problem setting gameID !");
            response.setStatus(500);
            Redirect.redirectBack(request, response);
            return;
        }
        final String contextPath = request.getContextPath();
        final MultiplayerGame gameToSimulate = DatabaseAccess.getMultiplayerGame(gameId);
        // MultiplayerGame simulatedGame = new MultiplayerGame();

        if (gameToSimulate == null) {
            logger.error("Could not retrieve game from database for gameId: {}", gameId);
            Redirect.redirectBack(request, response);
            return;
        }

        final String action = request.getParameter("formType");
        switch (action) {
            case "simulateGame": {
                if (gameToSimulate.getState().equals(GameState.ACTIVE)) {
                    logger.info("Starting simulation game {}", gameToSimulate.getId());
                    List<Test> testList = DatabaseAccess.getTestsForGame(gameToSimulate.getId());
                    List<Mutant> mutantList = gameToSimulate.getMutants();
                    System.out.println("sizes: " + testList.size() + "__" + mutantList.size());
                }
                break;
            }
            default:
                logger.info("Action not recognised: {}", action);
                Redirect.redirectBack(request, response);
                break;
        }
        // response.sendRedirect(request.getContextPath() + "/admin/simulate?id=" + simulatedGame.getId());
    }
}