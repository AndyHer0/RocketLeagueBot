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
    private boolean wasKickoffLastTick = false;
    private float flipStartTime = -1;
    private double distanceBallKickOff = 99999;
    private float kickoffStartTime = -1;
    private boolean didMomentumFlip = false;

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
            // How far we need to turn, wrapped into -PI..PI so we turn the short way
            float angleToBall = desiredYaw - curYaw;
            while (angleToBall > PI) angleToBall -= 2 * PI;
            while (angleToBall < -PI) angleToBall += 2 * PI;
            // Steer harder the further off we are; full lock beyond ~30 degrees
            float steer = angleToBall * 2;
            controller.setSteer(Math.max(-1, Math.min(1, steer)));
            float dx = ballLoc.getX() - playerLoc.getX();
            float dy = ballLoc.getY() - playerLoc.getY();
            float dz = ballLoc.getZ() - playerLoc.getZ();
            distanceBallKickOff = Math.sqrt(dx*dx + dy*dy + dz*dz);
        }
        // 1. Current value: is it a kickoff this tick?
        boolean kickoffNow = packet.getMatchInfo().getMatchPhase() == MatchPhase.Kickoff;
        float now = packet.getMatchInfo().getSecondsElapsed();

        // 2. Kickoff just started: remember when, and allow one momentum flip
        if (kickoffNow && !wasKickoffLastTick) {
            System.out.println("Kickoff!");
            kickoffStartTime = now;
            didMomentumFlip = false;
        }

        // 3. Momentum flip: once, 0.5s into the kickoff
        if (kickoffNow && !didMomentumFlip && now - kickoffStartTime > 0.5) {
            startFrontFlip(now);
            didMomentumFlip = true;
        }

        // 4. Flip into the ball when close
        if (kickoffNow && distanceBallKickOff < 500) {
            startFrontFlip(now);
        }

        // 5. Boost for the kickoff, but only once we're done flipping and back on the ground
        boolean onGround = packet.getPlayers()[index].getAirState() == AirState.OnGround;
        if (kickoffNow && flipStartTime == -1 && onGround) {
            controller.setBoost(true);
        }

        // 6. Press the flip buttons if a flip is running (every tick, no conditions)
        updateFrontFlip(controller, now);

        // 7. Remember this tick's value for next tick
        wasKickoffLastTick = kickoffNow;
        return controller;
    }

    // Call when you want to flip. Ignored if a flip is already running.
    public void startFrontFlip(float now) {
        if (flipStartTime == -1) {
            flipStartTime = now;
        }
    }

    // Call every tick. Presses the right buttons while a flip is running.
    public void updateFrontFlip(ControllerStateT controller, float now) {
        if(flipStartTime != -1){
            float t = now -flipStartTime;
            if(t<0.1){
                controller.setJump(true);
            }
            else if(t<0.15){
                controller.setJump(false);
            }
            else if(t<0.20){
                controller.setPitch(-1);
                controller.setJump(true);
            }
            else if(t<0.8){
                //do nothing let the flip finish;
            }
            else {
                flipStartTime = -1;
            }
        }
    }



    @Override
    public void onMatchCommReceived(MatchCommT comm) { }

    @Override
    public void onRetire() { }
}