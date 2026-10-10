# Rocket League Bot (Java)

A custom Rocket League bot written in Java, built on the [RLBot](https://rlbot.org/) v5 framework.

The bot connects to Rocket League through RLBot, reads the live game state many times per second, and decides what controller inputs to send: throttle, steering, jumping, boosting. The goal is to grow it from a simple "drive at the ball" bot into one that can shoot, defend, and manage boost, and later to compare it against a version trained with reinforcement learning.

> **Status:** In testing and tuning against a human opponent. The bot speed flips on kickoff and presses right after it, lines up shots, hits aerials reliably, saves shots with timed jumps, reads whether the opponent is dribbling or has just shot, and picks between attacking, challenging, shadowing, saving, retreating, and grabbing boost. Mode changes, aerial results, and special moves are printed to the console to help tuning.

---

## How it works

Three programs run at the same time during a match:

| Component | Role |
|---|---|
| **Rocket League** | The game itself. |
| **RLBot (v5)** | The middleman. Reads game data out of Rocket League and passes bot inputs back in. |
| **This bot** | A separate Java program that connects to RLBot. |

Every tick, RLBot sends the bot a **game packet**: a snapshot containing the position, velocity, and rotation of the ball and every car, plus boost amounts and match info. The bot responds with a **controller state** describing what buttons to press. Then the next packet arrives and the cycle repeats.

All of the bot's decision-making lives in `ExampleBot.getOutput(...)`, which is called once per tick.

### Current behavior

**Driving:** each tick the bot picks a target point and `steerToward(...)` drives at it.

1. Use `Math.atan2` to find the angle (yaw) from the car to the target.
2. Compare that to the direction the car is currently facing, wrapping the difference into the range −π to π so the car always turns the shorter way.
3. Steer in proportion to that angle (full lock beyond ~30°), so the car drives smoothly instead of jittering left and right.
4. Powerslide on sharp turns (> ~115°), and boost when lined up with a target more than 1500 units away.
5. Slow down when the turn is too tight for the current speed. The bot compares the curvature it needs (`2 sin(angle) / distance`) with the car's real turning limits at each speed, and coasts or brakes until it can make the turn. Without this it swung wide past the ball, U-turned, and missed again ("snaking").

**Waiting at a spot:** shadow, save, and retreat spots are *hold* targets. The bot brakes in time to stop on them instead of flying past (or up the post). Once there, it lines up with the ball using its nose or tail, whichever is closer, rocking gently back and forth if needed, since a car can't turn while standing still. If the spot is behind it but the ball is in front, it reverses to the spot still facing the play.

**Defense:** if the ball is well past the car toward its own goal (500+ units), hitting it would push it toward our own net, so the bot *retreats* to the post on its side of the ball. It doesn't retreat when it would still clearly beat the opponent to the ball, and the retreat ends once it's goal-side again or back in its goal mouth. The start and stop thresholds differ so it doesn't flicker.

**Shooting:** the bot aims at a point *behind* the ball, on the line from the opponent's goal through the ball. Approaching from there means the hit sends the ball toward their goal. The offset shrinks as the car gets closer (40% of the distance, max 600 units), so the car curves onto the line and then drives straight through the ball. When the ball is close, low, and straight ahead, the bot flips into it, aimed at the ball itself, for extra power. It never flips the ball toward its own goal, and it stays on its wheels in its own third when an opponent is coming (a missed flip leaves the net open).

Team direction is handled with `side` (−1 for blue, which defends the −y goal; +1 for orange, which defends the +y goal), so the same code works for both teams.

**Aerials:** RLBot sends a ball prediction every tick: where the ball will be for the next few seconds, in 1/120 s slices.

1. *Pick a hit.* `findAerialHitTime` walks the prediction looking for the first ball above 400 units, 0.6–3 s away. For each one it works out where the car would end up if it jumped and then just coasted under gravity, and how far that is from the hitting spot, a point just behind the ball on the line to the enemy goal. Covering that gap needs an average acceleration of `2 × distance / time²`, where the time excludes the time spent first turning the nose toward the gap (0.5 s per radian). If that's within 80% of what boost can give, there's enough boost, and the target is roughly in front of the car, it goes.
2. *Jump.* Hold jump for 0.2 s, release, then double jump. The stick stays centered during the double jump, otherwise it turns into a dodge.
3. *Fly.* Every tick it re-reads the prediction, recomputes the gap, points the car's nose along it with `orient(...)`, and boosts while the nose is lined up. When the gap is closed it points the nose at the ball for the hit. If the ball's path changes so the hit can't be made, it re-plans for another point on the path, or gives up and lands.
4. *Finish.* The aerial ends when the game reports our touch, the hit time passes, or the car lands. The console prints `Aerial hit the ball!` or how close it got (center to center; a touch is ~110 with the roof, up to ~170 with the nose).

**Opponent awareness:** every tick the bot estimates when it and the opponent can each reach the ball. `findIntercept` walks the ball prediction and returns the first moment a car's `travelTime` (turning time plus driving time) is short enough. It also checks whether the opponent is **controlling** the ball (ball on their roof, close and moving with them, or 2+ of their touches in 1.5 s while it stays near them). A controlled ball can be flicked or turned at any moment, so the bot plays the opponent rather than the ball prediction. `chooseMode` then picks one mode, checking these rules in order:

| Mode | When | What it does |
|---|---|---|
| `KICKOFF` | kickoff | speed flip, then an aimed flip into the ball |
| `SAVE` | ball predicted to go in our net and nobody controlling it (a shot), or the opponent is about to shoot a loose ball on target | meet the ball on its path from the goal side if possible, otherwise get to the goal line where it will cross; jump or double jump timed to the ball's height |
| `CHALLENGE` | opponent controlling the ball in our third, or they're first to the ball but we're close behind | drive straight at the ball to take away their space, staying on our wheels |
| `DRIBBLE` | ball on our roof, facing their half | carry it, then flick |
| `RETREAT` | we're well on the wrong side of the ball and can't beat them to it | drive back to the post on our side of the ball and wait there |
| `ATTACK` | we reach the ball first | line up behind the ball and shoot |
| `GET_BOOST` | they're first and out of challenge range, we're low on boost, and they aren't a threat | grab the nearest big pad on our side of the ball |
| `SHADOW` | they're first by a lot | stay 1200 units goal-side of the ball facing it, or in the net once the ball is close to our goal |

How far ahead the bot needs to be before attacking depends on the situation: a loose ball is predictable, so it attacks even if it's 0.1 s behind; against a controlled ball it needs a 0.2 s lead; and once attacking, it keeps going unless the opponent is 0.3 s ahead. Every decision is kept for at least 0.4 s (saves and kickoffs excepted), so the bot commits instead of flickering between modes.

**Punishing the kickoff:** for 3 s after each kickoff's first touch, the opponent is usually out of position. If the lane from the ball to their goal is open (they're behind the ball or 600+ units off the line), the bot attacks even if the opponent would get there up to 0.3 s sooner, and boosts freely.

**Turning around in goal:** if the bot is driving into its own goal with the ball behind it and at least 1.5 s to spare, it drives up the middle of the goal's back wall, jumps off, and turns in the air to land facing the ball.

**Contesting from behind:** if the ball is within 300 units directly behind the bot with an opponent nearby, it backflips into the ball instead of turning around.

The opponent's shot is predicted by assuming they hit the ball in the direction they're driving into it, then following that line to our goal. The bot prints `Mode: ...` every time it switches, so you can watch its decisions in the console.

All the tuning numbers are constants at the top of `ExampleBot.java`.

`orient(...)` turns the car in the air. Pitch and yaw come from the angle between the nose and the aim direction, and roll keeps the roof up. For each axis it spins toward the target as fast as possible, then brakes just in time to stop on it (wanted spin = `sqrt(2 × accel × angle)`). In simulation with Rocket League's air-control numbers, a 90° turn settles in ~0.75 s instead of 1.3 s with the old PD controller. The same function levels the car out when it's falling, so it lands on its wheels, facing the ball when defending.

**Kickoff:** detected using the match phase (`MatchPhase.Kickoff`).

1. Boost toward the ball, but only while on the ground and not flipping.
2. Until the speed flip, angle the nose slightly away from the dodge side (`SPEED_FLIP_PRE_ANGLE`), so the diagonal dodge carries the car straight at the ball.
3. About 0.5 s in, do a speed flip (set `USE_SPEED_FLIP_KICKOFF = false` for a plain front flip).
4. Once within 500 units of the ball and back on the ground, flip into it, aimed at the ball.

**Maneuvers** are timed button sequences spread over several ticks. A `start...` method records the start time, and `updateManeuver(...)` runs every tick and presses the right buttons for the current point in the maneuver:
- **Jump / double jump**: for jump saves, timed so the car reaches the ball's height as it arrives, and for jumping off the back wall.
- **Flip**: a dodge in any direction, aimed at a target angle. Flips into the ball, flicks, and backflip contests.
- **Speed flip**: a diagonal dodge, cancelled by pulling back so the nose stays level, with boost held throughout.
- **Half flip**: back up first, then backflip, cancel halfway, and roll upright toward the target the short way. Turns the car around much faster than driving a circle.

**Boost management:** no boosting past supersonic (2200), where it's wasted. No dipping below a 20-boost reserve (kept for aerials) unless it's urgent (saves, challenges, retreating). With under 70 boost on a long trip, the bot swings through any pad that's only a small detour off its path.

**Dribbling and flicks:** when the ball is on the car's roof and the car faces the opponent's half, the bot carries it. It matches the ball's speed, keeps the ball just ahead of the car's center, and steers gently toward their goal. When the opponent gets close or the goal is near, it flicks with a delayed diagonal dodge.

---

## Tech stack

- **Language:** Java (developed on JDK 23; the RLBot library requires Java 16+)
- **Build tool:** Maven
- **Bot framework:** RLBot v5 via the official [`rlbot-framework`](https://github.com/RLBot/java-interface) Java library (`org.rlbot.commons:rlbot-framework:3.1.0`)
- **IDE:** IntelliJ IDEA
- **Platform:** Windows (required by RLBot)

---

## Project structure

```
rocketleague-bot/
├── pom.xml                         # Maven config + RLBot dependency
├── bot.toml                        # Bot config that RLBot loads (name, agent ID, run command)
├── README.md
└── src/main/java/rocketleague/
    ├── Main.java                   # Entry point: connects to RLBot and registers the bot
    ├── ExampleBot.java             # The bot's logic (getOutput runs every tick)
    └── Vec3.java                   # 3D vector math + car forward/right/up directions
```

The bot's agent ID is `andy/firstbot/v0.1`. The `bot.toml` config file must use the same ID.

---

## Setup

### Requirements

- Windows PC
- Rocket League (free on Epic Games, or Steam)
- [RLBot v5](https://rlbot.org/v5)
- JDK 16 or newer
- Maven (bundled with IntelliJ IDEA)
- Git

### Steps

1. Install Rocket League and RLBot v5.
2. Clone this repository and open it in IntelliJ IDEA as a Maven project.
3. Let Maven load the dependencies (reload the Maven project if prompted). `rlbot-framework` and `flatbuffers-java` should appear under External Libraries.
4. In the RLBot GUI, add the bot by selecting `bot.toml`, put it on a team, and start the match.
5. `bot.toml` has no `run_command`, so RLBot waits for the bot to connect. Run `Main.java` from IntelliJ.

---

## Roadmap

- [x] Choose framework and language (RLBot v5, Java)
- [x] Set up Maven project with the RLBot dependency
- [x] Add example bot code (drive toward the ball)
- [x] Create `bot.toml` and run the bot in a match
- [x] Push to GitHub
- [x] Kickoff: boost, momentum flip, and flip into the ball
- [x] Smooth proportional steering
- [x] Basic defense: return to own goal when on the wrong side of the ball
- [x] Shoot toward the opponent's goal instead of just hitting the ball
- [x] Jump saves: use ball prediction to time jumps and double jumps to the ball's height
- [ ] Jump hits on offense
- [x] Aerials
- [x] Opponent awareness: challenge, shadow, or grab boost depending on where the opponent is
- [x] Boost management: supersonic cap, aerial reserve, picking up pads near the path
- [x] Speed flip kickoff
- [x] Half flips for turning around
- [x] Dribbling and flicks
- [x] Opponent dribble/control detection, decisive mode switching, kickoff punishing
- [x] Back-wall turnaround in goal, backflip contests
- [ ] Team play (2v2/3v3 rotation; teammates are currently ignored)
- [ ] Flip resets, wavedashes, ceiling shots
- [ ] Demo video + write-up
- [ ] **Stretch:** train a reinforcement-learning bot (RLGym) and compare it to this hand-coded bot

---

## References

- [RLBot website](https://rlbot.org/) and [wiki](https://wiki.rlbot.org/)
- [RLBot Java interface](https://github.com/RLBot/java-interface)
- [RedUtils](https://github.com/ItsCodeRed/RedUtils): a C# RLBot utility library, used as a reference for driving and aerial logic
- [ExampleRocketLeagueBot](https://github.com/PiggyVevo/ExampleRocketLeagueBot): an RLGym reinforcement-learning example, for the stretch goal
