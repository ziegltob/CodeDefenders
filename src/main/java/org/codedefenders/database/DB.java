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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DB {
    private static ConnectionPool connPool = ConnectionPool.instance();
    private static final Logger logger = LoggerFactory.getLogger(DB.class);

    public synchronized static Connection getConnection() {
        return connPool.getDBConnection();
    }

    public static void cleanup(Connection conn, PreparedStatement stmt) {
        try {
            if (stmt != null) {
                stmt.close();
            }
        } catch (SQLException se) {
            logger.error("SQL exception while closing statement!", se);
        }
        connPool.releaseDBConnection(conn);
    }

    public static PreparedStatement createPreparedStatement(Connection conn, String query, DatabaseValue... values) {
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            int count = 1;
            for (DatabaseValue value : values) {
                final DatabaseValue.Type type = value.getType();
                switch (type) {
                    case NULL:
                        stmt.setNull(count++, type.typeValue);
                        break;
                    case BOOLEAN:
                    case INT:
                    case STRING:
                    case LONG:
                    case FLOAT:
                    case TIMESTAMP:
                        stmt.setObject(count++, value.getValue(), type.typeValue);
                        break;
                    default:
                        final IllegalArgumentException e =
                                new IllegalArgumentException("Unknown database value type: " + type);
                        logger.error("Failed to create prepared statement due to unknown database value type.", e);
                        throw e;
                }
            }
        } catch (SQLException se) {
            logger.error("SQLException while creating prepared statement. Query was:\n\t" + query, se);
            DB.cleanup(conn, stmt);
            throw new UncheckedSQLException(se);
        }
        return stmt;
    }

    public static boolean executeUpdate(PreparedStatement stmt, Connection conn) {
        try {
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("SQLException while executing Update for statement\n\t" + stmt, e);
        } finally {
            DB.cleanup(conn, stmt);
        }
        return false;
    }

    static boolean executeUpdateQuery(String query, DatabaseValue... params) {
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, query, params);

        return executeUpdate(stmt, conn);
    }

    public static int executeUpdateGetKeys(PreparedStatement stmt, Connection conn) {
        try {
            if (stmt.executeUpdate() > 0) {
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("SQLException while executing Update and getting generated Keys for statement\n\t" + stmt, e);
        } finally {
            DB.cleanup(conn, stmt);
        }
        return -1;
    }

    static int executeUpdateQueryGetKeys(String query, DatabaseValue... params) {
        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, query, params);

        return executeUpdateGetKeys(stmt, conn);
    }

    /**
     * Provides a way to extract the query result from a {@link ResultSet} entry.
     * The implementation must not advance the {@link ResultSet}. It can return {@code null} to skip an entry.
     * If it throws any {@link Exception}, the query will fail with a {@link SQLMappingException}.
     * If something is wrong with the query result, and the result can not properly be extracted from it,
     * the implementation should throw a {@link SQLMappingException}.
     *
     * @param <T> The class to convert {@link ResultSet} entries to.
     */
    @FunctionalInterface
    interface RSMapper<T> {
        T extractResultFrom(ResultSet rs) throws SQLException, Exception;
    }

    /**
     * Executes a database query, then uses a mapper function to extract the first value from the query result.
     * Cleans up the database connection and statement afterwards.
     *
     * @param query  The query.
     * @param mapper The mapper function.
     * @param params The parameters for the query.
     * @param <T>    The type of value to be queried.
     * @return The first result of the query, or {@code null} if the query had no result.
     * @throws UncheckedSQLException If a {@link SQLException} is thrown while executing the query
     *                               or advancing the {@link ResultSet}.
     * @throws SQLMappingException   If there is something wrong with the query result, and the result can not properly be
     *                               extracted from it.
     * @see RSMapper
     */
    static <T> T executeQueryReturnValue(String query, RSMapper<T> mapper, DatabaseValue... params)
            throws UncheckedSQLException, SQLMappingException {

        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, query, params);

        return executeQueryReturnValue(conn, stmt, mapper);
    }

    /**
     * Executes a database query, then uses a mapper function to extract the first value from the query result.
     * Cleans up the database connection and statement afterwards.
     *
     * @param conn   The database connection.
     * @param stmt   THe prepared database statement.
     * @param mapper The mapper function.
     * @param <T>    The type of value to be queried.
     * @return The first result of the query, or {@code null} if the query had no result.
     * @throws UncheckedSQLException If a {@link SQLException} is thrown while executing the query
     *                               or advancing the {@link ResultSet}.
     * @throws SQLMappingException   If there is something wrong with the query result, and the result can not properly be
     *                               extracted from it.
     * @see RSMapper
     */
    private static <T> T executeQueryReturnValue(Connection conn, PreparedStatement stmt, RSMapper<T> mapper)
            throws UncheckedSQLException, SQLMappingException {

        try {
            ResultSet resultSet = stmt.executeQuery();

            if (resultSet.next()) {
                try {
                    return mapper.extractResultFrom(resultSet);
                } catch (Exception e) {
                    logger.error("Exception while handling result set.", e);
                    throw new SQLMappingException("Exception while handling result set.", e);
                }
            }

            return null;

        } catch (SQLException e) {
            logger.error("SQL exception while executing query.", e);
            throw new UncheckedSQLException("SQL exception while executing query.", e);

        } finally {
            DB.cleanup(conn, stmt);
        }
    }

    /**
     * Executes a database query, then uses a mapper function to extract the values from the query result.
     * Cleans up the database connection and statement afterwards.
     *
     * @param query  The query.
     * @param mapper The mapper function.
     * @param params The parameters for the query.
     * @param <T>    The type of value to be queried.
     * @return The results of the query, as a {@link List}. Will never be null.
     * @throws UncheckedSQLException If a {@link SQLException} is thrown while executing the query
     *                               or advancing the {@link ResultSet}.
     * @throws SQLMappingException   If there is something wrong with the query result, and the result can not properly be
     *                               extracted from it.
     * @see RSMapper
     */
    static <T> List<T> executeQueryReturnList(String query, RSMapper<T> mapper, DatabaseValue... params)
            throws UncheckedSQLException, SQLMappingException {

        Connection conn = DB.getConnection();
        PreparedStatement stmt = DB.createPreparedStatement(conn, query, params);

        return executeQueryReturnList(conn, stmt, mapper);
    }

    /**
     * Executes a database query, then uses a mapper function to extract the values from the query result.
     * Cleans up the database connection and statement afterwards.
     *
     * @param conn   The database connection.
     * @param stmt   THe prepared database statement.
     * @param mapper The mapper function.
     * @param <T>    The type of value to be queried.
     * @return The results of the query, as a {@link List}. Will never be null.
     * @throws UncheckedSQLException If a {@link SQLException} is thrown while executing the query
     *                               or advancing the {@link ResultSet}.
     * @throws SQLMappingException   If there is something wrong with the query result, and the result can not properly be
     *                               extracted from it.
     * @see RSMapper
     */
    // TODO Phil 28/12/18: would love to have this private so everyone uses {@link #executeQueryReturnList(String, RSMapper, DatabaseValue...}
    static <T> List<T> executeQueryReturnList(Connection conn, PreparedStatement stmt, RSMapper<T> mapper)
            throws UncheckedSQLException, SQLMappingException {

        try {
            ResultSet resultSet = stmt.executeQuery();
            List<T> values = new ArrayList<>(resultSet.getFetchSize());

            while (resultSet.next()) {
                T value;

                try {
                    value = mapper.extractResultFrom(resultSet);
                } catch (Exception e) {
                    logger.error("Exception while handling result set.", e);
                    throw new SQLMappingException("Exception while handling result set.", e);
                }

                if (value != null) {
                    values.add(value);
                }
            }

            return values;

        } catch (SQLException e) {
            logger.error("SQL exception while executing query.", e);
            throw new UncheckedSQLException("SQL exception while executing query.", e);

        } finally {
            DB.cleanup(conn, stmt);
        }
    }

}
