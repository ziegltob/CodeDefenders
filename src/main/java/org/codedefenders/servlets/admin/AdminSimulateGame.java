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

import org.apache.commons.lang.ArrayUtils;
import org.codedefenders.database.AdminDAO;
import org.codedefenders.database.DatabaseAccess;
import org.codedefenders.execution.AntRunner;
import org.codedefenders.execution.MutationTester;
import org.codedefenders.execution.TargetExecution;
import org.codedefenders.game.*;
import org.codedefenders.game.multiplayer.MultiplayerGame;
import org.codedefenders.game.singleplayer.AiPlayer;
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
import java.sql.Timestamp;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class AdminSimulateGame extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(AdminSimulateGame.class);
    protected ArrayList<String> messages = new ArrayList<>();

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        request.getRequestDispatcher(Constants.ADMIN_SIMULATE_JSP).forward(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
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
            logger.error("Problem setting gameID !");
            response.setStatus(500);
            Redirect.redirectBack(request, response);
            return;
        }
        final String contextPath = request.getContextPath();
        final MultiplayerGame simulationGame = DatabaseAccess.getMultiplayerGame(gameId);
        final MultiplayerGame dummyGame = DatabaseAccess.getMultiplayerGame(gameId);

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
                    List<Test> testList = DatabaseAccess.getTestsForGame(dummyGame.getId());
                    List<Mutant> mutantList = dummyGame.getMutants();

                    // This list stores the sequence in which tests and mutants have been added to the game.
                    // The first element of the array stands for mutant(=0) or test(=1)
                    // [0] = test/mutant, [1] = timestamp, [2] = id
                    List<long[]> timeseriesList = new ArrayList<>();
                    mutantList.stream().forEach(mutant -> timeseriesList.add(new long[]{0, mutant.getTimestamp().getTime(), mutant.getId()}));
                    testList.stream().forEach(test -> timeseriesList.add(new long[]{1, test.getTimestamp().getTime(), test.getId()}));
                    timeseriesList.sort(Comparator.comparing(array -> array[1]));

                    Timestamp timeSinceAiTurn = new Timestamp(dummyGame.getStartInLong());

                    // create a new game where the tests/mutants are added and the simulation acutally takes place
                    simulationGame.setOriginGameId(dummyGame.getOriginGameId());
                    simulationGame.setSimulationGame(true);
                    simulationGame.setAiStrat(AiPlayer.GenerationMethod.valueOf(AdminDAO.getSystemSetting(AdminSystemSettings.SETTING_NAME.AI_STRAT).getStringValue()));
                    simulationGame.insert();
                    Arrays.stream(dummyGame.getDefenderIds()).forEach(playerId ->simulationGame.addPlayer(DatabaseAccess.getUserFromPlayer(playerId).getId(), Role.DEFENDER));
                    Arrays.stream(dummyGame.getAttackerIds()).forEach(playerId ->simulationGame.addPlayer(DatabaseAccess.getUserFromPlayer(playerId).getId(), Role.ATTACKER));
                    boolean isAiDefInGame = ArrayUtils.contains(simulationGame.getUserIds(Role.DEFENDER), AiDefender.ID);
                    boolean isAiAtkInGame = ArrayUtils.contains(simulationGame.getUserIds(Role.ATTACKER), AiAttacker.ID);
                    AiAttacker aiAttacker = isAiAtkInGame ? new AiAttacker(simulationGame.getId()) : null;
                    AiDefender aiDefender = isAiDefInGame ? new AiDefender(simulationGame.getId()) : null;

                    int aiTurnCounter = 0;
                    int iterationCount = 0;
                    MultiplayerGame constantlyUpdatedSimulationGame;
                    for (int i = 0; i < timeseriesList.size(); ++i) {
                        constantlyUpdatedSimulationGame = DatabaseAccess.getMultiplayerGame(simulationGame.getId());

                        // the bot won't do anything before the first move anyways so we skip him
                        if (i == 0) {
                            addTestOrMutant(constantlyUpdatedSimulationGame, dummyGame, timeseriesList.get(i));
                            timeSinceAiTurn = new Timestamp(timeseriesList.get(i)[1]);
                            constantlyUpdatedSimulationGame.update();
                            continue;
                        }

                        // for each minute time difference the bot makes a turn
                        int nrOfAiTurns = (((int) timeseriesList.get(i)[1] - (int) timeSinceAiTurn.getTime()) / 60000);
                        aiTurnCounter += nrOfAiTurns;
                        for (int j = 0; j < nrOfAiTurns; ++j) {
                            if (isAiAtkInGame) {
                                aiAttacker.runTurn(AiPlayer.GenerationMethod.valueOf(AdminDAO.getSystemSetting(AdminSystemSettings.SETTING_NAME.AI_STRAT).getStringValue()));
                                timeSinceAiTurn = new Timestamp(timeseriesList.get(i)[1]);
                            }
                            if (isAiDefInGame) {
                                aiDefender.runTurn(AiPlayer.GenerationMethod.valueOf(AdminDAO.getSystemSetting(AdminSystemSettings.SETTING_NAME.AI_STRAT).getStringValue()));
                                timeSinceAiTurn = new Timestamp(timeseriesList.get(i)[1]);
                            }
                        }

                        if (!addTestOrMutant(constantlyUpdatedSimulationGame, dummyGame, timeseriesList.get(i))) {
                            logger.debug("Test(=1)/Mutant(=0) {} with ID {} was not added" +
                                    " during simulation.", timeseriesList.get(i)[0], timeseriesList.get(i)[2]);
                        }
                        constantlyUpdatedSimulationGame.update();
                        iterationCount = i;
                    }

                    int countt = 0;
                    int countm = 0;
                    for (int x = 0; x < timeseriesList.size(); ++x) {
                        if (timeseriesList.get(x)[0] == 0) {
                            countt++;
                        }
                        if (timeseriesList.get(x)[0] == 1) {
                            countm++;
                        }
                    }
                    simulationGame.setState(GameState.FINISHED);
                    simulationGame.update();
                    response.sendRedirect(request.getContextPath() + "/admin/simulate?id=" + simulationGame.getId());
                }
                break;
            }
            default:
                logger.info("Action not recognised: {}", action);
                Redirect.redirectBack(request, response);
                break;
        }
    }

    private boolean addTestOrMutant(MultiplayerGame simulationGame, MultiplayerGame dummyGame, long[] testOrMutantToAdd) {
        // [0]: 0 = mutant, 1 = test; [1] = timestamp; [2] = id
        try {
            if (testOrMutantToAdd[0] == 0) {
                useMutantFromSuite(simulationGame, dummyGame, (int) testOrMutantToAdd[2]);
                return true;
            } else if (testOrMutantToAdd[0] == 1) {
                useTestFromSuite(simulationGame, dummyGame, (int) testOrMutantToAdd[2]);
                return true;
            }
        } catch (NoMutantsException e) {
            e.printStackTrace();
            return false;
        } catch (NoDummyGameException e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    private void useTestFromSuite(MultiplayerGame game, MultiplayerGame dummyGame, int origTestNum) throws NoDummyGameException {
        // dummyGame has the stored tests
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
            if (origTest.getClassFile() == null) {
                origTest = AntRunner.recompileTest(origTest.getId(), dummyGame.getCUT());
            }
            String cFile = origTest.getClassFile();
            int playerId = DatabaseAccess.getPlayerIdForMultiplayerGame(DatabaseAccess.getUserFromPlayer(origTest.getPlayerId()).getId(), game.getId());
            Test t = new Test(origTest.getClassId(), game.getId(), jFile, cFile, playerId);
            t.insert(false);
            t.setLineCoverage(origTest.getLineCoverage());
            t.update();
            // instead of compiling it again we can take the old execution -> save time
            TargetExecution newExec = new TargetExecution(t.getId(), 0, TargetExecution.Target.COMPILE_TEST, TargetExecution.Status.SUCCESS, null);
            newExec.insert();
            TargetExecution testExecution = new TargetExecution(t.getId(), 0, TargetExecution.Target.TEST_ORIGINAL, TargetExecution.Status.SUCCESS, null);
            testExecution.insert();
            MutationTester.runTestOnAllMultiplayerMutants(game, t, messages);
            game.update();
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
        if (origM.getClassFile() == null) {
            origM = AntRunner.recompileMutant(origM.getId(), dummyGame.getCUT());
        }
        String jFile = origM.getSourceFile();
        String cFile = origM.getClassFile();
        int playerId = DatabaseAccess.getPlayerIdForMultiplayerGame(DatabaseAccess.getUserFromPlayer(origM.getPlayerId()).getId(), game.getId());
        Mutant m = new Mutant(game.getId(), origM.getClassId(), jFile, cFile, true, playerId);
        m.insert(false);
        TargetExecution newExec = new TargetExecution(0, m.getId(), TargetExecution.Target.COMPILE_MUTANT, TargetExecution.Status.SUCCESS, null);
        newExec.insert();

        MutationTester.runAllTestsOnMutant(game, m, messages);
        game.update();
        }
}