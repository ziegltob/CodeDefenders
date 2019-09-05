package org.codedefenders.game.singleplayer.automated.defender;

import org.codedefenders.execution.AntRunner;
import org.codedefenders.game.GameClass;
import org.codedefenders.game.duel.DuelGame;
import org.codedefenders.database.DatabaseAccess;
import org.codedefenders.execution.TargetExecution;
import org.codedefenders.game.Test;
import org.codedefenders.game.multiplayer.MultiplayerGame;
import org.codedefenders.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.codedefenders.util.Constants.*;

public class GenerateTestPool {

    private int cId;
    private GameClass cut;
    private DuelGame dGame;
    private ArrayList<Test> validTests;

    public GenerateTestPool(int classId, DuelGame dummyGame) {
        cId = classId;
        cut = DatabaseAccess.getClassForKey("Class_ID", cId);
        dGame = dummyGame;
    }

    public ArrayList<Test> getValidTests() {
        return validTests;
    }

    public boolean generateTests() throws Exception {
        List<MultiplayerGame> gamesWithCUT = DatabaseAccess.getGamesForClass(cId);

        ArrayList<String> testStrings = new ArrayList<String>();
        for (MultiplayerGame game : gamesWithCUT) {
            // this does not check for targetexecutions since there are only 1/3 targetexecutions
            // for all tests in the database dump i am using for the simulation
            List<Test> testsForGame = DatabaseAccess.getTestsForGame(game.getId());
            for (Test test : testsForGame) {
                testStrings.add(test.getAsString());
            }
        }

        validTests = new ArrayList<>();

        try {
            for (String t : testStrings) {
                File newTestDir = FileUtils.getNextSubDir(AI_DIR + F_SEP + "tests" +
                        F_SEP + cut.getAlias());
                String jFile = FileUtils.createJavaFile(newTestDir, cut.getBaseName(), t);
                Test newTest = AntRunner.compileTest(newTestDir, jFile, dGame.getId(), cut, AiDefender.ID);
                TargetExecution compileTestTarget = DatabaseAccess.getTargetExecutionForTest(newTest, TargetExecution.Target.COMPILE_TEST);
                if (compileTestTarget != null && compileTestTarget.status.equals("SUCCESS")) {
                    AntRunner.testOriginal(newTestDir, newTest);
                    validTests.add(newTest);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        if(validTests.isEmpty()) {
            //No valid tests.
            NoTestsException e = new NoTestsException();
            throw e;
        }
        return true;
    }
}
