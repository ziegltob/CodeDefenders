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
package org.codedefenders.game.singleplayer;

import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.codedefenders.game.AbstractGame;
import org.codedefenders.game.GameMode;
import org.codedefenders.game.duel.DuelGame;
import org.codedefenders.game.Role;
import org.codedefenders.game.multiplayer.MultiplayerGame;

import java.util.ArrayList;

/**
 * @author Ben Clegg
 * Base class for AI players.
 */
public abstract class AiPlayer {

	public enum GenerationMethod {
		RANDOM, //Randomly select mutant.
		COVERAGE, //Select random mutant by least covered lines.
		KILLCOUNT //Number of mutants a test kills, or number of tests that kill mutant.
	}

	// one variable game which will be assigned to a specific type
	protected AbstractGame game;
	protected MultiplayerGame multiplayerGame;
	protected SinglePlayerGame singlePlayerGame;
	protected Role role;
	protected ArrayList<String> messages;

	public AiPlayer(AbstractGame g) {
		/*if (g.getMode() == GameMode.DUEL) {
			game = (DuelGame) g;
		} else if (g.getMode() == GameMode.PARTY) {
			multiplayerGame = (MultiplayerGame) g;
		} else if (g.getMode() == GameMode.SINGLE) {
			singlePlayerGame = (SinglePlayerGame) g;
		} */
		game = g;

		messages = new ArrayList<String>();
	}

	/**
	 * Make the AI's turn if it is its turn.
	 * @return true if turn successfully made, false otherwise - can be used to show notifications
	 */
	public boolean makeTurn() {
		boolean success = false;
		DuelGame duelGame = (DuelGame) game;
		messages.clear();
		if (duelGame.getActiveRole().equals(role)) {
			if (tryTurn()) { success = true; }
			duelGame.endTurn();
		}
		return success;
	}

	public boolean tryTurn() {
		switch (game.getLevel()) {
			case EASY: return turnEasy();
			case HARD: return turnHard();
			default: return turnHard();
		}
	}

	public abstract boolean turnEasy();

	public abstract boolean turnHard();

	protected abstract boolean runTurn(GenerationMethod strat);

	public abstract ArrayList<String> getMessagesLastTurn();
}
