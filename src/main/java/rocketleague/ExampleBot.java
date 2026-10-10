package rocketleague;
import rlbot.commons.agents.Bot;
import rlbot.commons.protocol.RLBotInterface;
import rlbot.flat.*;
import java.lang.Float; // rlbot.flat has its own Float class; we want Java's
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public class ExampleBot implements Bot {

    public static final float PI = (float) Math.PI;

    // ===== Game facts (set by Rocket League, don't tune these) =====
    private static final float GOAL_Y = 5120;            // goal lines are at y = +-5120
    private static final float FIELD_X = 4096;           // side walls are at x = +-4096
    private static final float GOAL_HALF_WIDTH = 893;    // goal posts are at x = +-893
    private static final float GOAL_DEPTH = 880;         // the goal's back wall is this far behind the goal line
    private static final float GRAVITY = -650;
    private static final float BOOST_ACCEL = 1060;       // boost (~992) + throttle in the air (~67)
    private static final float BOOST_PER_SECOND = 33.3f;
    private static final float MAX_SPEED = 2300;         // top speed with boost
    private static final float MAX_DRIVE_SPEED = 1410;   // top speed without boost
    private static final float SUPERSONIC_SPEED = 2200;  // boosting past this barely speeds you up
    private static final float BRAKE_ACCEL = 3500;       // slowdown from throttling against the way we're rolling
    private static final float CAR_REST_HEIGHT = 17;     // car center height when sitting on the ground
    private static final float JUMP_IMPULSE = 291.667f;  // upward speed from pressing jump (same for the double jump)
    private static final float JUMP_HOLD_ACCEL = 1458.333f; // extra upward accel while jump is held...
    private static final float JUMP_HOLD_TIME = 0.2f;    // ...for at most this long
    // Tightest turn (curvature = 1 / turning radius) the car can make at each speed
    private static final float[] TURN_SPEEDS = {0, 500, 1000, 1500, 1750, 2300};
    private static final float[] TURN_CURVATURES = {0.0069f, 0.00398f, 0.00235f, 0.001375f, 0.0011f, 0.00088f};

    // ===== Tuning: driving =====
    private static final float STEER_GAIN = 2;            // steer = angle * this (full lock beyond 1/this radians)
    private static final float HANDBRAKE_ANGLE = 2.0f;    // powerslide when the target is this far off (radians)
    private static final float BOOST_ANGLE = 0.3f;        // only boost when the target is within this angle
    private static final float BOOST_MIN_DISTANCE = 1500; // only boost when the target is further than this
    private static final float TURN_CHECK_MIN_DISTANCE = 300; // closer targets skip the "can we make this turn" check
    private static final float TURN_BRAKE_MARGIN = 200;   // too fast to make the turn by this much: brake, not just coast

    // ===== Tuning: boost management =====
    private static final float BOOST_RESERVE = 20;          // don't spend below this unless it's urgent (saves aerial fuel)
    private static final float PAD_TOP_UP = 70;             // below this, pick up pads that are near our path
    private static final float PAD_MIN_TRIP = 1500;         // only detour for pads on trips longer than this
    private static final float PAD_MAX_DETOUR_SMALL = 250;  // extra distance we'll drive for a small pad (+12)
    private static final float PAD_MAX_DETOUR_BIG = 600;    // extra distance we'll drive for a big pad (+100)

    // ===== Tuning: kickoff =====
    private static final boolean USE_SPEED_FLIP_KICKOFF = true; // false = plain front flip kickoff
    private static final float KICKOFF_FLIP_TIME = 0.5f;        // speed/momentum flip this long into the kickoff
    private static final float KICKOFF_FLIP_DISTANCE = 500;     // flip into the ball when this close
    private static final float KICKOFF_SPEED_FLIP_DIR = 1;      // speed flip dodge side: +1 = right, -1 = left
    // Before the speed flip, point the nose this far (radians) to the other side, so the diagonal
    // dodge carries us straight at the ball. Ends up to the dodge side of the ball: raise it. Other side: lower it.
    private static final float SPEED_FLIP_PRE_ANGLE = 0.2f;

    // ===== Tuning: flips (all times are seconds since the maneuver started) =====
    private static final float FLIP_JUMP_TIME = 0.1f;     // hold the first jump this long
    private static final float FLIP_DODGE_TIME = 0.15f;   // press jump again (the dodge) at this time
    private static final float SPEED_FLIP_YAW = 0.7f;    // sideways part of the diagonal dodge (0 = straight, 1 = 45 degrees)
    private static final float SPEED_FLIP_JUMP_TIME = 0.08f;
    private static final float SPEED_FLIP_DODGE_TIME = 0.12f;
    private static final float SPEED_FLIP_CANCEL_TIME = 0.17f; // pull back to stop the nose going down
    private static final float SPEED_FLIP_END_TIME = 0.8f;
    private static final float HALF_FLIP_ANGLE = 2.6f;          // half flip when the target is this far behind (radians)
    private static final float HALF_FLIP_MIN_DISTANCE = 1500;   // ...and further away than this
    private static final float HALF_FLIP_MAX_SPEED = 500;       // ...and we aren't driving forward faster than this
    private static final float HALF_FLIP_REVERSE_SPEED = 300;   // back up to this speed first, so the flip carries it
    private static final float HALF_FLIP_CANCEL_TIME = 0.33f;   // push forward to stop the backflip halfway
    private static final float HALF_FLIP_CANCEL_HOLD = 0.3f;    // keep pushing forward this long while rolling upright
    private static final float DOUBLE_JUMP_TIME = 0.25f;        // press jump again at this time for a double jump

    // ===== Tuning: shooting =====
    private static final float SHOT_OFFSET_RATIO = 0.4f;      // line up this fraction of the distance behind the ball
    private static final float SHOT_OFFSET_MAX = 600;         // ...but never further behind than this
    private static final float FLIP_HIT_DISTANCE = 400;       // flip into the ball when this close
    private static final float FLIP_HIT_MAX_BALL_HEIGHT = 200;
    private static final float FLIP_HIT_ANGLE = 0.25f;        // ...and lined up within this angle

    // ===== Tuning: dribbling and flicks =====
    private static final float DRIBBLE_MAX_OFFSET = 130;      // ball counts as "on the car" within this sideways distance
    private static final float DRIBBLE_MIN_HEIGHT = 90;       // ...and this far above the car's center
    private static final float DRIBBLE_MAX_HEIGHT = 220;
    private static final float DRIBBLE_BALL_AHEAD = 30;       // keep the ball this far in front of the car's center
    private static final float DRIBBLE_SPEED_GAIN = 1 / 200f; // throttle per unit of speed difference with the ball
    private static final float DRIBBLE_POSITION_GAIN = 1 / 40f; // throttle per unit the ball is out of place
    private static final float DRIBBLE_STEER_LEAD = 300;      // steer at a point this far past the ball toward their goal
    private static final float DRIBBLE_STEER_SCALE = 0.5f;    // gentler steering so the ball doesn't roll off
    private static final float FLICK_OPPONENT_DISTANCE = 900; // flick when the opponent gets this close...
    private static final float FLICK_GOAL_DISTANCE = 2500;    // ...or when we're this close to their goal
    private static final float FLICK_DODGE_TIME = 0.25f;      // later dodge than a normal flip, so the ball pops up first
    private static final float FLICK_YAW = 0.5f;              // diagonal flicks hit harder than straight ones

    // ===== Tuning: retreating =====
    private static final float RETREAT_START = 500;   // retreat once the ball is this far goal-side of us...
    private static final float RETREAT_LEAD = 0.3f;   // ...unless we beat them to it by this many seconds (then just play it)
    private static final float RETREAT_STOP = 300;    // stop once we're this far goal-side of the ball
    private static final float RETREAT_POST_X = 800;  // x of the post we retreat to
    private static final float RETREAT_DEPTH = 400;   // how far in front of the goal line to retreat to

    // ===== Tuning: opponent awareness =====
    private static final float FIRST_TO_BALL_MARGIN = 0.2f;   // we must be this many seconds ahead to attack
    private static final float CHALLENGE_WINDOW = 0.6f;       // they're ahead by less than this: challenge, else shadow
    private static final float SHADOW_DISTANCE = 1200;        // how far goal-side of the ball to shadow
    private static final float HOLD_DISTANCE = 600;           // within this of a wait spot, ease off and face the ball
    private static final float HOLD_LINEUP_ANGLE = 0.3f;      // at a wait spot, keep rolling (to be able to turn) until this lined up
    private static final float HOLD_TURN_THROTTLE = 0.5f;     // ...at this throttle, rocking back and forth across the spot
    private static final float REVERSE_MAX_DISTANCE = 2000;   // wait spot behind us, ball in front: back up to it if this close
    private static final float BACKFLIP_CONTEST_DISTANCE = 300; // ball this close right behind us...
    private static final float CONTEST_OPPONENT_DISTANCE = 1000;// ...with an opponent this close: backflip into it
    private static final float LOW_BOOST = 30;                // below this, grab a big pad when it's safe
    private static final float NO_THREAT_TIME = 1.5f;         // opponent this far from the ball isn't a threat
    private static final float SHOT_THREAT_TIME = 0.6f;       // opponent this close to hitting it: get in front of the shot
    private static final float SHOT_WIDTH_MARGIN = 200;       // count shots this far outside the posts as on target
    private static final float SAVE_DEPTH = 200;              // how far in front of the goal line to stand for a save
    private static final float SAVE_LOOKAHEAD = 4f;           // look this many seconds ahead for balls going in
    private static final float SAVE_COMMIT_SLACK = 0.3f;      // once going for a block, keep at it if we're this many seconds late
    private static final float SAVE_COMMIT_MARGIN = 150;      // ...or the ball gets this far past our depth
    private static final float JUMP_SAVE_REACH = 150;         // jump for balls that will pass within this far of us (sideways)
    private static final float JUMP_SAVE_LOOKAHEAD = 1.5f;    // how far ahead to look for that
    private static final float HOLD_SPEED_GAIN = 2;           // at a wait spot: wanted speed per unit of distance from it
    private static final float HOLD_THROTTLE_GAIN = 1 / 500f; // ...and throttle per unit of speed off from that
    private static final float ARRIVAL_BRAKE_FRACTION = 0.7f; // brake for wait spots as if we only had this much brake
    private static final float TURN_TIME_PER_RADIAN = 0.3f;   // arrival estimate: seconds lost per radian of turning
    private static final float GROUND_HIT_MAX_HEIGHT = 250;   // arrival estimate: balls above this can't be driven into
    private static final float DEFENSIVE_THIRD = 3400;        // ball this close to our goal line is in our defensive third
    private static final float OPP_THREAT_MIN_SPEED = 500;    // opponent driving at least this fast...
    private static final float OPP_THREAT_ALIGN = 0.8f;       // ...within ~37 degrees of the ball or our goal is coming at us

    // ===== Tuning: decisiveness =====
    private static final float MODE_MIN_TIME = 0.4f;          // stick with a decision at least this long (saves/kickoffs excepted)
    private static final float FREE_BALL_AGGRESSION = 0.1f;   // loose ball: attack even if they'd get there this much sooner
    private static final float ATTACK_COMMIT = 0.3f;          // once attacking, keep going unless they're this much ahead

    // ===== Tuning: punishing right after a kickoff (the opponent is most out of position then) =====
    private static final float KICKOFF_PUNISH_WINDOW = 3f;    // this many seconds after the kickoff's first touch...
    private static final float PUNISH_AGGRESSION = 0.3f;      // ...with an open lane, attack even if they'd get there this much sooner
    private static final float SHOT_LANE_CLEARANCE = 600;     // lane to their goal is open if they're this far off it (or behind the ball)

    // ===== Tuning: opponent ball control (dribbling, carrying, pushing it along) =====
    private static final float CONTROL_DISTANCE = 400;        // ball this close to them...
    private static final float CONTROL_REL_SPEED = 500;       // ...moving with them (speed difference under this) = carrying it
    private static final int CONTROL_TOUCHES = 2;             // or this many of their touches...
    private static final float CONTROL_TOUCH_WINDOW = 1.5f;   // ...within this many seconds...
    private static final float CONTROL_TOUCH_DISTANCE = 800;  // ...while the ball stays this close to them = pushing it along
    private static final float CONTROL_MEMORY = 0.4f;         // keep treating it as controlled this long after the signs stop

    // ===== Tuning: turning around in goal (up the back wall, jump off, land facing the ball) =====
    private static final float BACKWALL_START_DEPTH = 1200;   // start when this close to our goal line and facing it...
    private static final float BACKWALL_MIN_TIME = 1.5f;      // ...and the ball won't go in for at least this long
    private static final float BACKWALL_TIMEOUT = 2.5f;       // give up on it after this long

    // ===== Tuning: aerials =====
    private static final float JUMP_SPEED = 800;          // rough upward speed from a held jump + double jump
    private static final float HIT_DISTANCE = 110;        // car center to ball center when we touch it
    private static final float AERIAL_MIN_HEIGHT = 400;   // lower balls are for the ground game
    private static final float AERIAL_MIN_TIME = 0.6f;    // need at least this long to jump and turn
    private static final float AERIAL_MAX_TIME = 3f;      // don't trust the prediction further ahead than this
    private static final float AERIAL_ACCEL_MARGIN = 0.8f;// only go if we need under this fraction of max boost accel
    private static final float AERIAL_MIN_BOOST = 20;
    private static final float AERIAL_BOOST_ALIGN = 0.9f; // boost when nose . aim > this (0.9 = within ~25 degrees)
    private static final float AERIAL_TURN_TIME = 0.5f;   // seconds per radian to point the nose before boosting
    private static final float AERIAL_ABORT_MISS = 150;   // give up if we'll miss by more than this and can't fix it

    // ===== Tuning: air control (orient) =====
    // Spin toward the target as fast as possible, then brake just in time to stop on it.
    // Max spin-up per axis in rad/s^2 (game facts), and how much of it we plan the braking with.
    private static final float ROLL_ACCEL = 36.08f, PITCH_ACCEL = 12.15f, YAW_ACCEL = 8.92f;
    private static final float AIR_BRAKE_FRACTION = 0.7f; // lower = brake earlier (less overshoot, slower turns)
    private static final float AIR_SPIN_GAIN = 2;         // stick per rad/s of spin off from the spin we want

    private enum Mode { KICKOFF, ATTACK, DRIBBLE, CHALLENGE, SHADOW, RETREAT, GET_BOOST, SAVE }

    // Timed button sequences that run over several ticks
    private enum Maneuver { NONE, JUMP, DOUBLE_JUMP, FLIP, SPEED_FLIP, HALF_FLIP }

    // When a car can get to the ball, and where the ball will be then
    private record Intercept(float time, Vec3 ball) { }

    private final RLBotInterface rlbot;
    public final int index;
    public final int team;
    public final String name;
    public final String agentId;
    public final MatchConfigurationT matchConfig;
    public final FieldInfoT fieldInfo;
    private boolean wasKickoffLastTick = false;
    private float kickoffStartTime = -1;
    private boolean didMomentumFlip = false;
    private boolean retreating = false;
    private boolean savingByHit = false;   // committed to blocking the ball on its way in (vs. waiting on the line)
    private Mode mode = null;
    private float modeSince = -1;
    private final Map<Integer, Float> lastTouchSeen = new HashMap<>(); // per player index: their latest touch time
    private final ArrayDeque<Float> opponentTouches = new ArrayDeque<>(); // times of recent opponent touches
    private float opponentControlUntil = -1;
    private float backWallStartTime = -1;  // turning around in goal via the back wall (-1 = not doing it)
    private float backWallJumpTime = -100;
    private float aerialClosest;           // closest we got to the ball during this aerial (for tuning)
    private float aerialTouchBefore;       // our latest touch time when the aerial started (a newer one = we hit it)
    private float kickoffEndTime = -100;   // when the last kickoff's first touch happened
    private Maneuver maneuver = Maneuver.NONE;
    private float maneuverStartTime = -1;
    private float flipPitch, flipYaw, flipDodgeTime;   // for FLIP: dodge direction and timing
    private float maneuverDir = 1;                     // for SPEED_FLIP: +1 = dodge right, -1 = left
    private float aerialStartTime = -1;
    private float aerialTargetTime = -1;
    private float lastOrientTime = -1;
    private float lastPitchErr, lastYawErr, lastRollErr;

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

        if (packet.getBalls().length == 0 || packet.getPlayers().length <= index) {
            return controller;
        }

        var player = packet.getPlayers()[index];
        Vec3 car = Vec3.of(player.getPhysics().getLocation());
        Vec3 carVel = Vec3.of(player.getPhysics().getVelocity());
        Vec3 forward = Vec3.forward(player.getPhysics().getRotation()).flat().normalized();
        Vec3 ball = Vec3.of(packet.getBalls()[0].getPhysics().getLocation());
        Vec3 ballVel = Vec3.of(packet.getBalls()[0].getPhysics().getVelocity());
        float now = packet.getMatchInfo().getSecondsElapsed();
        boolean onGround = player.getAirState() == AirState.OnGround;
        boolean kickoffNow = packet.getMatchInfo().getMatchPhase() == MatchPhase.Kickoff;
        float distToBall = ball.sub(car).length();
        float boost = player.getBoost();
        float forwardSpeed = carVel.dot(forward);

        // side = -1 for blue (defends the -y goal), +1 for orange (defends the +y goal).
        // Multiplying a y value by side gives "how close to our own goal" it is.
        float side = (team == 0) ? -1 : 1;
        float ownGoalY = GOAL_Y * side;
        float enemyGoalY = -ownGoalY;
        Vec3 enemyGoal = new Vec3(0, enemyGoalY, 0);
        float carDepth = car.y() * side;
        float ballDepth = ball.y() * side;

        // 1. Kickoff just started: reset everything left over from the last play
        if (kickoffNow && !wasKickoffLastTick) {
            System.out.println("Kickoff!");
            kickoffStartTime = now;
            didMomentumFlip = false;
            retreating = false;
            savingByHit = false;
            aerialStartTime = -1;
            backWallStartTime = -1;
            opponentControlUntil = -1;
            opponentTouches.clear();
            maneuver = Maneuver.NONE;
        }
        // The kickoff phase ends with the first touch: start the window where we try to punish it
        if (!kickoffNow && wasKickoffLastTick) kickoffEndTime = now;

        // 2. Read the situation: who gets to the ball first, is it going in, where's the opponent shooting
        Intercept ours = findIntercept(player, ballPrediction, ball, now, Float.MAX_VALUE, 0);
        PlayerInfoT opponent = null;
        Intercept theirs = null;
        var players = packet.getPlayers();
        for (int i = 0; i < players.length; i++) {
            if (i == index || players[i].getTeam() == team) continue;
            Intercept it = findIntercept(players[i], ballPrediction, ball, now, Float.MAX_VALUE, 0);
            // In 2v2/3v3, worry about whichever opponent gets there first
            if (theirs == null || it.time() < theirs.time()) {
                opponent = players[i];
                theirs = it;
            }
        }
        Intercept goingIn = findOwnGoalCrossing(ballPrediction, now, side);
        // Where we can block a ball that's going in: the first spot on its path we reach before it
        // crosses the line, and only from the goal side (hitting it from behind would knock it in).
        // Once committed we allow some slack, so the plan doesn't flip-flop when it's close either way.
        float slack = savingByHit ? SAVE_COMMIT_SLACK : 0;
        float margin = savingByHit ? SAVE_COMMIT_MARGIN : 0;
        Intercept saveHit = goingIn == null ? null
                : findIntercept(player, ballPrediction, ball, now, goingIn.time(), slack);
        if (saveHit != null && saveHit.ball().y() * side > carDepth + margin) saveHit = null;
        savingByHit = saveHit != null;
        recordOpponentTouches(players, now);
        float shotX = Float.NaN;
        float opponentDist = Float.MAX_VALUE;
        boolean opponentAttacking = false;
        boolean shotLaneOpen = true;
        if (opponent != null) {
            Vec3 oppLoc = Vec3.of(opponent.getPhysics().getLocation());
            shotLaneOpen = isShotLaneOpen(ball, enemyGoal, oppLoc);
            Vec3 oppVel = Vec3.of(opponent.getPhysics().getVelocity());
            shotX = predictShotX(oppLoc, theirs.ball(), side, ownGoalY);
            opponentDist = oppLoc.sub(car).length();
            // Are they close to the ball and driving at it (or at our goal)?
            Vec3 oppHeading = oppVel.flat();
            if (theirs.time() < NO_THREAT_TIME && oppHeading.length() > OPP_THREAT_MIN_SPEED) {
                Vec3 heading = oppHeading.normalized();
                opponentAttacking = heading.dot(ball.sub(oppLoc).flat().normalized()) > OPP_THREAT_ALIGN
                        || heading.dot(new Vec3(0, ownGoalY, 0).sub(oppLoc).flat().normalized()) > OPP_THREAT_ALIGN;
            }
            if (isControlling(opponent, ball, ballVel)) opponentControlUntil = now + CONTROL_MEMORY;
        }
        // Is the opponent dribbling / carrying / pushing the ball along? Then its path is unpredictable
        // (they can flick or change direction any time), so we play them, not the ball prediction.
        boolean opponentControlling = now < opponentControlUntil;
        // Right after a kickoff the opponent is usually out of position (still landing, or past the ball).
        // If the way to their goal is open, that's our best scoring chance: go for it hard.
        boolean punish = now - kickoffEndTime < KICKOFF_PUNISH_WINDOW && shotLaneOpen && !opponentControlling;
        // Ball in our end with an opponent coming: stay on our wheels near the ball, ready to react,
        // instead of committing to a flip (a missed flip leaves the net open for a second)
        boolean stayGrounded = opponentAttacking && ballDepth > GOAL_Y - DEFENSIVE_THIRD;

        // Is the ball sitting on our roof, and are we pointed at their half?
        Vec3 ballRel = ball.sub(car);
        boolean ballOnCar = onGround
                && ballRel.flat().length() < DRIBBLE_MAX_OFFSET
                && ballRel.z() > DRIBBLE_MIN_HEIGHT && ballRel.z() < DRIBBLE_MAX_HEIGHT;
        boolean facingTheirHalf = forward.y() * -side > 0;

        // Defense: we're on the wrong side of the ball (it's between us and our goal),
        // so hitting it now would push it toward our own net. Go back first.
        // But if we'll clearly beat them to the ball, keep the pressure on and play it instead of backing off.
        // The two thresholds keep it from flickering between modes at the boundary. We also stop once we're
        // back in our goal mouth: there's nowhere further back to go (the ball may be deep in a corner).
        boolean wellAhead = theirs == null || ours.time() + RETREAT_LEAD < theirs.time();
        boolean wayAhead = theirs == null || ours.time() + 2 * RETREAT_LEAD < theirs.time();
        boolean inGoalMouth = Math.abs(car.x()) < GOAL_HALF_WIDTH + 200 && carDepth > GOAL_Y - SAVE_DEPTH - 200;
        if (!retreating && carDepth < ballDepth - RETREAT_START && !wellAhead && !inGoalMouth) {
            retreating = true;
        } else if (retreating && (carDepth > ballDepth + RETREAT_STOP || wayAhead || inGoalMouth)) {
            retreating = false;
        }

        Vec3 bigPad = findBoostPad(packet, car, ballDepth, side);
        Mode wanted = chooseMode(kickoffNow, ours, theirs, goingIn, shotX, boost, ballDepth, bigPad,
                ballOnCar && facingTheirHalf, opponentControlling, punish);
        // Be decisive: once we pick something, stick with it for a moment instead of flickering between
        // modes. Saves and kickoffs can't wait, and some modes stop making sense the moment their reason is gone.
        boolean mustSwitch = mode == null || wanted == Mode.KICKOFF || wanted == Mode.SAVE
                || mode == Mode.KICKOFF || mode == Mode.DRIBBLE || (mode == Mode.GET_BOOST && bigPad == null);
        if (wanted != mode && (mustSwitch || now - modeSince > MODE_MIN_TIME)) {
            System.out.println("Mode: " + wanted + (punish && wanted == Mode.ATTACK ? " (punishing the kickoff)" : ""));
            mode = wanted;
            modeSince = now;
        }

        // 3. Aerial: start one if a ball in the air is reachable, then let it take over
        // When saving from behind the ball, touching it would push it in, so only go for it from the goal side
        boolean goalSide = carDepth > ballDepth;
        boolean goingForBall = mode == Mode.ATTACK || mode == Mode.CHALLENGE || (mode == Mode.SAVE && goalSide);
        // (With an opponent attacking our end, only leave the ground if it's to save a goal)
        if (goingForBall && (!stayGrounded || mode == Mode.SAVE)
                && aerialStartTime == -1 && onGround && maneuver == Maneuver.NONE) {
            float hitTime = findAerialHitTime(player, ballPrediction, now, enemyGoalY, false);
            if (hitTime != -1) {
                System.out.println("Aerial! Hitting the ball in " + (hitTime - now) + "s");
                aerialStartTime = now;
                aerialClosest = Float.MAX_VALUE;
                aerialTouchBefore = latestTouchTime(player);
                aerialTargetTime = hitTime;
            }
        }
        if (aerialStartTime != -1
                && updateAerial(controller, player, ballPrediction, ball, now, enemyGoalY)) {
            wasKickoffLastTick = kickoffNow;
            return controller;
        }

        // 4. Pick where to drive. "hold" means: once close to the spot, stop and wait there facing the ball
        //    (instead of arriving at full speed and circling it, or climbing the wall behind it).
        boolean hold = mode == Mode.SHADOW || mode == Mode.RETREAT || (mode == Mode.SAVE && saveHit == null);
        Vec3 target = switch (mode) {
            case KICKOFF -> ball.flat();
            case SAVE -> {
                // We can get in front of it before it goes in: meet it there and knock it away
                if (saveHit != null) yield saveHit.ball().flat();
                // Otherwise get to the goal line where the ball (or their shot) will cross, and block it.
                // (No crossing known, e.g. the shot just got touched: cover the ball's side of the goal.)
                float crossX = goingIn != null ? goingIn.ball().x() : Float.isNaN(shotX) ? ball.x() : shotX;
                yield new Vec3(clamp(crossX, -GOAL_HALF_WIDTH + 100, GOAL_HALF_WIDTH - 100),
                        ownGoalY - SAVE_DEPTH * side, 0);
            }
            // Head to the post on our side of the ball so we don't drive through it. If the ball is deep in our
            // end, go further back, goal-side of it (at most to the goal line), so the retreat can actually finish.
            case RETREAT -> {
                float depth = Math.max(GOAL_Y - RETREAT_DEPTH, Math.min(ballDepth + RETREAT_STOP + 100, GOAL_Y - SAVE_DEPTH));
                yield new Vec3(car.x() >= ball.x() ? RETREAT_POST_X : -RETREAT_POST_X, depth * side, 0);
            }
            case ATTACK -> {
                // Shooting: aim at a point behind where we'll meet the ball, on the line from the
                // enemy goal through it. Hitting the ball from there sends it toward their goal.
                Vec3 hit = ours.ball();
                Vec3 toGoal = enemyGoal.sub(hit).flat().normalized();
                // Back off further when we're far away; shrink to 0 as we get close
                float offset = Math.min(hit.sub(car).length() * SHOT_OFFSET_RATIO, SHOT_OFFSET_MAX);
                yield hit.sub(toGoal.scale(offset)).flat();
            }
            // Steer at a point past the ball toward their goal, so we turn the dribble goalward
            case DRIBBLE -> ball.add(enemyGoal.sub(ball).flat().normalized().scale(DRIBBLE_STEER_LEAD)).flat();
            // They're carrying it at us: drive straight at the ball on their car, taking away their space.
            // Otherwise (loose ball, they're slightly ahead): go to where we'll meet the ball to block them.
            case CHALLENGE -> opponentControlling ? ball.flat() : ours.ball().flat();
            case GET_BOOST -> bigPad.flat();
            // Stay goal-side of the ball a set distance back, close enough to keep the pressure on. Once that
            // spot would be in (or behind) the goal mouth, wait in the net instead, covering the ball's side.
            case SHADOW -> {
                Vec3 spot = ball.add(new Vec3(0, ownGoalY, 0).sub(ball).flat().normalized().scale(SHADOW_DISTANCE));
                yield spot.y() * side > GOAL_Y - SAVE_DEPTH ? goalieSpot(ball, side) : spot.flat();
            }
        };
        if (mode != Mode.KICKOFF) {
            // Keep the target inside the field
            target = new Vec3(clamp(target.x(), -FIELD_X + 200, FIELD_X - 200),
                    clamp(target.y(), -GOAL_Y + 200, GOAL_Y - 200), 0);
        }

        // Turning around in goal: we're driving into our goal with the ball behind us and time to spare.
        // Instead of a slow U-turn in the goal mouth, drive up the goal's back wall and jump off it,
        // turning in the air (step 8) so we land facing the ball, ready to save.
        Vec3 carUp = Vec3.up(player.getPhysics().getRotation());
        boolean ballBehind = Math.abs(angleTo(player, ball.x(), ball.y())) > HANDBRAKE_ANGLE;
        // Only start from well inside the posts and heading roughly straight in, so we reach the back wall
        // instead of clipping a side wall of the goal.
        if (backWallStartTime == -1 && hold && onGround && carUp.z() > 0.9f && ballBehind
                && forward.y() * side > 0.7f && carDepth > GOAL_Y - BACKWALL_START_DEPTH
                && Math.abs(car.x()) < GOAL_HALF_WIDTH - 200 && (goingIn == null || goingIn.time() > BACKWALL_MIN_TIME)) {
            System.out.println("Turning around off the back wall");
            backWallStartTime = now;
        }
        if (backWallStartTime != -1) {
            // On the back wall our roof points out of the goal, toward the field (side walls point it sideways)
            boolean onBackWall = onGround && carUp.y() * side < -0.7f;
            if (!hold || now - backWallStartTime > BACKWALL_TIMEOUT) {
                System.out.println("Back wall turn cancelled");
                backWallStartTime = -1; // something more urgent came up, or it isn't working
            } else if (onBackWall) {
                // Jump off it: a jump pushes away from the wall, back out toward the field
                startManeuver(Maneuver.JUMP, now);
                backWallJumpTime = now;
                backWallStartTime = -1;
            } else {
                // Drive at the middle of the back wall
                target = new Vec3(0, ownGoalY + GOAL_DEPTH * side, 0);
                hold = false;
            }
        }
        float distToTarget = target.sub(car.flat()).length();

        // Boost management: on longer trips, swing through a pad if it's close to our path
        boolean padsOk = (mode == Mode.ATTACK || mode == Mode.SHADOW || mode == Mode.RETREAT)
                && backWallStartTime == -1;
        if (padsOk && boost < PAD_TOP_UP && distToTarget > PAD_MIN_TRIP
                && !(hold && distToTarget < HOLD_DISTANCE)) {
            Vec3 pad = findPadOnPath(packet, car, target);
            if (pad != null) {
                target = pad;
                hold = false; // drive through the pad, don't stop on it
                distToTarget = target.sub(car.flat()).length();
            }
        }

        // 5. Drive there
        float angle;
        float targetAngle = angleTo(player, target.x(), target.y());
        float ballAngle = angleTo(player, ball.x(), ball.y());
        boolean holding = hold && distToTarget < HOLD_DISTANCE;
        boolean backingUp = hold && !holding && onGround && distToTarget < REVERSE_MAX_DISTANCE
                && Math.abs(targetAngle) > PI / 2 && Math.abs(ballAngle) < PI / 2;
        boolean braking = false;
        if (mode == Mode.DRIBBLE) {
            // Keep the ball balanced: match its speed, and nudge it back to just in front of center
            angle = steerToward(controller, player, target.x(), target.y());
            controller.setSteer(controller.getSteer() * DRIBBLE_STEER_SCALE);
            float ahead = ballRel.dot(forward);
            float speedDiff = ballVel.dot(forward) - forwardSpeed;
            float throttle = clamp(speedDiff * DRIBBLE_SPEED_GAIN
                    + (ahead - DRIBBLE_BALL_AHEAD) * DRIBBLE_POSITION_GAIN, -1, 1);
            controller.setThrottle(throttle);
            // Throttle tops out around 1410; boost if the ball is running away faster than that
            if (throttle >= 1 && speedDiff > 300 && boost > BOOST_RESERVE) {
                controller.setBoost(true);
            }
        } else if (holding) {
            // Close to our spot: line up with the ball, nose or tail (whichever is closer, so we never
            // need a slow 180), and ease onto the spot, slowing down as we get there
            angle = steerToward(controller, player, ball.x(), ball.y());
            float lineUp = Math.abs(angle) > PI / 2 ? wrapAngle(angle - PI) : angle;
            float along = forward.dot(target.sub(car.flat()));
            float throttle = clamp((along * HOLD_SPEED_GAIN - forwardSpeed) * HOLD_THROTTLE_GAIN, -1, 1);
            // A car can't turn while standing still. Until we're lined up, keep rolling toward the spot,
            // so we rock back and forth across it (a 3-point turn) and the steering can turn us.
            if (Math.abs(lineUp) > HOLD_LINEUP_ANGLE && Math.abs(throttle) < HOLD_TURN_THROTTLE) {
                throttle = along >= 0 ? HOLD_TURN_THROTTLE : -HOLD_TURN_THROTTLE;
            }
            controller.setThrottle(throttle);
            // Rolling backward turns the car the other way, so flip the steering then
            // (when nearly stopped, go by which way we're about to roll)
            boolean rollingBack = Math.abs(forwardSpeed) > 50 ? forwardSpeed < 0 : throttle < 0;
            float steer = clamp(lineUp * STEER_GAIN, -1, 1);
            controller.setSteer(rollingBack ? -steer : steer);
        } else if (backingUp) {
            // Our spot is behind us but the ball (and whoever has it) is in front: back up to the spot still
            // facing them, instead of turning our back and driving away. Point the tail at the spot.
            float tailAngle = wrapAngle(targetAngle - PI);
            angle = tailAngle;
            controller.setSteer(clamp((forwardSpeed < 0 ? -tailAngle : tailAngle) * STEER_GAIN, -1, 1));
            // Reverse, braking in time to stop on the spot
            float stopSpeed = (float) Math.sqrt(2 * BRAKE_ACCEL * ARRIVAL_BRAKE_FRACTION * distToTarget);
            controller.setThrottle(-forwardSpeed > stopSpeed ? 1 : -1);
        } else {
            angle = steerToward(controller, player, target.x(), target.y());
            // Heading for a spot to wait at: brake in time to stop there, instead of arriving at full
            // speed and flying past it (or up the post)
            float stopSpeed = (float) Math.sqrt(2 * BRAKE_ACCEL * ARRIVAL_BRAKE_FRACTION * distToTarget);
            if (hold && forwardSpeed > stopSpeed) {
                controller.setThrottle(-1);
                braking = true;
            }
            // Too fast to make the turn to the target: we'd swing wide, miss it by a little, and have to
            // U-turn back (over and over, like a snake). Ease off until our turning circle is tight enough.
            // The arc that's tangent to our heading and passes through the target has curvature 2 sin(angle) / dist.
            if (mode != Mode.KICKOFF && distToTarget > TURN_CHECK_MIN_DISTANCE) {
                float sin = (float) Math.sin(Math.min(Math.abs(angle), PI / 2));
                float turnSpeed = speedForCurvature(2 * sin / distToTarget);
                if (forwardSpeed > turnSpeed) {
                    controller.setThrottle(forwardSpeed > turnSpeed + TURN_BRAKE_MARGIN ? -1 : 0);
                    braking = true;
                }
            }
        }

        if (mode == Mode.KICKOFF) {
            // 6a. Speed flip (or plain front flip) shortly into the kickoff. Until then, angle the nose
            //     away from the dodge side so the diagonal dodge sends us straight at the ball.
            if (USE_SPEED_FLIP_KICKOFF && !didMomentumFlip) {
                float aimAngle = angle - KICKOFF_SPEED_FLIP_DIR * SPEED_FLIP_PRE_ANGLE;
                controller.setSteer(clamp(aimAngle * STEER_GAIN, -1, 1));
            }
            if (!didMomentumFlip && now - kickoffStartTime > KICKOFF_FLIP_TIME) {
                if (USE_SPEED_FLIP_KICKOFF) {
                    startSpeedFlip(now, KICKOFF_SPEED_FLIP_DIR);
                } else {
                    startFrontFlip(now);
                }
                didMomentumFlip = true;
            }
            // 6b. Flip into the ball when close, aimed at it in case we're a little off line
            if (distToBall < KICKOFF_FLIP_DISTANCE && onGround) {
                startAimedFlip(now, angle);
            }
            // 6c. Boost for the kickoff, but only once we're done flipping and back on the ground
            if (maneuver == Maneuver.NONE && onGround) {
                controller.setBoost(true);
            }
        } else if (mode == Mode.DRIBBLE) {
            // 7a. Flick: the opponent is closing in, or we're near their goal
            float goalDist = enemyGoal.sub(car).flat().length();
            if (opponentDist < FLICK_OPPONENT_DISTANCE || goalDist < FLICK_GOAL_DISTANCE) {
                // Dodge diagonally, toward whichever side their goal is on
                Vec3 right = Vec3.right(player.getPhysics().getRotation());
                float yaw = enemyGoal.sub(car).dot(right) >= 0 ? FLICK_YAW : -FLICK_YAW;
                System.out.println("Flick!");
                startFlip(now, -1, yaw, FLICK_DODGE_TIME);
            }
        } else {
            // 7b. Sharp turn: powerslide so we come around faster (not while waiting on a spot,
            //     where the ball being behind us is fine)
            if (Math.abs(angle) > HANDBRAKE_ANGLE && onGround && !holding) {
                controller.setHandbrake(true);
            }
            // 7c. Boost when lined up and far away, but not past supersonic (wasted)
            //     and not below our reserve unless we're in a hurry
            boolean urgent = mode == Mode.SAVE || mode == Mode.CHALLENGE || mode == Mode.RETREAT
                    || (mode == Mode.ATTACK && punish);
            if (Math.abs(angle) < BOOST_ANGLE && distToTarget > BOOST_MIN_DISTANCE && !braking && !backingUp
                    && onGround && maneuver == Maneuver.NONE && forwardSpeed < SUPERSONIC_SPEED
                    && (urgent || boost > BOOST_RESERVE)) {
                controller.setBoost(true);
            }
            // 7d. Shot: flip into the ball when it's close, low, and straight ahead (aimed at the ball
            //     itself, not the drive target, which sits a bit behind it). Only when the flip is worth
            //     it: it mustn't send the ball toward our goal, and with an opponent coming at our end
            //     we stay on our wheels and just drive through it, ready for the next touch.
            boolean awayFromOurGoal = ballRel.y() * side < 0;
            if (goingForBall && onGround && !stayGrounded && awayFromOurGoal && distToBall < FLIP_HIT_DISTANCE
                    && ball.z() < FLIP_HIT_MAX_BALL_HEIGHT && Math.abs(ballAngle) < FLIP_HIT_ANGLE) {
                startAimedFlip(now, ballAngle);
            }
            // 7d''. Contest from behind: the ball is right behind us with an opponent on it. Backflip into it
            //       instead of turning around, which takes too long and hands them the ball. (Allowed even when
            //       staying grounded: at this range the touch is close to certain.)
            if (onGround && maneuver == Maneuver.NONE && awayFromOurGoal && distToBall < BACKFLIP_CONTEST_DISTANCE
                    && Math.abs(ballAngle) > HALF_FLIP_ANGLE && ball.z() < FLIP_HIT_MAX_BALL_HEIGHT
                    && opponentDist < CONTEST_OPPONENT_DISTANCE) {
                System.out.println("Backflip contest!");
                startAimedFlip(now, ballAngle);
            }
            // 7d'. Jump save: jump (or double jump) so we're up at the ball's height right as it reaches us
            if (mode == Mode.SAVE && goalSide && onGround && maneuver == Maneuver.NONE) {
                tryJumpSave(ballPrediction, car, now);
            }
            // 7e. Half flip: the target is far behind us and we're slow, so flip around instead of
            //     driving a big circle. Back up first: the flip carries our backward speed into the turn.
            //     Not while defending against an attacker: stay on the wheels so we can react.
            if (onGround && maneuver == Maneuver.NONE && !stayGrounded && Math.abs(angle) > HALF_FLIP_ANGLE
                    && distToTarget > HALF_FLIP_MIN_DISTANCE && forwardSpeed < HALF_FLIP_MAX_SPEED) {
                controller.setThrottle(-1);
                controller.setBoost(false);
                controller.setHandbrake(false);
                // Point the tail at the target (steering is reversed while rolling backward)
                float tailAngle = wrapAngle(angle - PI);
                controller.setSteer(clamp((forwardSpeed < 0 ? -tailAngle : tailAngle) * STEER_GAIN, -1, 1));
                if (forwardSpeed < -HALF_FLIP_REVERSE_SPEED) {
                    System.out.println("Half flip!");
                    startHalfFlip(now);
                }
            }
        }

        // 8. Landing: in the air with nothing else to do, level out so we land on our wheels.
        //    On defense (or coming off the back wall) land facing the ball, ready for it;
        //    otherwise land facing the way we're moving, to keep our speed.
        if (!onGround && maneuver == Maneuver.NONE) {
            boolean faceBall = hold || now - backWallJumpTime < 2;
            Vec3 heading = faceBall ? ball.sub(car).flat()
                    : carVel.flat().length() > 500 ? carVel.flat() : forward;
            if (heading.length() > 0) {
                orient(controller, player, heading, now);
            }
        }

        // 9. Press the buttons for any maneuver that's running (every tick, no conditions)
        updateManeuver(controller, player, target, now);

        // 10. Remember this tick's value for next tick
        wasKickoffLastTick = kickoffNow;
        return controller;
    }

    // Decides what to do this tick. Checked top to bottom; the first match wins.
    private Mode chooseMode(boolean kickoff, Intercept ours, Intercept theirs, Intercept goingIn, float shotX,
                            float boost, float ballDepth, Vec3 bigPad, boolean canDribble, boolean opponentControlling,
                            boolean punish) {
        if (kickoff) return Mode.KICKOFF;

        // How far ahead of them we need to be to go for the ball. A loose ball moves predictably, so be
        // aggressive (go even if it's close to a tie); if they have it under control, we need a clear lead.
        // Once we're going, keep going unless they're clearly ahead, instead of bailing the moment they get near.
        float margin;
        if (punish) margin = -PUNISH_AGGRESSION;
        else if (mode == Mode.ATTACK) margin = -ATTACK_COMMIT;
        else if (opponentControlling) margin = FIRST_TO_BALL_MARGIN;
        else margin = -FREE_BALL_AGGRESSION;
        boolean weAreFirst = theirs == null || ours.time() < theirs.time() - margin;
        boolean theyAreShooting = !weAreFirst && !Float.isNaN(shotX) && theirs.time() < SHOT_THREAT_TIME;
        boolean inOurEnd = ballDepth > GOAL_Y - DEFENSIVE_THIRD;

        // A shot is coming at our goal and nobody's controlling it (they hit it, or it's rolling in):
        // block it. Also when we're on the wrong side of the ball: get back to the line first.
        if (goingIn != null && (!opponentControlling || retreating)) return Mode.SAVE;
        // They're bringing the ball at our goal under control: don't wait for the flick or the last touch.
        // Drive straight at them to take away their space and time.
        if (opponentControlling && !retreating && (goingIn != null || inOurEnd)) return Mode.CHALLENGE;
        // They're about to shoot a loose ball at our goal: get in front of the shot (if we're goal-side)
        if (!retreating && theyAreShooting) return Mode.SAVE;
        // Ball on our roof, heading their way: carry it
        if (canDribble) return Mode.DRIBBLE;
        // Wrong side of the ball: get back
        if (retreating) return Mode.RETREAT;
        // We get there first: go hit it
        if (weAreFirst) return Mode.ATTACK;
        // They get there first. If we're close behind, stay on them: pressure them at the ball rather than
        // backing off and handing them space.
        if (ours.time() - theirs.time() < CHALLENGE_WINDOW) return Mode.CHALLENGE;
        // Out of range. If we're low and they aren't threatening, grab boost on the way back
        // (once we've started for a pad, only a real threat pulls us off it).
        float threatTime = mode == Mode.GET_BOOST ? NO_THREAT_TIME - 0.5f : NO_THREAT_TIME;
        boolean noThreat = theirs.time() > threatTime || ballDepth < 0;
        if (boost < LOW_BOOST && noThreat && bigPad != null) return Mode.GET_BOOST;
        // Otherwise shadow: stay goal-side of the ball and wait for them to commit
        return Mode.SHADOW;
    }

    // Rough guess at how long a car needs to drive to a spot: time to turn + time to drive there
    // (loc, vel, forward and maxSpeed are passed in so callers can work them out once per car)
    private static float travelTime(Vec3 loc, Vec3 vel, Vec3 forward, float maxSpeed, Vec3 target) {
        Vec3 toTarget = target.sub(loc).flat();
        float dist = Math.max(toTarget.length() - HIT_DISTANCE, 0);
        Vec3 dir = toTarget.normalized();
        float turnAngle = (float) Math.acos(clamp(forward.dot(dir), -1, 1));
        // Average of the speed we have toward it now and the top speed we'll reach
        float avgSpeed = clamp((vel.dot(dir) + maxSpeed) / 2, 500, maxSpeed);
        return turnAngle * TURN_TIME_PER_RADIAN + dist / avgSpeed;
    }

    // Walks the ball prediction to find the first moment this car can drive to the ball.
    // Returns null if it can't get there within maxTime seconds; if the prediction runs out first,
    // guesses using where the ball ends up. slack = how many seconds late we can be and still count it.
    private Intercept findIntercept(PlayerInfoT p, BallPredictionT prediction, Vec3 ballNow, float now,
                                    float maxTime, float slack) {
        Vec3 loc = Vec3.of(p.getPhysics().getLocation());
        Vec3 vel = Vec3.of(p.getPhysics().getVelocity());
        Vec3 forward = Vec3.forward(p.getPhysics().getRotation()).flat().normalized();
        float maxSpeed = p.getBoost() > 10 ? MAX_SPEED : MAX_DRIVE_SPEED;
        Vec3 last = ballNow;
        float lastTime = 0;
        if (prediction != null && prediction.getSlices() != null) {
            var slices = prediction.getSlices();
            for (int i = 0; i < slices.length; i += 4) {
                float t = slices[i].getGameSeconds() - now;
                if (t < 0) continue;
                if (t > maxTime) return null;
                Vec3 b = Vec3.of(slices[i].getPhysics().getLocation());
                last = b;
                lastTime = t;
                if (b.z() > GROUND_HIT_MAX_HEIGHT) continue;
                if (travelTime(loc, vel, forward, maxSpeed, b) <= t + slack) return new Intercept(t, b);
            }
        }
        // Can't catch it within the prediction: guess using where it ends up
        return new Intercept(Math.max(travelTime(loc, vel, forward, maxSpeed, last), lastTime), last);
    }

    // If the ball is predicted to roll into our goal, returns when and where. Otherwise null.
    private Intercept findOwnGoalCrossing(BallPredictionT prediction, float now, float side) {
        if (prediction == null || prediction.getSlices() == null) return null;
        for (var slice : prediction.getSlices()) {
            float t = slice.getGameSeconds() - now;
            if (t > SAVE_LOOKAHEAD) break;
            Vec3 b = Vec3.of(slice.getPhysics().getLocation());
            // Past our goal line means inside our net (everywhere else is wall)
            if (b.y() * side > GOAL_Y) return new Intercept(t, b);
        }
        return null;
    }

    // Is the way from the ball to their goal clear of the opponent? Clear if they're behind the ball
    // (from their goal's point of view) or well off to the side of the line from the ball to the goal.
    private static boolean isShotLaneOpen(Vec3 ball, Vec3 enemyGoal, Vec3 opponent) {
        Vec3 dir = enemyGoal.sub(ball).flat().normalized();
        Vec3 rel = opponent.sub(ball).flat();
        float along = rel.dot(dir);
        if (along <= 0) return true;
        return rel.sub(dir.scale(along)).length() > SHOT_LANE_CLEARANCE;
    }

    // Where to wait in goal: on the line from the ball to the middle of our goal, just in front of the
    // goal line (so we cover the angle the ball would come from), kept between the posts
    private static Vec3 goalieSpot(Vec3 ball, float side) {
        float ballDepth = ball.y() * side;
        float fraction = SAVE_DEPTH / Math.max(GOAL_Y - ballDepth, 1); // how far from the goal toward the ball
        float x = clamp(ball.x() * fraction, -GOAL_HALF_WIDTH + 100, GOAL_HALF_WIDTH - 100);
        return new Vec3(x, (GOAL_Y - SAVE_DEPTH) * side, 0);
    }

    // Notices each new opponent touch (from their "latest touch" time changing) and remembers when it
    // happened, so we can tell when someone is pushing the ball along with lots of small touches
    private void recordOpponentTouches(PlayerInfoT[] players, float now) {
        for (int i = 0; i < players.length; i++) {
            if (i == index || players[i].getTeam() == team) continue;
            TouchT touch = players[i].getLatestTouch();
            if (touch == null) continue;
            Float seen = lastTouchSeen.put(i, touch.getGameSeconds());
            // (The first time we see a player, their latest touch could be from long ago: don't count it)
            if (seen != null && touch.getGameSeconds() > seen) opponentTouches.addLast(touch.getGameSeconds());
        }
        while (!opponentTouches.isEmpty() && now - opponentTouches.peekFirst() > CONTROL_TOUCH_WINDOW) {
            opponentTouches.removeFirst();
        }
    }

    // Is this opponent controlling the ball: balancing it on their roof, carrying it along (close and moving
    // together), or pushing it along with repeated touches? Then they can flick or turn it any time.
    private boolean isControlling(PlayerInfoT opponent, Vec3 ball, Vec3 ballVel) {
        Vec3 oppLoc = Vec3.of(opponent.getPhysics().getLocation());
        Vec3 rel = ball.sub(oppLoc);
        boolean onTheirRoof = opponent.getAirState() == AirState.OnGround
                && rel.flat().length() < DRIBBLE_MAX_OFFSET
                && rel.z() > DRIBBLE_MIN_HEIGHT && rel.z() < DRIBBLE_MAX_HEIGHT;
        boolean movingTogether = rel.length() < CONTROL_DISTANCE
                && ballVel.sub(Vec3.of(opponent.getPhysics().getVelocity())).length() < CONTROL_REL_SPEED;
        boolean pushingIt = opponentTouches.size() >= CONTROL_TOUCHES && rel.length() < CONTROL_TOUCH_DISTANCE;
        return onTheirRoof || movingTogether || pushingIt;
    }

    // Guesses the opponent's shot: they'll probably hit the ball the way they're driving into it.
    // Returns the x where that shot crosses our goal line, or NaN if it won't be on target.
    private float predictShotX(Vec3 opponent, Vec3 hitBall, float side, float ownGoalY) {
        Vec3 dir = hitBall.sub(opponent).flat();
        if (dir.y() * side <= 0) return Float.NaN; // heading away from our goal
        // Follow the line from the ball until it reaches our goal line
        float s = (ownGoalY - hitBall.y()) / dir.y();
        float x = hitBall.x() + dir.x() * s;
        return Math.abs(x) < GOAL_HALF_WIDTH + SHOT_WIDTH_MARGIN ? x : Float.NaN;
    }

    // The closest active big boost pad that's goal-side of the ball (so grabbing it is "on the way back")
    private Vec3 findBoostPad(GamePacketT packet, Vec3 car, float ballDepth, float side) {
        if (fieldInfo == null || fieldInfo.getBoostPads() == null || packet.getBoostPads() == null) return null;
        var pads = fieldInfo.getBoostPads();
        var states = packet.getBoostPads();
        Vec3 best = null;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < pads.length && i < states.length; i++) {
            if (!pads[i].getIsFullBoost() || !states[i].getIsActive()) continue;
            Vec3 loc = Vec3.of(pads[i].getLocation());
            if (loc.y() * side < ballDepth) continue;
            float d = loc.sub(car).length();
            if (d < bestDist) {
                best = loc;
                bestDist = d;
            }
        }
        return best;
    }

    // The active pad (big or small) that costs the least extra driving on the way to target,
    // or null if every pad is too far out of the way
    private Vec3 findPadOnPath(GamePacketT packet, Vec3 car, Vec3 target) {
        if (fieldInfo == null || fieldInfo.getBoostPads() == null || packet.getBoostPads() == null) return null;
        var pads = fieldInfo.getBoostPads();
        var states = packet.getBoostPads();
        Vec3 carFlat = car.flat();
        float direct = target.sub(carFlat).length();
        Vec3 best = null;
        float bestDetour = Float.MAX_VALUE;
        for (int i = 0; i < pads.length && i < states.length; i++) {
            if (!states[i].getIsActive()) continue;
            Vec3 loc = Vec3.of(pads[i].getLocation()).flat();
            // Extra distance from going car -> pad -> target instead of straight there
            float detour = loc.sub(carFlat).length() + target.sub(loc).length() - direct;
            float maxDetour = pads[i].getIsFullBoost() ? PAD_MAX_DETOUR_BIG : PAD_MAX_DETOUR_SMALL;
            if (detour < maxDetour && detour < bestDetour) {
                best = loc;
                bestDetour = detour;
            }
        }
        return best;
    }

    // Steers the car toward (x, y). Returns the angle to the target, wrapped into -PI..PI.
    private float steerToward(ControllerStateT controller, PlayerInfoT player, float x, float y) {
        float angle = angleTo(player, x, y);
        // Steer harder the further off we are
        controller.setSteer(clamp(angle * STEER_GAIN, -1, 1));
        return angle;
    }

    // How far the car has to turn to face (x, y), wrapped into -PI..PI so we turn the short way.
    // Positive = to the right.
    private static float angleTo(PlayerInfoT player, float x, float y) {
        var carLoc = player.getPhysics().getLocation();
        var desiredYaw = (float) Math.atan2(y - carLoc.getY(), x - carLoc.getX());
        return wrapAngle(desiredYaw - player.getPhysics().getRotation().getYaw());
    }

    // Looks through the ball prediction for the first ball in the air we can reach with an aerial.
    // Returns the game time of that hit, or -1 if there isn't one. inAir = re-planning mid-aerial
    // (no jump left to use, and any reachable ball will do).
    private float findAerialHitTime(PlayerInfoT player, BallPredictionT prediction, float now, float enemyGoalY,
                                    boolean inAir) {
        if (prediction == null || prediction.getSlices() == null) return -1;
        float boost = player.getBoost();
        if (boost < (inAir ? 1 : AERIAL_MIN_BOOST)) return -1;

        Vec3 carLoc = Vec3.of(player.getPhysics().getLocation());
        Vec3 carVel = Vec3.of(player.getPhysics().getVelocity());
        Vec3 forward = Vec3.forward(player.getPhysics().getRotation());
        if (!inAir) forward = forward.flat().normalized();
        float minTime = inAir ? 0.1f : AERIAL_MIN_TIME;
        float margin = inAir ? 1 : AERIAL_ACCEL_MARGIN;

        var slices = prediction.getSlices();
        // Slices are 1/120s apart; checking every 4th is plenty
        for (int i = 0; i < slices.length; i += 4) {
            float timeLeft = slices[i].getGameSeconds() - now;
            if (timeLeft < minTime) continue;
            if (timeLeft > AERIAL_MAX_TIME) break;
            Vec3 ball = Vec3.of(slices[i].getPhysics().getLocation());
            if (ball.z() < AERIAL_MIN_HEIGHT) continue;

            Vec3 target = aerialTarget(ball, enemyGoalY);
            Vec3 needed = neededMovement(carLoc, carVel, target, timeLeft, inAir ? 0 : JUMP_SPEED);
            float accel = aerialAccel(forward, needed, timeLeft);
            // Leave some slack for everything the estimate doesn't model
            if (accel > BOOST_ACCEL * margin) continue;
            if (BOOST_PER_SECOND * timeLeft * (accel / BOOST_ACCEL) > boost) continue;
            // The car can't turn on the ground mid-jump, so the target has to be roughly in front of us
            Vec3 flatNeeded = needed.flat();
            if (!inAir && flatNeeded.length() > 200 && forward.dot(flatNeeded.normalized()) < 0.7f) continue;
            return slices[i].getGameSeconds();
        }
        return -1;
    }

    // Runs one tick of an aerial. Returns false when the aerial is over (hit, missed, or landed).
    private boolean updateAerial(ControllerStateT controller, PlayerInfoT player, BallPredictionT prediction,
                                 Vec3 ballNow, float now, float enemyGoalY) {
        float t = now - aerialStartTime;      // time since we jumped
        float timeLeft = aerialTargetTime - now;
        boolean onGround = player.getAirState() == AirState.OnGround;
        Vec3 carLoc = Vec3.of(player.getPhysics().getLocation());
        aerialClosest = Math.min(aerialClosest, ballNow.sub(carLoc).length());

        // The game says we touched the ball: the aerial did its job
        if (latestTouchTime(player) > aerialTouchBefore) {
            endAerial("hit the ball!");
            return false;
        }
        if (timeLeft < -0.2f || (t > 0.5f && onGround)) {
            endAerial("over");
            return false;
        }

        // Re-read the prediction every tick: it updates if the ball gets touched
        Vec3 ball = predictedBallAt(prediction, aerialTargetTime, ballNow);
        Vec3 target = aerialTarget(ball, enemyGoalY);
        Vec3 carVel = Vec3.of(player.getPhysics().getVelocity());
        Vec3 forward = Vec3.forward(player.getPhysics().getRotation());
        float tLeft = Math.max(timeLeft, 0.05f);
        // Only the part of the jump we haven't gotten yet: carVel already includes the rest
        float jumpLeft = t < 0.3f ? Math.max(JUMP_SPEED - carVel.z(), 0) : 0;
        // Where we'll miss the target by if we stop accelerating now
        Vec3 needed = neededMovement(carLoc, carVel, target, tLeft, jumpLeft);
        float accel = 2 * needed.length() / (tLeft * tLeft);

        // The ball's path changed (someone touched it) or we fell behind, and we can't make this hit anymore.
        // Look for a different point on the ball's path we can still reach; if there's none, give up and
        // let the landing code take over instead of flying off after a ball that isn't there.
        if (t > 0.3f && needed.length() > AERIAL_ABORT_MISS
                && (aerialAccel(forward, needed, tLeft) > BOOST_ACCEL || player.getBoost() < 1)) {
            float newTime = findAerialHitTime(player, prediction, now, enemyGoalY, true);
            if (newTime == -1 || newTime == aerialTargetTime) {
                endAerial("aborted, can't reach the ball");
                return false;
            }
            System.out.println("Aerial re-planned: hitting it in " + (newTime - now) + "s");
            aerialTargetTime = newTime;
            return updateAerial(controller, player, prediction, ballNow, now, enemyGoalY);
        }

        controller.setThrottle(1);

        // Jump: hold for 0.2s, let go briefly, then double jump
        if (t < 0.2f) {
            controller.setJump(true);
        } else if (t < 0.25f) {
            controller.setJump(false);
        } else if (t < 0.3f) {
            controller.setJump(true);
        }

        // Point the nose where we need to accelerate; once we're on course, point it at the ball.
        // During the double jump the stick must stay centered, or it turns into a dodge.
        Vec3 aim = needed.length() > 50 ? needed : ball.sub(carLoc);
        if (t < 0.2f || t >= 0.3f) {
            orient(controller, player, aim, now);
        }

        // Boost when facing roughly the right way and we still need to speed up
        if (forward.dot(aim.normalized()) > AERIAL_BOOST_ALIGN && accel > 150) {
            controller.setBoost(true);
        }
        return true;
    }

    // Stops the aerial and prints how close the car's center got to the ball's center, so misses can be
    // tuned. (A touch happens at about 110 with the roof or underside, up to about 170 with the nose.)
    private void endAerial(String why) {
        System.out.printf("Aerial %s: closest %.0f units from the ball%n", why, aerialClosest);
        aerialStartTime = -1;
    }

    // When this player last touched the ball (game seconds), or -1 if they never have
    private static float latestTouchTime(PlayerInfoT player) {
        TouchT touch = player.getLatestTouch();
        return touch == null ? -1 : touch.getGameSeconds();
    }

    // Where the car's center should be to hit the ball toward the enemy goal
    private Vec3 aerialTarget(Vec3 ball, float enemyGoalY) {
        Vec3 toGoal = new Vec3(0, enemyGoalY, 300).sub(ball).normalized();
        return ball.sub(toGoal.scale(HIT_DISTANCE));
    }

    // How far off the target we'd be after timeLeft seconds if we just coasted (gravity, plus jumpVz
    // of upward speed still to come from jumping). That's the distance boosting has to make up.
    private static Vec3 neededMovement(Vec3 carLoc, Vec3 carVel, Vec3 target, float timeLeft, float jumpVz) {
        Vec3 coastTo = carLoc
                .add(carVel.scale(timeLeft))
                .add(new Vec3(0, 0, jumpVz * Math.max(timeLeft - 0.15f, 0) + 0.5f * GRAVITY * timeLeft * timeLeft));
        return target.sub(coastTo);
    }

    // Acceleration needed to cover 'needed' in timeLeft seconds, counting the time we spend first
    // turning the nose toward it (we can only boost forward). MAX_VALUE if there's no time left to boost.
    private static float aerialAccel(Vec3 forward, Vec3 needed, float timeLeft) {
        float turnAngle = (float) Math.acos(clamp(forward.normalized().dot(needed.normalized()), -1, 1));
        float boostTime = timeLeft - turnAngle * AERIAL_TURN_TIME;
        return boostTime > 0 ? 2 * needed.length() / (boostTime * boostTime) : Float.MAX_VALUE;
    }

    // The predicted ball location at a game time (falls back to where the ball is now)
    private Vec3 predictedBallAt(BallPredictionT prediction, float gameTime, Vec3 fallback) {
        if (prediction == null || prediction.getSlices() == null) return fallback;
        for (var slice : prediction.getSlices()) {
            if (slice.getGameSeconds() >= gameTime) {
                return Vec3.of(slice.getPhysics().getLocation());
            }
        }
        return fallback;
    }

    // Air control: turns the car's nose toward 'aim' while keeping the roof pointed up.
    // Uses how far off we are, plus how fast that's changing so we don't overshoot.
    private void orient(ControllerStateT controller, PlayerInfoT player, Vec3 aim, float now) {
        var rot = player.getPhysics().getRotation();
        Vec3 forward = Vec3.forward(rot);
        Vec3 right = Vec3.right(rot);
        Vec3 up = Vec3.up(rot);

        float pitchErr = (float) Math.atan2(aim.dot(up), aim.dot(forward));
        float yawErr = (float) Math.atan2(aim.dot(right), aim.dot(forward));
        // Roll so the right side is level. Skip it when the nose points nearly straight up or down,
        // because "roof up" doesn't mean anything then.
        float rollErr = Math.abs(forward.z()) < 0.9f ? (float) Math.atan2(right.z(), up.z()) : 0;

        // How fast we're spinning toward the target on each axis, from how fast the error shrinks
        // (0 on the first tick of a new turn). The change is wrapped so an error crossing +-PI
        // (target directly behind) doesn't look like a huge spin.
        float dt = now - lastOrientTime;
        boolean fresh = lastOrientTime == -1 || dt <= 0 || dt > 0.1f;
        float pitchSpin = fresh ? 0 : clamp(-wrapAngle(pitchErr - lastPitchErr) / dt, -10, 10);
        float yawSpin = fresh ? 0 : clamp(-wrapAngle(yawErr - lastYawErr) / dt, -10, 10);
        float rollSpin = fresh ? 0 : clamp(-wrapAngle(rollErr - lastRollErr) / dt, -10, 10);

        controller.setPitch(airStick(pitchErr, pitchSpin, PITCH_ACCEL));
        controller.setYaw(airStick(yawErr, yawSpin, YAW_ACCEL));
        controller.setRoll(airStick(rollErr, rollSpin, ROLL_ACCEL));

        lastPitchErr = pitchErr;
        lastYawErr = yawErr;
        lastRollErr = rollErr;
        lastOrientTime = now;
    }

    // Stick input for one air axis. The spin we want is the fastest one we can still brake to a stop
    // from by the time we reach the target (v = sqrt(2 a d)); push the stick toward that spin.
    private static float airStick(float err, float spin, float accel) {
        float want = Math.signum(err) * (float) Math.sqrt(2 * accel * AIR_BRAKE_FRACTION * Math.abs(err));
        return clamp((want - spin) * AIR_SPIN_GAIN, -1, 1);
    }

    // The fastest speed at which the car can still turn as tightly as 'curvature' (1 / turning radius)
    private static float speedForCurvature(float curvature) {
        if (curvature >= TURN_CURVATURES[0]) return 0;
        for (int i = 1; i < TURN_SPEEDS.length; i++) {
            if (curvature >= TURN_CURVATURES[i]) {
                // Between two table entries: interpolate
                float f = (TURN_CURVATURES[i - 1] - curvature) / (TURN_CURVATURES[i - 1] - TURN_CURVATURES[i]);
                return TURN_SPEEDS[i - 1] + f * (TURN_SPEEDS[i] - TURN_SPEEDS[i - 1]);
            }
        }
        return MAX_SPEED;
    }

    // Wraps an angle into -PI..PI
    private static float wrapAngle(float angle) {
        return (float) Math.IEEEremainder(angle, 2 * PI);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // Call when you want to front flip. Ignored if a maneuver is already running.
    public void startFrontFlip(float now) {
        startFlip(now, -1, 0, FLIP_DODGE_TIME);
    }

    // Flip toward a target 'angle' radians off the nose (positive = right), e.g. the angle steerToward returns
    private void startAimedFlip(float now, float angle) {
        float pitch = (float) -Math.cos(angle);
        float yaw = (float) Math.sin(angle);
        // Push the stick all the way in that direction
        float scale = Math.max(Math.abs(pitch), Math.abs(yaw));
        startFlip(now, pitch / scale, yaw / scale, FLIP_DODGE_TIME);
    }

    // A flip in any direction: pitch -1 = forward, +1 = backward; yaw +-1 = sideways.
    // dodgeTime is when the second jump happens; later means the car rises more first.
    private void startFlip(float now, float pitch, float yaw, float dodgeTime) {
        if (!startManeuver(Maneuver.FLIP, now)) return;
        flipPitch = pitch;
        flipYaw = yaw;
        flipDodgeTime = dodgeTime;
    }

    // Speed flip: a diagonal front flip, cancelled so the nose stays level, while boosting.
    // Faster than a front flip because we never stop boosting forward. dir = +1 right, -1 left.
    private void startSpeedFlip(float now, float dir) {
        if (startManeuver(Maneuver.SPEED_FLIP, now)) maneuverDir = dir;
    }

    // Half flip: backflip, cancel it halfway (nose up, roof down), then roll upright.
    // Turns the car around 180 degrees much faster than driving a circle.
    private void startHalfFlip(float now) {
        startManeuver(Maneuver.HALF_FLIP, now);
    }

    // Jump save: finds when the ball will pass over/in front of us and how high it'll be, then starts
    // a jump (or double jump if it's higher) at the moment that puts us at that height just in time
    private void tryJumpSave(BallPredictionT prediction, Vec3 car, float now) {
        if (prediction == null || prediction.getSlices() == null) return;
        for (var slice : prediction.getSlices()) {
            float t = slice.getGameSeconds() - now;
            if (t < 0) continue;
            if (t > JUMP_SAVE_LOOKAHEAD) return;
            Vec3 b = Vec3.of(slice.getPhysics().getLocation());
            if (b.sub(car).flat().length() > JUMP_SAVE_REACH) continue;
            // How far our center has to rise for the roof to touch the ball
            float rise = b.z() - CAR_REST_HEIGHT - HIT_DISTANCE;
            if (rise <= 0) return; // low enough to drive or flip into
            boolean dbl = timeToJumpHeight(rise, false) < 0;
            float needed = timeToJumpHeight(rise, dbl);
            if (needed < 0) return; // too high to jump to: that's the aerial's job
            // Jump once waiting any longer would get us there late
            if (t <= needed + 1 / 60f) startManeuver(dbl ? Maneuver.DOUBLE_JUMP : Maneuver.JUMP, now);
            return;
        }
    }

    // Seconds after starting a jump until the car's center has risen 'height', or -1 if it never does.
    // Steps through the same button timing the JUMP / DOUBLE_JUMP maneuvers use.
    private static float timeToJumpHeight(float height, boolean doubleJump) {
        float dt = 1 / 120f;
        float z = 0, vz = JUMP_IMPULSE;
        boolean doubled = false;
        for (float t = 0; t < 1.5f; t += dt) {
            if (z >= height) return t;
            if (doubleJump && !doubled && t >= DOUBLE_JUMP_TIME) {
                vz += JUMP_IMPULSE;
                doubled = true;
            }
            vz += ((t < JUMP_HOLD_TIME ? JUMP_HOLD_ACCEL : 0) + GRAVITY) * dt;
            if (vz < 0 && (doubled || !doubleJump)) return -1; // past the top
            z += vz * dt;
        }
        return -1;
    }

    // Starts maneuver m unless one is already running. Returns whether it started.
    private boolean startManeuver(Maneuver m, float now) {
        if (maneuver != Maneuver.NONE) return false;
        maneuver = m;
        maneuverStartTime = now;
        return true;
    }

    // Call every tick. Presses the right buttons for the current point in the maneuver.
    private void updateManeuver(ControllerStateT controller, PlayerInfoT player, Vec3 target, float now) {
        if (maneuver == Maneuver.NONE) return;
        float t = now - maneuverStartTime;
        boolean onGround = player.getAirState() == AirState.OnGround;

        switch (maneuver) {
            case JUMP, DOUBLE_JUMP -> {
                // Hold jump as long as it adds height, then (for a double jump) let go and press it again.
                // The stick stays centered so the second press is a jump, not a dodge.
                boolean isDouble = maneuver == Maneuver.DOUBLE_JUMP;
                if (t < JUMP_HOLD_TIME) {
                    controller.setJump(true);
                } else if (isDouble && t >= DOUBLE_JUMP_TIME && t < DOUBLE_JUMP_TIME + 0.05f) {
                    controller.setJump(true);
                } else if (!isDouble || t >= DOUBLE_JUMP_TIME + 0.05f) {
                    maneuver = Maneuver.NONE; // the landing code takes it from here
                }
            }
            case FLIP -> {
                if (t < FLIP_JUMP_TIME) {
                    controller.setJump(true);
                } else if (t < flipDodgeTime) {
                    controller.setJump(false);
                } else if (t < flipDodgeTime + 0.05f) {
                    controller.setJump(true);
                    controller.setPitch(flipPitch);
                    controller.setYaw(flipYaw);
                } else if (t > flipDodgeTime + 0.65f) {
                    // (in between: do nothing, let the flip finish)
                    maneuver = Maneuver.NONE;
                }
            }
            case SPEED_FLIP -> {
                controller.setThrottle(1);
                controller.setBoost(true);
                if (t < SPEED_FLIP_JUMP_TIME) {
                    controller.setJump(true);
                } else if (t < SPEED_FLIP_DODGE_TIME) {
                    controller.setJump(false);
                } else if (t < SPEED_FLIP_CANCEL_TIME) {
                    // Diagonal forward dodge
                    controller.setJump(true);
                    controller.setPitch(-1);
                    controller.setYaw(maneuverDir * SPEED_FLIP_YAW);
                } else if (t < SPEED_FLIP_END_TIME && !(t > 0.3f && onGround)) {
                    // Cancel: pull back so the nose stays level, and roll against the flip to land flat
                    controller.setPitch(1);
                    controller.setRoll(-maneuverDir);
                } else {
                    maneuver = Maneuver.NONE;
                }
            }
            case HALF_FLIP -> {
                if (t < HALF_FLIP_CANCEL_TIME) {
                    controller.setThrottle(-1);
                    if (t < FLIP_JUMP_TIME) {
                        controller.setJump(true);
                    } else if (t >= FLIP_DODGE_TIME && t < FLIP_DODGE_TIME + 0.05f) {
                        // Backflip
                        controller.setJump(true);
                        controller.setPitch(1);
                    }
                } else if (!onGround && t < 1.5f) {
                    // Roll upright and face the target. orient picks whichever roll direction is shorter,
                    // so we come out of it smoothly toward the target instead of always rolling one way.
                    Vec3 carLoc = Vec3.of(player.getPhysics().getLocation());
                    orient(controller, player, target.sub(carLoc).flat(), now);
                    // Hold the stick forward a while longer: that's what cancels the backflip halfway
                    if (t < HALF_FLIP_CANCEL_TIME + HALF_FLIP_CANCEL_HOLD) controller.setPitch(-1);
                    controller.setThrottle(1);
                } else {
                    maneuver = Maneuver.NONE;
                }
            }
            default -> { }
        }
    }

    @Override
    public void onMatchCommReceived(MatchCommT comm) { }

    @Override
    public void onRetire() { }
}
