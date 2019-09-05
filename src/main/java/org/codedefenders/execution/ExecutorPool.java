package org.codedefenders.execution;

import org.codedefenders.database.DatabaseAccess;
import org.codedefenders.game.Role;
import org.codedefenders.game.multiplayer.MultiplayerGame;
import org.codedefenders.game.singleplayer.CheckAiMoveThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public final class ExecutorPool {

    private static ExecutorPool executorPool;
    private static ScheduledThreadPoolExecutor executor;
    private Map<Integer, ScheduledFuture> scheduledTasks;
    private Logger logger = LoggerFactory.getLogger(ExecutorPool.class.getName());

    // make this a changable constant
    private static int MAX_THREADS = 10;


    private ExecutorPool() {
        init();
    }

    /**
     * Initializes the task list and sets removeCancelPolicy to true so tasks will be removed
     * from the Threadpool when they get canceled.
     */
    private void init() {
        executor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(MAX_THREADS);
        executor.setRemoveOnCancelPolicy(true);
        scheduledTasks = new HashMap<>();

        logger.info("ExecutorPool initialized with " + MAX_THREADS + "threads.");
    }

    /**
     * Returns an the executor.
     *
     * @return a ScheduledThreadPoolExecutor.
     * @throws NoMoreThreadsException if there are no free threads
     */
    public synchronized ScheduledThreadPoolExecutor getScheduledExecutor() throws NoMoreThreadsException {
        if (executor.getPoolSize() < executor.getMaximumPoolSize()) {
            return executor;
        } else {
            throw new NoMoreThreadsException();
        }
    }

    /**
     * Shuts down the executor.
     */
    public void shutdownExecutor() {
        logger.info("Shutdown of ExecutorPool threads...");
        // should we shutdown each thread on its own first?
        executor.shutdownNow();

        logger.info("Shutdown of ExecutorPool successfully.");
        executorPool = null;
    }

    /**
     * Adds an Ai-Player to a game. Executes the CheckAiMoveThread every minute, which
     * executes moves for the Ai-Player.
     * @param playerId The playerId is needed to identify the task in the ThreadPool
     * @param game
     * @param role
     */
    public synchronized void scheduleTask(int playerId, MultiplayerGame game, Role role) {
        scheduledTasks.put(playerId, executor.scheduleAtFixedRate(new CheckAiMoveThread(game, role), 0, 1, TimeUnit.MINUTES));
    }

    /**
     * Pauses an Ai-Player that is already in a game. We can't remove him completely from a game.
     * @param playerId
     */
    public synchronized void cancelTask(int playerId, boolean isActive) {
        System.out.println("cancelTask:" + playerId);
        System.out.println("cancelTaskkeycontained:" + scheduledTasks.containsKey(playerId));
        if (scheduledTasks.containsKey(playerId)) {
            if (scheduledTasks.get(playerId).cancel(false)) {
                scheduledTasks.remove(playerId);
                System.out.println("setplayeractive db"+ playerId);
                DatabaseAccess.setPlayerIsActive(playerId, isActive);
            }
        }
    }

    /**
     * Adds the Ai-Players in active games on startup.
     */
    public synchronized void addAiPlayersOnStartup() {
        Map<Integer, MultiplayerGame> gamesWithActiveAiAttackers = DatabaseAccess.getActiveGamesWithActiveAiAttacker();
        Map<Integer, MultiplayerGame> gamesWithActiveAiDefenders = DatabaseAccess.getActiveGamesWithActiveAiDefender();
        for (Map.Entry<Integer, MultiplayerGame> entry : gamesWithActiveAiAttackers.entrySet()) {
            executorPool.scheduleTask(entry.getKey(), entry.getValue(), Role.ATTACKER);
        }
        for (Map.Entry<Integer, MultiplayerGame> entry  : gamesWithActiveAiDefenders.entrySet()) {
            executorPool.scheduleTask(entry.getKey(), entry.getValue(), Role.DEFENDER);
        }
    }

    /**
     * Returns the singleton object.
     *
     * @return single ExecutorPool object.
     */
    public static synchronized ExecutorPool getInstanceOf() {
        if (executorPool == null) {
            executorPool = new ExecutorPool();
        }
        return executorPool;
    }

    /**
     * Exception to be thrown by ExecutorPool and caught in calling
     * objects if it has no more available threads upon request.
     *
     */
    public class NoMoreThreadsException extends Exception {
        NoMoreThreadsException() {
        }

        public NoMoreThreadsException(String message) {
            super(message);
        }
    }
}
