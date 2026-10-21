# Prone animal replacements

Scope: adult cow, sheep, pig, cat, wolf, spider and cave spider that carry the penguin effect are
drawn as a horizontal penguin body sliding on its belly, with their own native head. This is a
render-only feature: AI, hitboxes, movement speed, networking, resources and dependencies are not
changed.

## Pose contract

- The shared five-root penguin model is reset and bound to one static pose. Wings are splayed along
  the sides and the feet keep their bind pose; no limb rotation reads `limbSwing`, `limbSwingAmount`
  or entity age, so moving and stationary animals show the same silhouette (a slide, not a walk).
- One whole-assembly transform is applied in `PenguinBodyLayer` before all five roots are drawn:
  translate from the measured floor bound, uniform family scale, then `+90°` about X.
- Local axes before that transform: `+Y` runs from the neck/front towards the tail/feet, `-Z` is the
  entity's forward direction, `+X` is the penguin's anatomical left. The `+90°` X rotation maps the
  old belly (`-Z`) downward and the old `+Y` axis backward, giving a belly-down body whose feet trail
  behind.
- "Floor bound" is the largest `-Z` value of the five static roots (cube body or the selected
  HIGH/MEDIUM/LOW mesh tier), cached per immutable tier. The body Y offset puts that bound exactly
  on the renderer ground plane. The tier cache uses weak mesh keys so a resource reload can drop
  old tiers. The cave spider keeps its native renderer `.7` overall scale; the family scale below
  applies inside that renderer scale.
- The native head is never rotated face-down. `PenguinPronePose.applyHead` translates only the
  original native head pivot onto the same body-front neck origin used by the body transform:
  `Y = 24/16 - bodyScale * floorNegZ`, `Z = neckZPixels/16`. No scale or rotation is applied, and
  the native `head.x`/child offsets are kept, so native head size, yaw, pitch, cow horns, pig snout,
  cat nose/ears and the wolf `real_head` child remain intact. Both head and body use one shared
  origin helper and the prepared body's current mesh-tier floor bound. The head pivot is read every
  frame, so a sheep grazing moves relative to that neck anchor.
- Family scales: cow `.80`, sheep `.68`, pig `.66`, cat `.45`, wolf `.68`, spider/cave spider `.70`.
  These are visual tuning values only and can be adjusted per family in `PenguinPronePose`.

## Overlays and states

- Sheep wool and cat collar go through the existing `RenderLayerMixin` head-only redirect, so no
  original body or legs come back. Wolf collar has a narrow redirect of its direct parent-model
  call with the same tame/invisible condition, collar colour and texture.
- Pig saddle and wolf armor are suppressed only while the prone replacement is active. Spider and
  cave-spider glowing eyes reuse the existing `EyesLayerMixin` head-only path.
- Effect removal or any unhandled state falls through to the complete vanilla renderer and all
  native layers.
- Kept fallbacks: baby, invisible, glowing, spectator, passenger, swimming, non-player fall-flying,
  native sitting (tame wolf/cat) and cat lie-down. Cat crouching (stalking) keeps the replacement
  because the prone body already looks lowered; the upright crouch fallback applies only to the
  original upright mob set.

## Action boundary

`PenguinEntitySupport.isSupportedType` keeps its original upright/action-capable set. The seven
prone animals live in the separate `isProneType` set. Server kick/taunt/death eligibility still
uses only the original set, and `PenguinRenderContext.pose()` returns null for prone animals
before evaluating any clip, so an effect-add TAUNT cannot start a humanoid animation on them.
The old dead-copy renderer remains upright-only.

## Known limitations

- Cat lie-down and tame sitting are intentionally not replaced (complete vanilla rendering).
- The prone body uses the same penguin texture/mesh as every other replacement; only scale, floor
  offset and neck anchor vary by family.
- Runtime visual acceptance is upstream: tier bounds and neck fit were chosen from model geometry,
  not visually confirmed in this implementation step.
