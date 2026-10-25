# Minecraft mods: client drawing (NeoForge 1.21.1)

HUD layers, particles, camera vectors, entity renderer layers. For the rest of the client, see
[minecraft-mod-client.md](minecraft-mod-client.md).

## A custom GUI layer is drawn with F1 too

A layer from `RegisterGuiLayersEvent.registerAboveAll` is called even when `options.hideGui` (F1)
is set. The vanilla layers check the flag themselves. Ours does not. Check
`Minecraft.getInstance().options.hideGui` in the layer's `render`. Noticed in Strategos from the
golden images: a scene with the GUI hidden differed by 4.8e-4.

## Particles farther than 32 blocks from the camera are not created

`LevelRenderer#addParticleInternal` drops an ordinary particle if it is more than 32 blocks from
the camera (squared distance > 1 024). With the setting "particles: minimal" it drops almost any
particle. The RTS camera always hangs high, so markers and hints from there are not visible at
all. Use `level.addParticle(options, true, …)`: `force` bypasses both the distance and the
setting. The scenario harness also sets `particles:2` in options.txt, which is the minimum.

## Camera vectors: left × up points away from the camera

`Camera` has the base vectors `FORWARDS (0,0,-1)`, `UP (0,1,0)`, `LEFT (-1,0,0)`, so left × up
equals `getLookVector()`, the look direction, *away from* the camera. Right is minus left. To move
a quad layer (the fill of a bar over its backing) toward the camera, shift by −look. A shift along
left × up moves the fill behind the backing, and the depth test hides it. Reference: this
repository, `HealthBarOverlay`.

## A custom `LivingEntityRenderer` draws no armor and no held items

Layers are added by the subclass: `LivingEntityRenderer` has none, so the equipment that the
server sends in `ClientboundSetEquipmentPacket` is silently invisible. You need `HumanoidArmorLayer`
(models `PLAYER_INNER_ARMOR`/`PLAYER_OUTER_ARMOR`) and `ItemInHandLayer` (`PlayerItemInHandLayer`
requires `Player`). Client-only cosmetics (dyed leather, a shield with `BASE_COLOR`) are returned
by the entity's `getItemBySlot`, without touching the server slots. Reference: this repository,
`UnitRenderer`.

## `GuiGraphics` fills are drawn later than `blit`

`GuiGraphics#fill` collects quads in a buffer and flushes it at the end of the layer, while `blit`
of its own texture draws at once. A backing panel filled before the image lands on top of it and
covers it. Call `guiGraphics.flush()` before a `blit` over a backing. Reference: this repository,
`RtsHud#drawMinimap`.

## "Long ago" as `Long.MIN_VALUE` breaks `now - then`

The "never happened" mark `Long.MIN_VALUE` overflows in `now - then` and gives a negative number.
The check "a second has passed" never fires (the minimap texture was never released), and the
check "less than 5 s has passed" always fires (a false alarm). The initial value must be a clearly
negative number such as `-1_000_000`, not `MIN_VALUE`.

## Armor and held items on a crowd: vanilla against Sodium

A hundred-plus humanoids with `HumanoidArmorLayer` and `ItemInHandLayer` (helmet, tunic, trident,
shield with a banner) are expensive on vanilla. Held items are drawn with a translucent type, so
`BufferSource#endBatch` sorts their quads (`MeshData#sortQuads`, `IntArrays.mergeSort`), and every
new render type in the immediate buffer closes the previous batch. 200 such bodies on an M4 Pro
cost 8.9 ms per frame (2.1 bare). The outline of a selected hundred costs another 37 ms (the glow
draws the body with its layers once more into the outline buffer). With Sodium the same 200 cost
1.6 ms, and the outline about 1 ms.

The fix (Strategos M4 T3, 47 → 4.9 ms per frame on vanilla): `OutlineBufferSource#getBuffer` for an
outlined entity writes every render type both into the ordinary buffer and into the outline
buffer, where each armor texture is its own type and its own batch. A `MultiBufferSource` wrapper
hands the outline buffer only to the first `getBuffer` (the `LivingEntityRenderer` body asks
before the layers) and the ordinary `renderBuffers().bufferSource()` to the rest. This keeps the
outline on the silhouette of the body. Put the layers into a `RenderLayer` wrapper that on vanilla
(not `ModList.isLoaded("sodium")`) draws them only for the first ~50 bodies per frame (reset in
`RenderFrameEvent.Pre`). The distance threshold must be farther than the strategy camera looks.
Otherwise the helmet and the weapon, which show the role, disappear from 60 blocks.

## A vanilla animal model for your own entity

`PigModel<T extends Entity>` and `ChickenModel<T extends Entity>` take any entity:
`new PigModel<>(ctx.bakeLayer(ModelLayers.PIG))` is enough. `SheepModel` and `SheepFurModel` are
tied to `Sheep`, but their mesh is the baked layer `ModelLayers.SHEEP` / `SHEEP_FUR`. Your own
`QuadrupedModel` subclass (the constructor is protected; the sheep numbers are
`false, 8, 4, 2, 2, 24`) over this `ModelPart` draws it without copying the mesh. The wool is
`RenderLayer.coloredCutoutModelCopyLayerRender` with `sheep_fur.png` and the color `-1`. Several
models for one entity type: an `EntityRenderer` that hands `render` to its own
`LivingEntityRenderer`s by the entity's data.

A body that must lie down (a carcass) must not be killed. `die()` starts `deathTime` and the red
tint (`getOverlayCoords` checks `deathTime > 0`), and on the 20th tick the server removes the
entity. Instead use `setPose(Pose.DYING)` (vanilla synchronizes the pose) with the health alive,
and in the renderer's `setupRotations`: `hasPose(DYING) && deathTime == 0` →
`Axis.ZP.rotationDegrees(getFlipDegrees(e))`.

## A scene for "by stage" frames: `/tick sprint`, stop by amount, `/tick freeze`

- Bringing a scene to its stages (a deposit at ¾, ½, ¼, empty; a field half used up) by unit work
  takes tens of thousands of ticks. On an integrated server `tick sprint 1000000` runs them in
  seconds. The core ticks in the same order, so the state is the same as without the rush.
- Determinism comes not from the tick but from the condition: the match listener looks at the
  amount in the node every tick and sends `Stop` to its workers as soon as it is ≤ the target.
  When all targets are taken, run `tick sprint stop`, then `tick freeze`. The amount does not stop
  exactly at the target (749, not 750), but it is the same on every run.
- A field that is still ripening holds `amount` 0, so the condition "≤ target" is true for it at
  once. Check `ripening() == 0` before the comparison. It helps to write the result of the run
  into the scene JSON (the amounts of all nodes at the freeze): small differences between stages
  are not visible by eye on a frame from 60 blocks.
- Particles: everything the server ran in the sprint in fractions of a second, the client did not
  have time to live out, and `tick freeze` leaves them in random places on the frame (a destroyed
  house had nmse 1e-4 between runs). Run the last ~80 ticks before the freeze at the normal pace
  (`tick sprint stop` beforehand).
- Scene checks (`check(...)` → `"ok":false`) do not fail the run: `scenarioDiff` judges only
  pixels. In Strategos an "empty" click landed under the HUD panel and did not clear the
  selection from M3 to M4, and the golden image was recorded with this error. After a run, look
  at `"ok"` in the result, not only at the diff.
