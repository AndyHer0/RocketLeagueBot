# Rocket League Bot (Java)

A custom Rocket League bot written in Java, built on the [RLBot](https://rlbot.org/) v5 framework.

The bot connects to Rocket League through RLBot, reads the live game state many times per second, and decides what controller inputs to send: throttle, steering, jumping, boosting. The goal is to grow it from a simple "drive at the ball" bot into one that can shoot, defend, and manage boost, and later to compare it against a version trained with reinforcement learning.

> **Status:** Early development. The bot runs in matches, drives at the ball, and has a boost-and-flip kickoff. Next step is basic defense (returning to goal when the ball is on our side).

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

**Driving:** the bot holds full throttle and steers toward the ball.

1. Read the car's location and the ball's location from the packet.
2. Use `Math.atan2` to find the angle (yaw) from the car to the ball.
3. Compare that to the direction the car is currently facing, wrapping the difference into the range −π to π so the car always turns the shorter way.
4. Steer in proportion to that angle (full lock beyond ~30°), so the car drives smoothly instead of jittering left and right.

**Kickoff:** detected using the match phase (`MatchPhase.Kickoff`).

1. Boost toward the ball, but only while on the ground and not flipping.
2. About 0.5 s in, do one front flip for extra speed.
3. Once within 500 units of the ball, front flip into it.

**Front flips** are timed button sequences spread over several ticks (jump, release, jump with the stick forward). `startFrontFlip(now)` records the start time from anywhere in the code, and `updateFrontFlip(controller, now)` runs every tick and presses the right buttons for the current point in the flip. State that has to survive between ticks, such as the flip start time and whether the last tick was a kickoff, is stored in fields on the bot.

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
    └── ExampleBot.java             # The bot's logic (getOutput runs every tick)
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
- [ ] Basic defense: return to own goal when the ball is on our side
- [ ] Shoot toward the opponent's goal instead of just hitting the ball
- [ ] Jump hits: use ball prediction to time hits on balls in the air
- [ ] Aerials
- [ ] Boost management
- [ ] Demo video + write-up
- [ ] **Stretch:** train a reinforcement-learning bot (RLGym) and compare it to this hand-coded bot

---

## References

- [RLBot website](https://rlbot.org/) and [wiki](https://wiki.rlbot.org/)
- [RLBot Java interface](https://github.com/RLBot/java-interface)
- [RedUtils](https://github.com/ItsCodeRed/RedUtils): a C# RLBot utility library, used as a reference for driving and aerial logic
- [ExampleRocketLeagueBot](https://github.com/PiggyVevo/ExampleRocketLeagueBot): an RLGym reinforcement-learning example, for the stretch goal
