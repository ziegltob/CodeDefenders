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

import org.codedefenders.execution.TargetExecution;
import org.codedefenders.game.GameClass;
import org.codedefenders.game.Mutant;
import org.codedefenders.game.Role;
import org.codedefenders.game.Test;
import org.codedefenders.game.duel.DuelGame;
import org.codedefenders.game.leaderboard.Entry;
import org.codedefenders.game.singleplayer.AiPlayer;
import org.codedefenders.model.Event;
import org.codedefenders.model.EventStatus;
import org.codedefenders.model.EventType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * This class handles database logic for functionality which has not
 * yet been extracted to specific data access objects (DAO).
 * <p>
 * This means that more or less most methods are legacy and/or should
 * be moved to DAOs.
 */
public class DatabaseAccess {

    /**
     * Sanitises user input. If a whole SQL query is entered, syntax
     * errors may occur.
     *
     * @param s user input String
     * @return sanitised String s
     */
    public static String sanitise(String s) {
        s = s.replaceAll("\\<", "&lt;");
        s = s.replaceAll("\\>", "&gt;");
        s = s.replaceAll("\\\"", "&quot;");
        s = s.replaceAll("\\'", "&apos;");
        return s;
    }

    static String addSlashes(String s) {
        if (s == null) {
            return null;
        }
        return s.replaceAll("\\\\", "\\\\\\\\");
    }

    public static List<Event> getEventsForGame(int gameId) {
        String query = String.join("\n",
                "SELECT *",
                "FROM events ",
                "LEFT JOIN event_messages AS em",
                "  ON events.Event_Type = em.Event_Type",
                "LEFT JOIN event_chat AS ec",
                "  ON events.Event_Id = ec.Event_Id ",
                "WHERE Game_ID=? ",
                "  AND Event_Status=?");
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(gameId),
                DatabaseValue.of(EventStatus.GAME.toString())
        };
        return DB.executeQueryReturnList(query, DatabaseAccess::getEvents, values);
    }

    public static void removePlayerEventsForGame(int gameId, int playerId) {
        String query = "UPDATE events SET Event_Status=? WHERE Game_ID=? AND Player_ID=?";
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(EventStatus.DELETED.toString()),
                DatabaseValue.of(gameId),
                DatabaseValue.of(playerId)};
        DB.executeUpdateQuery(query, values);
    }

    public static List<Event> getNewEventsForGame(int gameId, long time, Role role) {
        String query = String.join("\n",
                "SELECT *",
                        "FROM events",
                        "LEFT JOIN event_messages AS em",
                        "  ON events.Event_Type = em.Event_Type ",
                        "LEFT JOIN event_chat AS ec",
                        "  ON events.Event_Id = ec.Event_Id",
                        "WHERE Game_ID=?",
                        "  AND Event_Status=? ",
                        "  AND Timestamp >= FROM_UNIXTIME(?)");
        if (role.equals(Role.ATTACKER)) {
            query += " AND events.Event_Type!='DEFENDER_MESSAGE'";
        } else if (role.equals(Role.DEFENDER)) {
            query += " AND events.Event_Type!='ATTACKER_MESSAGE'";
        }

        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(gameId),
                DatabaseValue.of(EventStatus.GAME.toString()),
                DatabaseValue.of(time)};

        return DB.executeQueryReturnList(query, DatabaseAccess::getEvents, values);
    }

    /**
     * Retrieve the latest (in the past 5 minutes and not yet seen)
     * events that belong to a game and relate to equivalence duels
     */
    // FIXME userId not useful
    public static List<Event> getNewEquivalenceDuelEventsForGame(int gameId, int lastMessageId) {
        String query = String.join("\n",
                "SELECT *",
                "FROM events",
                "LEFT JOIN event_messages AS em",
                "  ON events.Event_Type = em.Event_Type ",
                "LEFT JOIN event_chat AS ec",
                "  ON events.Event_Id = ec.Event_Id ", // FIXME this is here otherwise the getEvents call fails, get rid of this...
                "WHERE Game_ID=?",
                "  AND Event_Status=?",
                "  AND (events.Event_Type=? OR events.Event_Type=? OR events.Event_Type=?) ",
                "  AND Timestamp >= FROM_UNIXTIME(UNIX_TIMESTAMP()-300) ",
                "  AND events.Event_ID > ?");
        // DEFENDER_MUTANT_CLAIMED_EQUIVALENT
        // EventType.ATTACKER_MUTANT_KILLED_EQUIVALENT, EventStatus.GAME,
        // ATTACKER_MUTANT_KILLED_EQUIVALENT
        DatabaseValue[] values = new DatabaseValue[]{
//				DatabaseValue.of(userId),
                DatabaseValue.of(gameId),
                DatabaseValue.of(EventStatus.GAME.toString()),
                DatabaseValue.of(EventType.DEFENDER_MUTANT_CLAIMED_EQUIVALENT.toString()),
                DatabaseValue.of(EventType.DEFENDER_MUTANT_EQUIVALENT.toString()),
                DatabaseValue.of(EventType.ATTACKER_MUTANT_KILLED_EQUIVALENT.toString()),
                DatabaseValue.of(lastMessageId)};
        return DB.executeQueryReturnList(query, DatabaseAccess::getEventsWithMessage, values);
    }

    public static List<Event> getEventsForUser(int userId) {
        String query = String.join("\n",
                "SELECT *",
                "FROM events ",
                "LEFT JOIN event_messages AS em",
                "  ON events.Event_Type = em.Event_Type ",
                "LEFT JOIN event_chat AS ec",
                "  ON events.Event_Id = ec.Event_Id",
                "WHERE Event_Status!='DELETED' ",
                "  AND Player_ID=?;");
        return DB.executeQueryReturnList(query, DatabaseAccess::getEvents, DatabaseValue.of(userId));
    }

    public static List<Event> getNewEventsForUser(int userId, long time) {
        String query = String.join("\n",
                "SELECT *",
                "FROM events ",
                "LEFT JOIN event_messages AS em",
                "  ON events.Event_Type = em.Event_Type ",
                "LEFT JOIN event_chat AS ec",
                "  ON events.Event_Id = ec.Event_Id ",
                "WHERE Player_ID=?",
                "  AND Event_Status<>?",
                "  AND Event_Status<>? ",
                "  AND Timestamp >= FROM_UNIXTIME(?)");
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(userId),
                DatabaseValue.of(EventStatus.DELETED.toString()),
                DatabaseValue.of(EventStatus.GAME.toString()),
                DatabaseValue.of(time)};
        return DB.executeQueryReturnList(query, DatabaseAccess::getEvents, values);
    }

    public static void setGameAsAIDummy(int gameId) {
        String query = "UPDATE games SET IsAIDummyGame = 1 WHERE ID = ?;";
        DB.executeUpdateQuery(query, DatabaseValue.of(gameId));
    }

    public static DuelGame getAiDummyGameForClass(int classId) {
        String query = "SELECT * FROM games WHERE Class_ID=? AND IsAIDummyGame=1";
        return DB.executeQueryReturnValue(query, DuelGameDAO::duelGameFromRS, DatabaseValue.of(classId));
    }

    public static boolean isAiPrepared(GameClass c) {
        String query = "SELECT * FROM classes WHERE AiPrepared = 1 AND Class_ID = ?";
        Boolean bool = DB.executeQueryReturnValue(query, rs -> true, DatabaseValue.of(c.getId()));
        return Optional.ofNullable(bool).orElse(false);
    }

    public static void setAiPrepared(GameClass c) {
        String query = "UPDATE classes SET AiPrepared = 1 WHERE Class_ID = ?;";
        DB.executeUpdateQuery(query, DatabaseValue.of(c.getId()));
    }

    private static Event getEvents(ResultSet rs) throws SQLException {
        Event event = new Event(
                rs.getInt("events.Event_ID"),
                rs.getInt("Game_ID"),
                rs.getInt("Player_ID"),
                rs.getString("em.Message"),
                rs.getString("events.Event_Type"),
                rs.getString("Event_Status"),
                rs.getTimestamp("Timestamp"));
        String chatMessage = rs.getString("ec.Message");
        event.setChatMessage(chatMessage);
        return event;
    }
	public static Map<Integer, MultiplayerGame> getActiveGamesWithActiveAiAttacker() {
		String query = "SELECT DISTINCT g.*,\n" +
				"IFNULL(att.User_ID,0) AS Attacker_ID, att.ID AS Attacker_Player_ID\n" +
				"FROM games AS g\n" +
				"LEFT JOIN players AS att ON g.ID=att.Game_ID AND att.Role='ATTACKER' AND att.Active=TRUE\n" +
				"WHERE g.State='ACTIVE'\n" +
				"AND g.IsAIDummyGame = 0\n" +
				"AND att.User_ID = 1\n" +
                "AND att.Active = 1;";

		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query);
        Map<Integer, MultiplayerGame>  gameMap = new HashMap<>();
        try {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                gameMap.put(rs.getInt("Attacker_Player_ID"), new MultiplayerGame(rs.getInt("Class_ID"), rs.getInt("Creator_ID"),
                        GameLevel.valueOf(rs.getString("Level")), (float) rs.getDouble("Coverage_Goal"),
                        (float) rs.getDouble("Mutant_Goal"), rs.getInt("Prize"), rs.getInt("Defender_Value"),
                        rs.getInt("Attacker_Value"), rs.getInt("Defenders_Limit"), rs.getInt("Attackers_Limit"),
                        rs.getInt("Defenders_Needed"), rs.getInt("Attackers_Needed"), rs.getTimestamp("Start_Time").getTime(),
                        rs.getTimestamp("Finish_Time").getTime(), rs.getString("State"), rs.getBoolean("RequiresValidation"),
                        rs.getInt("MaxAssertionsPerTest"),rs.getBoolean("ChatEnabled"),
                        CodeValidatorLevel.valueOf(rs.getString("MutantValidator")), rs.getBoolean("MarkUncovered")));
            }
        } catch (SQLException se) {
            logger.error("SQL exception caught", se);
        } catch (Exception e) {
            logger.error("Exception caught", e);
        } finally {
            DB.cleanup(conn, stmt);
        }
        return gameMap;
	}

	public static Map<Integer, MultiplayerGame> getActiveGamesWithActiveAiDefender() {
		String query = "SELECT DISTINCT g.*,\n" +
				"IFNULL(def.User_ID,0) AS Defender_ID, def.ID AS Defender_Player_ID\n" +
				"FROM games AS g\n" +
				"LEFT JOIN players AS def ON g.ID=def.Game_ID AND def.Role='DEFENDER' AND def.Active=TRUE\n" +
				"WHERE g.State='ACTIVE'\n" +
				"AND g.IsAIDummyGame = 0\n" +
				"AND def.User_ID = 2\n" +
                "AND def.Active = 1;";

        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, query);
        Map<Integer, MultiplayerGame>  gameMap = new HashMap<>();
        try {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                gameMap.put(rs.getInt("Defender_Player_ID"), new MultiplayerGame(rs.getInt("Class_ID"), rs.getInt("Creator_ID"),
                        GameLevel.valueOf(rs.getString("Level")), (float) rs.getDouble("Coverage_Goal"),
                        (float) rs.getDouble("Mutant_Goal"), rs.getInt("Prize"), rs.getInt("Defender_Value"),
                        rs.getInt("Attacker_Value"), rs.getInt("Defenders_Limit"), rs.getInt("Attackers_Limit"),
                        rs.getInt("Defenders_Needed"), rs.getInt("Attackers_Needed"), rs.getTimestamp("Start_Time").getTime(),
                        rs.getTimestamp("Finish_Time").getTime(), rs.getString("State"), rs.getBoolean("RequiresValidation"),
                        rs.getInt("MaxAssertionsPerTest"),rs.getBoolean("ChatEnabled"),
                        CodeValidatorLevel.valueOf(rs.getString("MutantValidator")), rs.getBoolean("MarkUncovered")));
            }
        } catch (SQLException se) {
            logger.error("SQL exception caught", se);
        } catch (Exception e) {
            logger.error("Exception caught", e);
        } finally {
            DB.cleanup(conn, stmt);
        }
        return gameMap;
	}

	public static void setPlayerIsActive(int playerId, boolean isActive) {
        String query = "UPDATE players SET Active=? WHERE ID=?";
        DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(isActive),
                DB.getDBV(playerId)};
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
        DB.executeUpdate(stmt, conn);
    }

    public static boolean getPlayerIsActive(int playerId) {
        String query = "SELECT * FROM players WHERE ID=?;";
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(playerId));
        boolean isActive = false;
        try {
            ResultSet rs = DB.executeQueryReturnRS(conn, stmt);
            while (rs.next()) {
                isActive = rs.getBoolean("Active");
            }
        } catch (SQLException e) {
            logger.error("SQLException while parsing result set for statement\n\t" + query, stmt);
        } finally {
            DB.cleanup(conn, stmt);
        }
        return isActive;
    }

	public static Integer getSimulationOriginGame(int gameId) {
		String query = "SELECT * FROM games WHERE ID=?;";
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(gameId));
		try {
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				return rs.getInt("SimulationOriginGame_ID");
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

	private static ArrayList<Event> getEvents(PreparedStatement stmt, Connection conn) {
		ArrayList<Event> events = new ArrayList<Event>();
		try {
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				Event event = new Event(rs.getInt("events.Event_ID"),
						rs.getInt("Game_ID"),
						rs.getInt("Player_ID"),
						rs.getString("em.Message"),
						rs.getString("events.Event_Type"),
						rs.getString("Event_Status"),
						rs.getTimestamp("Timestamp"));
				String chatMessage = rs.getString("ec.Message");
				event.setChatMessage(chatMessage);
				events.add(event);
			}
		} catch (SQLException se) {
			logger.error("SQL exception caught", se);
		} catch (Exception e) {
			logger.error("Exception caught", e);
		} finally {
			DB.cleanup(conn, stmt);
		}
		return events;
	}

    private static Event getEventsWithMessage(ResultSet rs) throws SQLException {
        Event event = new Event(
                rs.getInt("events.Event_ID"),
                rs.getInt("Game_ID"),
                rs.getInt("Player_ID"),
                rs.getString("events.Event_Message"),
                rs.getString("events.Event_Type"),
                rs.getString("Event_Status"),
                rs.getTimestamp("Timestamp"));
        String chatMessage = rs.getString("ec.Message");
        event.setChatMessage(chatMessage);
        return event;
    }

	private static GameClass getClass(PreparedStatement stmt, Connection conn) {
		try {
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				GameClass classRecord = new GameClass(rs.getInt("Class_ID"), rs.getString("Name"), rs.getString("Alias"), rs.getString("JavaFile"), rs.getString("ClassFile"), rs.getBoolean("RequireMocking"));
				return classRecord;
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

	public static List<GameClass> getAllClasses() {
		List<GameClass> classList = new ArrayList<>();

		String query = "SELECT * FROM classes;";
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query);
		ResultSet rs = DB.executeQueryReturnRS(conn, stmt);
		try {
			while (rs.next()) {
				classList.add(new GameClass(rs.getInt("Class_ID"), rs.getString("Name"), rs.getString("Alias"), rs.getString("JavaFile"), rs.getString("ClassFile"), rs.getBoolean("RequireMocking")));
			}
		} catch (SQLException se) {
			logger.error("SQL exception caught", se);
		} catch (Exception e) {
			logger.error("Exception caught", e);
		} finally {
			DB.cleanup(conn, stmt);
		}
		return classList;
	}

	public static List<User> getAllUsers() {
		List<User> uList = new ArrayList<>();
		String query = "SELECT * FROM users";
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query);
		ResultSet rs = DB.executeQueryReturnRS(conn, stmt);
		try {
			while (rs.next()) {
				User userRecord = new User(rs.getInt("User_ID"), rs.getString("Username"), rs.getString("Password"), rs.getString("Email"), rs.getBoolean("Validated"), rs.getBoolean("Active"));
				uList.add(userRecord);
			}
		} catch (SQLException se) {
			logger.error("SQL exception caught", se);
			DB.cleanup(conn, stmt);
		} catch (Exception e) {
			logger.error("Exception caught", e);
		} finally {
			DB.cleanup(conn, stmt);
		}
		return uList;
	}

	public static User getUser(int uid) {
		return getUserForKey("User_ID", uid);
	}

	public static User getUserForEmail(String email) {
		String query = "SELECT * FROM users WHERE Email=?;";
		DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(email)};
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
		return getUserFromDB(stmt, conn);
	}

	public static User getUserForName(String username) {
		String query = "SELECT * FROM users WHERE Username=?;";
		DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(username)};
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
		return getUserFromDB(stmt, conn);
	}

	public static User getUserFromPlayer(int playerId) {
		String query = "SELECT * FROM users AS u " + "LEFT JOIN players AS p ON p.User_ID=u.User_ID " + "WHERE p.ID=?;";
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(playerId));
		return getUserFromDB(stmt, conn);
	}

	public static User getUserForKey(String keyName, int id) {
		String query = "SELECT * FROM users WHERE " + keyName + "=?;";
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(id));
		return getUserFromDB(stmt, conn);
	}

	private static User getUserFromDB(PreparedStatement stmt, Connection conn) {
		try {
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				User userRecord = new User(rs.getInt("User_ID"), rs.getString("Username"), rs.getString("Password"), rs.getString("Email"), rs.getBoolean("Validated"), rs.getBoolean("Active"));
				return userRecord;
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

	public static DuelGame getGameForKey(String keyName, int id) {
		String query = "SELECT g.ID, g.Class_ID, g.Level, g.Creator_ID, g.State," + "g.CurrentRound, g.FinalRound, g.ActiveRole, g.Mode, g.Creator_ID,\n" + "IFNULL(att.User_ID,0) AS Attacker_ID, IFNULL(def.User_ID,0) AS Defender_ID\n" + "FROM games AS g\n" + "LEFT JOIN players AS att ON g.ID=att.Game_ID AND att.Role='ATTACKER' AND att.Active=TRUE\n" + "LEFT JOIN players AS def ON g.ID=def.Game_ID AND def.Role='DEFENDER' AND def.Active=TRUE\n" + "WHERE g." + keyName + "=?;\n";
		// Load the MultiplayerGame Data with the provided ID.
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(id));
		ResultSet rs = DB.executeQueryReturnRS(conn, stmt);
		try {
			if (rs.next()) {
				DuelGame gameRecord;
				if (rs.getString("Mode").equals(GameMode.SINGLE.name()))
					gameRecord = new SinglePlayerGame(rs.getInt("ID"), rs.getInt("Attacker_ID"), rs.getInt("Defender_ID"), rs.getInt("Class_ID"),
							rs.getInt("CurrentRound"), rs.getInt("FinalRound"), Role.valueOf(rs.getString("ActiveRole")), GameState.valueOf(rs.getString("State")),
							GameLevel.valueOf(rs.getString("Level")), GameMode.valueOf(rs.getString("Mode")));
				else
					gameRecord = new DuelGame(rs.getInt("ID"), rs.getInt("Attacker_ID"), rs.getInt("Defender_ID"), rs.getInt("Class_ID"),
							rs.getInt("CurrentRound"), rs.getInt("FinalRound"), Role.valueOf(rs.getString("ActiveRole")), GameState.valueOf(rs.getString("State")),
							GameLevel.valueOf(rs.getString("Level")), GameMode.valueOf(rs.getString("Mode")));
				return gameRecord;
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

	/**
	 * Returns list of <b>active</b> games for a user
	 *
	 * @param userId
	 * @return
	 */
	public static List<DuelGame> getGamesForUser(int userId) {
		String query = "SELECT g.ID, g.Class_ID, g.Level, g.Creator_ID, g.State, g.CurrentRound, g.FinalRound, g.ActiveRole, g.Mode, g.Creator_ID,\n" + "IFNULL(att.User_ID,0) AS Attacker_ID, IFNULL(def.User_ID,0) AS Defender_ID FROM games AS g LEFT JOIN players AS att ON g.ID=att.Game_ID  AND att.Role='ATTACKER' AND att.Active=TRUE\n" + "LEFT JOIN players AS def ON g.ID=def.Game_ID AND def.Role='DEFENDER' AND def.Active=TRUE WHERE g.Mode != 'PARTY' AND g.State!='FINISHED' AND (g.Creator_ID=? OR IFNULL(att.User_ID,0)=? OR IFNULL(def.User_ID,0)=?);";
		DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(userId),
				DB.getDBV(userId),
				DB.getDBV(userId)};
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
		return getGames(stmt, conn);
	}

	public static List<MultiplayerGame> getJoinedMultiplayerGamesForUser(int userId) {
		String query = "SELECT DISTINCT m.* FROM games AS m " + "LEFT JOIN players AS p ON p.Game_ID=m.ID \n" + "WHERE m.Mode = 'PARTY' AND (p.User_ID=?);";
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(userId));
		return getMultiplayerGames(stmt, conn);
	}

	public static List<MultiplayerGame> getMultiplayerGamesForUser(int userId) {
		String query = "SELECT DISTINCT m.* FROM games AS m LEFT JOIN players AS p ON p.Game_ID=m.ID  AND p.Active=TRUE" +
				" WHERE m.Mode = 'PARTY' AND (p.User_ID=? OR m.Creator_ID=?) AND m.State != 'FINISHED' AND m.IsSimulationGame = FALSE;";
		DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(userId),
				DB.getDBV(userId)};
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
		return getMultiplayerGames(stmt, conn);
	}

	public static List<MultiplayerGame> getFinishedMultiplayerGamesForUser(int userId) {
		String query = "SELECT DISTINCT m.* FROM games AS m " + "LEFT JOIN players AS p ON p.Game_ID=m.ID  AND p.Active=TRUE \n" + "WHERE (p.User_ID=? OR m.Creator_ID=?) AND m.State = 'FINISHED' AND m.Mode='PARTY';";
		DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(userId),
				DB.getDBV(userId)};
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
		return getMultiplayerGames(stmt, conn);
	}

	public static List<MultiplayerGame> getOpenMultiplayerGamesForUser(int userId) {
		String query = "SELECT * FROM games AS g\n"
				+ "INNER JOIN (SELECT gatt.ID, sum(CASE WHEN Role = 'ATTACKER' THEN 1 ELSE 0 END) nAttackers, sum(CASE WHEN Role = 'DEFENDER' THEN 1 ELSE 0 END) nDefenders\n"
				+ "              FROM games AS gatt LEFT JOIN players ON gatt.ID=players.Game_ID AND players.Active=TRUE GROUP BY gatt.ID) AS nplayers\n"
				+ "ON g.ID=nplayers.ID\n"
				+ "WHERE g.Mode='PARTY' AND g.Creator_ID!=? AND (g.State='CREATED' OR g.State='ACTIVE')\n"
				+ "AND (g.RequiresValidation=FALSE OR (? IN (SELECT User_ID FROM users WHERE Validated=TRUE)))\n"
				+ "AND g.ID NOT IN (SELECT g.ID FROM games g INNER JOIN players p ON g.ID=p.Game_ID WHERE p.User_ID=? AND p.Active=TRUE)\n"
				+ "AND (nplayers.nAttackers < g.Attackers_Limit OR nplayers.nDefenders < g.Defenders_Limit)\n"
				+ "AND g.IsSimulationGame = FALSE;";
		DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(userId),
				DB.getDBV(userId),
				DB.getDBV(userId)};
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
		return getMultiplayerGames(stmt, conn);
	}

	public static Role getRoleOfPlayer(int playerId) {
		Role role = Role.NONE;
		String query = "SELECT * FROM players WHERE ID = ?;";
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(playerId));

		try {
			ResultSet rs = DB.executeQueryReturnRS(conn, stmt);
			while (rs.next()) {
				try {
					role = Role.valueOf(rs.getString("Role"));
				} catch (NullPointerException | SQLException e) {
					logger.info("Failed to retrieve role for player {}.", playerId);
				}
			}
		} catch (SQLException e) {
			logger.error("SQLException while parsing result set for statement\n\t" + query, stmt);
		} finally {
			DB.cleanup(conn, stmt);
		}
		return role;
	}public static Role getRole(int userId, int gameId) {

		String query = String.join("\n","SELECT * ",
                "FROM games AS m " , "LEFT JOIN players AS p",
                " ON p.Game_ID = m.ID",
                " AND p.Active=TRUE " , "WHERE m.ID = ?",
                " AND (m.Creator_ID=?",
                " OR (p.User_ID=?",
                " AND p.Game_ID=?))");
		DatabaseValue[] values = new DatabaseValue[]{DatabaseValue.of(gameId),
				DatabaseValue.of(userId),
				DatabaseValue.of(userId),
				DatabaseValue.of(gameId)};
		 DB.RSMapper<Role> mapper = rs -> {
				if (rs.getInt("Creator_ID") == userId) {
					return Role.CREATOR;
				} else {
					return Role.valueOrNull(rs.getString("Role"));
					}
		} ;
			final Role role =DB.executeQueryReturnValue(query, mapper, values);
		return Optional.ofNullable(role).orElse(Role.NONE);
	}

    /**
     * Returns list of <b>finished</b> games for a user, which are not multiplayer games.
     */
    public static List<DuelGame> getHistoryForUser(int userId) {
        String query = String.join("\n",
                "SELECT g.*,",
                "  IFNULL(att.User_ID,0) AS Attacker_ID,",
                "  IFNULL(def.User_ID,0) AS Defender_ID",
                "FROM games AS g",
                "LEFT JOIN players AS att",
                "  ON g.ID=att.Game_ID",
                "  AND att.Role='ATTACKER'",
                "LEFT JOIN players AS def",
                "  ON g.ID=def.Game_ID",
                "  AND def.Role='DEFENDER'",
                "WHERE g.Mode != 'PARTY'",
                "  AND g.State='FINISHED'",
                "  AND (g.Creator_ID=?",
                "      OR IFNULL(att.User_ID,0)=?",
                "      OR IFNULL(def.User_ID,0)=?);");
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(userId),
                DatabaseValue.of(userId),
                DatabaseValue.of(userId)
        };
        return DB.executeQueryReturnList(query, DuelGameDAO::duelGameFromRS, values);
    }

	public static DuelGame getActiveUnitTestingSession(int userId) {
		String query = "SELECT * FROM games WHERE Defender_ID=? AND Mode='UTESTING' AND State='ACTIVE';";
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(userId));
		List<DuelGame> games = getGames(stmt, conn);
		if (games.isEmpty())
			return null;
		else
			return games.get(0);
	}

	public static List<DuelGame> getGames(PreparedStatement stmt, Connection conn) {
		List<DuelGame> gameList = new ArrayList<>();
		try {
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				gameList.add(new DuelGame(rs.getInt("ID"), rs.getInt("Attacker_ID"), rs.getInt("Defender_ID"),
						rs.getInt("Class_ID"), rs.getInt("CurrentRound"), rs.getInt("FinalRound"),
						Role.valueOf(rs.getString("ActiveRole")), GameState.valueOf(rs.getString("State")),
						GameLevel.valueOf(rs.getString("Level")), GameMode.valueOf(rs.getString("Mode"))));
			}
		} catch (SQLException se) {
			logger.error("SQL exception caught", se);
		} catch (Exception e) {
			logger.error("Exception caught", e);
		} finally {
			DB.cleanup(conn, stmt);
		}
		return gameList;
	}

	public static MultiplayerGame getMultiplayerGame(int id) {
		String query = "SELECT * FROM games AS m WHERE ID=? AND m.Mode='PARTY'";
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(id));
		List<MultiplayerGame> mgs = getMultiplayerGames(stmt, conn);
		if (mgs.size() > 0) {
			return mgs.get(0);
		}
		return null;
	}

	public static List<MultiplayerGame> getMultiplayerGames(PreparedStatement stmt, Connection conn) {
		List<MultiplayerGame> gameList = new ArrayList<>();
		try {
			ResultSet rs = stmt.executeQuery();
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
				mg.setOriginGameId(rs.getInt("SimulationOriginGame_ID"));
				if (rs.getString("AiStrat") != null) {
					mg.setAiStrat(AiPlayer.GenerationMethod.valueOf(rs.getString("AiStrat")));
				}
				gameList.add(mg);
			}
		} catch (SQLException se) {
			logger.error("SQL exception caught", se);
		} catch (Exception e) {
			logger.error("Exception caught", e);
		} finally {
			DB.cleanup(conn, stmt);
		}
		return gameList;
	}


	public static List<Mutant> getNonEquivalentMutantsForGameWithoutOnePlayer(int gameId, int playerId) {
		String query = "SELECT * " +
				"FROM mutants " +
				"WHERE Game_ID=? " +
				"AND Player_ID!=? " +
				"AND (mutants.Equivalent = 'ASSUMED_NO' OR mutants.Equivalent = 'PROVEN_NO') " +
				"AND ClassFile IS NOT NULL;";
		DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(gameId),
				DB.getDBV(playerId)};
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
		return getMutants(stmt, conn);
	}

	public static List<Mutant> getMutantsForGame(int gid) {
		String query = String.join("\n",
				"SELECT * FROM mutants ",
				"LEFT JOIN players ON players.ID=mutants.Player_ID ",
				"LEFT JOIN users ON players.User_ID = users.User_ID ",
				"WHERE mutants.Game_ID=? AND mutants.ClassFile IS NOT NULL ",
				"ORDER BY Timestamp;");
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(gid));
		return getMutants(stmt, conn);
	}

	public static List<Mutant> getMutantsForGameWithoutEquivalents(int gid) {
		String query = String.join("\n",
				"SELECT * FROM mutants ",
				"LEFT JOIN players ON players.ID=mutants.Player_ID ",
				"LEFT JOIN users ON players.User_ID = users.User_ID ",
				"WHERE mutants.Game_ID=? AND mutants.ClassFile IS NOT NULL AND (mutants.Equivalent = 'ASSUMED_NO' OR mutants.Equivalent = 'PROVEN_NO') ",
				"ORDER BY Timestamp;");
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(gid));
		return getMutants(stmt, conn);
	}

	public static List<Mutant> getMutantsForPlayer(int pid) {
		String query = String.join("\n",
				"SELECT * FROM mutants ",
				"LEFT JOIN players ON players.ID=mutants.Player_ID ",
				"LEFT JOIN users ON players.User_ID = users.User_ID ",
				"WHERE mutants.Player_ID=? AND mutants.ClassFile IS NOT NULL;");
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(pid));
		return getMutants(stmt, conn);
	}

	public static List<Mutant> getMutantsForClass(int classId) {
		String query = String.join("\n",
				"SELECT mutants.*",
				"FROM mutants, games",
				"LEFT JOIN players ON players.ID=mutants.Player_ID ",
				"LEFT JOIN users ON players.User_ID = users.User_ID ",
				"WHERE mutants.Game_ID = games.ID",
				"  AND games.Class_ID = ?",
				"  AND mutants.ClassFile IS NOT NULL;"
		);
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(classId));
		return getMutants(stmt, conn);
	}

	public static Mutant getMutantFromDB(PreparedStatement stmt, Connection conn) {
		Mutant newMutant = null;
		try {
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				newMutant = new Mutant(rs.getInt("Mutant_ID"), rs.getInt("Game_ID"),
						rs.getString("JavaFile"), rs.getString("ClassFile"),
						rs.getBoolean("Alive"), Mutant.Equivalence.valueOf(rs.getString("Equivalent")),
						rs.getInt("RoundCreated"), rs.getInt("RoundKilled"), rs.getInt("Player_ID"));
			}
		} catch (SQLException se) {
			logger.error("SQL exception caught", se);
		} catch (Exception e) {
			logger.error("Exception caught", e);
		} finally {
			DB.cleanup(conn, stmt);
		}
		return newMutant;
	}

	public static Mutant getMutant(DuelGame game, int mutantID) {
		String query = String.join("\n",
				"SELECT * FROM mutants ",
				"LEFT JOIN players ON players.ID=mutants.Player_ID ",
				"LEFT JOIN users ON players.User_ID = users.User_ID ",
				"WHERE mutants.Mutant_ID=? AND mutants.Game_ID=?;");
		DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(mutantID),
				DB.getDBV(game.getId())};
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
		return getMutantFromDB(stmt, conn);
	}

	public static Mutant getMutant(int gameId, String md5) {
		String query = String.join("\n",
				"SELECT * FROM mutants ",
				"LEFT JOIN players ON players.ID=mutants.Player_ID ",
				"LEFT JOIN users ON players.User_ID = users.User_ID ",
				"WHERE mutants.Game_ID=? AND mutants.MD5=?;");
		DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(gameId),
				DB.getDBV(md5)};
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
		return getMutantFromDB(stmt, conn);
	}

	public static List<Integer> getUsedAiTestsForGame(AbstractGame g) {
		List<Integer> testList = new ArrayList<>();

		String query = "SELECT * FROM usedaitests WHERE Game_ID=?;";
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(g.getId()));
		try {
			ResultSet rs = DB.executeQueryReturnRS(conn, stmt);
			while (rs.next()) {
				testList.add(rs.getInt("Value"));
			}
		} catch (SQLException e) {
			logger.error("SQLException while parsing result set for statement\n\t" + query, stmt);
		} finally {
			DB.cleanup(conn, stmt);
		}
		return testList;
	}

    public static void increasePlayerPoints(int points, int player) {
        String query = "UPDATE players SET Points=Points+? WHERE ID=?";
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(points),
                DatabaseValue.of(player)
        };
        DB.executeUpdateQuery(query, values);
    }

    public static int getEquivalentDefenderId(Mutant m) {
        String query = "SELECT * FROM equivalences WHERE Mutant_ID=?;";
        final Integer id = DB.executeQueryReturnValue(query, rs -> rs.getInt("Defender_ID"), DatabaseValue.of(m.getId()));
        return Optional.ofNullable(id).orElse(-1);
    }

    public static int getPlayerPoints(int playerId) {
        String query = "SELECT Points FROM players WHERE ID=?;";
        final Integer points = DB.executeQueryReturnValue(query, rs -> rs.getInt("Points"), DatabaseValue.of(playerId));
        return Optional.ofNullable(points).orElse(0);
    }

    public static boolean insertEquivalence(Mutant mutant, int defender) {
        String query = String.join("\n",
                "INSERT INTO equivalences (Mutant_ID, Defender_ID, Mutant_Points)",
                "VALUES (?, ?, ?)"
        );
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(mutant.getId()),
                DatabaseValue.of(defender),
                DatabaseValue.of(mutant.getScore())
        };
        return DB.executeUpdateQuery(query, values);
    }

    public static boolean setAiTestAsUsed(int testNumber, AbstractGame g) {
        String query = "INSERT INTO usedaitests (Value, Game_ID) VALUES (?, ?);";
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(testNumber),
                DatabaseValue.of(g.getId())
        };
        return DB.executeUpdateQueryGetKeys(query, values) > -1;
    }

    public static List<Integer> getUsedAiMutantsForGame(AbstractGame g) {
        String query = "SELECT * FROM usedaimutants WHERE Game_ID=?;";
        return DB.executeQueryReturnList(query, rs -> rs.getInt("Value"), DatabaseValue.of(g.getId()));
    }

    /**
     * @param mutantNumber the number of the mutant
     * @param g            the game the mutant belongs to
     * @return
     */
    public static boolean setAiMutantAsUsed(int mutantNumber, DuelGame g) {
        String query = "INSERT INTO usedaimutants (Value, Game_ID) VALUES (?, ?);";
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(mutantNumber),
                DatabaseValue.of(g.getId())
        };
        return DB.executeUpdateQueryGetKeys(query, values) > -1;
    }

    public static List<Test> getTestsForGame(int gid) {
        String query = "SELECT * FROM tests WHERE Game_ID=? AND ClassFile IS NOT NULL ORDER BY Timestamp;";
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(gid));
        return getTests(stmt, conn);
    }

    public static List<Test> getTestsForGameWithoutOnePlayer(int gameId, int playerId) {
        String query = "SELECT tests.* FROM tests "
                + "	WHERE tests.Game_ID=? AND tests.ClassFile IS NOT NULL AND Player_ID!=?"
                + "  AND EXISTS ("
                + "    SELECT * FROM targetexecutions ex"
                + "    WHERE ex.Test_ID = tests.Test_ID"
                + "      AND ex.Target='TEST_ORIGINAL'"
                + "      AND ex.Status='SUCCESS'"
                + "  );";
        DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(gameId),
                DB.getDBV(playerId)};
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
        return getTests(stmt, conn);
    }

    public static Test getTestForId(int tid) {
        String query = "SELECT * FROM tests WHERE Test_ID=?;";
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(tid));
        return getTests(stmt, conn).get(0);
    }

    /**
     * Get games where a certain class is under test.
     * @param gid
     * @return
     */
    public static List<MultiplayerGame> getGamesForClass(int cid) {
        String query = "SELECT * FROM games WHERE Class_ID = ? AND Mode = 'PARTY';";
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(cid));
        return getMultiplayerGames(stmt, conn);
    }

    // TODO Phil 27/12/18: this isn't limited to multiplayer games
    public static int getPlayerIdForMultiplayerGame(int userId, int gameId) {
        String query = String.join("\n",
                "SELECT players.ID",
                "FROM players",
                "WHERE User_ID = ?",
                "  AND Game_ID = ?");
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(userId),
                DatabaseValue.of(gameId)
        };
        final Integer id = DB.executeQueryReturnValue(query, rs -> rs.getInt("ID"), values);
        return Optional.ofNullable(id).orElse(-1);
    }

	/**
	 * @param gameId
	 * @param defendersOnly
	 * @return Tests submitted by defenders which compiled and passed on CUT
	 */
	public static List<Test> getExecutableTests(int gameId, boolean defendersOnly) {
		String query = String.join("\n",
				"SELECT tests.* FROM tests",
				 (defendersOnly ? "INNER JOIN players pl on tests.Player_ID = pl.ID" : ""),
				 "WHERE tests.Game_ID=? AND tests.ClassFile IS NOT NULL",
				 (defendersOnly ? "AND pl.Role='DEFENDER'" : ""),
				 "  AND EXISTS (",
				 "    SELECT * FROM targetexecutions ex",
				 "    WHERE ex.Test_ID = tests.Test_ID",
				 "      AND ex.Target='TEST_ORIGINAL'",
				 "      AND ex.Status='SUCCESS'",
				 "  );"
		);
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(gameId));
		return getTests(stmt, conn);
	}

	public static List<Mutant> getExecutableMutants(int gameId) {
		String query = String.join("\n",
				"SELECT mutants.* FROM mutants",
				"WHERE mutants.Game_ID=? AND mutants.ClassFile IS NOT NULL",
				"	AND EXISTS (",
				"	SELECT * FROM targetexecutions ex",
				"	WHERE ex.Mutant_ID = mutants.Mutant_ID",
				"		AND ex.Target='COMPILE_MUTANT'",
				"		AND ex.Status='SUCCESS'",
				"		AND mutants.Equivalent='ASSUMED_NO'",
				"	);"
		);
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(gameId));
		return getMutants(stmt, conn);
	}

	public static List<Test> getExecutableTestsForClass(int classId) {
		String query = String.join("\n",
				"SELECT tests.*",
				"FROM tests, games",
				"WHERE tests.Game_ID = games.ID",
				"  AND games.Class_ID = ?",
				"  AND tests.ClassFile IS NOT NULL",
				"  AND EXISTS (",
				"    SELECT * FROM targetexecutions ex",
				"    WHERE ex.Test_ID = tests.Test_ID",
				"      AND ex.Target='TEST_ORIGINAL'",
				"      AND ex.Status='SUCCESS'",
				"  );"
		);
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, DB.getDBV(classId));
		return getTests(stmt, conn);
	}

	public static int getPlayerIdForMultiplayerGame(int userId, int gameId) {
		String query = "SELECT * FROM players AS p " + "WHERE p.User_ID = ? AND p.Game_ID = ?";
		DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(userId),
				DB.getDBV(gameId)};
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
		return getInt(stmt, "ID", conn);
	}

	/**
	 * We need to check if an Ai-Defender/Attacker has joined a game, therfore we need to
	 * check inactive and active players since the Ai-Player can be paused. This is used to
	 * activate and pause him again if he is in a certain game.
	 * @param gameId the game we want to check
	 * @param role Attacker/Defender Ai-Player
	 * @return the array of playerIds found for a game
	 */
	public static int[] getInactiveAndActivePlayersForMultiplayerGame(int gameId, Role role) {
		int[] players = new int[0];
		String query = "SELECT * FROM players WHERE Game_ID = ? AND Role=?;";
		DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(gameId),
				DB.getDBV(role.toString())};
		// Load the MultiplayerGame Data with the provided ID.
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
		try {
			ResultSet rs = DB.executeQueryReturnRS(conn, stmt);
			List<Integer> atks = new ArrayList<>();
			while (rs.next()) {
				atks.add(rs.getInt("ID"));
				players = new int[atks.size()];
				for (int i = 0; i < atks.size(); i++) {
					players[i] = atks.get(i);
				}
			}
		} catch (SQLException e) {
			logger.error("SQLException while parsing result set for statement\n\t" + query, stmt);
		} finally {
			DB.cleanup(conn, stmt);
		}
		return players;
	}

	public static int[] getUserIdsForMultiplayerGame(int gameId, Role role) {
		int[] players = new int[0];
		String query = "SELECT * FROM players WHERE Game_ID = ? AND Role=? AND Active=TRUE;";
		DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(gameId),
				DB.getDBV(role.toString())};
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
		try {
			ResultSet rs = DB.executeQueryReturnRS(conn, stmt);
			List<Integer> atks = new ArrayList<>();
			while (rs.next()) {
				atks.add(rs.getInt("User_ID"));
				players = new int[atks.size()];
				for (int i = 0; i < atks.size(); i++) {
					players[i] = atks.get(i);
				}
			}
		} catch (SQLException e) {
			logger.error("SQLException while parsing result set for statement\n\t" + query, stmt);
		} finally {
			DB.cleanup(conn, stmt);
		}
		return players;
	}

	public static int[] getPlayersForMultiplayerGame(int gameId, Role role) {
		int[] players = new int[0];
		String query = "SELECT * FROM players WHERE Game_ID = ? AND Role=? AND Active=TRUE ORDER BY ID ASC;";
		DatabaseValue[] valueList = new DatabaseValue[]{DB.getDBV(gameId),
				DB.getDBV(role.toString())};
		// Load the MultiplayerGame Data with the provided ID.
		Connection conn = DB.getConnection();
		PreparedStatement stmt = DB.createPreparedStatement(conn, query, valueList);
		try {
			ResultSet rs = DB.executeQueryReturnRS(conn, stmt);
			List<Integer> atks = new ArrayList<>();
			while (rs.next()) {
				atks.add(rs.getInt("ID"));
				players = new int[atks.size()];
				for (int i = 0; i < atks.size(); i++) {
					players[i] = atks.get(i);
				}
			}
		} catch (SQLException e) {
			logger.error("SQLException while parsing result set for statement\n\t" + query, stmt);
		} finally {
			DB.cleanup(conn, stmt);
		}
		return players;
	}

 	static List<Test> getTests(PreparedStatement stmt, Connection conn) {
		List<Test> testList = new ArrayList<>();
		try {
			// Load the MultiplayerGame Data with the provided ID.
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				String linesCovered = rs.getString("Lines_Covered");
				String linesUncovered = rs.getString("Lines_Uncovered");
				List<Integer> covered = new ArrayList<>();
				List<Integer> uncovered = new ArrayList<>();
				if (linesCovered != null && !linesCovered.isEmpty()) {
					covered.addAll(Arrays.stream(linesCovered.split(",")).map(Integer::parseInt).collect(Collectors.toList()));
				}
				if (linesUncovered != null && !linesUncovered.isEmpty()) {
					uncovered.addAll(Arrays.stream(linesUncovered.split(",")).map(Integer::parseInt).collect(Collectors.toList()));
				}
				Test newTest = new Test(rs.getInt("Test_ID"), rs.getInt("Game_ID"),
						rs.getString("JavaFile"), rs.getString("ClassFile"),
						rs.getInt("RoundCreated"), rs.getInt("MutantsKilled"), rs.getInt("Player_ID"),
						covered, uncovered, rs.getInt("Points"));
				newTest.setScore(rs.getInt("Points"));
				newTest.setTimestamp(rs.getTimestamp("Timestamp"));
				testList.add(newTest);
			}
		} catch (SQLException se) {
			logger.error("SQL exception caught", se);
		} catch (Exception e) {
			logger.error("Exception caught", e);
		} finally {
			DB.cleanup(conn, stmt);
		}
		return testList;
	}

	private static List<Mutant> getMutants(PreparedStatement stmt, Connection conn) {
		List<Mutant> mutantsList = new ArrayList<>();
		try {
			// Load the MultiplayerGame Data with the provided ID.
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				Mutant newMutant = new Mutant(rs.getInt("Mutant_ID"), rs.getInt("Game_ID"),
						rs.getString("JavaFile"), rs.getString("ClassFile"),
						rs.getBoolean("Alive"), Mutant.Equivalence.valueOf(rs.getString("Equivalent")),
						rs.getInt("RoundCreated"), rs.getInt("RoundKilled"), rs.getInt("Player_ID"));
				newMutant.setScore(rs.getInt("Points"));
				newMutant.setTimestamp(rs.getTimestamp("Timestamp"));

				try {
					String username = rs.getString("users.Username");
					int userId = rs.getInt("users.User_ID");

    static Entry entryFromRS(ResultSet rs) throws SQLException {
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

    public static List<Entry> getLeaderboard() {
        String query = String.join("\n",
                "SELECT U.username AS username, IFNULL(NMutants,0) AS NMutants, IFNULL(AScore,0) AS AScore, IFNULL(NTests,0) AS NTests, IFNULL(DScore,0) AS DScore, IFNULL(NKilled,0) AS NKilled, IFNULL(AScore,0)+IFNULL(DScore,0) AS TotalScore",
                "FROM view_valid_users U",
                "LEFT JOIN (SELECT PA.user_id, count(M.Mutant_ID) AS NMutants, sum(M.Points) AS AScore FROM players PA LEFT JOIN mutants M ON PA.id = M.Player_ID GROUP BY PA.user_id) AS Attacker ON U.user_id = Attacker.user_id",
                "LEFT JOIN (SELECT PD.user_id, count(T.Test_ID) AS NTests, sum(T.Points) AS DScore, sum(T.MutantsKilled) AS NKilled FROM players PD LEFT JOIN tests T ON PD.id = T.Player_ID GROUP BY PD.user_id) AS Defender ON U.user_id = Defender.user_id");
        return DB.executeQueryReturnList(query, DatabaseAccess::entryFromRS);
    }

    public static int getKillingTestIdForMutant(int mutantId) {
        String query = String.join("\n",
                "SELECT *",
                "FROM targetexecutions",
                "WHERE Target = ?",
                "  AND Status != ?",
                "  AND Mutant_ID = ?;"
        );
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(TargetExecution.Target.TEST_MUTANT.name()),
                DatabaseValue.of(TargetExecution.Status.SUCCESS.name()),
                DatabaseValue.of(mutantId)
        };
        TargetExecution targ = DB.executeQueryReturnValue(query, TargetExecutionDAO::targetExecutionFromRS, values);
        // TODO: We shouldn't give away that we don't know which test killed the mutant?
        return Optional.ofNullable(targ).map(t -> t.testId).orElse(-1);
    }

    public static Set<Mutant> getKilledMutantsForTestId(int testId) {
        String query = String.join("\n",
                "SELECT DISTINCT m.*",
                "FROM targetexecutions te, mutants m",
                "WHERE te.Target = ?",
                "  AND te.Status != ?",
                "  AND te.Test_ID = ?",
                "  AND te.Mutant_ID = m.Mutant_ID",
                "ORDER BY m.Mutant_ID ASC");
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(TargetExecution.Target.TEST_MUTANT.name()),
                DatabaseValue.of(TargetExecution.Status.SUCCESS.name()),
                DatabaseValue.of(testId)
        };
        final List<Mutant> mutants = DB.executeQueryReturnList(query, MutantDAO::mutantFromRS, values);
        return new HashSet<>(mutants);
    }

    /**
     * This also automatically update the Timestamp field using CURRENT_TIMESTAMP()
     */
    public static void logSession(int uid, String ipAddress) {
        String query = "INSERT INTO sessions (User_ID, IP_Address) VALUES (?, ?);";
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(uid),
                DatabaseValue.of(ipAddress)
        };
        DB.executeUpdateQuery(query, values);
    }

    public static int getLastCompletedSubmissionForUserInGame(int userId, int gameId, boolean isDefender) {
        String query = isDefender ? "SELECT MAX(test_id) FROM tests" : "SELECT MAX(mutant_id) FROM mutants";
        query += " WHERE game_id=? AND player_id = (SELECT id FROM players WHERE game_id=? AND user_id=?);";
        DatabaseValue[] valueList = new DatabaseValue[]{
                DatabaseValue.of(gameId),
                DatabaseValue.of(gameId),
                DatabaseValue.of(userId)
        };

        final Integer result = DB.executeQueryReturnValue(query, rs -> rs.getInt(1), valueList);
        return Optional.ofNullable(result).orElse(-1);
    }

    public static TargetExecution.Target getStatusOfRequestForUserInGame(int userId, int gameId, int lastSubmissionId, boolean isDefender) {
        // Current test is the one right after lastTestId in the user/game context
        String query = isDefender ?
                "SELECT * FROM targetexecutions WHERE Test_ID > ? AND Test_ID in (SELECT Test_ID FROM tests" :
                "SELECT * FROM targetexecutions WHERE Mutant_ID > ? AND Mutant_ID in (SELECT Mutant_ID FROM mutants";
        query += " WHERE game_id=? AND player_id = (SELECT id from players where game_id=? and user_id=?))"
                + "AND TargetExecution_ID >= (SELECT MAX(TargetExecution_ID) from targetexecutions);";

        DatabaseValue[] valueList = new DatabaseValue[]{
                DatabaseValue.of(lastSubmissionId),
                DatabaseValue.of(gameId),
                DatabaseValue.of(gameId),
                DatabaseValue.of(userId)
        };
        TargetExecution t = DB.executeQueryReturnValue(query, TargetExecutionDAO::targetExecutionFromRS, valueList);
        return Optional.ofNullable(t).map(te -> te.target).orElse(null);
    }

    public static boolean setPasswordResetSecret(int userId, String pwResetSecret) {
        String query = String.join("\n",
                "UPDATE users",
                "SET pw_reset_secret = ?,",
                "    pw_reset_timestamp = CURRENT_TIMESTAMP",
                "WHERE User_ID = ?;");
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(pwResetSecret),
                DatabaseValue.of(userId)
        };
        return DB.executeUpdateQuery(query, values);
    }

    public static int getUserIDForPWResetSecret(String pwResetSecret) {
        String query = "SELECT User_ID\n" +
                "FROM users\n" +
                "WHERE TIMESTAMPDIFF(HOUR, pw_reset_timestamp, CURRENT_TIMESTAMP) < (SELECT INT_VALUE\n" +
                "                                                                    FROM settings\n" +
                "                                                                    WHERE name =\n" +
                "                                                                    'PASSWORD_RESET_SECRET_LIFESPAN')\n" +
                "      AND\n" +
                "      pw_reset_secret = ?;";

        final Integer userId = DB.executeQueryReturnValue(query, rs -> rs.getInt("User_ID"), DatabaseValue.of(pwResetSecret));
        return Optional.ofNullable(userId).orElse(-1);
    }
}
