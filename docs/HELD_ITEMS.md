# Third-person held items during the penguin effect

## Scope

Only the third-person main/off-hand item pass is changed. The vanilla
`ItemInHandLayer.render` loop, `ItemStack` selection, `ItemDisplayContext`
(`THIRD_PERSON_LEFT_HAND` / `THIRD_PERSON_RIGHT_HAND`), the `leftHand` flag,
interaction/attack poses, inventory, first-person rendering and all model assets
stay untouched.

The previous `HEAD` cancel in `ItemInHandLayerMixin` is removed. The mixin now
redirects only the call to `ArmedModel.translateToHand(HumanoidArm, PoseStack)`
inside `ItemInHandLayer.renderArmWithItem`. Every other armed entity (anything
not replaced by `PenguinRenderSupport`) calls the original method.

## Shared mount

`PenguinRenderContext.applyHand(PoseStack, HumanoidArm)` is the single entry
point for every `ArmedModel` entity (player, zombie, skeleton, wither skeleton,
piglin, zombified piglin, zombie villager, ...). It reuses the existing per-draw
frame (entity, original model, one-evaluated `PenguinAnimationPose`), the
accepted `PenguinProportions.Profile` and the `PenguinPoseAdapter`.

* While the taunt clip is active, the mount uses the last wing-segment joint
  (`pose.world[6]` / `pose.world[9]`) so the grip follows the three-stage bent
  wing. On clip end the pose becomes `null` and the hand returns to the standing
  wing.
* Outside the clip, including `BLOCK` quality, the shared `PenguinBodyModel` is
  reset and the adapter is applied, then the actual `right_wing` / `left_wing`
  `ModelPart` transform is used.
* If no supported replacement/frame exists, `applyHand` returns `false` and the
  vanilla hand translation runs unchanged.

## Wing-end coordinates and unit conversion

All mount numbers are model pixels, matching the rig bind (`16 px = 1 block`).

| Path | Right wing | Left wing |
| --- | --- | --- |
| Active tip offset from last joint | `(0.78, 2.0, 0.17)` | `(-0.78, 2.0, 0.17)` |
| Idle offset from wing root | `(3.35, 9.4, 0.5)` | `(-3.35, 9.4, 0.5)` |
| Resulting visible end (bind space) | `(8.8, 12, 0.7)` | `(-8.8, 12, 0.7)` |

`HumanoidArm.RIGHT` uses the rig's `right_wing` / positive X side and
`HumanoidArm.LEFT` the mirrored negative X side, matching the bind data and the
existing `rightArm -> right_wing` adapter mapping.

The rendered body is drawn as `translate(0, bodyOffsetBlocks, 0)` followed by
`scale(bodyScale)`, so the item anchor is converted to blocks as:

```
anchorBlocks = handPixels * bodyScale / 16 + (0, bodyOffsetBlocks, 0)
```

The item frame itself is **not** scaled: the item keeps its normal vanilla size
and only its anchor position follows the short body. Because `applyHand` is only
reached for adult, non-crouching replacements, the vanilla baby `0.75`/`0.5`
pre-scale and crouch offsets are never applied twice.

## Keeping the vanilla held-item transform

Vanilla applies `XP(-90)`, `YP(180)` and a fixed block-space translation after
`translateToHand`. Those calls are preserved. Their fixed translation
(`±1/16, 0.125, -0.625`, i.e. `(±1, 2, -10)` px) is pre-subtracted from the
appended wing frame, so the resulting item origin is the wing end while the item
still receives the normal vanilla third-person held orientation. The wing
rotation is composed with the same `XP(-90) * YP(180)` convention.

## Verification boundary

Code and Gradle `classes`/`build` are checked here. Actual in-game two-hand,
dual-wield, taunt and `BLOCK` visuals, including left/right appearance, remain
upstream runtime verification.

## Anatomical side correction

Legacy wing names are opposite to vanilla anatomical sides: `left_wing` (negative model X) is the actual RIGHT arm, and `right_wing` is LEFT. Grip joints and native humanoid arm adaptation use this explicit mapping. Vanilla item-context/left-hand flags still use anatomical HumanoidArm. The earlier name-based numerical check was insufficient; acceptance also asserts the actual X side and checks a frontal screenshot.
