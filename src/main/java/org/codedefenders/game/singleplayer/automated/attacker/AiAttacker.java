/*
 * Copyright (C) 2016-2019 Code Defenders contributors
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
package org.codedefenders.game.singleplayer.automated.attacker;

import org.apache.commons.lang3.Range;
import org.codedefenders.database.AdminDAO;
import org.codedefenders.execution.AntRunner;
import org.codedefenders.execution.ExecutorPool;
import org.codedefenders.execution.TargetExecution;
import org.codedefenders.game.*;
import org.codedefenders.execution.MutationTester;
import org.codedefenders.game.duel.DuelGame;
import org.codedefenders.game.multiplayer.MultiplayerGame;
import org.codedefenders.game.multiplayer.PlayerScore;
import org.codedefenders.game.singleplayer.AiPlayer;
import org.codedefenders.game.singleplayer.NoDummyGameException;
import org.codedefenders.game.singleplayer.PrepareAI;
import org.codedefenders.database.DatabaseAccess;
import org.codedefenders.servlets.admin.AdminSystemSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * @author Ben Clegg
 * An AI attacker, which chooses mutants generated by Major when the class is uploaded.
 */
public class AiAttacker extends AiPlayer {

    private static final Logger logger = LoggerFactory.getLogger(AiAttacker.class);

    public static final int ID = 1;

    public AiAttacker(int gameId) {
        super(DatabaseAccess.getMultiplayerGame(gameId));
        role = Role.ATTACKER;
    }

    /**
     * Hard difficulty attacker turn.
     * @return true if mutant generation succeeds, or if no non-existing mutants have been found to prevent infinite loop.
     */
    public boolean turnHard() {
        //Choose a mutant which is killed by few generated tests.
        return runTurn(GenerationMethod.KILLCOUNT);
    }

    /**
     * Easy difficulty attacker turn.
     * @return true if mutant generation succeeds, or if no non-existing mutants have been found to prevent infinite loop.
     */
    public boolean turnEasy() {
        //Choose a random mutant.
        return runTurn(GenerationMethod.RANDOM);
    }

    /**
     * Attempts to submit a mutant, according to a strategy
     * @param strat Generation strategy to use
     * @return true if mutant submitted, false otherwise
     */
    public boolean runTurn(GenerationMethod strat) {
        multiplayerGame = DatabaseAccess.getMultiplayerGame(game.getId());
        if (multiplayerGame.getState() == GameState.FINISHED) {
            if (DatabaseAccess.getJoinedMultiplayerGamesForUser(AiAttacker.ID).stream()
                    .filter(joinedGames -> joinedGames.getId() == multiplayerGame.getId())
                    .findFirst().isPresent()) {
                int aiAttackerPlayerId = IntStream.of(multiplayerGame.getAttackerIds())
                        .filter(id -> DatabaseAccess.getUserFromPlayer(id).getId() == AiAttacker.ID).findFirst().getAsInt();
                ExecutorPool.getInstanceOf().cancelTask(aiAttackerPlayerId, true);
                return false;
            }
        }

        // should the bot actually do something: depends on game score and mutant/test relation
        // numbers are made up and calculated from the db dump of a testing session
        // TODO: move this to AiPlayer class together with the similar function in AiDefender
        HashMap mutantScores = multiplayerGame.getMutantScores();
        HashMap testScores = multiplayerGame.getTestScores();
        int attackerScore = 0;
        int defenderScore = 0;
        if (mutantScores.containsKey(-1) && mutantScores.get(-1) != null) {
            attackerScore += ((PlayerScore) mutantScores.get(-1)).getTotalScore();
        }
        if (testScores.containsKey(-2) && testScores.get(-2) != null) {
            attackerScore += ((PlayerScore) testScores.get(-2)).getTotalScore();
        }
        if (testScores.containsKey(-1) && testScores.get(-1) != null) {
            defenderScore += ((PlayerScore) testScores.get(-1)).getTotalScore();
        }
        if (defenderScore + AdminDAO.getSystemSetting(AdminSystemSettings.SETTING_NAME.AI_ATTACKER_POINTS_DIFFERENCE).getIntValue() < attackerScore
                || multiplayerGame.getMutants().size() > multiplayerGame.getTests().size() * AdminDAO.getSystemSetting(AdminSystemSettings.SETTING_NAME.AI_TEST_MUTANT_RELATION).getFloatValue()
                || multiplayerGame.getTests().size() == 0) {
            logger.info("AI-Attacker doing nothing due to game scores or test-mutant relation.");
            return false;
        }

        try {
            int mNum = selectMutant(strat);
            useMutantFromSuite(mNum);
        } catch (NoMutantsException e) {
            //No more unused mutants remain,
            return false;
        } catch (Exception e) {
            //Something's gone wrong
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private int selectMutant(GenerationMethod strategy) throws NoMutantsException, NoDummyGameException {
        List<Integer> usedMutants = DatabaseAccess.getUsedAiMutantsForGame(game);
        GameClass cut = game.getCUT();
        DuelGame dummyGame = cut.getDummyGame();
        List<Mutant> candidateMutants = dummyGame.getMutantsFromPool().stream().filter(mutant -> !usedMutants.contains(mutant.getId())).collect(Collectors.toList());

        if (candidateMutants.isEmpty()) {
            throw new NoMutantsException("No unused generated mutants remain.");
        }

        switch (strategy) {
            case RANDOM:
                Random r = new Random();
                Mutant selected = candidateMutants.get(r.nextInt(candidateMutants.size()));
                return selected.getId();
            case KILLCOUNT:
                return getMutantIdByKillcount(candidateMutants);
            case COVERAGE:
                return getMutantIdByCoverage(candidateMutants);
            default:
                throw new UnsupportedOperationException("Not implemented");
        }
    }

    private int getMutantIdByCoverage(List<Mutant> possibleMutants) {
        // HashMap Key: line number of class, Value: total count how many tests and mutants are covering this line
        HashMap<Integer, Integer> modifiedLines = new HashMap<>();
        GameClass cut = game.getCUT();
        List<Range<Integer>> linesOfCoverableCode = new ArrayList<>();
        linesOfCoverableCode.addAll(cut.getLinesOfMethods());

        for (Test test : multiplayerGame.getTests()) {
            for (Integer line : test.getLineCoverage().getLinesCovered()) {
                if (modifiedLines.keySet().contains(line)) {
                    Integer i = modifiedLines.get(line);
                    modifiedLines.put(line, ++i);
                } else {
                    modifiedLines.put(line, 1);
                }
            }
        }

        for (Mutant m : multiplayerGame.getMutants()) {
            for (Integer line : m.getLines()) {
                if (modifiedLines.keySet().contains(line)) {
                    Integer i = modifiedLines.get(line);
                    modifiedLines.put(line, ++i);
                } else {
                    modifiedLines.put(line, 1);
                }
            }
        }
        logger.debug("All modifeid lines: {}", modifiedLines.toString());

        // check if there are lines that are not covered at all and if there are any find a mutant for those lines
        for (Range<Integer> coverableLines : linesOfCoverableCode) {
            for (int i = coverableLines.getMinimum(); i <= coverableLines.getMaximum(); ++i) {
                if (!modifiedLines.keySet().contains(i)) {
                    final int line = i;
                    Mutant selectedMutant = possibleMutants.stream()
                            .filter(mutant -> mutant.getLines().contains(line))
                            .findFirst().orElse(null);
                    if (selectedMutant != null) {
                        return selectedMutant.getId();
                    } else {
                        continue;
                    }
                }
            }
        }

        // sort the map so the lines covered the least are on top
        Map<Integer, Integer> sortedModifiedLines = modifiedLines.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        for (Map.Entry<Integer, Integer> line : sortedModifiedLines.entrySet()) {
            Mutant selectedMutant = possibleMutants.stream()
                    .filter(mutant -> mutant.getLines().contains(line))
                    .findFirst().orElse(null);
            if (selectedMutant != null) {
                return selectedMutant.getId();
            }
        }

        return -1;
    }

    private int getMutantIdByKillcount(List<Mutant> possibleMutants) {
        possibleMutants.sort(new MutantComparator());
        List<Test> tests = multiplayerGame.getTests();

        // HashMap Key: line number of class, Value: count by how many tests the line is covered
        HashMap<Integer, Integer> lineCoverage = new HashMap<>();
        for (Test test : tests) {
            for (Integer line : test.getLineCoverage().getLinesCovered()) {
                if (lineCoverage.keySet().contains(line)) {
                    Integer i = lineCoverage.get(line);
                    lineCoverage.put(line, ++i);
                } else {
                    lineCoverage.put(line, 1);
                }
            }
        }

        // sort the map so the lines covered the most are on top
        Map<Integer, Integer> sortedLineCoverage = lineCoverage.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        // counter to only try a certain amount of mutants on an AiAttacker move
        int skipAiAttackerMove = 0;
        for (Mutant mutant : possibleMutants) {
            for (Map.Entry<Integer, Integer> line : sortedLineCoverage.entrySet()) {
                if (mutant.getLines().contains(line.getKey())) {
                    boolean mutantKilled = MutationTester.runAllTestsOnMutantWithoutScoring(multiplayerGame, mutant);
                    if (!mutantKilled) {
                        return mutant.getId();
                    }
                }
                ++skipAiAttackerMove;
            }
            if (skipAiAttackerMove > 5) {
                return -1;
            }
        }

        return -1;
    }

    private void useMutantFromSuite(int origMutNum) throws NoMutantsException, NoDummyGameException {
        if (origMutNum != -1) {
            GameClass cut = game.getCUT();
            DuelGame dummyGame = cut.getDummyGame();
            List<Mutant> origMutants = dummyGame.getMutants();

            Mutant origM = null;

            for (Mutant m : origMutants) {
                if (m.getId() == origMutNum) {
                    origM = m;
                    break;
                }
            }

            if (origM == null) {
                throw new NoMutantsException("No mutant exists for ID: " + origMutNum);
            }

            if (origM.getClassFile() == null) {
                origM = AntRunner.recompileMutant(origM.getId(), dummyGame.getCUT());
            }
            String jFile = origM.getSourceFile();
            String cFile = origM.getClassFile();
            int playerId = DatabaseAccess.getPlayerIdForMultiplayerGame(ID, game.getId());
            Mutant m = new Mutant(game.getId(), origM.getClassId(), jFile, cFile, true, playerId);
            m.insert(false);
            TargetExecution newExec = new TargetExecution(0, m.getId(), TargetExecution.Target.COMPILE_MUTANT, TargetExecution.Status.SUCCESS, null);
            newExec.insert();

            MutationTester.runAllTestsOnMutant(multiplayerGame, m, messages);
            DatabaseAccess.setAiMutantAsUsed(origMutNum, (MultiplayerGame) game);
            multiplayerGame.update();

            getMessagesLastTurn();
        } else {
            logger.info("Ai Attacker did not find a Mutant to use from the MutantPool.");
        }
    }

    @Override
    public ArrayList<String> getMessagesLastTurn() {
        boolean killed = false;
        for (String s : messages) {
            if (s.contains("killed your mutant")) {
                killed = true;
                break;
            }
        }
        messages.clear();
        if (killed)
            messages.add("The AI submitted a new mutant, but one of your tests killed it immediately!");
        else
            messages.add("The AI submitted a new mutant.");
        return messages;
    }
}