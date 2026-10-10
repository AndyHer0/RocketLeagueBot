package rocketleague;
import rlbot.commons.agents.Bot;
import rlbot.commons.protocol.RLBotInterface;
import rlbot.flat.*;
import java.lang.Float; // rlbot.flat has its own Float class; we want Java's

public class ExampleBot implements Bot {

    public static final float PI = (float) Math.PI;

    // ===== Game facts (set by Rocket League, don't tune these) =====
    private static final float GOAL_Y = 5120;            // goal lines are at y = +-5120
    private static final float FIELD_X = 4096;           // side walls are at x = +-4096
    private static final float GOAL_HALF_WIDTH = 893;    // goal posts are at x = +-893
    private static final float GRAVITY = -650;
    private static final float BOOST_ACCEL = 1060;       // boost (~992) + throttle in the air (~67)
    private static final float BOOST_PER_SECOND = 33.3f;
    private static final float MAX_SPEED = 2300;         // top speed with boost
    private static final float MAX_DRIVE_SPEED = 1410;   // top speed without boost
    private static final float SUPERSONIC_SPEED = 2200;  // boosting past this barely speeds you up

    // ===== Tuning: driving =====
    private static final float STEER_GAIN = 2;            // steer = angle * this (full lock beyond 1/this radians)
    private static final float HANDBRAKE_ANGLE = 2.0f;    // powerslide when the target is this far off (radians)
    private static final float BOOST_ANGLE = 0.3f;        // only boost when the target is within this angle
    private static final float BOOST_MIN_DISTANCE = 1500; // only boost when the target is further than this

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
    private static final float HALF_FLIP_CANCEL_TIME = 0.33f;   // push forward to stop the backflip halfway
    private static final float HALF_FLIP_ROLL_TIME = 0.3f;      // then air roll for this long before leveling out

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
    private static final float RETREAT_START = 100;   // retreat once the ball is this far goal-side of us
    private static final float RETREAT_STOP = 300;    // stop once we're this far goal-side of the ball
    private static final float RETREAT_POST_X = 800;  // x of the post we retreat to
    private static final float RETREAT_DEPTH = 400;   // how far in front of the goal line to retreat to

    // ===== Tuning: opponent awareness =====
    private static final float FIRST_TO_BALL_MARGIN = 0.2f;   // we must be this many seconds ahead to attack
    private static final float CHALLENGE_WINDOW = 0.6f;       // they're ahead by less than this: challenge, else shadow
    private static final float SHADOW_DISTANCE = 1500;        // how far goal-side of the ball to shadow
    private static final float HOLD_DISTANCE = 600;           // within this of a shadow/save spot, ease off and face the ball
    private static final float LOW_BOOST = 30;                // below this, grab a big pad when it's safe
    private static final float NO_THREAT_TIME = 1.5f;         // opponent this far from the ball isn't a threat
    private static final float SHOT_THREAT_TIME = 0.6f;       // opponent this close to hitting it: get in front of the shot
    private static final float SHOT_WIDTH_MARGIN = 200;       // count shots this far outside the posts as on target
    private static final float SAVE_DEPTH = 200;              // how far in front of the goal line to stand for a save
    private static final float SAVE_LOOKAHEAD = 4f;           // look this many seconds ahead for balls going in
    private static final float TURN_TIME_PER_RADIAN = 0.3f;   // arrival estimate: seconds lost per radian of turning
    private static final float GROUND_HIT_MAX_HEIGHT = 250;   // arrival estimate: balls above this can't be driven into

    // ===== Tuning: aerials =====
    private static final float JUMP_SPEED = 800;          // rough upward speed from a held jump + double jump
    private static final float HIT_DISTANCE = 110;        // car center to ball center when we touch it
    private static final float AERIAL_MIN_HEIGHT = 400;   // lower balls are for the ground game
    private static final float AERIAL_MIN_TIME = 0.6f;    // need at least this long to jump and turn
    private static final float AERIAL_MAX_TIME = 3f;      // don't trust the prediction further ahead than this
    private static final float AERIAL_ACCEL_MARGIN = 0.8f;// only go if we need under this fraction of max boost accel
    private static final float AERIAL_MIN_BOOST = 20;
    private static final float AERIAL_BOOST_ALIGN = 0.9f; // boost when nose . aim > this (0.9 = within ~25 degrees)

    // ===== Tuning: air control (orient) =====
    // P = how hard to turn per radian off target, D = how much to ease off while already turning
    private static final float PITCH_P = 3, PITCH_D = 1.0f;
    private static final float YAW_P = 3, YAW_D = 1.0f;
    private static final float ROLL_P = 3, ROLL_D = 0.5f;

    private enum Mode { KICKOFF, ATTACK, DRIBBLE, CHALLENGE, SHADOW, RETREAT, GET_BOOST, SAVE }

    // Timed button sequences that run over several ticks
    private enum Maneuver { NONE, FLIP, SPEED_FLIP, HALF_FLIP }

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
    private Mode lastMode = null;
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
            aerialStartTime = -1;
            maneuver = Maneuver.NONE;
        }

        // 2. Read the situation: who gets to the ball first, is it going in, where's the opponent shooting
        Intercept ours = findIntercept(player, ballPrediction, ball, now);
        PlayerInfoT opponent = null;
        Intercept theirs = null;
        var players = packet.getPlayers();
        for (int i = 0; i < players.length; i++) {
            if (i == index || players[i].getTeam() == team) continue;
            Intercept it = findIntercept(players[i], ballPrediction, ball, now);
            // In 2v2/3v3, worry about whichever opponent gets there first
            if (theirs == null || it.time() < theirs.time()) {
                opponent = players[i];
                theirs = it;
            }
        }
        Intercept goingIn = findOwnGoalCrossing(ballPrediction, now, side);
        float shotX = Float.NaN;
        float opponentDist = Float.MAX_VALUE;
        if (opponent != null) {
            Vec3 oppLoc = Vec3.of(opponent.getPhysics().getLocation());
            shotX = predictShotX(oppLoc, theirs.ball(), side, ownGoalY);
            opponentDist = oppLoc.sub(car).length();
        }

        // Is the ball sitting on our roof, and are we pointed at their half?
        Vec3 ballRel = ball.sub(car);
        boolean ballOnCar = onGround
                && ballRel.flat().length() < DRIBBLE_MAX_OFFSET
                && ballRel.z() > DRIBBLE_MIN_HEIGHT && ballRel.z() < DRIBBLE_MAX_HEIGHT;
        boolean facingTheirHalf = forward.y() * -side > 0;

        // Defense: we're on the wrong side of the ball (it's between us and our goal),
        // so hitting it now would push it toward our own net. Go back first.
        // The two thresholds keep it from flickering between modes at the boundary.
        if (!retreating && carDepth < ballDepth - RETREAT_START) {
            retreating = true;
        } else if (retreating && carDepth > ballDepth + RETREAT_STOP) {
            retreating = false;
        }

        Vec3 bigPad = findBoostPad(packet, car, ballDepth, side);
        Mode mode = chooseMode(kickoffNow, ours, theirs, goingIn, shotX, boost, ballDepth, bigPad,
                ballOnCar && facingTheirHalf);
        if (mode != lastMode) {
            System.out.println("Mode: " + mode);
            lastMode = mode;
        }

        // 3. Aerial: start one if a ball in the air is reachable, then let it take over
        boolean goingForBall = mode == Mode.ATTACK || mode == Mode.CHALLENGE || mode == Mode.SAVE;
        if (goingForBall && aerialStartTime == -1 && onGround && maneuver == Maneuver.NONE) {
            float hitTime = findAerialHitTime(player, ballPrediction, now, enemyGoalY);
            if (hitTime != -1) {
                System.out.println("Aerial! Hitting the ball in " + (hitTime - now) + "s");
                aerialStartTime = now;
                aerialTargetTime = hitTime;
            }
        }
        if (aerialStartTime != -1
                && updateAerial(controller, player, ballPrediction, ball, now, enemyGoalY)) {
            wasKickoffLastTick = kickoffNow;
            return controller;
        }

        // 4. Pick where to drive. "hold" means: once close to the spot, wait there facing the ball.
        boolean canSaveByHitting = goingIn != null && ours.time() < goingIn.time();
        boolean hold = mode == Mode.SHADOW || (mode == Mode.SAVE && !canSaveByHitting);
        Vec3 target = switch (mode) {
            case KICKOFF -> ball.flat();
            case SAVE -> {
                // We can reach it before it goes in: hit it, from the goal side we're already on
                if (canSaveByHitting) yield ours.ball().flat();
                // Otherwise stand on the goal line where the ball (or their shot) will cross
                float crossX = goingIn != null ? goingIn.ball().x() : shotX;
                yield new Vec3(clamp(crossX, -GOAL_HALF_WIDTH + 100, GOAL_HALF_WIDTH - 100),
                        ownGoalY - SAVE_DEPTH * side, 0);
            }
            // Head to the post on our side of the ball so we don't drive through it
            case RETREAT -> new Vec3(car.x() >= ball.x() ? RETREAT_POST_X : -RETREAT_POST_X,
                    ownGoalY - RETREAT_DEPTH * side, 0);
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
            // We're goal-side and it's close: drive straight at where we'll meet the ball to block them
            case CHALLENGE -> ours.ball().flat();
            case GET_BOOST -> bigPad.flat();
            // Sit between the ball and our goal, a set distance back, and match their pace
            // (the field clamp below keeps it out of the goal)
            case SHADOW -> ball.add(new Vec3(0, ownGoalY, 0).sub(ball).flat().normalized()
                    .scale(SHADOW_DISTANCE)).flat();
        };
        if (mode != Mode.KICKOFF) {
            // Keep the target inside the field
            target = new Vec3(clamp(target.x(), -FIELD_X + 200, FIELD_X - 200),
                    clamp(target.y(), -GOAL_Y + 200, GOAL_Y - 200), 0);
        }
        float distToTarget = target.sub(car.flat()).length();

        // Boost management: on longer trips, swing through a pad if it's close to our path
        boolean padsOk = mode == Mode.ATTACK || mode == Mode.SHADOW || mode == Mode.RETREAT;
        if (padsOk && boost < PAD_TOP_UP && distToTarget > PAD_MIN_TRIP
                && !(hold && distToTarget < HOLD_DISTANCE)) {
            Vec3 pad = findPadOnPath(packet, car, target);
            if (pad != null) {
                target = pad;
                distToTarget = target.sub(car.flat()).length();
            }
        }

        // 5. Drive there
        float angle;
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
        } else if (hold && distToTarget < HOLD_DISTANCE) {
            // Close to our spot: face the ball and creep forward/back to stay on the spot
            angle = steerToward(controller, player, ball.x(), ball.y());
            float throttle = clamp(forward.dot(target.sub(car.flat())) / 300, -1, 1);
            controller.setThrottle(throttle);
            // Steering works backwards in reverse, so flip it to keep turning toward the ball
            if (throttle < 0) controller.setSteer(-controller.getSteer());
        } else {
            angle = steerToward(controller, player, target.x(), target.y());
        }

        if (mode == Mode.KICKOFF) {
            // 6a. Speed flip (or plain front flip) shortly into the kickoff
            if (!didMomentumFlip && now - kickoffStartTime > KICKOFF_FLIP_TIME) {
                if (USE_SPEED_FLIP_KICKOFF) {
                    startSpeedFlip(now, angle >= 0 ? 1 : -1);
                } else {
                    startFrontFlip(now);
                }
                didMomentumFlip = true;
            }
            // 6b. Flip into the ball when close
            if (distToBall < KICKOFF_FLIP_DISTANCE) {
                startFrontFlip(now);
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
            // 7b. Sharp turn: powerslide so we come around faster
            if (Math.abs(angle) > HANDBRAKE_ANGLE && onGround) {
                controller.setHandbrake(true);
            }
            // 7c. Boost when lined up and far away, but not past supersonic (wasted)
            //     and not below our reserve unless we're in a hurry
            boolean urgent = mode == Mode.SAVE || mode == Mode.CHALLENGE || mode == Mode.RETREAT;
            if (Math.abs(angle) < BOOST_ANGLE && distToTarget > BOOST_MIN_DISTANCE
                    && onGround && maneuver == Maneuver.NONE && forwardSpeed < SUPERSONIC_SPEED
                    && (urgent || boost > BOOST_RESERVE)) {
                controller.setBoost(true);
            }
            // 7d. Shot: front flip into the ball when it's close, low, and straight ahead
            if (goingForBall && onGround && distToBall < FLIP_HIT_DISTANCE
                    && ball.z() < FLIP_HIT_MAX_BALL_HEIGHT && Math.abs(angle) < FLIP_HIT_ANGLE) {
                startFrontFlip(now);
            }
            // 7e. Half flip: the target is far behind us and we're slow, so flip around instead of
            //     driving a big circle
            if (onGround && maneuver == Maneuver.NONE && !hold && Math.abs(angle) > HALF_FLIP_ANGLE
                    && distToTarget > HALF_FLIP_MIN_DISTANCE && forwardSpeed < HALF_FLIP_MAX_SPEED) {
                System.out.println("Half flip!");
                startHalfFlip(now);
            }
        }

        // 8. Landing: in the air with nothing else to do, level out so we land on our wheels
        if (!onGround && maneuver == Maneuver.NONE) {
            Vec3 heading = carVel.flat().length() > 500 ? carVel.flat() : forward;
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
    private Mode chooseMode(boolean kickoff, Intercept ours, Intercept theirs, Intercept goingIn,
                            float shotX, float boost, float ballDepth, Vec3 bigPad, boolean canDribble) {
        if (kickoff) return Mode.KICKOFF;

        boolean weAreFirst = theirs == null || ours.time() < theirs.time() - FIRST_TO_BALL_MARGIN;
        boolean theyAreShooting = !weAreFirst && !Float.isNaN(shotX) && theirs.time() < SHOT_THREAT_TIME;

        // The ball is going in, or they're about to shoot it in: save it (if we're goal-side)
        if (!retreating && (goingIn != null || theyAreShooting)) return Mode.SAVE;
        // Ball on our roof, heading their way: carry it
        if (canDribble) return Mode.DRIBBLE;
        // Wrong side of the ball: get back
        if (retreating) return Mode.RETREAT;
        // We get there first: go hit it
        if (weAreFirst) return Mode.ATTACK;
        // They get there first. If we're low and they aren't threatening, grab boost on the way back.
        boolean noThreat = theirs.time() > NO_THREAT_TIME || ballDepth < 0;
        if (boost < LOW_BOOST && noThreat && bigPad != null) return Mode.GET_BOOST;
        // It's close: meet them at the ball. Otherwise hang back and wait for them to commit.
        if (ours.time() - theirs.time() < CHALLENGE_WINDOW) return Mode.CHALLENGE;
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

    // Walks the ball prediction to find the first moment this car can drive to the ball
    private Intercept findIntercept(PlayerInfoT p, BallPredictionT prediction, Vec3 ballNow, float now) {
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
                Vec3 b = Vec3.of(slices[i].getPhysics().getLocation());
                last = b;
                lastTime = t;
                if (b.z() > GROUND_HIT_MAX_HEIGHT) continue;
                if (travelTime(loc, vel, forward, maxSpeed, b) <= t) return new Intercept(t, b);
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
        var carLoc = player.getPhysics().getLocation();
        var curYaw = player.getPhysics().getRotation().getYaw();
        var desiredYaw = (float) Math.atan2(y - carLoc.getY(), x - carLoc.getX());
        // How far we need to turn, wrapped into -PI..PI so we turn the short way
        float angle = wrapAngle(desiredYaw - curYaw);
        // Steer harder the further off we are
        controller.setSteer(clamp(angle * STEER_GAIN, -1, 1));
        return angle;
    }

    // Looks through the ball prediction for the first ball in the air we can reach with an aerial.
    // Returns the game time of that hit, or -1 if there isn't one.
    private float findAerialHitTime(PlayerInfoT player, BallPredictionT prediction, float now, float enemyGoalY) {
        if (prediction == null || prediction.getSlices() == null) return -1;
        float boost = player.getBoost();
        if (boost < AERIAL_MIN_BOOST) return -1;

        Vec3 carLoc = Vec3.of(player.getPhysics().getLocation());
        Vec3 carVel = Vec3.of(player.getPhysics().getVelocity());
        Vec3 forward = Vec3.forward(player.getPhysics().getRotation()).flat().normalized();

        var slices = prediction.getSlices();
        // Slices are 1/120s apart; checking every 4th is plenty
        for (int i = 0; i < slices.length; i += 4) {
            float timeLeft = slices[i].getGameSeconds() - now;
            if (timeLeft < AERIAL_MIN_TIME) continue;
            if (timeLeft > AERIAL_MAX_TIME) break;
            Vec3 ball = Vec3.of(slices[i].getPhysics().getLocation());
            if (ball.z() < AERIAL_MIN_HEIGHT) continue;

            Vec3 target = aerialTarget(ball, enemyGoalY);
            Vec3 needed = neededMovement(carLoc, carVel, target, timeLeft, true);
            float accel = 2 * needed.length() / (timeLeft * timeLeft);
            // Leave some slack, since we lose time turning the car to face the right way
            if (accel > BOOST_ACCEL * AERIAL_ACCEL_MARGIN) continue;
            if (BOOST_PER_SECOND * timeLeft * (accel / BOOST_ACCEL) > boost) continue;
            // The car can't turn on the ground mid-jump, so the target has to be roughly in front of us
            Vec3 flatNeeded = needed.flat();
            if (flatNeeded.length() > 200 && forward.dot(flatNeeded.normalized()) < 0.7f) continue;
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

        if (timeLeft < -0.2f || (t > 0.5f && onGround)) {
            aerialStartTime = -1;
            return false;
        }

        // Re-read the prediction every tick: it updates if the ball gets touched
        Vec3 ball = predictedBallAt(prediction, aerialTargetTime, ballNow);
        Vec3 target = aerialTarget(ball, enemyGoalY);
        Vec3 carLoc = Vec3.of(player.getPhysics().getLocation());
        Vec3 carVel = Vec3.of(player.getPhysics().getVelocity());
        float tLeft = Math.max(timeLeft, 0.05f);
        boolean jumping = t < 0.3f;
        // Where we'll miss the target by if we stop accelerating now
        Vec3 needed = neededMovement(carLoc, carVel, target, tLeft, jumping);
        float accel = 2 * needed.length() / (tLeft * tLeft);

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
        Vec3 forward = Vec3.forward(player.getPhysics().getRotation());
        if (forward.dot(aim.normalized()) > AERIAL_BOOST_ALIGN && accel > 150) {
            controller.setBoost(true);
        }
        return true;
    }

    // Where the car's center should be to hit the ball toward the enemy goal
    private Vec3 aerialTarget(Vec3 ball, float enemyGoalY) {
        Vec3 toGoal = new Vec3(0, enemyGoalY, 300).sub(ball).normalized();
        return ball.sub(toGoal.scale(HIT_DISTANCE));
    }

    // How far off the target we'd be after timeLeft seconds if we just coasted (gravity only).
    // That's the distance boosting has to make up.
    private Vec3 neededMovement(Vec3 carLoc, Vec3 carVel, Vec3 target, float timeLeft, boolean addJump) {
        float vz = addJump ? JUMP_SPEED : 0;
        Vec3 coastTo = carLoc
                .add(carVel.scale(timeLeft))
                .add(new Vec3(0, 0, vz * Math.max(timeLeft - 0.15f, 0) + 0.5f * GRAVITY * timeLeft * timeLeft));
        return target.sub(coastTo);
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

        // How fast each error is changing (0 on the first tick of a new turn). The change is wrapped
        // so an error crossing +-PI (target directly behind) doesn't look like a huge spin.
        float dt = now - lastOrientTime;
        boolean fresh = lastOrientTime == -1 || dt <= 0 || dt > 0.1f;
        float pitchRate = fresh ? 0 : clamp(wrapAngle(pitchErr - lastPitchErr) / dt, -10, 10);
        float yawRate = fresh ? 0 : clamp(wrapAngle(yawErr - lastYawErr) / dt, -10, 10);
        float rollRate = fresh ? 0 : clamp(wrapAngle(rollErr - lastRollErr) / dt, -10, 10);

        controller.setPitch(clamp(PITCH_P * pitchErr + PITCH_D * pitchRate, -1, 1));
        controller.setYaw(clamp(YAW_P * yawErr + YAW_D * yawRate, -1, 1));
        controller.setRoll(clamp(ROLL_P * rollErr + ROLL_D * rollRate, -1, 1));

        lastPitchErr = pitchErr;
        lastYawErr = yawErr;
        lastRollErr = rollErr;
        lastOrientTime = now;
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

    // A flip in any direction: pitch -1 = forward, +1 = backward; yaw +-1 = sideways.
    // dodgeTime is when the second jump happens; later means the car rises more first.
    private void startFlip(float now, float pitch, float yaw, float dodgeTime) {
        if (maneuver != Maneuver.NONE) return;
        maneuver = Maneuver.FLIP;
        maneuverStartTime = now;
        flipPitch = pitch;
        flipYaw = yaw;
        flipDodgeTime = dodgeTime;
    }

    // Speed flip: a diagonal front flip, cancelled so the nose stays level, while boosting.
    // Faster than a front flip because we never stop boosting forward. dir = +1 right, -1 left.
    private void startSpeedFlip(float now, float dir) {
        if (maneuver != Maneuver.NONE) return;
        maneuver = Maneuver.SPEED_FLIP;
        maneuverStartTime = now;
        maneuverDir = dir;
    }

    // Half flip: backflip, cancel it halfway (nose up, roof down), then roll upright.
    // Turns the car around 180 degrees much faster than driving a circle.
    private void startHalfFlip(float now) {
        if (maneuver != Maneuver.NONE) return;
        maneuver = Maneuver.HALF_FLIP;
        maneuverStartTime = now;
    }

    // Call every tick. Presses the right buttons for the current point in the maneuver.
    private void updateManeuver(ControllerStateT controller, PlayerInfoT player, Vec3 target, float now) {
        if (maneuver == Maneuver.NONE) return;
        float t = now - maneuverStartTime;
        boolean onGround = player.getAirState() == AirState.OnGround;

        switch (maneuver) {
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
                if (t < 0.1f) {
                    controller.setJump(true);
                    controller.setThrottle(-1);
                } else if (t < 0.15f) {
                    controller.setJump(false);
                    controller.setThrottle(-1);
                } else if (t < 0.2f) {
                    // Backflip
                    controller.setJump(true);
                    controller.setPitch(1);
                    controller.setThrottle(-1);
                } else if (t < HALF_FLIP_CANCEL_TIME) {
                    controller.setThrottle(-1);
                } else if (t < HALF_FLIP_CANCEL_TIME + HALF_FLIP_ROLL_TIME) {
                    // Cancel by pushing forward, and air roll to get the wheels back down
                    controller.setPitch(-1);
                    controller.setRoll(1);
                    controller.setThrottle(1);
                } else if (!onGround && t < 1.5f) {
                    // Finish leveling out, facing the target
                    Vec3 carLoc = Vec3.of(player.getPhysics().getLocation());
                    orient(controller, player, target.sub(carLoc).flat(), now);
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
