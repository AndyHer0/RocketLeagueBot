package rocketleague;

import rlbot.commons.agents.BotManager;
import rlbot.commons.protocol.RLBotInterface;

public class Main {
    public static void main(String[] args) {
        var rlbot = new RLBotInterface();
        var botManager = new BotManager(rlbot, "andy/firstbot/v0.1", ExampleBot::new);
        botManager.run();
    }
}