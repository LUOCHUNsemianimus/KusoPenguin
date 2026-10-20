# Flying Kick

## Triggering

- Player: a confirmed `Player.attack` hit is reported from the single `Entity.hurt` call.
  Flying Kick starts only when the player has the penguin effect, was sprinting when the hit was
  resolved, and `getAttackStrengthScale(0.5F) > 0.9F`. Misses, weak attacks and ordinary hits never
  start it.
- Mob: a successful `Mob.doHurtTarget` return starts Flying Kick for supported penguin mobs that are
  `Enemy`/`Monster` and hit a non-self living target. No attack AI, hit result, damage, knockback,
  weapon use or durability is changed. Creepers and ranged mobs receive no new melee AI.
- Server cooldown: 36 ticks for players and 80 ticks for mobs, measured in `ServerLevel`
  `gameTime`. A cooldown suppresses only the cosmetic replay; vanilla attacks still run.

## Shared action state

`PenguinAction` is the single action list (`TAUNT`, `FLYING_KICK`). One payload, sequence,
start game time, snapshot and client cache serve both actions. `FLYING_KICK` has higher priority
and may interrupt `TAUNT`; an expired kick no longer blocks a new action. Effect removal clears the
active action, and effect-duration refreshes do not replay it. A late observer receives the current
action together with its original start time.

## Rendering

The client loads every action from `assets/kuso_penguin/animations/` into one immutable clip map.
Every clip is validated against the same 16-joint names, parents and bind pivots as Taunt. Skinned
meshes are still baked once per quality, not once per action. `BLOCK` quality disables all action
playback.

Flying Kick uses the upstream preprocessed 46-frame / 1.5 s source with 0.1 s fade-in and 0.2 s
fade-out (1.8 s total). The legs use the animated pelvis pose plus the fixed hip bind; hip, knee and
foot are all driven by the clip and blend from the native leg pose. Taunt keeps its original
grounded-leg branch and resources. Root motion is limited to the source's visual vertical lift; no
horizontal source travel, hitbox, camera or real position changes are copied.

## Not implemented here

- Death visual afterimage and Flair record playback are outside this task. A future death effect
  could reuse the same action/cache pipeline but would need a death-state snapshot and a separate
  render lifetime; Flair would need an independent item/audio/event design. Neither is implemented
  or wired in this step.
- Runtime gameplay and in-game visual verification remain upstream tasks. This document does not
  claim a launched-client acceptance result.
