package org.codedefenders.game.singleplayer.automated.defender;

import org.codedefenders.execution.AntRunner;
import org.codedefenders.game.GameClass;
import org.codedefenders.game.duel.DuelGame;
import org.codedefenders.database.DatabaseAccess;
import org.codedefenders.execution.TargetExecution;
import org.codedefenders.game.Test;
import org.codedefenders.util.Constants;
import org.codedefenders.util.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        List<DuelGame> gamesWithCUT = DatabaseAccess.getGamesForClass(cId);
        // no need to compile those cause they already were compiled

        // in the test.getJavaFile is the path. we need the content...

        // >>> we first need to compile the testsuit so it is in the ai folder!!!!<<<<
        // do we even need to compile cause the tests are submitted by users...
        // maybe figure out which test is better for a certain line of code?
        ArrayList<String> testStrings = new ArrayList<String>();
        for (DuelGame game : gamesWithCUT) {
            List<Test> testsForGame = game.getTests();
            for (Test test : testsForGame) {
                testStrings.add(test.getAsString());
                System.out.println("hier" + test.getAsString());
            }
        }

        validTests = new ArrayList<Test>();

        try {
            System.out.println("size:" + testStrings.size());
            for (String t : testStrings) {
                File newTestDir = FileUtils.getNextSubDir(AI_DIR + F_SEP + "tests" +
                        F_SEP + cut.getAlias());
                String jFile = FileUtils.createJavaFile(newTestDir, cut.getBaseName(), t);
                Test newTest = AntRunner.compileTest(newTestDir, jFile, dGame.getId(), cut, AiDefender.ID);
                TargetExecution compileTestTarget = DatabaseAccess.getTargetExecutionForTest(newTest, TargetExecution.Target.COMPILE_TEST);
                System.out.println("exe" + jFile);
                if (compileTestTarget != null && compileTestTarget.status.equals("SUCCESS")) {
                    AntRunner.testOriginal(newTestDir, newTest);
                    validTests.add(newTest);
                }
            }
        } catch (IOException e) {
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
