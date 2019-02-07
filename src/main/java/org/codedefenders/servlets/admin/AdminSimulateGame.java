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

import com.sun.org.apache.xpath.internal.operations.Mult;
import org.apache.commons.lang.ArrayUtils;
import org.codedefenders.database.DatabaseAccess;
import org.codedefenders.execution.AntRunner;
import org.codedefenders.execution.MutationTester;
import org.codedefenders.execution.TargetExecution;
import org.codedefenders.game.*;
import org.codedefenders.game.duel.DuelGame;
import org.codedefenders.game.multiplayer.MultiplayerGame;
import org.codedefenders.game.singleplayer.NoDummyGameException;
import org.codedefenders.game.singleplayer.automated.attacker.AiAttacker;
import org.codedefenders.game.singleplayer.automated.attacker.NoMutantsException;
import org.codedefenders.game.singleplayer.automated.defender.AiDefender;
import org.codedefenders.servlets.util.Redirect;
import org.codedefenders.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.crypto.Data;

public class AdminSimulateGame extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(AdminSimulateGame.class);
    protected ArrayList<String> messages = new ArrayList<>();

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
        final MultiplayerGame simulationGame = DatabaseAccess.getMultiplayerGame(gameId);
        final MultiplayerGame dummyGame = DatabaseAccess.getMultiplayerGame(gameId);
        // MultiplayerGame simulatedGame = new MultiplayerGame();

        if (simulationGame == null) {
            logger.error("Could not retrieve game from database for gameId: {}", gameId);
            Redirect.redirectBack(request, response);
            return;
        }

        final String action = request.getParameter("formType");
        switch (action) {
            case "simulateGame": {
                if (simulationGame.getState().equals(GameState.ACTIVE)) {
                    logger.info("Starting simulation game {}", simulationGame.getId());
                    List<Test> testList = DatabaseAccess.getTestsForGame(simulationGame.getId());
                    List<Mutant> mutantList = simulationGame.getMutants();
                    System.out.println("sizes: " + testList.size() + "__" + mutantList.size());

                    // This list stores the sequence in which tests and mutants have been added to the game.
                    // The first element of the array stands for mutant(=0) or test(=1)
                    // [0] = test/mutant, [1] = timestamp, [2] = id
                    List<long[]> timeseriesList = new ArrayList<>();
                    mutantList.stream().forEach(mutant -> timeseriesList.add(new long[]{0, mutant.getTimestamp().getTime(), mutant.getId()}));
                    testList.stream().forEach(test -> timeseriesList.add(new long[]{1, test.getTimestamp().getTime(), test.getId()}));
                    timeseriesList.sort(Comparator.comparing(array -> array[1]));

                    Timestamp timeSinceAiTurn = new Timestamp(simulationGame.getStartInLong());

                    // create a new game where the tests/mutants are added and the simulation acutally takes place
                    simulationGame.insert();
                    Arrays.stream(dummyGame.getDefenderIds()).forEach(playerId ->simulationGame.addPlayer(DatabaseAccess.getUserFromPlayer(playerId).getId(), Role.DEFENDER));
                    Arrays.stream(dummyGame.getAttackerIds()).forEach(playerId ->simulationGame.addPlayer(DatabaseAccess.getUserFromPlayer(playerId).getId(), Role.ATTACKER));
                    boolean isAiDefInGame = ArrayUtils.contains(simulationGame.getUserIds(Role.DEFENDER), AiDefender.ID);
                    boolean isAiAtkInGame = ArrayUtils.contains(simulationGame.getUserIds(Role.ATTACKER), AiAttacker.ID);
                    AiAttacker aiAttacker = isAiAtkInGame ? new AiAttacker(simulationGame.getId()) : null;
                    AiDefender aiDefender = isAiDefInGame ? new AiDefender(simulationGame.getId()) : null;

                    for (int i = 0; i < timeseriesList.size(); ++i) {
                        // System.out.println("timeserieslist"+ timeseriesList.get(i)[0] +"__"+ new Timestamp(timeseriesList.get(i)[1]));
                        if (i == 0) {
                            addTestOrMutant(simulationGame, dummyGame, timeseriesList.get(i));
                            timeSinceAiTurn = new Timestamp(timeseriesList.get(i)[1]);
                            continue;
                        }
                        if (timeseriesList.size() - 1 == i) {
                            addTestOrMutant(simulationGame, dummyGame, timeseriesList.get(i));
                            continue;
                        }
                        // TODO COMPUTATION WRONG
                        // for each minute time difference the bot makes a turn
                        int nrOfAiTurns = (((int) timeseriesList.get(i)[2] - (int) timeSinceAiTurn.getTime()) / 60000) > 1 ?
                                5 : ((int) timeseriesList.get(i)[2] - (int) timeSinceAiTurn.getTime()) / 60000;

                        // System.out.println("turnnrr:" + nrOfAiTurns);

                        for (int j = 0; j < nrOfAiTurns; ++j) {
                            if (isAiAtkInGame) {
                                aiAttacker.turnEasy();
                                timeSinceAiTurn = new Timestamp(timeseriesList.get(i)[1]);
                            }
                            if (isAiDefInGame) {
                                aiDefender.turnHard();
                                timeSinceAiTurn = new Timestamp(timeseriesList.get(i)[1]);
                            }
                        }

                        addTestOrMutant(simulationGame, dummyGame, timeseriesList.get(i));
                    }
                    response.sendRedirect(request.getContextPath() + "/admin/simulate?id=" + simulationGame.getId());
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

    private void addTestOrMutant(MultiplayerGame simulationGame, MultiplayerGame dummyGame, long[] testOrMutantToAdd) {
        // [0]: 0 = mutant, 1 = test
        try {
            if (testOrMutantToAdd[0] == 0) {
                useMutantFromSuite(simulationGame, dummyGame, (int) testOrMutantToAdd[2]);
            } else if (testOrMutantToAdd[0] == 1) {
                useTestFromSuite(simulationGame, dummyGame, (int) testOrMutantToAdd[2]);
            }
        } catch (NoMutantsException e) {
            e.printStackTrace();
        } catch (NoDummyGameException e) {
            e.printStackTrace();
        }
    }

    private void useTestFromSuite(MultiplayerGame game, MultiplayerGame dummyGame, int origTestNum) throws NoDummyGameException {
        List<Test> origTests = DatabaseAccess.getTestsForGame(dummyGame.getId());

        Test origTest = null;

        for (Test t : origTests) {
            if(t.getId() == origTestNum) {
                origTest = t;
                break;
            }
        }

        if(origTest != null) {
            String jFile = origTest.getJavaFile();
            String cFile = origTest.getClassFile();
            int playerId = origTest.getPlayerId();
            Test t = new Test(game.getId(), jFile, cFile, playerId);
            System.out.println("jfile of t " + t.getJavaFile());
            t.insert(false);
            t.update();
            TargetExecution newExec = new TargetExecution(t.getId(), 0, TargetExecution.Target.COMPILE_TEST, "SUCCESS", null);
            newExec.insert();
            MutationTester.runTestOnAllMultiplayerMutants(game, t, messages);
            File dir = new File(origTest.getDirectory());
            AntRunner.testOriginal(dir, t);
            game.update();
            // getMessagesLastTurn();
        }
    }

    private void useMutantFromSuite(MultiplayerGame game, MultiplayerGame dummyGame, int origMutNum) throws NoMutantsException, NoDummyGameException {
        List<Mutant> origMutants = dummyGame.getMutants();

        Mutant origM = null;

        for (Mutant m : origMutants) {
            if(m.getId() == origMutNum) {
                origM = m;
                break;
            }
        }

        if(origM == null) {
            throw new NoMutantsException("No mutant exists for ID: " + origMutNum);
        }

        String jFile = origM.getSourceFile();
        String cFile = origM.getClassFile();
        int playerId = origM.getPlayerId();
        Mutant m = new Mutant(game.getId(), jFile, cFile, true, playerId);
        m.insert(false);
        m.update();
        TargetExecution newExec = new TargetExecution(0, m.getId(), TargetExecution.Target.COMPILE_MUTANT, "SUCCESS", null);
        newExec.insert();

        MutationTester.runAllTestsOnMutant(game, m, messages);
        DatabaseAccess.setAiMutantAsUsed(origMutNum, game);
        game.update();

        // getMessagesLastTurn();
    }
}