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
package org.codedefenders.database;

import org.codedefenders.game.GameLevel;
import org.codedefenders.game.GameMode;
import org.codedefenders.game.GameState;
import org.codedefenders.game.multiplayer.MultiplayerGame;
import org.codedefenders.game.singleplayer.AiPlayer;
import org.codedefenders.validation.code.CodeValidatorLevel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import static org.codedefenders.database.DB.RSMapper;

/**
 * This class handles the database logic for multiplayer games.
 *
 * @author <a href="https://github.com/werli">Phil Werli<a/>
 * @see MultiplayerGame
 */
public class MultiplayerGameDAO {

    /**
     * Constructs a {@link MultiplayerGame} from a {@link ResultSet} entry.
     *
     * @param rs The {@link ResultSet}.
     * @return The constructed battleground game, or {@code null} if the game is no multiplayer game.
     * @see RSMapper
     */
    static MultiplayerGame multiplayerGameFromRS(ResultSet rs) throws SQLException {
        GameMode mode = GameMode.valueOf(rs.getString("Mode"));
        if (mode != GameMode.PARTY) {
            return null;
        }
        int id = rs.getInt("ID");
        int classId = rs.getInt("Class_ID");
        int creatorId = rs.getInt("Creator_ID");
        GameState state = GameState.valueOf(rs.getString("State"));
        GameLevel level = GameLevel.valueOf(rs.getString("Level"));
        long startTime = rs.getTimestamp("Start_Time").getTime();
        long finishTime = rs.getTimestamp("Finish_Time").getTime();
        int maxAssertionsPerTest = rs.getInt("MaxAssertionsPerTest");
        boolean chatEnabled = rs.getBoolean("ChatEnabled");
        CodeValidatorLevel mutantValidator = CodeValidatorLevel.valueOf(rs.getString("MutantValidator"));
        boolean markUncovered = rs.getBoolean("MarkUncovered");
        boolean capturePlayersIntention = rs.getBoolean("CapturePlayersIntention");
        int minDefenders = rs.getInt("Defenders_Needed");
        int minAttackers = rs.getInt("Attackers_Needed");
        boolean requiresValidation = rs.getBoolean("RequiresValidation");
        int defenderLimit = rs.getInt("Defenders_Limit");
        int attackerLimit = rs.getInt("Attackers_Limit");
        float lineCoverage = rs.getFloat("Coverage_Goal");
        float mutantCoverage = rs.getFloat("Mutant_Goal");
        int defenderValue = rs.getInt("Defender_Value");
        int attackerValue = rs.getInt("Attacker_Value");

        return new MultiplayerGame.Builder(classId, creatorId, startTime, finishTime, maxAssertionsPerTest, defenderLimit, attackerLimit, minDefenders, minAttackers)
                .id(id)
                .state(state)
                .level(level)
                .attackerValue(attackerValue)
                .defenderValue(defenderValue)
                .chatEnabled(chatEnabled)
                .markUncovered(markUncovered)
                .capturePlayersIntention(capturePlayersIntention)
                .mutantValidatorLevel(mutantValidator)
                .requiresValidation(requiresValidation)
                .lineCoverage(lineCoverage)
                .mutantCoverage(mutantCoverage)
                .build();
    }

    /**
     * Stores a given {@link MultiplayerGame} in the database.
     * <p>
     * This method does not update the given game object.
     * Use {@link MultiplayerGame#insert()} instead.
     *
     * @param game the given game as a {@link MultiplayerGame}.
     * @return the generated identifier of the game as an {@code int}.
     * @throws UncheckedSQLException If storing the game was not successful.
     */
    public static int storeMultiplayerGame(MultiplayerGame game) throws UncheckedSQLException {
        int classId = game.getClassId();
        GameLevel level = game.getLevel();
        float prize = game.getPrize();
        int defenderValue = game.getDefenderValue();
        int attackerValue = game.getAttackerValue();
        float lineCoverage = game.getLineCoverage();
        float mutantCoverage = game.getMutantCoverage();
        int creatorId = game.getCreatorId();
        int minAttackers = game.getMinAttackers();
        int minDefenders = game.getMinDefenders();
        int attackerLimit = game.getAttackerLimit();
        int defenderLimit = game.getDefenderLimit();
        long startDateTime = game.getStartInLong();
        long finishDateTime = game.getFinishTimeInLong();
        GameState state = game.getState();
        int maxAssertionsPerTest = game.getMaxAssertionsPerTest();
        boolean chatEnabled = game.isChatEnabled();
        CodeValidatorLevel mutantValidatorLevel = game.getMutantValidatorLevel();
        boolean markUncovered = game.isMarkUncovered();
        boolean capturePlayersIntention = game.isCapturePlayersIntention();
        GameMode mode = game.getMode();
        boolean isSimulationGame = game.isSimulationGame();
        int simulationOriginGame = game.getOriginGameId();
        String aiStrat = game.getAiStrat() != null ? game.getAiStrat().toString() : null;

        String query = String.join("\n",
                "INSERT INTO games",
                "(Class_ID,",
                "Level,",
                "Prize,",
                "Defender_Value,",
                "Attacker_Value,",
                "Coverage_Goal,",
                "Mutant_Goal,",
                "Creator_ID,",
                "Attackers_Needed,",
                "Defenders_Needed,",
                "Attackers_Limit,",
                "Defenders_Limit,",
                "Start_Time,",
                "Finish_Time,",
                "State,",
                "Mode,",
                "MaxAssertionsPerTest,",
                "ChatEnabled,",
                "MutantValidator,",
                "MarkUncovered,",
                "CapturePlayersIntention,",
                "IsSimulationGame,",
                "SimulationOriginGame_ID,",
                "AiStrat)",
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);");
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(classId),
                DatabaseValue.of(level.name()),
                DatabaseValue.of(prize),
                DatabaseValue.of(defenderValue),
                DatabaseValue.of(attackerValue),
                DatabaseValue.of(lineCoverage),
                DatabaseValue.of(mutantCoverage),
                DatabaseValue.of(creatorId),
                DatabaseValue.of(minAttackers),
                DatabaseValue.of(minDefenders),
                DatabaseValue.of(attackerLimit),
                DatabaseValue.of(defenderLimit),
                DatabaseValue.of(new Timestamp(startDateTime)),
                DatabaseValue.of(new Timestamp(finishDateTime)),
                DatabaseValue.of(state.name()),
                DatabaseValue.of(mode.name()),
                DatabaseValue.of(maxAssertionsPerTest),
                DatabaseValue.of(chatEnabled),
                DatabaseValue.of(mutantValidatorLevel.name()),
                DatabaseValue.of(markUncovered),
                DatabaseValue.of(capturePlayersIntention),
                DatabaseValue.of(isSimulationGame),
                DatabaseValue.of(simulationOriginGame),
                DatabaseValue.of(aiStrat)
        };

        final int result = DB.executeUpdateQueryGetKeys(query, values);
        if (result != -1) {
            return result;
        } else {
            throw new UncheckedSQLException("Could not store multiplayer game to database.");
        }
    }

    /**
     * Updates a given {@link MultiplayerGame} in the database.
     * <p>
     * This method does not update the given game object.
     *
     * @param game the given game as a {@link MultiplayerGame}.
     * @return {@code true} if updating was successful, {@code false} otherwise.
     */
    public static boolean updateMultiplayerGame(MultiplayerGame game) {
        int classId = game.getClassId();
        GameLevel level = game.getLevel();
        float prize = game.getPrize();
        int defenderValue = game.getDefenderValue();
        int attackerValue = game.getAttackerValue();
        float lineCoverage = game.getLineCoverage();
        float mutantCoverage = game.getMutantCoverage();
        int id = game.getId();
        GameState state = game.getState();

        String query = String.join("\n",
                "UPDATE games",
                "SET Class_ID = ?,",
                "    Level = ?,",
                "    Prize = ?,",
                "    Defender_Value = ?,",
                "    Attacker_Value = ?,",
                "    Coverage_Goal = ?,",
                "    Mutant_Goal = ?,",
                "    State = ?,",
                "    IsSimulationGame = ?",
                "WHERE ID = ?"
        );
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(classId),
                DatabaseValue.of(level.name()),
                DatabaseValue.of(prize),
                DatabaseValue.of(defenderValue),
                DatabaseValue.of(attackerValue),
                DatabaseValue.of(lineCoverage),
                DatabaseValue.of(mutantCoverage),
                DatabaseValue.of(state.name()),
                DatabaseValue.of(game.isSimulationGame()),
                DatabaseValue.of(id)};

        return DB.executeUpdateQuery(query, values);
    }

    /**
     * Returns a {@link MultiplayerGame} for a given game identifier or
     * {@code null} if no game was found or the game mode differs.
     *
     * @param gameId the game identifier.
     * @return a {@link MultiplayerGame} instance or {@code null} if none matching game was found.
     */
    public static MultiplayerGame getMultiplayerGame(int gameId) {
        String query = String.join("\n",
                "SELECT *",
                "FROM view_battleground_games",
                "WHERE ID=?;");

        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(gameId)
        };

        return DB.executeQueryReturnValue(query, MultiplayerGameDAO::multiplayerGameFromRS, values);
    }

    /**
     * Retrieves a list of all {@link MultiplayerGame MultiplayerGames} which are not finished, i.e. available.
     *
     * @return a list of {@link MultiplayerGame MultiplayerGames}, empty if none are found.
     */
    public static List<MultiplayerGame> getAvailableMultiplayerGames() {
        String query = String.join("\n",
                "SELECT *",
                "FROM view_battleground_games",
                "WHERE State != ?",
                "  AND Finish_Time > NOW();");
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(GameState.FINISHED.name())
        };
        return DB.executeQueryReturnList(query, MultiplayerGameDAO::multiplayerGameFromRS, values);
    }

    /**
     * Retrieves a list of all {@link MultiplayerGame MultiplayerGames} which are joinable for a given user identifier.
     *
     * @param userId the user identifier the games are retrieved for.
     * @return a list of {@link MultiplayerGame MultiplayerGames}, empty if none are found.
     */
    public static List<MultiplayerGame> getOpenMultiplayerGamesForUser(int userId) {
        String query = String.join("\n",
                "SELECT *" +
                        "FROM view_battleground_games AS g",
                "INNER JOIN (SELECT gatt.ID, sum(CASE WHEN Role = 'ATTACKER' THEN 1 ELSE 0 END) nAttackers, sum(CASE WHEN Role = 'DEFENDER' THEN 1 ELSE 0 END) nDefenders",
                "              FROM games AS gatt LEFT JOIN players ON gatt.ID = players.Game_ID AND players.Active = TRUE GROUP BY gatt.ID) AS nplayers",
                "  ON g.ID = nplayers.ID",
                "WHERE g.Creator_ID!=? AND (g.State='CREATED' OR g.State='ACTIVE')",
                "  AND (g.RequiresValidation=FALSE OR (? IN (SELECT User_ID FROM users WHERE Validated=TRUE)))",
                "  AND g.ID NOT IN (SELECT g.ID FROM games g INNER JOIN players p ON g.ID=p.Game_ID WHERE p.User_ID=? AND p.Active=TRUE)",
                "  AND (nplayers.nAttackers < g.Attackers_Limit OR nplayers.nDefenders < g.Defenders_Limit);");

        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(userId),
                DatabaseValue.of(userId),
                DatabaseValue.of(userId)
        };

        return DB.executeQueryReturnList(query, MultiplayerGameDAO::multiplayerGameFromRS, values);
    }

    /**
     * Retrieves a list of active {@link MultiplayerGame MultiplayerGames}, which are created or
     * played by a given user.
     *
     * @param userId the user identifier the games are retrieved for.
     * @return a list of {@link MultiplayerGame MultiplayerGames}, empty if none are found.
     */
    public static List<MultiplayerGame> getMultiplayerGamesForUser(int userId) {
        String query = String.join("\n",
                "SELECT DISTINCT m.*",
                "FROM view_battleground_games AS m",
                "LEFT JOIN players AS p",
                "  ON p.Game_ID=m.ID",
                "    AND p.Active=TRUE",
                "WHERE (p.User_ID = ? OR m.Creator_ID = ?)",
                "  AND m.State != ?;");
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(userId),
                DatabaseValue.of(userId),
                DatabaseValue.of(GameState.FINISHED.name())
        };
        return DB.executeQueryReturnList(query, MultiplayerGameDAO::multiplayerGameFromRS, values);
    }

    /**
     * Retrieves a list of active {@link MultiplayerGame MultiplayerGames}, which are
     * played by a given user.
     *
     * @param userId the user identifier the games are retrieved for.
     * @return a list of {@link MultiplayerGame MultiplayerGames}, empty if none are found.
     */
    public static List<MultiplayerGame> getJoinedMultiplayerGamesForUser(int userId) {
        String query = String.join("\n",
                "SELECT DISTINCT m.*",
                "FROM view_battleground_games AS m",
                "LEFT JOIN players AS p",
                "  ON p.Game_ID = m.ID \n",
                "WHERE (p.User_ID = ?);");

        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(userId)
        };
        return DB.executeQueryReturnList(query, MultiplayerGameDAO::multiplayerGameFromRS, values);
    }

    /**
     * Retrieves a list of {@link MultiplayerGame MultiplayerGames}, which were created or
     * played by a given user.
     *
     * @param userId the user identifier the games are retrieved for.
     * @return a list of {@link MultiplayerGame MultiplayerGames}, empty if none are found.
     */
    public static List<MultiplayerGame> getFinishedMultiplayerGamesForUser(int userId) {
        String query = String.join("\n",
                "SELECT DISTINCT m.* ",
                "FROM view_battleground_games AS m ",
                "LEFT JOIN players AS p ON p.Game_ID = m.ID ",
                "  AND p.Active = TRUE",
                "WHERE (p.User_ID = ? OR m.Creator_ID = ?)",
                "  AND m.State = ?;");

        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(userId),
                DatabaseValue.of(userId),
                DatabaseValue.of(GameState.FINISHED.name())
        };
        return DB.executeQueryReturnList(query, MultiplayerGameDAO::multiplayerGameFromRS, values);
    }

    /**
     * Retrieves a list of {@link MultiplayerGame MultiplayerGames}, which were created by a
     * given user and are not yet finished.
     *
     * @param creatorId the creator identifier the games are retrieved for.
     * @return a list of {@link MultiplayerGame MultiplayerGames}, empty if none are found.
     */
    public static List<MultiplayerGame> getUnfinishedMultiplayerGamesCreatedBy(int creatorId) {
        String query = String.join("\n",
                "SELECT *",
                "FROM view_battleground_games",
                "WHERE (State = ?",
                "    OR State = ?)",
                "  AND Creator_ID = ?;");
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(GameState.ACTIVE.name()),
                DatabaseValue.of(GameState.CREATED.name()),
                DatabaseValue.of(creatorId)
        };
        return DB.executeQueryReturnList(query, MultiplayerGameDAO::multiplayerGameFromRS, values);
    }
}
