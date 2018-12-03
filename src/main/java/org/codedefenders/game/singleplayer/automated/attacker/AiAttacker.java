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
package org.codedefenders.game.singleplayer.automated.attacker;

import org.apache.commons.lang.StringEscapeUtils;
import org.codedefenders.execution.AntRunner;
import org.codedefenders.execution.TargetExecution;
import org.codedefenders.game.AbstractGame;
import org.codedefenders.game.GameClass;
import org.codedefenders.game.Role;
import org.codedefenders.execution.MutationTester;
import org.codedefenders.game.duel.DuelGame;
import org.codedefenders.game.singleplayer.AiPlayer;
import org.codedefenders.game.singleplayer.NoDummyGameException;
import org.codedefenders.game.singleplayer.PrepareAI;
import org.codedefenders.database.DatabaseAccess;
import org.codedefenders.game.Mutant;
import org.codedefenders.model.Event;
import org.codedefenders.model.EventStatus;
import org.codedefenders.model.EventType;
import org.codedefenders.servlets.games.GameManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.codedefenders.util.Constants.MUTANT_COMPILED_MESSAGE;
import static org.codedefenders.util.Constants.MUTANT_UNCOMPILABLE_MESSAGE;
import static org.codedefenders.util.Constants.SESSION_ATTRIBUTE_PREVIOUS_MUTANT;

/**
 * @author Ben Clegg
 * An AI attacker, which chooses mutants generated by Major when the class is uploaded.
 */
public class AiAttacker extends AiPlayer {

	private static final Logger logger = LoggerFactory.getLogger(AiAttacker.class);

	public static final int ID = 1;

	public AiAttacker(AbstractGame g) {
		super(g);
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
	protected boolean runTurn(GenerationMethod strat) {
		try {
			int mNum = selectMutant(strat);
			System.out.println("lolaa");
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

		System.out.println("dummyID" + dummyGame.getId());
		// TODO: dummyGame.aiMutants should return all prepared Mutants for this cut
		// as it is now only mutants for the current game will be returned
		List<Mutant> candidateMutants = dummyGame.getMutantsFromPool().stream().filter(mutant -> !usedMutants.contains(mutant.getId())).collect(Collectors.toList());

        candidateMutants.stream().forEach(mutant -> System.out.println("candidatelist:" + mutant.getId()));
        if(candidateMutants.isEmpty()) {
			System.out.println("candidatemutantlist emptpty leider");
			throw new NoMutantsException("No unused generated mutants remain.");
		}

		switch(strategy) {
			case RANDOM:
				Random r = new Random();
				System.out.println("RANDOMMUTANT" + r.nextInt(candidateMutants.size()));
				Mutant selected = candidateMutants.get(r.nextInt(candidateMutants.size()));
				return selected.getId();
			case KILLCOUNT:
				candidateMutants.sort(new MutantComparator());

				//Get an index, using a random number biased towards earlier index.
				//Note mutants with low killcount are more likely to be equivalent.
				int n = PrepareAI.biasedSelection(candidateMutants.size(), 1.7);
				return candidateMutants.get(n).getId();

			case COVERAGE:
			default:
				// TODO: Why do we have these strategies if we don't use them?
				throw new UnsupportedOperationException("Not implemented");
		}
	}

	private void useMutantFromSuite(int origMutNum) throws NoMutantsException, NoDummyGameException {
		GameClass cut = game.getCUT();
		DuelGame dummyGame = cut.getDummyGame();
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

		/* Mutant newMutant = GameManager.createMutant(activeGame.getId(), activeGame.getClassId(), mutantText, uid, "mp");
		if (newMutant != null) {
			TargetExecution compileMutantTarget = DatabaseAccess.getTargetExecutionForMutant(newMutant, TargetExecution.Target.COMPILE_MUTANT);
			if (compileMutantTarget != null && compileMutantTarget.status.equals("SUCCESS")) {
				Event notif = new Event(-1, activeGame.getId(), uid,
						DatabaseAccess.getUser(uid).getUsername() + " created a mutant.",
						EventType.ATTACKER_MUTANT_CREATED, EventStatus.GAME,
						new Timestamp(System.currentTimeMillis() - 1000));
				notif.insert();
				messages.add(MUTANT_COMPILED_MESSAGE);
				MutationTester.runAllTestsOnMutant(activeGame, newMutant, messages);
				activeGame.update();

				// Clean the mutated code only if mutant is accepted
				session.removeAttribute(SESSION_ATTRIBUTE_PREVIOUS_MUTANT);

			} else {
				messages.add(MUTANT_UNCOMPILABLE_MESSAGE);
				if (compileMutantTarget != null && compileMutantTarget.message != null && !compileMutantTarget.message.isEmpty()) {
					messages.add(compileMutantTarget.message);
				}
				session.setAttribute(SESSION_ATTRIBUTE_PREVIOUS_MUTANT, StringEscapeUtils.escapeHtml(mutantText));
			} */

		String jFile = origM.getSourceFile();
		String cFile = origM.getClassFile();
		int playerId = DatabaseAccess.getPlayerIdForMultiplayerGame(ID, game.getId());
		Mutant m = new Mutant(game.getId(), jFile, cFile, true, playerId);
		m.insert();
		m.update();
		TargetExecution newExec = new TargetExecution(0, m.getId(), TargetExecution.Target.COMPILE_MUTANT, "SUCCESS", null);
		newExec.insert();

		MutationTester.runAllTestsOnMutant(game, m, messages);
		DatabaseAccess.setAiMutantAsUsed(origMutNum, game);
		game.update();

		getMessagesLastTurn();
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