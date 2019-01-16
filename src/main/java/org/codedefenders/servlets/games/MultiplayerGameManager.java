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
package org.codedefenders.servlets.games;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.xerces.util.SynchronizedSymbolTable;
import org.codedefenders.database.DatabaseAccess;
import org.codedefenders.execution.ExecutorPool;
import org.codedefenders.execution.MutationTester;
import org.codedefenders.execution.TargetExecution;
import org.codedefenders.game.GameState;
import org.codedefenders.game.Mutant;
import org.codedefenders.game.Role;
import org.codedefenders.game.Test;
import org.codedefenders.game.duel.DuelGame;
import org.codedefenders.game.multiplayer.MultiplayerGame;
import org.codedefenders.game.singleplayer.CheckAiMoveThread;
import org.codedefenders.game.singleplayer.automated.attacker.AiAttacker;
import org.codedefenders.game.singleplayer.automated.defender.AiDefender;
import org.codedefenders.model.Event;
import org.codedefenders.model.EventStatus;
import org.codedefenders.model.EventType;
import org.codedefenders.servlets.util.Redirect;
import org.codedefenders.util.Constants;
import org.codedefenders.validation.code.CodeValidator;
import org.codedefenders.validation.code.CodeValidatorException;
import org.codedefenders.validation.code.CodeValidatorLevel;
import org.codedefenders.validation.code.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.crypto.Data;

import static org.codedefenders.game.Mutant.Equivalence.ASSUMED_YES;
import static org.codedefenders.game.Mutant.Equivalence.PROVEN_NO;
import static org.codedefenders.util.Constants.GRACE_PERIOD_MESSAGE;
import static org.codedefenders.util.Constants.MUTANT_COMPILED_MESSAGE;
import static org.codedefenders.util.Constants.MUTANT_CREATION_ERROR_MESSAGE;
import static org.codedefenders.util.Constants.MUTANT_DUPLICATED_MESSAGE;
import static org.codedefenders.util.Constants.MUTANT_UNCOMPILABLE_MESSAGE;
import static org.codedefenders.util.Constants.SESSION_ATTRIBUTE_PREVIOUS_MUTANT;
import static org.codedefenders.util.Constants.SESSION_ATTRIBUTE_PREVIOUS_TEST;
import static org.codedefenders.util.Constants.TEST_DID_NOT_COMPILE_MESSAGE;
import static org.codedefenders.util.Constants.TEST_DID_NOT_KILL_CLAIMED_MUTANT_MESSAGE;
import static org.codedefenders.util.Constants.TEST_DID_NOT_PASS_ON_CUT_MESSAGE;
import static org.codedefenders.util.Constants.TEST_GENERIC_ERROR_MESSAGE;
import static org.codedefenders.util.Constants.TEST_INVALID_MESSAGE;
import static org.codedefenders.util.Constants.TEST_KILLED_CLAIMED_MUTANT_MESSAGE;
import static org.codedefenders.util.Constants.TEST_PASSED_ON_CUT_MESSAGE;

public class MultiplayerGameManager extends HttpServlet {

	private static final Logger logger = LoggerFactory.getLogger(MultiplayerGameManager.class);

	@SuppressWarnings("Duplicates")
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
			// TODO Not sure this is 100% right
			logger.error("Problem setting gameID !");
			response.setStatus(500);
			Redirect.redirectBack(request, response);
			return;
		}
		final String contextPath = request.getContextPath();
		final MultiplayerGame activeGame = DatabaseAccess.getMultiplayerGame(gameId);

		if (activeGame == null) {
			logger.error("Could not retrieve game from database for gameId: {}", gameId);
			Redirect.redirectBack(request, response);
			return;
		}

		final String action = request.getParameter("formType");
		switch (action) {
			case "startGame": {
				if (activeGame.getState().equals(GameState.CREATED)) {
					logger.info("Starting multiplayer game {} (Setting state to ACTIVE)", activeGame.getId());
					activeGame.setState(GameState.ACTIVE);
					activeGame.update();
				}
				break;
			}
			case "endGame": {
				if (activeGame.getState().equals(GameState.ACTIVE)) {
					logger.info("Ending multiplayer game {} (Setting state to FINISHED)", activeGame.getId());
					activeGame.setState(GameState.FINISHED);
					activeGame.update();

					response.sendRedirect(contextPath + "/multiplayer/games");
					return;
				}
				break;
			}
			case "reset": {
				session.removeAttribute(Constants.SESSION_ATTRIBUTE_PREVIOUS_MUTANT);
				break;
			}
			case "resolveEquivalence": {
				if (!activeGame.getRole(uid).equals(Role.ATTACKER)) {
					messages.add("Can only resolve equivalence duels if you are an Attacker!");
					break;
				}
				int currentEquivMutantID = Integer.parseInt(request.getParameter("currentEquivMutant"));

				if (activeGame.getState().equals(GameState.FINISHED)) {
					messages.add(String.format("Game %d has finished.", activeGame.getId()));
					response.sendRedirect(contextPath + "/multiplayer/games");
				}

				// Get the text submitted by the user.
				String testText = request.getParameter("test");

				// If it can be written to file and compiled, end turn. Otherwise, dont.

				Test newTest;
				try {
					newTest = GameManager.createTest(activeGame.getId(), activeGame.getClassId(), testText, uid, "mp", activeGame.getMaxAssertionsPerTest());
				} catch (CodeValidatorException cve) {
					messages.add(TEST_GENERIC_ERROR_MESSAGE);
					session.setAttribute(SESSION_ATTRIBUTE_PREVIOUS_TEST, StringEscapeUtils.escapeHtml(testText));
					response.sendRedirect(contextPath+"/multiplayer/play");
					return;
				}

				// If test is null, it compiled but codevalidator triggered
				if (newTest == null) {
					messages.add(String.format(TEST_INVALID_MESSAGE, activeGame.getMaxAssertionsPerTest()));
					session.setAttribute(SESSION_ATTRIBUTE_PREVIOUS_TEST, StringEscapeUtils.escapeHtml(testText));
					response.sendRedirect(contextPath+"/multiplayer/play");
					return;
				}

				logger.info("Executing Action resolveEquivalence for mutant {} and test {}", currentEquivMutantID, newTest.getId());
				TargetExecution compileTestTarget = DatabaseAccess.getTargetExecutionForTest(newTest, TargetExecution.Target.COMPILE_TEST);

				if (compileTestTarget.status.equals("SUCCESS")) {
					TargetExecution testOriginalTarget = DatabaseAccess.getTargetExecutionForTest(newTest, TargetExecution.Target.TEST_ORIGINAL);
					if (testOriginalTarget.status.equals("SUCCESS")) {
						logger.info("Test {} passed on the CUT", newTest.getId());

						// Instead of running equivalence on only one mutant, let's try with all mutants pending resolution
						List<Mutant> mutantsPendingTests = activeGame.getMutantsMarkedEquivalentPending();
						boolean killedClaimed = false;
						int killedOthers = 0;
						for (Mutant mPending : mutantsPendingTests) {
							// TODO: Doesnt distinguish between failing because the test didnt run at all and failing because it detected the mutant
							MutationTester.runEquivalenceTest(newTest, mPending); // updates mPending
							if (mPending.getEquivalent().equals(PROVEN_NO)) {
								logger.info("Test {} killed mutant {} and proved it non-equivalent", newTest.getId(), mPending.getId());
								// TODO Phil 23/09/18: comment below doesn't make sense, literally 0 points added.
								newTest.updateScore(0); // score 2 points for proving a mutant non-equivalent
								final String message = DatabaseAccess.getUser(uid).getUsername() + " killed mutant " + mPending.getId() + " in an equivalence duel.";
								Event notif = new Event(-1, activeGame.getId(), uid, message,
										EventType.ATTACKER_MUTANT_KILLED_EQUIVALENT, EventStatus.GAME,
										new Timestamp(System.currentTimeMillis()));
								notif.insert();
								if (mPending.getId() == currentEquivMutantID)
									killedClaimed = true;
								else
									killedOthers++;
							} else { // ASSUMED_YES
								if (mPending.getId() == currentEquivMutantID) {
									// only kill the one mutant that was claimed
									mPending.kill(ASSUMED_YES);
									final String message = DatabaseAccess.getUser(uid).getUsername() +
											" lost an equivalence duel. Mutant " + mPending.getId() +
											" is assumed equivalent.";
									Event notif = new Event(-1, activeGame.getId(), uid, message,
											EventType.DEFENDER_MUTANT_EQUIVALENT, EventStatus.GAME,
											new Timestamp(System.currentTimeMillis()));
									notif.insert();
								}
								logger.info("Test {} failed to kill mutant {}, hence mutant is assumed equivalent", newTest.getId(), mPending.getId());
							}
						}
						if (killedClaimed) {
							messages.add(TEST_KILLED_CLAIMED_MUTANT_MESSAGE);
							if (killedOthers == 1)
								messages.add("...and it also killed another claimed mutant!");
							else if (killedOthers > 1)
								messages.add(String.format("...and it also killed other %d claimed mutants!", killedOthers));
						} else {
							messages.add(TEST_DID_NOT_KILL_CLAIMED_MUTANT_MESSAGE);
							if (killedOthers == 1)
								messages.add("...however, your test did kill another claimed mutant!");
							else if (killedOthers > 1)
								messages.add(String.format("...however, your test killed other %d claimed mutants!", killedOthers));
						}
						newTest.update();
						activeGame.update();
						response.sendRedirect(contextPath+"/multiplayer/play");
						return;
					} else {
						//  (testOriginalTarget.state.equals("FAIL") || testOriginalTarget.state.equals("ERROR")
						logger.debug("testOriginalTarget: " + testOriginalTarget);
						messages.add(TEST_DID_NOT_PASS_ON_CUT_MESSAGE);
						messages.add(testOriginalTarget.message);
						session.setAttribute(SESSION_ATTRIBUTE_PREVIOUS_TEST, StringEscapeUtils.escapeHtml(testText));
					}
				} else {
					logger.debug("compileTestTarget: " + compileTestTarget);
					messages.add(TEST_DID_NOT_COMPILE_MESSAGE);
					messages.add(compileTestTarget.message);
					session.setAttribute(SESSION_ATTRIBUTE_PREVIOUS_TEST, StringEscapeUtils.escapeHtml(testText));
				}
				break;
			}
			case "createMutant": {
				if (!activeGame.getRole(uid).equals(Role.ATTACKER)) {
					messages.add("Can only submit mutants if you are an Attacker!");
					break;
				}

				if (activeGame.getState().equals(GameState.ACTIVE)) {
					int attackerID = DatabaseAccess.getPlayerIdForMultiplayerGame(uid, activeGame.getId());
					// Get the text submitted by the user.
					String mutantText = request.getParameter("mutant");

					// If the user has pending duels we cannot accept the mutant, but we keep it around
					// so students do not lose mutants once the duel is solved.
					if (GameManager.hasAttackerPendingMutantsInGame(activeGame.getId(), attackerID)
							&& (session.getAttribute(Constants.BLOCK_ATTACKER) != null) && ((Boolean) session.getAttribute(Constants.BLOCK_ATTACKER))) {
						messages.add(Constants.ATTACKER_HAS_PENDING_DUELS);
						// Keep the mutant code in the view for later
						session.setAttribute(SESSION_ATTRIBUTE_PREVIOUS_MUTANT, StringEscapeUtils.escapeHtml(mutantText));
						break;
					}

					CodeValidatorLevel codeValidatorLevel = activeGame.getMutantValidatorLevel();

					ValidationMessage validationMessage = CodeValidator.validateMutantGetMessage(activeGame.getCUT().getAsString(), mutantText, codeValidatorLevel);

					if (validationMessage != ValidationMessage.MUTANT_VALIDATION_SUCCESS) {
						// Mutant is either the same as the CUT or it contains invalid code
						messages.add(validationMessage.get());
						break;
					}
					Mutant existingMutant = GameManager.existingMutant(activeGame.getId(), mutantText);
					if (existingMutant != null) {
						messages.add(MUTANT_DUPLICATED_MESSAGE);
						TargetExecution existingMutantTarget = DatabaseAccess.getTargetExecutionForMutant(existingMutant, TargetExecution.Target.COMPILE_MUTANT);
						if (existingMutantTarget != null
								&& !existingMutantTarget.status.equals("SUCCESS")
								&& existingMutantTarget.message != null && !existingMutantTarget.message.isEmpty()) {
							messages.add(existingMutantTarget.message);
						}
						session.setAttribute(SESSION_ATTRIBUTE_PREVIOUS_MUTANT, StringEscapeUtils.escapeHtml(mutantText));
						break;
					}
					Mutant newMutant = GameManager.createMutant(activeGame.getId(), activeGame.getClassId(), mutantText, uid, "mp");
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
						}
					} else {
						messages.add(MUTANT_CREATION_ERROR_MESSAGE);
						session.setAttribute(SESSION_ATTRIBUTE_PREVIOUS_MUTANT, StringEscapeUtils.escapeHtml(mutantText));
						logger.error("Error creating mutant. Game: {}, Class: {}, User: {}", activeGame.getId(), activeGame.getClassId(), uid, mutantText);
					}
				} else {
					messages.add(GRACE_PERIOD_MESSAGE);
				}
				break;
			}
			case "createTest": {
				if (!activeGame.getRole(uid).equals(Role.DEFENDER)) {
					messages.add("Can only submit tests if you are an Defender!");
					break;
				}
				if (activeGame.getState().equals(GameState.ACTIVE)) {
					// Get the text submitted by the user.
					String testText = request.getParameter("test");

					// If it can be written to file and compiled, end turn. Otherwise, dont.
					Test newTest;
					try {
						newTest = GameManager.createTest(activeGame.getId(), activeGame.getClassId(), testText, uid, "mp", activeGame.getMaxAssertionsPerTest());
					} catch (CodeValidatorException cve) {
						messages.add(TEST_GENERIC_ERROR_MESSAGE);
						session.setAttribute(SESSION_ATTRIBUTE_PREVIOUS_TEST, StringEscapeUtils.escapeHtml(testText));
						response.sendRedirect(contextPath + "/multiplayer/play");
						return;
					}

					// If test is null, then test did compile but codevalidator triggered
					if (newTest == null) {
						messages.add(String.format(TEST_INVALID_MESSAGE, activeGame.getMaxAssertionsPerTest()));
						session.setAttribute(SESSION_ATTRIBUTE_PREVIOUS_TEST, StringEscapeUtils.escapeHtml(testText));
						response.sendRedirect(contextPath + "/multiplayer/play");
						return;
					}

					logger.info("New Test {} by user {}", newTest.getId(), uid);
					TargetExecution compileTestTarget = DatabaseAccess.getTargetExecutionForTest(newTest, TargetExecution.Target.COMPILE_TEST);

					if (compileTestTarget.status.equals("SUCCESS")) {
						TargetExecution testOriginalTarget = DatabaseAccess.getTargetExecutionForTest(newTest, TargetExecution.Target.TEST_ORIGINAL);
						if (testOriginalTarget.status.equals("SUCCESS")) {
							messages.add(TEST_PASSED_ON_CUT_MESSAGE);

							Event notif = new Event(-1, activeGame.getId(), uid,
									DatabaseAccess.getUser(uid).getUsername() + " created a test",
									EventType.DEFENDER_TEST_CREATED, EventStatus.GAME,
									new Timestamp(System.currentTimeMillis()));
							notif.insert();

							MutationTester.runTestOnAllMultiplayerMutants(activeGame, newTest, messages);
							activeGame.update();
						} else {
							// testOriginalTarget.state.equals("FAIL") || testOriginalTarget.state.equals("ERROR")
							messages.add(TEST_DID_NOT_PASS_ON_CUT_MESSAGE);
							// TODO This might not prevent injection of malicious code!
							messages.add(StringEscapeUtils.escapeHtml(testOriginalTarget.message));
							session.setAttribute(SESSION_ATTRIBUTE_PREVIOUS_TEST, StringEscapeUtils.escapeHtml(testText));
						}
					} else {
						messages.add(TEST_DID_NOT_COMPILE_MESSAGE);
						messages.add(StringEscapeUtils.escapeHtml(compileTestTarget.message));
						session.setAttribute(SESSION_ATTRIBUTE_PREVIOUS_TEST, StringEscapeUtils.escapeHtml(testText));
					}
				} else {
					messages.add(GRACE_PERIOD_MESSAGE);
				}
				break;
			}

            case "pauseAttacker": {
                if (DatabaseAccess.getJoinedMultiplayerGamesForUser(AiAttacker.ID).stream()
                        .filter(joinedGames -> joinedGames.getId() == activeGame.getId())
                        .findFirst().isPresent()) {
                    int aiAttackerPlayerId = IntStream.of(activeGame.getAttackerIds())
                            .filter(id -> DatabaseAccess.getUserFromPlayer(id).getId() == AiAttacker.ID).findFirst().getAsInt();
                    ExecutorPool.getInstanceOf().cancelTask(aiAttackerPlayerId, false);
                } else {
                    logger.info("No Ai-Attacker in this game");
                }
                break;
            }

			case "pauseDefender": {
				if (DatabaseAccess.getJoinedMultiplayerGamesForUser(AiDefender.ID).stream()
						.filter(joinedGames -> joinedGames.getId() == activeGame.getId())
						.findFirst().isPresent()) {
					int aiDefenderPlayerId = IntStream.of(activeGame.getDefenderIds())
							.filter(id -> DatabaseAccess.getUserFromPlayer(id).getId() == AiDefender.ID).findFirst().getAsInt();
					ExecutorPool.getInstanceOf().cancelTask(aiDefenderPlayerId, false);
				} else {
					logger.info("No Ai-Defender in this game");
				}
				break;
			}

			case "addDefender": {
				boolean aiDefenderJoinedGame = DatabaseAccess.getJoinedMultiplayerGamesForUser(AiDefender.ID).stream()
						.filter(joinedGames -> joinedGames.getId() == activeGame.getId())
						.findFirst().isPresent();
				int aiDefenderPlayerId = 0;
				if (aiDefenderJoinedGame) {
					aiDefenderPlayerId = IntStream.of(DatabaseAccess.getInactiveAndActivePlayersForMultiplayerGame(activeGame.getId(), Role.DEFENDER))
							.filter(id -> DatabaseAccess.getUserFromPlayer(id).getId() == AiDefender.ID).findFirst().getAsInt();
				}
				if (!aiDefenderJoinedGame || aiDefenderPlayerId != 0) {
					boolean joinedGame;
					if (aiDefenderPlayerId == 0) {
						joinedGame = activeGame.addPlayer(AiDefender.ID, Role.DEFENDER);
						aiDefenderPlayerId = IntStream.of(activeGame.getDefenderIds())
								.filter(id -> DatabaseAccess.getUserFromPlayer(id).getId() == AiDefender.ID).findFirst().getAsInt();
					} else {
						DatabaseAccess.setPlayerIsActive(aiDefenderPlayerId, true);
						joinedGame = true;
					}
					if (joinedGame) {
						// add: ExecutorPool could not have enough resources and throw exception no more threads
						try {
							ExecutorPool.getInstanceOf().scheduleTask(aiDefenderPlayerId, activeGame, Role.DEFENDER);
							messages.add("AI-Defender scheduled to game " + activeGame.getId());
						} catch (Exception e) {
							logger.error("No Executor for Defender available", e);
							// bad exception handling here. dont throw this here!?
							// throw new CouldNotAddAiPlayerException("No free Threads.");
						}
					} else {
						logger.info("Only one AI-Player per Team allowed");
					}
				} else {
					messages.add("AI-Defender was not added, something went wrong");
				}
				/* try {
					DuelGame jGame = DatabaseAccess.getGameForKey("ID", gameId);

					if ((jGame.getAttackerId() == uid) || (jGame.getDefenderId() == uid)) {
						// uid is already in the game
						if (jGame.getDefenderId() == uid)
							messages.add("Already a defender in this game!");
						else
							messages.add("Already an attacker in this game!");
						// either way, reload list of open games
						Redirect.redirectBack(request, response);
						break;
					} else {
						if (jGame.getAttackerId() == 0) {
							jGame.addPlayer(uid, Role.ATTACKER);
							messages.add("Joined game as an attacker.");
						} else if (jGame.getDefenderId() == 0) {
							messages.add("Joined game as a defender.");
							jGame.addPlayer(uid, Role.DEFENDER);
						} else {
							messages.add("DuelGame is no longer open.");
							Redirect.redirectBack(request, response);
							break;
						}
						// user joined, update game
						jGame.setState(GameState.ACTIVE);
						jGame.setActiveRole(Role.ATTACKER);
						jGame.update();
						// go to play view
						session.setAttribute("gid", gameId);
						response.sendRedirect(contextPath+"/"+jGame.getClass().getSimpleName().toLowerCase());
					}
				} catch (Exception e) {
					messages.add("There was a problem joining the game.");
					Redirect.redirectBack(request, response);
				} */

				break;
			}

			case "addAttacker": {
				boolean aiAttackerJoinedGame = DatabaseAccess.getJoinedMultiplayerGamesForUser(AiAttacker.ID).stream()
						.filter(joinedGames -> joinedGames.getId() == activeGame.getId())
						.findFirst().isPresent();

				System.out.println("AiAttackerjoined: "+ aiAttackerJoinedGame);
				int aiAttackerPlayerId = 0;
				System.out.println("AiAttackerplayerid 0" + aiAttackerPlayerId);
				if (aiAttackerJoinedGame) {
					aiAttackerPlayerId = IntStream.of(DatabaseAccess.getInactiveAndActivePlayersForMultiplayerGame(activeGame.getId(), Role.ATTACKER))
							.filter(id -> DatabaseAccess.getUserFromPlayer(id).getId() == AiAttacker.ID).findFirst().getAsInt();
				}
				System.out.println("AiAttackerplayerid 1" + aiAttackerPlayerId);
				if (!aiAttackerJoinedGame || aiAttackerPlayerId != 0) {
					boolean joinedGame;
					if (aiAttackerPlayerId == 0) {
						joinedGame = activeGame.addPlayer(AiAttacker.ID, Role.ATTACKER);
						aiAttackerPlayerId = IntStream.of(activeGame.getAttackerIds())
								.filter(id -> DatabaseAccess.getUserFromPlayer(id).getId() == AiAttacker.ID).findFirst().getAsInt();
					} else {
						DatabaseAccess.setPlayerIsActive(aiAttackerPlayerId, true);
						joinedGame = true;
					}
					if (joinedGame) {
						// add: ExecutorPool could not have enough resources and throw exception no more threads
						try {
							ExecutorPool.getInstanceOf().scheduleTask(aiAttackerPlayerId, activeGame, Role.ATTACKER);
							messages.add("AI-Attacker scheduled to game " + activeGame.getId());
						} catch (Exception e) {
							logger.error("No Executor for Attacker available", e);
							// bad exception handling here. dont throw this here!?
							// throw new CouldNotAddAiPlayerException("No free Threads.");
						}
					} else {
						logger.info("Only one AI-Player per Team allowed");
					}
				} else {
					messages.add("AI-Attacker was not added, something went wrong");
				}
				break;
			}
			default:
				logger.info("Action not recognised: {}", action);
				Redirect.redirectBack(request, response);
				break;
		}
		response.sendRedirect(contextPath + "/multiplayer/play");
	}

	/**
	 * Exception to be thrown by <code>ConnectionPool</code> and caught in calling
	 * objects if it has no more available connections upon request.
	 *
	 * @author wendling
	 */
	public class CouldNotAddAiPlayerException extends Exception {
		CouldNotAddAiPlayerException() {
		}

		public CouldNotAddAiPlayerException(String message) {
			super(message);
		}
	}
}
