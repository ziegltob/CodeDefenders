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
package org.codedefenders.execution;

import org.apache.commons.collections.CollectionUtils;
import org.codedefenders.database.TargetExecutionDAO;
import org.codedefenders.database.UserDAO;
import org.codedefenders.game.AbstractGame;
import org.codedefenders.game.Mutant;
import org.codedefenders.game.Test;
import org.codedefenders.game.multiplayer.MultiplayerGame;
import org.codedefenders.game.scoring.Scorer;
import org.codedefenders.model.Event;
import org.codedefenders.model.EventStatus;
import org.codedefenders.model.EventType;
import org.codedefenders.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import static org.codedefenders.execution.TargetExecution.Status.ERROR;
import static org.codedefenders.execution.TargetExecution.Status.FAIL;
import static org.codedefenders.game.Mutant.Equivalence.ASSUMED_NO;
import static org.codedefenders.game.Mutant.Equivalence.PROVEN_NO;
import static org.codedefenders.util.Constants.MUTANT_ALIVE_1_MESSAGE;
import static org.codedefenders.util.Constants.MUTANT_ALIVE_N_MESSAGE;
import static org.codedefenders.util.Constants.MUTANT_KILLED_BY_TEST_MESSAGE;
import static org.codedefenders.util.Constants.MUTANT_SUBMITTED_MESSAGE;
import static org.codedefenders.util.Constants.TEST_KILLED_LAST_MESSAGE;
import static org.codedefenders.util.Constants.TEST_KILLED_N_MESSAGE;
import static org.codedefenders.util.Constants.TEST_KILLED_ONE_MESSAGE;
import static org.codedefenders.util.Constants.TEST_KILLED_ZERO_MESSAGE;
import static org.codedefenders.util.Constants.TEST_SUBMITTED_MESSAGE;

// Class that handles compilation and testing by creating a Process with the relevant ant target
public class MutationTester {
	private static final Logger logger = LoggerFactory.getLogger(MutationTester.class);

	private static boolean parallelize = false;

	private static boolean useMutantCoverage = true;
	// Use a shared executor pool, prevents thread explosion.
	private static ExecutorService sharedExecutorService = Executors.newFixedThreadPool(30);

	// DO NOT REALLY LIKE THOSE...
	static {
		// First check the Web abb context
		InitialContext initialContext;
		try {
			initialContext = new InitialContext();
			NamingEnumeration<NameClassPair> list = initialContext.list("java:/comp/env");
			Context environmentContext = (Context) initialContext.lookup("java:/comp/env");

			// Looking up a name which is not there causes an exception
			// Some are unsafe !
			while (list.hasMore()) {
				String name = list.next().getName();
				switch (name) {
				case "mutant.coverage":
					useMutantCoverage = "enabled".equalsIgnoreCase((String) environmentContext.lookup(name));
					break;
				case "parallelize":
					parallelize = "enabled".equalsIgnoreCase((String) environmentContext.lookup(name));
					break;
				}
			}

		} catch (NamingException e) {
			logger.error("Failed to Java environment variables.", e);
		}
	}

	// RUN MUTATION TESTS: Runs all the mutation tests for a particular game,
	// using all the alive mutants and all tests

	// Inputs: The ID of the game to run mutation tests for
	// Outputs: None

	public static void runTestOnAllMutants(AbstractGame game, Test test, ArrayList<String> messages) {
		int killed = 0;
		List<Mutant> mutants = game.getAliveMutants();
		for (Mutant mutant : mutants) {
			killed += testVsMutant(test, mutant) ? 1 : 0;
		}
		if (killed == 0)
			if (mutants.size() == 0)
				messages.add(TEST_SUBMITTED_MESSAGE);
			else
				messages.add(TEST_KILLED_ZERO_MESSAGE);
		else {
			if (killed == 1) {
				if (mutants.size() == 1)
					messages.add(TEST_KILLED_LAST_MESSAGE);
				else
					messages.add(TEST_KILLED_ONE_MESSAGE);
			} else {
				messages.add(String.format(TEST_KILLED_N_MESSAGE, killed));
			}

		}
	}

	public static void runTestOnAllMultiplayerMutants(MultiplayerGame game, Test test, ArrayList<String> messages) {
		int killed = 0;
		List<Mutant> mutants = game.getAliveMutants();
		mutants.addAll(game.getMutantsMarkedEquivalentPending());
		List<Mutant> killedMutants = new ArrayList<Mutant>();

		// Acquire and release the connection
		User u = UserDAO.getUserForPlayer(test.getPlayerId());

		if (parallelize) {
			// Fork and Join parallelization
			Map<Mutant, FutureTask<Boolean>> tasks = new HashMap<Mutant, FutureTask<Boolean>>();
			for (final Mutant mutant : mutants) {
				if (useMutantCoverage && !test.isMutantCovered(mutant)) {
					// System.out.println("Skipping non-covered mutant "
					// + mutant.getId() + ", test " + test.getId());
					continue;
				}

				FutureTask<Boolean> task = new FutureTask<Boolean>(new Callable<Boolean>() {

					@Override
					public Boolean call() throws Exception {
						// This automatically update the 'mutants' and 'tests'
						// tables, as well as the test and mutant objects.
						return testVsMutant(test, mutant);
					}
				});

				// This is for checking later
				tasks.put(mutant, task);

				sharedExecutorService.execute(task);
			}

			// TODO Mayse use some timeout ?!
			for (final Mutant mutant : mutants) {
				if (useMutantCoverage && !test.isMutantCovered(mutant))
					continue;

				// checks if task done
				// System.out.println(
				// "Is mutant done? " + tasks.get(mutant).isDone());
				// checks if task canceled
				// System.out.println("Is mutant cancelled? "
				// + tasks.get(mutant).isCancelled());
				// fetches result and waits if not ready

				// THIS IS BLOCKING !!!
				try {
					if (tasks.get(mutant).get()) {
						killed++;
						killedMutants.add(mutant);
					}
				} catch (InterruptedException | ExecutionException | CancellationException e) {
                    logger.error("While waiting results for mutant " + mutant, e);
                }
			}

			tasks.clear();

		} else {
			// Normal execution
			for (Mutant mutant : mutants) {
				if (useMutantCoverage && !test.isMutantCovered(mutant)) {
					// System.out.println("Skipping non-covered mutant "
					// + mutant.getId() + ", test " + test.getId());
					continue;
				}

				if (testVsMutant(test, mutant)) {
					killed++;
					killedMutants.add(mutant);
				}
			}
		}

		for (Mutant mutant : mutants){
			if (mutant.isAlive()){
				ArrayList<Test> missedTests = new ArrayList<Test>();

				for (int lm : mutant.getLines()){
					boolean found = false;
					for (int lc : test.getLineCoverage().getLinesCovered()){
						if (lc == lm){
							found = true;
							missedTests.add(test);
						}
					}
					if (found){
						break;
					}
				}
				// mutant.setScore(Scorer.score(game, mutant, missedTests));
				// mutant.update();
				mutant.incrementScore(Scorer.score(game, mutant, missedTests));
			}
		}

		// test.setScore(Scorer.score(game, test, killedMutants));
		// test.update();
		test.incrementScore(Scorer.score(game, test, killedMutants));

		if (killed == 0)
			if (mutants.size() == 0)
				messages.add(TEST_SUBMITTED_MESSAGE);
			else
				messages.add(TEST_KILLED_ZERO_MESSAGE);
		else {
			Event notif = new Event(-1, game.getId(), u.getId(),
					u.getUsername() + "&#39;s test kills " + killed + " " + "mutants.",
					EventType.DEFENDER_KILLED_MUTANT, EventStatus.GAME, new Timestamp(System.currentTimeMillis()));
			notif.insert();
			if (killed == 1) {
				if (mutants.size() == 1)
					messages.add(TEST_KILLED_LAST_MESSAGE);
				else
					messages.add(TEST_KILLED_ONE_MESSAGE);
			} else {
				messages.add(String.format(TEST_KILLED_N_MESSAGE, killed));
			}

		}
	}

	/**
	 * Execute all the tests registered for the defenders against the provided
	 * mutant, using a random scheduling of test execution.
	 *
	 * @param game
	 * @param mutant
	 * @param messages
	 */
	public static void runAllTestsOnMutant(AbstractGame game, Mutant mutant, ArrayList<String> messages) {
		runAllTestsOnMutant(game, mutant, messages, new RandomTestScheduler());
	}

	/**
	 * Execute all the tests registered for the defenders against the provided
	 * mutant, using a the given TestScheduler for ordering the execution of
	 * tests.
	 *
	 * @param game
	 * @param mutant
	 * @param messages
	 * @param scheduler
	 */
	public static void runAllTestsOnMutant(AbstractGame game, Mutant mutant, ArrayList<String> messages,
			TestScheduler scheduler) {
		// Schedule the executable tests submitted by the defenders only (true)
		List<Test> tests = scheduler.scheduleTests( game.getTests(true) ) ;

		User u = UserDAO.getUserForPlayer(mutant.getPlayerId());

		if (parallelize) {
			final Map<Test, FutureTask<Boolean>> tasks = new HashMap<Test, FutureTask<Boolean>>();
			for (Test test : tests) {
				if (useMutantCoverage && !test.isMutantCovered(mutant)) {
					logger.info("Skipping non-covered mutant " + mutant.getId() + ", test " + test.getId());
					continue;
				}

				FutureTask<Boolean> task = new FutureTask<Boolean>(new Callable<Boolean>() {

					@Override
					public Boolean call() throws Exception {
						logger.info("Executing mutant " + mutant.getId() + ", test " + test.getId());
						// TODO Is this testVsMutant thread safe?
						return testVsMutant(test, mutant);
                    }
                });

                // Book keeping
                tasks.put(test, task);
            }

            // Submit all the tests in the given order
            for (Test test : tests ) {
                if (tasks.containsKey(test)) {
                	logger.debug("MutationTester.runAllTestsOnMutant() : Scheduling Task " + test);
                    sharedExecutorService.execute(tasks.get(test));
                }
            }

            // Wait for the result. Check by the order defined by the scheduler.
            for (Test test : tests ) {
                // Why this happens ?
                if (! tasks.containsKey(test)) {
                    logger.debug("Tasks does not contain " + test.getId() );
                    continue;
                }

                Future<Boolean> task = tasks.get(test);
                logger.debug("MutationTester.runAllTestsOnMutant() Checking task " + task + ". Done: "
                        + task.isDone() + ". Cancelled: " + task.isCancelled());
                try {

                    boolean hasTestkilledTheMutant = false;

                    try {
                        hasTestkilledTheMutant = task.get();
                    } catch (CancellationException ce) {
                        //
                        logger.warn("Swallowing ", ce);
                    }

                    if (hasTestkilledTheMutant) {
                        // This test killede the mutant...
                        messages.add(String.format(MUTANT_KILLED_BY_TEST_MESSAGE, test.getId()));

                        if (game instanceof MultiplayerGame) {
                            ArrayList<Mutant> mlist = new ArrayList<Mutant>();
                            mlist.add(mutant);

							logger.info(">> Test {} kills mutant {} get {} points. Mutant is still alive ? {}",
									test.getId(), mutant.getId(), Scorer.score((MultiplayerGame) game, test, mlist),
									mutant.isAlive());
							test.incrementScore(Scorer.score((MultiplayerGame) game, test, mlist));
						}

                        Event notif = new Event(-1, game.getId(),
                                UserDAO.getUserForPlayer(test.getPlayerId()).getId(),
                                u.getUsername() + "&#39;s mutant is killed", EventType.DEFENDER_KILLED_MUTANT,
                                EventStatus.GAME, new Timestamp(System.currentTimeMillis()));
                        notif.insert();

                        // Early return. No need to check for the other executions.
                        return;

                    }
                } catch (InterruptedException | ExecutionException e) {
                    System.out.println(
                            "MutationTester.runAllTestsOnMutant() ERROR While waiting results for task " + e.getMessage() );
//                    e.printStackTrace();
                }
            }

        } else {

            for (Test test : tests) {
                if (useMutantCoverage && !test.isMutantCovered(mutant)) {
                    logger.info("Skipping non-covered mutant " + mutant.getId() + ", test " + test.getId());
                    continue;
                }

                if (testVsMutant(test, mutant)) {
                    logger.info("Test {} kills mutant {}", test.getId(), mutant.getId());
                    messages.add(String.format(MUTANT_KILLED_BY_TEST_MESSAGE, test.getId()));
                    if (game instanceof MultiplayerGame) {
                        ArrayList<Mutant> mlist = new ArrayList<Mutant>();
                        mlist.add(mutant);
						// test.setScore(Scorer.score((MultiplayerGame) game, test, mlist));
						// test.update();
						test.incrementScore(Scorer.score((MultiplayerGame) game, test, mlist));
					}

                    Event notif = new Event(-1, game.getId(),
                            UserDAO.getUserForPlayer(test.getPlayerId()).getId(),
                            u.getUsername() + "&#39;s mutant is killed", EventType.DEFENDER_KILLED_MUTANT,
                            EventStatus.GAME, new Timestamp(System.currentTimeMillis()));
                    notif.insert();

                    return; // return as soon as the first test kills the mutant we return
                }
            }
        }

		// TODO In the original implementation (see commit 4fbdc78304374ee31a06d56f8ce67ca80309e24c for example)
		// the first block and the second one are swapped. Why ?
        ArrayList<Test> missedTests = new ArrayList<Test>();
        if (game instanceof MultiplayerGame) {
            for (Test t : tests) {
                if (CollectionUtils.containsAny(t.getLineCoverage().getLinesCovered(), mutant.getLines()))
                    missedTests.add(t);
            }
//            mutant.setScore(1 + Scorer.score((MultiplayerGame) game, mutant, missedTests));
//            mutant.update();
            mutant.incrementScore(1 + Scorer.score((MultiplayerGame) game, mutant, missedTests));
        }

        int nbRelevantTests = missedTests.size();
        // Mutant survived
        if (nbRelevantTests == 0)
            messages.add(MUTANT_SUBMITTED_MESSAGE);
        else if (nbRelevantTests <= 1)
            messages.add(MUTANT_ALIVE_1_MESSAGE);
        else
            messages.add(String.format(MUTANT_ALIVE_N_MESSAGE, nbRelevantTests));
        Event notif = new Event(-1, game.getId(), u.getId(), u.getUsername() + "&#39;s mutant survives the test suite.",
                EventType.ATTACKER_MUTANT_SURVIVED, EventStatus.GAME, new Timestamp(System.currentTimeMillis()));
        notif.insert();
    }

	/**
	 * This method is used for the AiAttacker to determine if a mutant survives all the tests.
     * The AiAttacker can try out several mutants without scoring and then take the best one.
     *
	 * @param game
	 * @param mutant
	 * @return returns true when the mutant is killed and false when it survives
	 */
	public static boolean runAllTestsOnMutantWithoutScoring(MultiplayerGame game, Mutant mutant) {
		List<Test> tests = game.getTests(true); // executable tests submitted by defenders

        boolean killed = false;
		for (Test test : tests) {
			if (useMutantCoverage && !test.isMutantCovered(mutant)) {
				logger.info("Skipping non-covered mutant " + mutant.getId() + ", test " + test.getId());
				continue;
			}
			killed = testOnMutantWithoutKilling(game, test, mutant);
			if (killed) {
				logger.info("Test {} kills mutant {}", test.getId(), mutant.getId());
				return true; // return as soon as a test kills the mutant
			}
		}
		return killed;
	}

	public static boolean testOnMutantWithoutKilling(MultiplayerGame game, Test test, Mutant mutant) {
		TargetExecution execution = TargetExecutionDAO.getTargetExecutionForPair(test.getId(), mutant.getId());
		if (execution == null) {
			// Run the test against the mutant and get the result
			execution = AntRunner.testMutant(mutant, test);
		} else {
			// this is for the ai trying out multiple tests on a mutant to check if they kill the mutant
			logger.info("There is already an execution result for (m: {},t: {})", mutant.getId(), test.getId());
		}
		if (execution.status.equals("FAIL") || execution.status.equals("ERROR")) {
			if (mutant.isAlive()) {
				logger.info("Test {} kills Mutant {}", test.getId(), mutant.getId());
				return true;
			} else {
				logger.info("Test {} would have killed Mutant {}, but Mutant {} was alredy dead!", test.getId(), mutant.getId(), mutant.getId());
				// this was false before because the mutant was already dead but it should return true since he is dead anyways
                return true;
			}
		} else {
			logger.debug("Test {} did not kill Mutant {}", test.getId(), mutant.getId());
			return false;
		}
	}

	/**
	 * Runs a test against a mutant.
	 *
	 * @param test
	 * @param mutant
	 * @return {@code true} if the test killed the mutant, {@code false} otherwise
	 */
	private static boolean testVsMutant(Test test, Mutant mutant) {
		if (TargetExecutionDAO.getTargetExecutionForPair(test.getId(), mutant.getId()) != null) {
			logger.error("Execution result found for Mutant {} and Test {}.", mutant.getId(), test.getId());
			return false;
		}
		final TargetExecution executedTarget = AntRunner.testMutant(mutant, test);

		// If the test did NOT pass, the mutant was detected and should be killed.
		if (!executedTarget.status.equals("FAIL") && !executedTarget.status.equals("ERROR")) {
			logger.debug("Test {} did not kill Mutant {}", test.getId(), mutant.getId());
			return false;
		}
		if (!mutant.kill(ASSUMED_NO)) {
			logger.info("Test {} would have killed Mutant {}, but Mutant {} was already dead!", test.getId(), mutant.getId(), mutant.getId());
			return false;
		}

		logger.info("Test {} killed Mutant {}", test.getId(), mutant.getId());
		test.killMutant();
		return true;
	}

	private static boolean didTestKillMutant(TargetExecution executedTarget, Mutant mutant, Test test) {
		// If the test did NOT pass, the mutant was detected and should be killed.
		if (executedTarget.status.equals("FAIL") || executedTarget.status.equals("ERROR")) {
			if (mutant.kill(ASSUMED_NO)) {
				logger.info("Test {} kills Mutant {}", test.getId(), mutant.getId());
				test.killMutant();
				return true;
			} else {
				logger.info("Test {} would have killed Mutant {}, but Mutant {} was alredy dead!", test.getId(), mutant.getId(), mutant.getId());
				return false;
			}
		} else {
			logger.debug("Test {} did not kill Mutant {}", test.getId(), mutant.getId());
			return false;
		}
	}

    /**
     * Runs an equivalence test using an attacker supplied test and a mutant thought to be equivalent.
     * Kills mutant either with ASSUMED_YES if test passes on the mutant or with PROVEN_NO otherwise
     *
     * @param test   attacker-created test
     * @param mutant a mutant
     */
    public static void runEquivalenceTest(Test test, Mutant mutant) {
        logger.info("Running equivalence test for test {} and mutant {}.", test.getId(), mutant.getId());
        // The test created is new and was made by the attacker (there is no
        // need to check if the mutant/test pairing has been run already)

        // As a result of this test, either the test the attacker has written
        // kills the mutant or doesnt.
        TargetExecution executedTarget = AntRunner.testMutant(mutant, test);

        // Kill the mutant if it was killed by the test or if it's marked
        // equivalent
        if (executedTarget.status.equals(ERROR) || executedTarget.status.equals(FAIL)) {
            // If the test did NOT pass, the mutant was detected and is proven
            // to be non-equivalent
        	if (mutant.kill(PROVEN_NO)) {
        		logger.info("Test {} kills mutant {} and resolve equivalence.", test.getId(), mutant.getId());
        		test.killMutant();
        	} else {
				logger.info("Test {} would have killed Mutant {} and resolve equivalence, but Mutant {} was alredy dead. No need to resolve equivalence.!", test.getId(), mutant.getId(), mutant.getId());
			}
        } else {
            // If the test DID pass, the mutant went undetected and it is
            // assumed to be equivalent.
            // Avoid killing, let player accept as equivalent instead
            // mutant.kill(ASSUMED_YES);
        }
    }

}