package org.codedefenders.game.singleplayer;

import org.codedefenders.game.duel.DuelGame;
import org.codedefenders.game.Role;
import org.codedefenders.game.multiplayer.MultiplayerGame;
import org.codedefenders.game.singleplayer.automated.attacker.AiAttacker;
import org.codedefenders.game.singleplayer.automated.defender.AiDefender;

public class CheckAiMoveThread implements Runnable {

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
            System.out.println("RUNNING DEFENDER");
            AiDefender defender = new AiDefender(game.getId());
            // defender.turnEasy();
            defender.turnHard();
        } else if (this.roleToCheckFor == Role.ATTACKER) {
            System.out.println("RUNNING ATTACKER");
            AiAttacker attacker = new AiAttacker(game.getId());
            attacker.turnEasy();
        }
    }
}
