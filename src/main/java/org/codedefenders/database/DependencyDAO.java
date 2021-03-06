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

import org.codedefenders.database.DB.RSMapper;
import org.codedefenders.model.Dependency;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * This class handles the database logic for dependencies.
 *
 * @author <a href="https://github.com/werli">Phil Werli<a/>
 * @see Dependency
 */
public class DependencyDAO {

    /**
     * Constructs a dependency from a {@link ResultSet} entry.
     * @param rs The {@link ResultSet}.
     * @return The constructed dependency.
     * @see RSMapper
     */
    static Dependency dependencyFromRS(ResultSet rs, int classId) throws SQLException {
        final int id = rs.getInt("Dependency_ID");
        final String javaFile = rs.getString("JavaFile");
        final String classFile = rs.getString("ClassFile");
        return new Dependency(id, classId, javaFile, classFile);
    }

    /**
     * Stores a given {@link Dependency} in the database.
     *
     * @param dependency the given dependency as a {@link Dependency}.
     * @return the generated identifier of the dependency as an {@code int}.
     * @throws Exception If storing the dependency was not successful.
     */
    public static int storeDependency(Dependency dependency) throws Exception {
        int classId = dependency.getClassId();
        String javaFile = DatabaseAccess.addSlashes(dependency.getJavaFile());
        String classFile = DatabaseAccess.addSlashes(dependency.getClassFile());

        String query = "INSERT INTO dependencies (Class_ID, JavaFile, ClassFile) VALUES (?, ?, ?);";
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(classId),
                DatabaseValue.of(javaFile),
                DatabaseValue.of(classFile)
        };

        final int result = DB.executeUpdateQueryGetKeys(query, values);
        if (result != -1) {
            return result;
        } else {
            throw new Exception("Could not store dependency to database.");
        }
    }

    /**
     * Removes a dependency for a given identifier.
     *
     * @param id the identifier of the dependency to be removed.
     * @return {@code true} for successful removal, {@code false} otherwise.
     */
    public static boolean removeDependencyForId(Integer id) {
        String query = "DELETE FROM dependencies WHERE Dependency_ID = ?;";
        DatabaseValue[] values = new DatabaseValue[]{
                DatabaseValue.of(id),
        };

        return DB.executeUpdateQuery(query, values);
    }

    /**
     * Removes multiple dependencies for a given list of identifiers.
     *
     * @param dependencies the identifiers of the dependencies to be removed.
     * @return {@code true} for successful removal, {@code false} otherwise.
     */
    public static boolean removeDependenciesForIds(List<Integer> dependencies) {
        if (dependencies.isEmpty()) {
            return false;
        }

        final StringBuilder bob = new StringBuilder("(");
        for (int i = 0; i < dependencies.size() - 1; i++) {
            bob.append("?,");
        }
        bob.append("?);");

        final String range = bob.toString();
        String query = "DELETE FROM dependencies WHERE Dependency_ID in " + range;

        DatabaseValue[] values = dependencies.stream().map(DatabaseValue::of).toArray(DatabaseValue[]::new);

        return DB.executeUpdateQuery(query, values);
    }
}
