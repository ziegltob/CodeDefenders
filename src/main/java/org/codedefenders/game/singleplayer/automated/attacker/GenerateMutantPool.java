package org.codedefenders.game.singleplayer.automated.attacker;

import org.codedefenders.database.DatabaseAccess;
import org.codedefenders.execution.AntRunner;
import org.codedefenders.execution.TargetExecution;
import org.codedefenders.game.AbstractGame;
import org.codedefenders.game.GameClass;
import org.codedefenders.game.Mutant;
import org.codedefenders.game.duel.DuelGame;
import org.codedefenders.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.codedefenders.util.Constants.AI_DIR;
import static org.codedefenders.util.Constants.F_SEP;
import static org.codedefenders.util.Constants.JAVA_SOURCE_EXT;

public class GenerateMutantPool {
    private static final Logger logger = LoggerFactory.getLogger(GenerateMutantPool.class);


    private int cId;
    private GameClass cut;
    private AbstractGame game;
    private ArrayList<Mutant> validMutants;

    public GenerateMutantPool(int cId, AbstractGame game) {
        this.cId = cId;
        this.cut = DatabaseAccess.getClassForKey("Class_ID", this.cId);
        this.game = game;
    }

    public ArrayList<Mutant> getValidMutants() {
        return this.validMutants;
    }

    public boolean generateMutants() throws NoMutantsException {
        List<DuelGame> gamesWithCUT = DatabaseAccess.getGamesForClass(cId);
        ArrayList<String> mutantStrings = new ArrayList<>();
        for (DuelGame game : gamesWithCUT) {
            List<Mutant> mutantsForGame = game.getMutants();
            for (Mutant mutant : mutantsForGame) {
                mutantStrings.add(mutant.getAsString());
            }
        }
        validMutants = new ArrayList<Mutant>();
        try {
            System.out.println("size:" + mutantStrings.size());
            for (String mutantString : mutantStrings) {
                File newMutantDir = FileUtils.getNextSubDir(AI_DIR + F_SEP + "mutants" +
                        F_SEP + cut.getAlias());
                String fileName = newMutantDir + F_SEP + cut.getBaseName() + JAVA_SOURCE_EXT;
                File mutantFile = new File(fileName);
                FileWriter fileWriter = new FileWriter(mutantFile);
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                bufferedWriter.write(mutantString);
                bufferedWriter.close();
                fileWriter.close();

                Mutant newMutant = AntRunner.compileMutant(newMutantDir, fileName, game.getId(), cut, AiAttacker.ID);
                TargetExecution compileMutantTarget = DatabaseAccess.getTargetExecutionForMutant(newMutant, TargetExecution.Target.COMPILE_MUTANT);

                if (newMutant != null && compileMutantTarget != null
                        && compileMutantTarget.status.equals("SUCCESS")) {
                    if (newMutant.getClass() != null) {
                        validMutants.add(newMutant);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Could not write mutant.", e);
            return false;
        }

        if(validMutants.isEmpty()) {
            // No valid mutants.
            NoMutantsException e = new NoMutantsException();
            throw e;
        }
        return true;
    }
}
