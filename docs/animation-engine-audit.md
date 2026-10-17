# Animation Engine Audit — Nomisma

Audit performed on `main` (commit `2800033`) before the `refactor/animation-motion-engine` rework.

## Relevant files

| File | Role |
| --- | --- |
| `app/src/main/java/com/nomisma/app/ui/theme/NomismaThemeScene.kt` (2623 lines) | Composable shell, frame loop, event playback, **and** every theme's drawing code |
| `app/src/main/java/com/nomisma/app/theme/ThemeEventCoordinator.kt` | ViewModel-side semantic event queue (collapses backlog into `Reconciled`) |
| `app/src/main/java/com/nomisma/app/theme/ThemeModels.kt` | `ThemeSceneState`, `ThemePresentationEvent`, resolver and mapper |
| `app/src/main/java/com/nomisma/app/ui/theme/ThemeAssetRepository.kt` | Asset loading + `ThemeAssetPack` |
| `app/src/main/java/com/nomisma/app/ui/theme/SceneMaterials.kt` | RuntimeShader brushes (API 33+) |
| `app/src/main/java/com/nomisma/app/ui/NomismaViewModel.kt` | Owns the coordinator, publishes `themeEvents` |
| `app/src/main/java/com/nomisma/app/ui/NomismaApp.kt` (`HomeScreen`) | Wires `NomismaThemeScene(state, events, onEventConsumed, onStaleEvents)` |

## Event flow (verified)

`LedgerSnapshot` change → `NomismaViewModel.publishSnapshotTransition` →
`ThemeEventCoordinator.accept(previous, current, origin)` → `ThemeEventQueueState.pending` →
`HomeScreen` → `NomismaThemeScene(events = pending)` → one `LaunchedEffect(activeEvent?.id)` plays
the first pending event with `Animatable` keyframes → `onEventConsumed(id)`.

## Confirmed problems

1. **Hardcoded timing drives everything.** Contribution playback is a fixed 1650 ms keyframe track,
   settlement 2250 ms. All drawing code derives positions from `progress` fractions
   (`smoothStep(0.01f, 0.34f, progress)`, `progress > 0.29f` → splash, `progress > 0.72f` → impact…).
   Durations cannot adapt to the amount of change or to quality settings.

2. **Two competing clocks for the same value.** `animatedFill` (a 1050 ms `animateFloatAsState`)
   runs independently of the contribution `Animatable`; during a contribution the fill is spliced
   from `event.fromFill/targetFill` with yet another smoothing curve. Interruptions can leave the
   spliced value and the tweened value disagreeing.

3. **Whole-sprite movement.** `drawWoodlandAssetCharacter` and `drawMinerAsset` translate/rotate
   single bitmaps. The procedural fallbacks (`drawNaturalQuadruped`, `drawMiningOperator`, …) draw
   limbs independently but animate them with raw `sin(ambient)` expressions — there is no reusable
   gait, spring, or state machinery, and the two code paths share nothing.

4. **Predetermined trajectories.** Woodland tokens and the Mempool block follow fixed quadratic
   Béziers sampled with `PathMeasure` at linear progress; droplets use a parametric formula with a
   fake per-particle time (`t = splashTime * (0.73 + pseudo(...))`). Nothing integrates forces, so
   gravity, restitution and settling are faked per-effect and cannot react to geometry.

5. **Fixed-timeout event loss.** `NomismaThemeScene` discards any action event older than 1000 ms
   (`onStaleEvents()` → `collapseToReconciliation`). Because a single contribution plays for
   1650 ms, a second contribution arriving during playback is guaranteed to be "stale" when it
   becomes active and is silently dropped from the visual narrative.

6. **Renderer does everything.** One file owns frame timing, event consumption, quality selection,
   accessibility handling, all three themes' drawing, and particle math. `SceneMotion` is a bag of
   unrelated fields passed everywhere. Adding a theme means editing this file.

7. **Per-frame allocations.** `Path`, `PathMeasure`, `Brush` and lambda objects are allocated inside
   `repeat` loops during draw (e.g. one `PathMeasure` per flying token per frame).

8. **Ambient motion is not delta-time based.** The ambient phase is
   `(frameTimeNanos % 16000) / 16000`, which is fine for phase, but every "speed" in the file is a
   fraction of that cycle, so effect pacing is coupled to the 16 s ambient loop rather than to
   seconds.

## What is retained

- `ThemeEventCoordinator` (ViewModel side): stable envelope ids, burst collapse to `Reconciled`,
  durable cursor. It stays the **only** bridge between financial state and visuals; the business
  state remains authoritative.
- `ThemePresentationEventResolver` / `ThemeStateMapper` — unchanged.
- `SceneMaterials` shaders, `ThemeAssetRepository`, all artwork, quality preference plumbing
  (`ThemeQualityPreference`, `ThemeMotionPreference`, `MinerAppearance`).
- The visual identity of each scene (colors, layout anchors, shader backgrounds).

## Proposed solution (implemented in this branch)

- New pure-Kotlin package `com.nomisma.app.theme.motion`: `Vec2`, `MotionBody` (semi-implicit
  Euler integration, gravity, damping, restitution, sleeping), `Spring1D` (damped spring),
  `ParticlePool` (fixed-capacity, zero allocation per frame), `AnimationTimeline` (named stages,
  explicit completion/cancellation), `ActorStateMachine`
  (IDLE/PREPARING/MOVING/INTERACTING/RECOVERING with explicit transitions), `MotionEngine`
  (fixed-step simulation at 120 Hz with clamped catch-up, driven by monotonic frame deltas), and a
  deterministic `SeededRandom`.
- New package `com.nomisma.app.ui.theme.scene`: `SceneController` interface +
  `SceneEnvironment`, one controller per theme (`VesselSceneController`,
  `WoodlandSceneController`, `MempoolSceneController`) holding simulated entities, and drawing
  code split per theme into renderer files. Controllers always animate **toward** the
  authoritative `ThemeSceneState`, so any interruption self-heals.
- `NomismaThemeScene.kt` shrinks to a shell: lifecycle-aware frame loop that converts
  `withFrameNanos` deltas into seconds, feeds one event at a time to the controller, and consumes
  events only when the controller reports completion. The 1000 ms staleness discard is removed;
  backlogs are handled by the coordinator's existing collapse policy.
- Reduced motion: controllers snap springs/particles to final state and report event completion
  immediately; decorative ambient motion stops.
- Debug overlay (debug builds only) showing engine stats.
