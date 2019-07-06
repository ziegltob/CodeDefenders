package org.codedefenders.game.singleplayer;

import org.codedefenders.database.AdminDAO;
import org.codedefenders.game.Role;
import org.codedefenders.game.multiplayer.MultiplayerGame;
import org.codedefenders.game.singleplayer.automated.attacker.AiAttacker;
import org.codedefenders.game.singleplayer.automated.defender.AiDefender;
import org.codedefenders.servlets.admin.AdminSystemSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckAiMoveThread implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(AiDefender.class);

    private MultiplayerGame game;
    private Role roleToCheckFor;

    public CheckAiMoveThread(MultiplayerGame game, Role role) {
        this.game = game;
        this.roleToCheckFor = role;
    }
    /**
     * Checks the game score, ratio of tests/mutants and
     * then decides if the AI-Player submits a test/mutant.
     */
    @Override
    public void run() {
        if (this.roleToCheckFor == Role.DEFENDER) {
            logger.info("AI-Defender executing a single turn. Strategy: {}", AdminDAO.getSystemSetting(AdminSystemSettings.SETTING_NAME.AI_STRAT).getStringValue());
            AiDefender defender = new AiDefender(game.getId());
            defender.runTurn(AiPlayer.GenerationMethod.valueOf(AdminDAO.getSystemSetting(AdminSystemSettings.SETTING_NAME.AI_STRAT).getStringValue()));
        } else if (this.roleToCheckFor == Role.ATTACKER) {
            logger.info("AI-Attacker executing a single turn. Strategy: {}", AdminDAO.getSystemSetting(AdminSystemSettings.SETTING_NAME.AI_STRAT).getStringValue());
            AiAttacker attacker = new AiAttacker(game.getId());
            attacker.runTurn(AiPlayer.GenerationMethod.valueOf(AdminDAO.getSystemSetting(AdminSystemSettings.SETTING_NAME.AI_STRAT).getStringValue()));
        }
    }
}
