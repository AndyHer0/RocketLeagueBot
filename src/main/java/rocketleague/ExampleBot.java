package rocketleague;
import rlbot.commons.agents.Bot;
import rlbot.commons.protocol.RLBotInterface;
import rlbot.flat.*;

public class ExampleBot implements Bot {

    public static final float PI = (float) Math.PI;

    private final RLBotInterface rlbot;
    public final int index;
    public final int team;
    public final String name;
    public final String agentId;
    public final MatchConfigurationT matchConfig;
    public final FieldInfoT fieldInfo;

    public ExampleBot(RLBotInterface rlbot, int index, int team, String name, String agentId,
                      MatchConfigurationT matchConfig, FieldInfoT fieldInfo) {
        this.rlbot = rlbot;
        this.index = index;
        this.team = team;
        this.name = name;
        this.agentId = agentId;
        this.matchConfig = matchConfig;
        this.fieldInfo = fieldInfo;
    }

    @Override
    public PlayerLoadoutT getInitialLoadout() {
        return null; // use the loadout from bot.toml
    }

    @Override
    public ControllerStateT getOutput(GamePacketT packet, BallPredictionT ballPrediction) {
        var controller = new ControllerStateT();
        controller.setThrottle(1f);

        if (packet.getBalls().length > 0) {
            var player = packet.getPlayers()[index];
            var playerLoc = player.getPhysics().getLocation();
            var ballLoc = packet.getBalls()[0].getPhysics().getLocation();

            var curYaw = player.getPhysics().getRotation().getYaw();
            var desiredYaw = (float) Math.atan2(ballLoc.getY() - playerLoc.getY(),
                    ballLoc.getX() - playerLoc.getX());
            var correction = (desiredYaw - curYaw + 2 * PI) % (2 * PI) - PI;
            controller.setSteer(-correction);
        }

        return controller;
    }

    @Override
    public void onMatchCommReceived(MatchCommT comm) { }

    @Override
    public void onRetire() { }
}