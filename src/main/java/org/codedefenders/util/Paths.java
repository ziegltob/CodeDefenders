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
package org.codedefenders.util;

/**
 * This class contains URL path constants.
 * <p>
 * If one path has to be adjusted, it has to be adjusted in the {@code web.xml}
 * servlet mapping configuration, too.
 */
public class Paths {

    private Paths() {
    }

    // URL Paths
    public static final String LOGIN = "/login";
    public static final String LOGOUT = "/logout";
    public static final String HELP_PAGE = "/help";
    public static final String ABOUT_PAGE = "/about";
    public static final String CONTACT_PAGE = "/contact";
    public static final String LEADERBOARD_PAGE = "/leaderboard";
    public static final String AI_PREPARER = "/ai_preparer";
    public static final String UTESTING_PATH = "/utesting";

    public static final String GAMES_OVERVIEW = "/games/overview";
    public static final String GAMES_HISTORY = "/games/history";
    public static final String CLASS_UPLOAD = "/class-upload";

    public static final String DUEL_GAME = "/duelgame";
    public static final String DUEL_SELECTION = "/duel/games";
    public static final String DUEL_CREATE = "/duel/create";

    public static final String BATTLEGROUND_GAME = "/multiplayergame";
    public static final String BATTLEGROUND_HISTORY = "/multiplayer/history";
    public static final String BATTLEGROUND_SELECTION = "/multiplayer/games";
    public static final String BATTLEGROUND_CREATE = "/multiplayer/create";

    public static final String PUZZLE_OVERVIEW = "/puzzles";
    public static final String PUZZLE_GAME = "/puzzlegame";

    public static final String ADMIN_PAGE = "/admin";
    public static final String ADMIN_GAMES = "/admin/games";
    public static final String ADMIN_MONITOR = "/admin/monitor";
    public static final String ADMIN_PUZZLES = "/admin/puzzles";
    public static final String ADMIN_USERS = "/admin/users";
    public static final String ADMIN_SETTINGS = "/admin/settings";
    public static final String ADMIN_KILLMAPS = "/admin/killmaps";

    public static final String ADMIN_ANALYTICS_USERS = "/admin/analytics/users";
    public static final String ADMIN_ANALYTICS_CLASSES = "/admin/analytics/classes";
    public static final String ADMIN_ANALYTICS_KILLMAPS = "/admin/analytics/killmaps";

    public static final String API_NOTIFICATION = "/api/notifications";
    public static final String API_MESSAGES = "/api/messages"; // path used in messaging.js
    public static final String API_MUTANTS = "/api/game_mutants";
    public static final String API_FEEDBACK = "/api/feedback";
    public static final String API_SEND_EMAIL = "/api/sendmail";
    public static final String API_ANALYTICS_USERS = "/admin/api/users";
    public static final String API_ANALYTICS_CLASSES = "/admin/api/classes";
    public static final String API_ANALYTICS_KILLMAP = "/admin/api/killmap";
}
