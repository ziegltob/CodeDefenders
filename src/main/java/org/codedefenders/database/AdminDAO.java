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

import org.codedefenders.game.leaderboard.Entry;
import org.codedefenders.servlets.admin.AdminSystemSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class AdminDAO {

    public static final String TIMESTAMP_NEVER = "never";
    private static final Logger logger = LoggerFactory.getLogger(AdminDAO.class);
    private static final String UNASSIGNED_USERS_QUERY =
            "SELECT DISTINCT\n" +
                    "  users.*\n" +
                    "FROM\n" +
                    "  users\n" +
                    "WHERE users.User_ID > 2 AND users.User_ID NOT IN (\n" +
                    "  SELECT DISTINCT players.User_ID\n" +
                    "  FROM (players\n" +
                    "    INNER JOIN games ON players.Game_ID = games.ID)\n" +
                    "  WHERE (State = 'ACTIVE' OR State = 'CREATED') AND Finish_Time > NOW() AND Role IN ('ATTACKER', 'DEFENDER') AND Active = TRUE\n" +
                    ")\n" +
                    "ORDER BY Username, User_ID;";
    private static final String AVAILABLE_GAMES_QUERY =
            "SELECT *\n" +
                    "FROM\n" +
                    "  games\n" +
                    "WHERE State != 'FINISHED' AND Mode = 'PARTY' AND Finish_Time > NOW();";

    private static final String UNFINISHED_MULTIPLAYER_GAMES_QUERY =
            "SELECT *\n" +
                    "FROM games\n" +
                    "WHERE Mode = 'PARTY' AND (State = 'ACTIVE' OR State = 'CREATED');";

    private static final String UNFINISHED_MULTIPLAYER_GAMES_BY_USER_QUERY =
            "SELECT *\n" +
                    "FROM games\n" +
                    "WHERE Mode = 'PARTY' AND (State = 'ACTIVE' OR State = 'CREATED') AND Creator_ID = ?;";

    private static final String FINISHED_MULTIPLAYER_GAMES_BY_USER_QUERY =
            "SELECT *\n" +
                    "FROM games\n" +
                    "   INNER JOIN players ON players.Game_ID = games.ID \n" +
                    "WHERE Mode = 'PARTY' AND State = 'FINISHED' AND Creator_ID = ? AND IsSimulationGame = false \n" +
                    "AND (SimulationOriginGame_ID IS NULL OR SimulationOriginGame_ID = -10) \n" +
                    "GROUP BY games.ID;";

    private static final String SIMULATED_GAMES_BY_USER =
            "SELECT *\n" +
                    "FROM games\n" +
                    "   INNER JOIN players ON players.Game_ID = games.ID \n" +
                    "WHERE Mode = 'PARTY' AND State = 'FINISHED' AND Creator_ID = ? AND IsSimulationGame = true \n" +
                    "AND (SimulationOriginGame_ID IS NOT NULL AND AiStrat IS NOT NULL) \n" +
                    "GROUP BY games.ID;";

    private static final String SIMULATION_MULTIPLAYER_GAMES_BY_USER_QUERY =
            "SELECT *\n" +
                    "FROM games\n" +
                    "WHERE Mode = 'PARTY' AND State = 'ACTIVE' AND Creator_ID = ? AND IsSimulationGame = 1\n" +
                    "GROUP BY games.ID;";

    private static final String LAST_ROLE_QUERY =
            "SELECT\n" +
                    "  players.User_ID,\n" +
                    "  Game_ID,\n" +
                    "  Role\n" +
                    "FROM users\n" +
                    "  INNER JOIN players ON users.User_ID = players.User_ID\n" +
                    "  INNER JOIN games ON players.Game_ID = games.ID\n" +
                    "  INNER JOIN\n" +
                    "  (SELECT\n" +
                    "     players.User_ID,\n" +
                    "     max(players.Game_ID) AS latestGame\n" +
                    "   FROM players\n" +
                    "   GROUP BY players.User_ID) AS lg ON lg.User_ID = players.User_ID AND lg.latestGame = games.ID\n" +
                    "WHERE lg.User_ID=?;";

    public static final String USER_SCORE_QUERY =
            "SELECT\n" +
                    "  U.username                            AS username,\n" +
                    "  IFNULL(NMutants, 0)                   AS NMutants,\n" +
                    "  IFNULL(AScore, 0)                     AS AScore,\n" +
                    "  IFNULL(NTests, 0)                     AS NTests,\n" +
                    "  IFNULL(DScore, 0)                     AS DScore,\n" +
                    "  IFNULL(NKilled, 0)                    AS NKilled,\n" +
                    "  IFNULL(AScore, 0) + IFNULL(DScore, 0) AS TotalScore\n" +
                    "FROM users U LEFT JOIN\n" +
                    "  (SELECT\n" +
                    "     PA.user_id,\n" +
                    "     count(M.Mutant_ID) AS NMutants,\n" +
                    "     sum(M.Points)      AS AScore\n" +
                    "   FROM players PA LEFT JOIN mutants M ON PA.id = M.Player_ID\n" +
                    "   GROUP BY PA.user_id)\n" +
                    "    AS Attacker ON U.user_id = Attacker.user_id\n" +
                    "  LEFT JOIN\n" +
                    "  (SELECT\n" +
                    "     PD.user_id,\n" +
                    "     count(T.Test_ID)     AS NTests,\n" +
                    "     sum(T.Points)        AS DScore,\n" +
                    "     sum(T.MutantsKilled) AS NKilled\n" +
                    "   FROM players PD LEFT JOIN tests T ON PD.id = T.Player_ID\n" +
                    "   GROUP BY PD.user_id)\n" +
                    "    AS Defender ON U.user_id = Defender.user_id\n" +
                    "WHERE U.user_id = ?;";

    public final static String UNASSIGNED_USERS_INFO_QUERY =
            "SELECT DISTINCT\n" +
                    "  users.User_ID,\n" +
                    "  users.Username,\n" +
                    "  users.Email,\n" +
                    "  lastLogin.ts AS lastLogin,\n" +
                    "  Role         AS lastRole,\n" +
                    "  totalScore\n" +
                    "FROM\n" +
                    "  users\n" +
                    "  LEFT JOIN (SELECT\n" +
                    "          MAX(Timestamp) AS ts,\n" +
                    "          user_id\n" +
                    "        FROM sessions\n" +
                    "        GROUP BY User_ID) AS lastLogin ON lastLogin.User_ID = users.User_ID\n" +
                    "  LEFT JOIN\n" +
                    "  (SELECT\n" +
                    "     players.User_ID,\n" +
                    "     Role\n" +
                    "   FROM users\n" +
                    "     INNER JOIN players ON users.User_ID = players.User_ID\n" +
                    "     INNER JOIN games ON players.Game_ID = games.ID\n" +
                    "     INNER JOIN\n" +
                    "     (SELECT\n" +
                    "        players.User_ID,\n" +
                    "        max(players.Game_ID) AS latestGame\n" +
                    "      FROM players\n" +
                    "      GROUP BY players.User_ID) AS lg ON lg.User_ID = players.User_ID AND lg.latestGame = games.ID) AS lastRole\n" +
                    "    ON lastRole.User_ID = users.User_ID\n" +
                    "  JOIN\n" +
                    "  (SELECT\n" +
                    "     U.User_ID,\n" +
                    "     IFNULL(AScore, 0) + IFNULL(DScore, 0) AS TotalScore\n" +
                    "   FROM users U LEFT JOIN\n" +
                    "     (SELECT\n" +
                    "        PA.user_id,\n" +
                    "        sum(M.Points) AS AScore\n" +
                    "      FROM players PA LEFT JOIN mutants M ON PA.id = M.Player_ID\n" +
                    "      GROUP BY PA.user_id)\n" +
                    "       AS Attacker ON U.user_id = Attacker.user_id\n" +
                    "     LEFT JOIN\n" +
                    "     (SELECT\n" +
                    "        PD.user_id,\n" +
                    "        sum(T.Points) AS DScore\n" +
                    "      FROM players PD LEFT JOIN tests T ON PD.id = T.Player_ID\n" +
                    "      GROUP BY PD.user_id)\n" +
                    "       AS Defender ON U.user_id = Defender.user_id) AS totalScore ON totalScore.User_ID = users.User_ID\n" +
                    "WHERE users.User_ID > 4 AND users.User_ID NOT IN (\n" +
                    "  SELECT DISTINCT players.User_ID\n" +
                    "  FROM (players\n" +
                    "    INNER JOIN games ON players.Game_ID = games.ID)\n" +
                    "  WHERE\n" +
                    "    (State = 'ACTIVE' OR State = 'CREATED') AND Finish_Time > NOW() AND Role IN ('ATTACKER', 'DEFENDER') AND Active = 1\n" +
                    ")\n" +
                    "ORDER BY lastLogin DESC, User_ID;";


    public final static String USERS_INFO_QUERY =
            "SELECT DISTINCT\n" +
                    "  users.User_ID,\n" +
                    "  users.Username,\n" +
                    "  users.Email,\n" +
                    "  lastLogin.ts AS lastLogin,\n" +
                    "  Role         AS lastRole,\n" +
                    "  totalScore\n" +
                    "FROM\n" +
                    "  users\n" +
                    "  LEFT JOIN (SELECT\n" +
                    "          MAX(Timestamp) AS ts,\n" +
                    "          user_id\n" +
                    "        FROM sessions\n" +
                    "        GROUP BY User_ID) AS lastLogin ON lastLogin.User_ID = users.User_ID\n" +
                    "  LEFT JOIN\n" +
                    "  (SELECT\n" +
                    "     players.User_ID,\n" +
                    "     Role\n" +
                    "   FROM users\n" +
                    "     INNER JOIN players ON users.User_ID = players.User_ID\n" +
                    "     INNER JOIN games ON players.Game_ID = games.ID\n" +
                    "     INNER JOIN\n" +
                    "     (SELECT\n" +
                    "        players.User_ID,\n" +
                    "        max(players.Game_ID) AS latestGame\n" +
                    "      FROM players\n" +
                    "      GROUP BY players.User_ID) AS lg ON lg.User_ID = players.User_ID AND lg.latestGame = games.ID) AS lastRole\n" +
                    "    ON lastRole.User_ID = users.User_ID\n" +
                    "  JOIN\n" +
                    "  (SELECT\n" +
                    "     U.User_ID,\n" +
                    "     IFNULL(AScore, 0) + IFNULL(DScore, 0) AS TotalScore\n" +
                    "   FROM users U LEFT JOIN\n" +
                    "     (SELECT\n" +
                    "        PA.user_id,\n" +
                    "        sum(M.Points) AS AScore\n" +
                    "      FROM players PA LEFT JOIN mutants M ON PA.id = M.Player_ID\n" +
                    "      GROUP BY PA.user_id)\n" +
                    "       AS Attacker ON U.user_id = Attacker.user_id\n" +
                    "     LEFT JOIN\n" +
                    "     (SELECT\n" +
                    "        PD.user_id,\n" +
                    "        sum(T.Points) AS DScore\n" +
                    "      FROM players PD LEFT JOIN tests T ON PD.id = T.Player_ID\n" +
                    "      GROUP BY PD.user_id)\n" +
                    "       AS Defender ON U.user_id = Defender.user_id) AS totalScore ON totalScore.User_ID = users.User_ID\n" +
                    "ORDER BY lastLogin DESC, User_ID;";

    public static final String PLAYERS_INFO_QUERY =
            "SELECT\n" +
                    "  ID,\n" +
                    "  Username,\n" +
                    "  Role,\n" +
                    "  lastSubmission,\n" +
                    "  TotalScore,\n" +
                    "  IFNULL(nbSubmissions, 0) as nbSubmissions\n" +
                    "FROM (SELECT\n" +
                    "        User_ID,\n" +
                    "        Role,\n" +
                    "        ID\n" +
                    "      FROM players\n" +
                    "      WHERE Game_ID = ? AND Active = TRUE) AS activePlayers\n" +
                    "  JOIN users ON activePlayers.User_ID = users.User_ID\n" +
                    "  JOIN\n" +
                    "  (SELECT\n" +
                    "     U.User_ID,\n" +
                    "     IFNULL(AScore, 0) + IFNULL(DScore, 0) AS TotalScore\n" +
                    "   FROM users U LEFT JOIN\n" +
                    "     (SELECT\n" +
                    "        PA.user_id,\n" +
                    "        sum(M.Points) AS AScore\n" +
                    "      FROM players PA LEFT JOIN mutants M ON PA.id = M.Player_ID\n" +
                    "      GROUP BY PA.user_id)\n" +
                    "       AS Attacker ON U.user_id = Attacker.user_id\n" +
                    "     LEFT JOIN\n" +
                    "     (SELECT\n" +
                    "        PD.user_id,\n" +
                    "        sum(T.Points) AS DScore\n" +
                    "      FROM players PD LEFT JOIN tests T ON PD.id = T.Player_ID\n" +
                    "      GROUP BY PD.user_id)\n" +
                    "       AS Defender ON U.user_id = Defender.user_id) AS totalScore ON totalScore.User_ID = users.User_ID\n" +
                    "  LEFT JOIN (SELECT\n" +
                    "               MAX(ts) AS lastSubmission,\n" +
                    "               Player_ID\n" +
                    "             FROM (SELECT\n" +
                    "                     MAX(mutants.Timestamp) AS ts,\n" +
                    "                     Player_ID\n" +
                    "                   FROM mutants\n" +
                    "                   GROUP BY Player_ID\n" +
                    "                   UNION\n" +
                    "                   SELECT\n" +
                    "                     MAX(tests.Timestamp) AS ts,\n" +
                    "                     Player_ID\n" +
                    "                   FROM tests\n" +
                    "                   GROUP BY Player_ID\n" +
                    "                  ) AS t\n" +
                    "             GROUP BY Player_ID) AS lastAction\n" +
                    "    ON Player_ID = activePlayers.ID\n" +
                    "  LEFT JOIN (SELECT\n" +
                    "               COUNT(*) AS nbSubmissions,\n" +
                    "               Player_ID\n" +
                    "             FROM (SELECT\n" +
                    "                     Player_ID,\n" +
                    "                     tests.Test_ID\n" +
                    "                   FROM (tests\n" +
                    "                     JOIN targetexecutions t2 ON tests.Test_ID = t2.Test_ID)\n" +
                    "                   WHERE t2.Target = 'COMPILE_TEST' AND t2.Status = 'SUCCESS'\n" +
                    "                   UNION SELECT\n" +
                    "                           Player_ID,\n" +
                    "                           mutants.Mutant_ID\n" +
                    "                         FROM (mutants\n" +
                    "                           JOIN targetexecutions t2 ON mutants.Mutant_ID = t2.Mutant_ID)\n" +
                    "                         WHERE t2.Target = 'COMPILE_MUTANT' AND t2.Status = 'SUCCESS') AS TestsAndMutants\n" +
                    "             GROUP BY Player_ID) AS submissions ON submissions.Player_ID = ID\n" +
                    "ORDER BY Role, nbSubmissions;";

    public final static String DELETE_PLAYER = "DELETE FROM players WHERE ID =?;";
    public final static String DELETE_TESTS = "DELETE FROM tests WHERE Player_ID =?;";
    public final static String DELETE_MUTANTS = "DELETE FROM mutants WHERE Player_ID =?;";
    public final static String DELETE_DEFENDER_EQUIVALENCES = "DELETE FROM equivalences WHERE Defender_ID =?;";
    public final static String DELETE_ATTACKER_EQUIVALENCES = "DELETE FROM equivalences\n" +
			"WHERE Mutant_ID IN (SELECT Mutant_ID\n" +
			"                    FROM mutants\n" +
			"                    WHERE Player_ID = ?);";
    public final static String DELETE_TEST_TARGETEXECUTIONS = "DELETE FROM targetexecutions WHERE Test_ID =?;";
    public final static String DELETE_MUTANT_TARGETEXECUTIONS = "DELETE FROM targetexecutions WHERE Mutant_ID = ?;";
    public final static String SET_USER_PW = "UPDATE users SET Password = ? WHERE User_ID = ?;";
    public final static String DELETE_USER = "DELETE FROM users WHERE User_ID = ?;";
	public final static String GET_ALL_SETTINGS = "SELECT * FROM settings;";
	public  final static String UPDATE_SETTING_1 = "UPDATE settings\n" +
            "SET ";
    public  final static String UPDATE_SETTING_2 = " = ?\n" +
            "WHERE name = ?;";
    private static final String GET_SETTING = "SELECT *\n" +
            "FROM settings WHERE settings.name = ?;";

    public static Entry getScore(int userID) throws UncheckedSQLException, SQLMappingException {
        String query = String.join("\n",
                "SELECT",
                "  U.username                            AS username,",
                "  IFNULL(NMutants, 0)                   AS NMutants,",
                "  IFNULL(AScore, 0)                     AS AScore,",
                "  IFNULL(NTests, 0)                     AS NTests,",
                "  IFNULL(DScore, 0)                     AS DScore,",
                "  IFNULL(NKilled, 0)                    AS NKilled,",
                "  IFNULL(AScore, 0) + IFNULL(DScore, 0) AS TotalScore",
                "FROM users U LEFT JOIN",
                "  (SELECT",
                "     PA.user_id,",
                "     count(M.Mutant_ID) AS NMutants,",
                "     sum(M.Points)      AS AScore",
                "   FROM players PA LEFT JOIN mutants M ON PA.id = M.Player_ID",
                "   GROUP BY PA.user_id)",
                "    AS Attacker ON U.user_id = Attacker.user_id",
                "  LEFT JOIN",
                "  (SELECT",
                "     PD.user_id,",
                "     count(T.Test_ID)     AS NTests,",
                "     sum(T.Points)        AS DScore,",
                "     sum(T.MutantsKilled) AS NKilled",
                "   FROM players PD LEFT JOIN tests T ON PD.id = T.Player_ID",
                "   GROUP BY PD.user_id)",
                "    AS Defender ON U.user_id = Defender.user_id",
                "WHERE U.user_id = ?;");
        return DB.executeQueryReturnValue(query, DatabaseAccess::entryFromRS, DatabaseValue.of(userID));
    }

    public static List<User> getUsers(String query) {
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, query);
        ResultSet rs = DB.executeQueryReturnRS(conn, stmt);

        List<User> activeUsers = new ArrayList<User>();
        try {
            while (rs.next()) {
                User userRecord = new User(rs.getInt("User_ID"), rs.getString("Username"), rs.getString("Password"), rs.getString("Email"), rs.getBoolean("Validated"), rs.getBoolean("Active"));
                userRecord.setId(rs.getInt("User_ID"));
                activeUsers.add(userRecord);
            }
        } catch (SQLException se) {
            logger.error("SQL exception caught", se);
        } catch (Exception e) {
            logger.error("Exception caught", e);
        } finally {
            DB.cleanup(conn, stmt);
        }
        return activeUsers;
    }

    public static List<User> getUnassignedUsers() {
        return getUsers(UNASSIGNED_USERS_QUERY);
    }

    public static List<MultiplayerGame> getGamesFromRS(ResultSet rs, Connection conn, PreparedStatement stmt) {
        List<MultiplayerGame> gamesList = new ArrayList<MultiplayerGame>();
        try {
            while (rs.next()) {
                MultiplayerGame mg = new MultiplayerGame(rs.getInt("Class_ID"), rs.getInt("Creator_ID"),
                        GameLevel.valueOf(rs.getString("Level")), (float) rs.getDouble("Coverage_Goal"),
                        (float) rs.getDouble("Mutant_Goal"), rs.getInt("Prize"), rs.getInt("Defender_Value"),
                        rs.getInt("Attacker_Value"), rs.getInt("Defenders_Limit"), rs.getInt("Attackers_Limit"),
                        rs.getInt("Defenders_Needed"), rs.getInt("Attackers_Needed"), rs.getTimestamp("Start_Time").getTime(),
                        rs.getTimestamp("Finish_Time").getTime(), rs.getString("State"), rs.getBoolean("RequiresValidation"),
                        rs.getInt("MaxAssertionsPerTest"),rs.getBoolean("ChatEnabled"),
                        CodeValidatorLevel.valueOf(rs.getString("MutantValidator")), rs.getBoolean("MarkUncovered"));
                mg.setId(rs.getInt("ID"));
                gamesList.add(mg);
            }
        } catch (SQLException se) {
            logger.error("SQL exception caught", se);
        } catch (Exception e) {
            logger.error("Exception caught", e);
        } finally {
            DB.cleanup(conn, stmt);
        }
        return gamesList;
    }


    public static List<MultiplayerGame> getAvailableGames() {
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, AVAILABLE_GAMES_QUERY);
        ResultSet rs = DB.executeQueryReturnRS(conn, stmt);
        return getGamesFromRS(rs, conn, stmt);
    }

    public static List<MultiplayerGame> getUnfinishedMultiplayerGames() {
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, UNFINISHED_MULTIPLAYER_GAMES_QUERY);
        ResultSet rs = DB.executeQueryReturnRS(conn, stmt);
        return getGamesFromRS(rs, conn, stmt);
    }

    public static List<MultiplayerGame> getUnfinishedMultiplayerGamesCreatedBy(int userID) {
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, UNFINISHED_MULTIPLAYER_GAMES_BY_USER_QUERY, DB.getDBV(userID));
        ResultSet rs = DB.executeQueryReturnRS(conn, stmt);
        return getGamesFromRS(rs, conn, stmt);
    }

    public static List<MultiplayerGame> getFinishedMultiplayerGamesWithPlayersCreatedBy(int userID) {
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, FINISHED_MULTIPLAYER_GAMES_BY_USER_QUERY, DB.getDBV(userID));
        ResultSet rs = DB.executeQueryReturnRS(conn, stmt);
        return getGamesFromRS(rs, conn, stmt);
    }

    public static List<MultiplayerGame> getSimulatedGamesByCreator(int userID) {
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, SIMULATED_GAMES_BY_USER, DB.getDBV(userID));
        return DatabaseAccess.getMultiplayerGames(stmt, conn);
    }

    public static List<MultiplayerGame> getMultiplayerSimulationGamesCreatedBy(int userID) {
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, SIMULATION_MULTIPLAYER_GAMES_BY_USER_QUERY, DB.getDBV(userID));
        return DatabaseAccess.getMultiplayerGames(stmt, conn);
    }


    public static Role getLastRole(int uid) {
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, LAST_ROLE_QUERY, DB.getDBV(uid));
        ResultSet rs = DB.executeQueryReturnRS(conn, stmt);
        try {
            if (rs.next()) {
                return Role.valueOf(rs.getString(3));
            }
        } catch (SQLException se) {
            logger.error("SQL exception caught", se);
        } catch (Exception e) {
            logger.error("Exception caught", e);
        } finally {
            DB.cleanup(conn, stmt);
        }
        return null;
    }

    public static Entry getScore(int userID) {
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, USER_SCORE_QUERY, DB.getDBV(userID));
        ResultSet rs = DB.executeQueryReturnRS(conn, stmt);
        try {
            if (rs.next()) {
                Entry p = new Entry();
                p.setUsername(rs.getString("username"));
                p.setMutantsSubmitted(rs.getInt("NMutants"));
                p.setAttackerScore(rs.getInt("AScore"));
                p.setTestsSubmitted(rs.getInt("NTests"));
                p.setDefenderScore(rs.getInt("DScore"));
                p.setMutantsKilled(rs.getInt("NKilled"));
                p.setTotalPoints(rs.getInt("TotalScore"));
                return p;
            }
        } catch (SQLException se) {
            logger.error("SQL exception caught", se);
            DB.cleanup(conn, stmt);
        } catch (Exception e) {
            logger.error("Exception caught", e);
        } finally {
            DB.cleanup(conn, stmt);
        }
        return null;
    }

    public static boolean deletePlayerTest(int pid) {
        String query = "DELETE FROM tests WHERE Player_ID =?;";
        return DB.executeUpdateQuery(query, DatabaseValue.of(pid));
    }

    public static boolean deletePlayerMutants(int pid) {
        String query = "DELETE FROM mutants WHERE Player_ID =?;";
        return DB.executeUpdateQuery(query, DatabaseValue.of(pid));
    }

    public static boolean deleteDefenderEquivalences(int pid) {
        String query = "DELETE FROM equivalences WHERE Defender_ID =?;";
        return DB.executeUpdateQuery(query, DatabaseValue.of(pid));
    }

    public static boolean deleteAttackerEquivalences(int pid) {
        String query = String.join("\n",
                "DELETE FROM equivalences",
                "WHERE Mutant_ID IN (SELECT Mutant_ID",
                "                    FROM mutants",
                "                    WHERE Player_ID = ?);");
        return DB.executeUpdateQuery(query, DatabaseValue.of(pid));
    }

    public static boolean deletePlayer(int pid) {
        String query = "DELETE FROM players WHERE ID =?;";
        return DB.executeUpdateQuery(query, DatabaseValue.of(pid));
    }

    public static boolean deleteTestTargetExecutions(int tid) {
        String query = "DELETE FROM targetexecutions WHERE Test_ID =?;";
        return DB.executeUpdateQuery(query, DatabaseValue.of(tid));
    }

    public static boolean deleteMutantTargetExecutions(int mid) {
        String query = "DELETE FROM targetexecutions WHERE Mutant_ID = ?;";
        return DB.executeUpdateQuery(query, DatabaseValue.of(mid));
    }

    private static List<String> userInfoFromRS(ResultSet rs) throws SQLException {
        List<String> userInfo = new ArrayList<>();
        userInfo.add(String.valueOf(rs.getInt("User_ID")));
        userInfo.add(rs.getString("Username"));
        userInfo.add(rs.getString("Email"));
        Timestamp ts = rs.getTimestamp("lastLogin");
        userInfo.add(ts == null ? "-- never --" : ts.toString().substring(0, ts.toString().length() - 5));
        userInfo.add(rs.getString("lastRole"));
        userInfo.add(String.valueOf(rs.getInt("TotalScore")));
        return userInfo;
    }

    public static List<List<String>> getUnassignedUsersInfo() throws UncheckedSQLException, SQLMappingException {
        final String query = String.join("\n",
                "SELECT DISTINCT",
                "  u.User_ID,",
                "  u.Username,",
                "  u.Email,",
                "  lastLogin.ts AS lastLogin,",
                "  Role         AS lastRole,",
                "  totalScore",
                "FROM",
                "  view_valid_users u",
                "  LEFT JOIN (SELECT",
                "          MAX(Timestamp) AS ts,",
                "          user_id",
                "        FROM sessions",
                "        GROUP BY User_ID) AS lastLogin ON lastLogin.User_ID = u.User_ID",
                "  LEFT JOIN",
                "  (SELECT",
                "     players.User_ID,",
                "     Role",
                "   FROM users",
                "     INNER JOIN players ON users.User_ID = players.User_ID",
                "     INNER JOIN games ON players.Game_ID = games.ID",
                "     INNER JOIN",
                "     (SELECT",
                "        players.User_ID,",
                "        max(players.Game_ID) AS latestGame",
                "      FROM players",
                "      GROUP BY players.User_ID) AS lg ON lg.User_ID = players.User_ID AND lg.latestGame = games.ID) AS lastRole",
                "    ON lastRole.User_ID = u.User_ID",
                "  JOIN",
                "  (SELECT",
                "     U.User_ID,",
                "     IFNULL(AScore, 0) + IFNULL(DScore, 0) AS TotalScore",
                "   FROM users U LEFT JOIN",
                "     (SELECT",
                "        PA.user_id,",
                "        sum(M.Points) AS AScore",
                "      FROM players PA LEFT JOIN mutants M ON PA.id = M.Player_ID",
                "      GROUP BY PA.user_id)",
                "       AS Attacker ON U.user_id = Attacker.user_id",
                "     LEFT JOIN",
                "     (SELECT",
                "        PD.user_id,",
                "        sum(T.Points) AS DScore",
                "      FROM players PD LEFT JOIN tests T ON PD.id = T.Player_ID",
                "      GROUP BY PD.user_id)",
                "       AS Defender ON U.user_id = Defender.user_id) AS totalScore ON totalScore.User_ID = u.User_ID",
                "WHERE u.User_ID NOT IN (",
                "  SELECT DISTINCT players.User_ID",
                "  FROM (players",
                "    INNER JOIN games ON players.Game_ID = games.ID)",
                "  WHERE",
                "    (State = 'ACTIVE' OR State = 'CREATED') AND Finish_Time > NOW() AND Role IN ('ATTACKER', 'DEFENDER') AND Active = 1",
                ")",
                "ORDER BY lastLogin DESC, User_ID;");
        return DB.executeQueryReturnList(query, AdminDAO::userInfoFromRS);
    }

    public static List<List<String>> getAllUsersInfo() throws UncheckedSQLException, SQLMappingException {
        String query = String.join("\n",
                "SELECT DISTINCT",
                "  users.User_ID,",
                "  users.Username,",
                "  users.Email,",
                "  lastLogin.ts AS lastLogin,",
                "  Role         AS lastRole,",
                "  totalScore",
                "FROM",
                "  users",
                "  LEFT JOIN (SELECT",
                "          MAX(Timestamp) AS ts,",
                "          user_id",
                "        FROM sessions",
                "        GROUP BY User_ID) AS lastLogin ON lastLogin.User_ID = users.User_ID",
                "  LEFT JOIN",
                "  (SELECT",
                "     players.User_ID,",
                "     Role",
                "   FROM users",
                "     INNER JOIN players ON users.User_ID = players.User_ID",
                "     INNER JOIN games ON players.Game_ID = games.ID",
                "     INNER JOIN",
                "     (SELECT",
                "        players.User_ID,",
                "        max(players.Game_ID) AS latestGame",
                "      FROM players",
                "      GROUP BY players.User_ID) AS lg ON lg.User_ID = players.User_ID AND lg.latestGame = games.ID) AS lastRole",
                "    ON lastRole.User_ID = users.User_ID",
                "  JOIN",
                "  (SELECT",
                "     U.User_ID,",
                "     IFNULL(AScore, 0) + IFNULL(DScore, 0) AS TotalScore",
                "   FROM users U LEFT JOIN",
                "     (SELECT",
                "        PA.user_id,",
                "        sum(M.Points) AS AScore",
                "      FROM players PA LEFT JOIN mutants M ON PA.id = M.Player_ID",
                "      GROUP BY PA.user_id)",
                "       AS Attacker ON U.user_id = Attacker.user_id",
                "     LEFT JOIN",
                "     (SELECT",
                "        PD.user_id,",
                "        sum(T.Points) AS DScore",
                "      FROM players PD LEFT JOIN tests T ON PD.id = T.Player_ID",
                "      GROUP BY PD.user_id)",
                "       AS Defender ON U.user_id = Defender.user_id) AS totalScore ON totalScore.User_ID = users.User_ID",
                "ORDER BY lastLogin DESC, User_ID;");
        return DB.executeQueryReturnList(query, AdminDAO::userInfoFromRS);
    }

    public static List<List<String>> getPlayersInfo(int gameId) throws UncheckedSQLException, SQLMappingException {
        String query = String.join("\n",
        "SELECT",
                "  ID,",
                "  Username,",
                "  Role,",
                "  lastSubmission,",
                "  TotalScore,",
                "  IFNULL(nbSubmissions, 0) as nbSubmissions",
                "FROM (SELECT",
                "        User_ID,",
                "        Role,",
                "        ID",
                "      FROM players",
                "      WHERE Game_ID = ? AND Active = TRUE) AS activePlayers",
                "  JOIN users ON activePlayers.User_ID = users.User_ID",
                "  JOIN",
                "  (SELECT",
                "     U.User_ID,",
                "     IFNULL(AScore, 0) + IFNULL(DScore, 0) AS TotalScore",
                "   FROM users U LEFT JOIN",
                "     (SELECT",
                "        PA.user_id,",
                "        sum(M.Points) AS AScore",
                "      FROM players PA LEFT JOIN mutants M ON PA.id = M.Player_ID",
                "      GROUP BY PA.user_id)",
                "       AS Attacker ON U.user_id = Attacker.user_id",
                "     LEFT JOIN",
                "     (SELECT",
                "        PD.user_id,",
                "        sum(T.Points) AS DScore",
                "      FROM players PD LEFT JOIN tests T ON PD.id = T.Player_ID",
                "      GROUP BY PD.user_id)",
                "       AS Defender ON U.user_id = Defender.user_id) AS totalScore ON totalScore.User_ID = users.User_ID",
                "  LEFT JOIN (SELECT",
                "               MAX(ts) AS lastSubmission,",
                "               Player_ID",
                "             FROM (SELECT",
                "                     MAX(mutants.Timestamp) AS ts,",
                "                     Player_ID",
                "                   FROM mutants",
                "                   GROUP BY Player_ID",
                "                   UNION",
                "                   SELECT",
                "                     MAX(tests.Timestamp) AS ts,",
                "                     Player_ID",
                "                   FROM tests",
                "                   GROUP BY Player_ID",
                "                  ) AS t",
                "             GROUP BY Player_ID) AS lastAction",
                "    ON Player_ID = activePlayers.ID",
                "  LEFT JOIN (SELECT",
                "               COUNT(*) AS nbSubmissions,",
                "               Player_ID",
                "             FROM (SELECT",
                "                     Player_ID,",
                "                     tests.Test_ID",
                "                   FROM (tests",
                "                     JOIN targetexecutions t2 ON tests.Test_ID = t2.Test_ID)",
                "                   WHERE t2.Target = 'COMPILE_TEST' AND t2.Status = 'SUCCESS'",
                "                   UNION SELECT",
                "                           Player_ID,",
                "                           mutants.Mutant_ID",
                "                         FROM (mutants",
                "                           JOIN targetexecutions t2 ON mutants.Mutant_ID = t2.Mutant_ID)",
                "                         WHERE t2.Target = 'COMPILE_MUTANT' AND t2.Status = 'SUCCESS') AS TestsAndMutants",
                "             GROUP BY Player_ID) AS submissions ON submissions.Player_ID = ID",
                "ORDER BY Role, nbSubmissions;");

        final DB.RSMapper<List<String>> mapper = rs -> {
            List<String> playerInfo = new ArrayList<>();
            playerInfo.add(String.valueOf(rs.getInt("ID")));
            playerInfo.add(rs.getString("Username"));
            playerInfo.add(rs.getString("Role"));
            Timestamp ts = rs.getTimestamp("lastSubmission");
            playerInfo.add(ts == null ? TIMESTAMP_NEVER : "" + ts.getTime());
            playerInfo.add(String.valueOf(rs.getInt("TotalScore")));
            playerInfo.add(String.valueOf(rs.getInt("nbSubmissions")));
            return playerInfo;
        };
        return DB.executeQueryReturnList(query, mapper, DatabaseValue.of(gameId));
    }

    public static boolean setUserPassword(int uid, String password) {
        String query = "UPDATE users SET Password = ? WHERE User_ID = ?;";
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(password),
                DatabaseValue.of(uid)
        };
        return DB.executeUpdateQuery(query, values);
    }

    public static boolean deleteUser(int userId) {
        String query = "DELETE FROM users WHERE User_ID = ?;";
        return DB.executeUpdateQuery(query, DatabaseValue.of(userId));
        // this does not work as foreign keys are not deleted (recommended: update w/ ON DELETE CASCADE)
    }

    public static boolean updateSystemSetting(AdminSystemSettings.SettingsDTO setting) {
        String valueToSet = setting.getType().name();

        DatabaseValue value = null;
        switch (setting.getType()) {
            case STRING_VALUE:
                value = DatabaseValue.of(setting.getStringValue());
                break;
            case INT_VALUE:
                value = DatabaseValue.of(setting.getIntValue());
                break;
            case BOOL_VALUE:
                value = DatabaseValue.of(setting.getBoolValue());
                break;
            case FLOAT_VALUE:
                value = DatabaseValue.of(setting.getFloatValue());
                break;
        }

        String query = String.join("\n",
                "UPDATE settings",
                "SET " + valueToSet + " = ?",
                "WHERE name = ?;");

        return DB.executeUpdateQuery(query, value, DatabaseValue.of(setting.getName().name()));
    }

    private static List<AdminSystemSettings.SettingsDTO> getSettings(ResultSet rs, Connection conn, PreparedStatement stmt) {
        List<AdminSystemSettings.SettingsDTO> settings = new ArrayList<>();
        try {
            while (rs.next()) {
                AdminSystemSettings.SettingsDTO setting = null;
                AdminSystemSettings.SETTING_NAME name = AdminSystemSettings.SETTING_NAME.valueOf(rs.getString("name"));
                AdminSystemSettings.SETTING_TYPE settingType = AdminSystemSettings.SETTING_TYPE.valueOf(rs.getString("type"));
                switch (settingType) {
                    case STRING_VALUE:
                        setting = new AdminSystemSettings.SettingsDTO(name, rs.getString(settingType.name()));
                        break;
                    case INT_VALUE:
                        setting = new AdminSystemSettings.SettingsDTO(name,rs.getInt(settingType.name()));
                        break;
                    case BOOL_VALUE:
                        setting = new AdminSystemSettings.SettingsDTO(name,rs.getBoolean(settingType.name()));
                        break;
                    case FLOAT_VALUE:
                        setting = new AdminSystemSettings.SettingsDTO(name,rs.getFloat(settingType.name()));
                        break;
                }
                settings.add(setting);
            }
        } catch (SQLException se) {
            logger.error("SQL exception caught", se);
        } catch (Exception e) {
            logger.error("Exception caught", e);
        } finally {
            DB.cleanup(conn, stmt);
        }
        return settings;
    }

    public static List<AdminSystemSettings.SettingsDTO> getSystemSettings()
            throws UncheckedSQLException, SQLMappingException {
        String query = "SELECT * FROM settings;";
        return DB.executeQueryReturnList(query, AdminDAO::settingFromRS);
    }

    public static AdminSystemSettings.SettingsDTO getSystemSetting(AdminSystemSettings.SETTING_NAME name)
            throws UncheckedSQLException, SQLMappingException {
        String query = "SELECT * FROM settings WHERE settings.name = ?;";
        return DB.executeQueryReturnValue(query, AdminDAO::settingFromRS, DatabaseValue.of(name.name()));
    }

    // TODO this is just used to initialize the connection pool, we should probably make more specific methods for that
    /**
     * This does not close the given {@link Connection}.
     */
	static AdminSystemSettings.SettingsDTO getSystemSettingInt(AdminSystemSettings.SETTING_NAME name, Connection conn) throws SQLException {
        String query = "SELECT * FROM settings WHERE settings.name = ?;";
		PreparedStatement stmt = conn.prepareStatement(query);
		stmt.setString(1, name.name());
		ResultSet rs = stmt.executeQuery();
		if (rs.next()) {
			AdminSystemSettings.SETTING_TYPE settingType = AdminSystemSettings.SETTING_TYPE.valueOf(rs.getString("type"));
			return new AdminSystemSettings.SettingsDTO(name, rs.getInt(settingType.name()));
		}
		return null;
	}
}
