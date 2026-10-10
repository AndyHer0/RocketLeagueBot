# Rocket League Bot (Java)

A custom Rocket League bot written in Java, built on the [RLBot](https://rlbot.org/) v5 framework.

The bot connects to Rocket League through RLBot, reads the live game state many times per second, and decides what controller inputs to send: throttle, steering, jumping, boosting. The goal is to grow it from a simple "drive at the ball" bot into one that can shoot, defend, and manage boost, and later to compare it against a version trained with reinforcement learning.

> **Status:** Early development. The bot has a boost-and-flip kickoff, retreats to defend when it's on the wrong side of the ball, and lines up shots on the opponent's goal, goes for aerials using ball prediction, and picks between attacking, challenging, shadowing, saving, and grabbing boost based on where the opponent is. It also manages boost, speed flips on kickoff, half flips to turn around, and dribbles and flicks. Next step is testing and tuning in real matches.

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

**Defense:** if the ball is between the car and its own goal, hitting it would push it toward our own net. The bot switches to *retreating* and drives back to the goal post on its side of the ball, so it doesn't drive through the ball on the way. Once it is clearly goal-side of the ball again, it switches back to attacking. The two modes use different thresholds so the bot doesn't flicker between them.

**Shooting:** the bot aims at a point *behind* the ball, on the line from the opponent's goal through the ball. Approaching from there means the hit sends the ball toward their goal. The offset shrinks as the car gets closer (40% of the distance, max 600 units), so the car curves onto the line and then drives straight through the ball. When the ball is close, low, and straight ahead, the bot front flips into it for extra power.

Team direction is handled with `side` (−1 for blue, which defends the −y goal; +1 for orange, which defends the +y goal), so the same code works for both teams.

**Aerials:** RLBot sends a ball prediction every tick: where the ball will be for the next few seconds, in 1/120 s slices.

1. *Pick a hit.* `findAerialHitTime` walks the prediction looking for the first ball above 400 units, 0.6–3 s away. For each one it works out where the car would end up if it jumped and then just coasted under gravity, and how far that is from the hitting spot, a point just behind the ball on the line to the enemy goal. Covering that gap in the time left needs an average acceleration of `2 × distance / time²`. If that's within 80% of what boost can give, there's enough boost, and the target is roughly in front of the car, it goes.
2. *Jump.* Hold jump for 0.2 s, release, then double jump. The stick stays centered during the double jump, otherwise it turns into a dodge.
3. *Fly.* Every tick it re-reads the prediction, recomputes the gap, points the car's nose along it with `orient(...)`, and boosts while the nose is lined up. When the gap is closed it points the nose at the ball for the hit.
4. *Finish.* The aerial ends once the hit time has passed or the car lands.

**Opponent awareness:** every tick the bot estimates when it and the opponent can each reach the ball. `findIntercept` walks the ball prediction and returns the first moment a car's `travelTime` (turning time plus driving time) is short enough. `chooseMode` then picks one mode, checking these rules in order:

| Mode | When | What it does |
|---|---|---|
| `KICKOFF` | kickoff | boost, momentum flip, flip into the ball |
| `SAVE` | ball predicted to go in our net, or the opponent is about to shoot on target (and we're goal-side) | hit it if we get there before it goes in, otherwise stand on the goal line where it will cross |
| `RETREAT` | we're on the wrong side of the ball | drive back to the post |
| `DRIBBLE` | ball on our roof, facing their half | carry it, then flick |
| `ATTACK` | we reach the ball first by a margin | line up behind the ball and shoot |
| `GET_BOOST` | they're first, we're low on boost, and they aren't a threat | grab the nearest big pad on our side of the ball |
| `CHALLENGE` | they're first, but only just | drive straight at the ball to block them |
| `SHADOW` | they're first by a lot | sit between the ball and our goal, facing the ball, and wait for them to commit |

The opponent's shot is predicted by assuming they hit the ball in the direction they're driving into it, then following that line to our goal. The bot prints `Mode: ...` every time it switches, so you can watch its decisions in the console.

All the tuning numbers are constants at the top of `ExampleBot.java`.

`orient(...)` turns the car in the air. Pitch and yaw are set from the angle between the nose and the aim direction, and roll keeps the roof up. The rate those angles are changing is used to ease off before overshooting. The same function levels the car out when it's falling, so it lands on its wheels.

**Kickoff:** detected using the match phase (`MatchPhase.Kickoff`).

1. Boost toward the ball, but only while on the ground and not flipping.
2. About 0.5 s in, do a speed flip (set `USE_SPEED_FLIP_KICKOFF = false` for a plain front flip).
3. Once within 500 units of the ball, front flip into it.

**Maneuvers** are timed button sequences spread over several ticks. A `start...` method records the start time, and `updateManeuver(...)` runs every tick and presses the right buttons for the current point in the maneuver. There are three:
- **Flip**: a dodge in any direction. Front flips into the ball, and flicks.
- **Speed flip**: a diagonal dodge, cancelled by pulling back so the nose stays level, with boost held throughout.
- **Half flip**: a backflip cancelled halfway, then rolled upright. Turns the car around much faster than driving a circle.

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
- [ ] Jump hits: use ball prediction to time hits on balls in the air
- [x] Aerials
- [x] Opponent awareness: challenge, shadow, or grab boost depending on where the opponent is
- [x] Boost management: supersonic cap, aerial reserve, picking up pads near the path
- [x] Speed flip kickoff
- [x] Half flips for turning around
- [x] Dribbling and flicks
- [ ] Flip resets, wavedashes, ceiling shots
- [ ] Demo video + write-up
- [ ] **Stretch:** train a reinforcement-learning bot (RLGym) and compare it to this hand-coded bot

---

## References

- [RLBot website](https://rlbot.org/) and [wiki](https://wiki.rlbot.org/)
- [RLBot Java interface](https://github.com/RLBot/java-interface)
- [RedUtils](https://github.com/ItsCodeRed/RedUtils): a C# RLBot utility library, used as a reference for driving and aerial logic
- [ExampleRocketLeagueBot](https://github.com/PiggyVevo/ExampleRocketLeagueBot): an RLGym reinforcement-learning example, for the stretch goal
