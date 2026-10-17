# Nomisma Theme Animation Specification

Status: production animation contract. Timings may be tuned after profiling, but event order and lifecycle rules are normative.

## 1. Core rules

- Financial state changes first. Animation only visualizes the persisted result.
- Animation completion must never commit, reject, or delay a financial operation.
- All motion is time-based using frame timestamps.
- Event randomness is deterministic and seeded from the event ID.
- A contribution uses the participant attached to the accepted operation, regardless of whether it was local or remote.
- Historical backlog never generates one animation per operation.
- Reduced motion uses dedicated short clips, not a global speed multiplier.
- Ambient motion pauses while the scene is not visible.
- No clip restarts because of recomposition.
- Sound is optional, but named markers are included so audio can be synchronized later.

## 2. Timing and curve vocabulary

| Name | Definition | Use |
|---|---|---|
| `easeOutPhysical` | Cubic Bézier `(0.16, 0.84, 0.24, 1.00)` | Body settle and visible arrivals |
| `easeInGravity` | Cubic Bézier `(0.42, 0.00, 0.88, 0.36)` | Falling objects and accelerating streams |
| `anticipation` | Cubic Bézier `(0.38, 0.00, 0.64, 1.00)` | Wind-up and weight shift |
| `softReturn` | Cubic Bézier `(0.22, 0.61, 0.36, 1.00)` | Return to idle |
| `impactSpring` | Damped spring, damping ratio `0.52`, response `0.24 s` | Cart, lid, and body impact |
| `heavySpring` | Damped spring, damping ratio `0.72`, response `0.42 s` | Heavy cart and bear settling |
| `liquidWave` | Damped oscillator, `4.5 Hz`, damping `3.2 s⁻¹` | Liquid surface response |
| `ballistic` | Position `p = p0 + v0*t + 0.5*g*t²` | Acorns, ingots, droplets, debris |
| `viscous` | Stretch with slow necking and delayed separation | Honey and adhered liquid |

Large frame deltas are clamped to `33 ms` for simulation. After a pause longer than `250 ms`, the engine evaluates lifecycle recovery instead of integrating the missing time.

## 3. Event priority and interruption

Priority from highest to lowest:

1. theme change / renderer replacement;
2. settlement;
3. reconciliation;
4. capacity threshold event;
5. contribution;
6. reaction;
7. ambient idle action.

Rules:

- Ambient actions are immediately interruptible and blend out over `100–180 ms`.
- Contribution clips are not interrupted before object/liquid release. After release they may transition to settlement at the declared safe marker.
- Settlement clears queued contribution visuals because the target financial state is already a new cycle.
- A theme change crossfades from the current rendered state to the new theme's reconciled state over `280–420 ms`.
- A queue of more than three live contributions collapses into `RECONCILE`.
- Backlog origin always maps to `RECONCILE`, never individual contribution clips.

## 4. Lifecycle behavior

| Situation | Behavior |
|---|---|
| Recomposition | Continue unchanged; event cursor and clock are not composable-local. |
| Navigate away for < 2 s | Pause ambient; retain active clip time and resume. |
| Navigate away for ≥ 2 s | Reconcile to latest scene state on return. |
| App background for < 1 s | Resume active clip with clamped delta. |
| App background for ≥ 1 s | Skip stale action phase, run short settle/reconcile. |
| Configuration change | Retain engine playback state in ViewModel scope; reattach viewport and assets. |
| Process death | Restore persisted financial state and start with reconciliation only. |
| Theme changed while hidden | Attach the new renderer directly in reconciled idle state. |

## 5. Backlog reconciliation

`RECONCILE` duration: `650–900 ms`, not looping, fully interruptible by settlement or theme change.

- Vessel: level eases to target, color boundary diffuses once, surface performs one damped settle.
- Woodland: container contents crossfade/settle to the correct density and the lid gives one subtle weight response.
- Mempool: cart load resolves to target density, suspension compresses once, and dust settles.
- Reduced motion: `180 ms` opacity/value transition with no particles or character action.

## 6. Vessel animation states

### `VESSEL_IDLE`

- Duration: continuous with non-repeating channels.
- Looping: material phase loops over `18 s`; bubble schedules use deterministic intervals of `4–11 s`; highlight drift uses `23–31 s` periods.
- Interruptibility: immediate.
- Motion:
  - liquid curl amplitude remains under `2.2%` of vessel width;
  - surface vertical movement remains under `2.5 dp` at HIGH;
  - one to three bubbles may be active, depending on quality;
  - a residual wall droplet may slide once every `12–28 s` if the vessel is non-empty;
  - environmental light changes by no more than `6%` luminance.
- Secondary effects: slight rim highlight drift and base refraction.
- Reduced motion: static liquid, one very slow highlight drift only.

### `VESSEL_CONTRIBUTION_LEFT`

- Duration: `1.80 s`.
- Looping: no.
- Interruptibility: safe after `1.18 s` (`surface_settled_enough`).
- Timeline:
  - `0–180 ms`: source gathers; small pre-drop forms;
  - `180–620 ms`: stream head accelerates along a gravity-biased curve;
  - `360–820 ms`: stream body follows with changing width and lateral breakup;
  - `650 ms`: marker `pour_contact`;
  - `650–980 ms`: splash crown, secondary droplets, and inward surface displacement;
  - `760–1280 ms`: target liquid level rises with conserved volume;
  - `850–1500 ms`: two or three droplets contact inner glass, deform, and begin sliding;
  - `1050–1800 ms`: color curls diffuse from left contribution region and waves settle.
- Physics/easing: stream head uses `easeInGravity`; free droplets use `ballistic`; surface uses `liquidWave`.
- Participant color: source and fresh impact region use participant A color; existing liquid remains spatially mixed.
- Audio markers: `pour_start`, `pour_contact`, `splash`, `drip_contact`.
- Reduced motion: `320 ms` left-side color pulse, direct level interpolation, one surface ring, no stream.

### `VESSEL_CONTRIBUTION_RIGHT`

Same timing and physics as `VESSEL_CONTRIBUTION_LEFT`, mirrored spatially and using participant B color. Random droplet offsets must be regenerated from event ID rather than geometrically mirrored.

### `VESSEL_CAPACITY_REACHED`

- Duration: `900 ms`.
- Looping: no.
- Interruptibility: merge into the contribution clip that crosses capacity.
- Motion: meniscus approaches the inner rim, performs one restrained swell, and emits two rim-adjacent droplets that fall inward.
- Lighting: rim highlight gains at most `12%` luminance for `180 ms`.
- Audio marker: `capacity_touch`.
- Reduced motion: `220 ms` rim highlight pulse.

### `VESSEL_OVERFLOW`

- Entry duration: `1.20 s`; persistent idle drips continue while overflow remains.
- Looping: irregular drip schedule `3.2–7.5 s`, not a fixed loop.
- Interruptibility: settlement stops new drips immediately; existing droplet may complete for at most `240 ms`.
- Motion: meniscus bulges locally, one droplet crosses the rim, adheres to outer glass, stretches, then slides downward.
- Secondary effects: small contact darkening on the table; never a continuous waterfall.
- Reduced motion: static high meniscus and one opacity pulse.

### `VESSEL_SETTLEMENT`

- Duration: `2.45 s`.
- Looping: no.
- Interruptibility: theme change only; otherwise complete or fast-forward on lifecycle recovery.
- Timeline:
  - `0–260 ms`: liquid surface draws inward; reflections tighten;
  - `260–760 ms`: slow rotational motion builds and the level begins to fall;
  - `760 ms`: marker `drain_open`;
  - `760–1580 ms`: main level falls with a curved meniscus; color regions spiral without becoming neon;
  - `1180–1750 ms`: remaining wall droplets elongate and join the falling liquid;
  - `1580 ms`: marker `vessel_empty`;
  - `1580–2100 ms`: one or two residual droplets remain and slide;
  - `2100–2450 ms`: glass highlights return to clear idle state.
- Audio markers: `liquid_rotate`, `drain_open`, `vessel_empty`, `glass_settle`.
- Reduced motion: `420 ms` level fade/drop, followed by `180 ms` clear-glass settle.

## 7. Woodland common animation states

Idle actions are scheduled, not visibly looped. Only one major idle action per character may run at a time.

### `WOODLAND_CHARACTER_BREATHE`

- Duration: `3.8–5.6 s`, selected per schedule.
- Interval: `1.5–4 s` between cycles with slight phase drift.
- Looping: ambient while visible, but no exact short repeat.
- Motion: spine/torso scale or translation under `1.2%`; head counter-motion under `0.8°`.
- Interruptibility: immediate blend-out over `120 ms`.
- Reduced motion: disabled.

### `WOODLAND_CHARACTER_BLINK`

- Duration: `110–170 ms` single blink; occasional double blink separated by `90 ms`.
- Interval: deterministic `3.5–10 s`.
- Interruptibility: complete current blink.
- Eyes must close through eyelid artwork, not by shrinking a circular eye to a line.

### `WOODLAND_REACT`

- Duration: `620 ms`.
- Looping: no.
- Interruptibility: contribution can take over after `160 ms`.
- Motion: gaze leads, head follows after `60–90 ms`, ears/tail overlap, body shifts less than `3 dp`.
- Use: response to the other participant's contribution or capacity event.

### `WOODLAND_SETTLEMENT`

- Duration: `2.55 s`.
- Looping: no.
- Interruptibility: theme change only.
- Timeline:
  - `0–300 ms`: animals notice the mechanism and settle their paws;
  - `300–680 ms`: lid lifts slightly, then rotates closed with weight;
  - `680 ms`: marker `wood_latch`;
  - `680–1180 ms`: latch/mechanism compresses; container body responds with `heavySpring`;
  - `980–1480 ms`: contents lower and fade behind the closed lid, with two foreground objects settling last;
  - `1480 ms`: marker `contents_collected`;
  - `1480–2130 ms`: lid opens to empty interior;
  - `2130–2550 ms`: moss/dust settles and characters return to idle.
- Secondary effects: 6/12/20 dust or pollen particles by quality; no magical light.
- Audio markers: `wood_close`, `wood_latch`, `contents_collected`, `wood_open`.
- Reduced motion: `480 ms` lid close/open silhouette with direct contents transition.

## 8. Woodland species states

### `WOODLAND_SQUIRREL_IDLE`

- Scheduler interval: `5–13 s`.
- Action duration: `1.1–2.4 s`.
- Variants: tail balance adjustment, two-step gaze change, ear twitch, brief forepaw grooming.
- Tail uses three overlapping rotations with `70–110 ms` lag from base to tip.
- Interruptibility: immediate at neutral pose; otherwise blend out within `180 ms`.

### `WOODLAND_SQUIRREL_CONTRIBUTE`

- Duration: `2.15 s`.
- Looping: no.
- Safe interrupt marker: `1.42 s`.
- Timeline:
  - `0–260 ms`: gaze locks onto acorn and torso lowers;
  - `260–620 ms`: near paw grips; hind legs compress; tail counterbalances;
  - `620–820 ms`: throwing anticipation with shoulder rotation;
  - `820 ms`: marker `object_release`;
  - `820–1260 ms`: acorn follows `ballistic`, rotates with angular drag;
  - `1210 ms`: marker `container_impact`;
  - `1210–1440 ms`: one small bounce on wood, then falls inside;
  - `1260–1780 ms`: paw/shoulder follow-through and tail overshoot;
  - `1780–2150 ms`: body returns with `softReturn`.
- Secondary effects: 2–5 tiny wood/moss specks at impact.
- Audio markers: `acorn_grip`, `object_release`, `wood_impact`, `object_inside`.
- Reduced motion: `360 ms` gaze/paw indication plus direct acorn arc without full-body rig.

### `WOODLAND_BEAR_IDLE`

- Scheduler interval: `7–16 s`.
- Variants: slow weight shift, head turn, nose movement, shoulder breathing.
- Duration: `1.8–4.5 s`.
- Body uses `heavySpring`; no repetitive swaying.

### `WOODLAND_BEAR_CONTRIBUTE`

- Duration: `2.75 s`.
- Timeline:
  - `0–420 ms`: bear shifts weight and raises honey container/paw;
  - `420–980 ms`: honey strand grows slowly with viscous necking;
  - `980–1320 ms`: droplet gains mass and stretches lower;
  - `1320 ms`: marker `honey_separate`;
  - `1320–1710 ms`: heavy droplet falls with lower acceleration appearance than water due to stretch trail;
  - `1710 ms`: marker `honey_impact`;
  - `1710–2050 ms`: low viscous splash, no sharp water crown;
  - `1900–2750 ms`: paw lowers, strand retracts, torso settles.
- Reduced motion: `420 ms` amber viscous drop and container pulse; minimal bear motion.

### `WOODLAND_FOX_IDLE`

- Scheduler interval: `5–14 s`.
- Variants: ear twitch, head listening turn, tail-tip sweep.
- Tail overlap: tip lags base by `90–140 ms`.

### `WOODLAND_FOX_CONTRIBUTE`

- Duration: `1.95 s`.
- Action: low crouch, quick berry toss, two-berry clustered arc, light wooden impact, agile follow-through.
- Release: `720 ms`; impact: `1080 ms`; return begins: `1320 ms`.
- Secondary effects: one berry may rebound once before entering.

### `WOODLAND_RABBIT_IDLE`

- Scheduler interval: `4–12 s`.
- Variants: independent ear turn, nose pulse, small haunch adjustment.
- Ear motion leads head response and uses soft spring settling.

### `WOODLAND_RABBIT_CONTRIBUTE`

- Duration: `1.85 s`.
- Action: haunch compression, short forward paw movement, small seed/vegetable drop with shallow arc, quick ear follow-through.
- Release: `680 ms`; impact: `1030 ms`; settle: `1500–1850 ms`.

### `WOODLAND_RACCOON_IDLE`

- Scheduler interval: `6–15 s`.
- Variants: object inspection, two-paw adjustment, gaze toward container, tail balance.

### `WOODLAND_RACCOON_CONTRIBUTE`

- Duration: `2.10 s`.
- Action: inspects token, transfers between paws, places/tosses with controlled short arc, watches the bounce.
- Release: `940 ms`; impact: `1220 ms`; return: `1650–2100 ms`.
- Metal/wood impact is visually smaller but sharper than an acorn impact.

### `WOODLAND_HEDGEHOG_IDLE`

- Scheduler interval: `7–17 s`.
- Variants: nose search, small body compression, quill ripple traveling back once.

### `WOODLAND_HEDGEHOG_CONTRIBUTE`

- Duration: `2.05 s`.
- Action: low body push, chestnut nudged/rolled into a short hop, head follows object, quills settle after body.
- Release: `800 ms`; impact: `1250 ms`; return: `1680–2050 ms`.

### Additional compatibility identities

- `WOODLAND_OWL_IDLE`: `6–16 s` scheduler; head turn, blink, feather settle.
- `WOODLAND_OWL_CONTRIBUTE`: `2.10 s`; talon/wing release of seed with restrained wing follow-through.
- `WOODLAND_OTTER_IDLE`: `6–15 s` scheduler; whisker/head motion and low tail shift.
- `WOODLAND_OTTER_CONTRIBUTE`: `2.00 s`; shell transfer and low underhand toss.

These remain fallback-only until their production asset packs are approved.

## 9. Woodland progress states

Container fill is represented by four content bands with foreground objects:

- `0–25%`: sparse layer opacity ramps from 0 to 1;
- `25–50%`: sparse remains, medium layer crossfades in;
- `50–75%`: dense layer replaces medium with a `220 ms` settle;
- `75–100%`: full layer rises slightly toward the opening;
- overflow: at most two foreground objects rest near the lip; no infinite object simulation.

Crossing a band during contribution triggers one `impactSpring` displacement under `4 dp`.

## 10. Mempool common states

### `MEMPOOL_AMBIENT`

- Duration: continuous with asynchronous channels.
- Dust intervals: `2–7 s`; lantern variation period: `9–17 s`; cable/cloth movement remains below `1.5 dp`.
- Rails and metal do not emit continuous neon light.
- Interruptibility: immediate.
- Reduced motion: static lantern illumination and no ambient dust.

### `MEMPOOL_MINER_A_IDLE`

- Scheduler interval: `7–16 s`.
- Variants: weight shift, glove adjustment, short cart glance, lamp/head turn.
- Duration: `1.4–3.2 s`.
- Breathing remains subtle under work clothing.

### `MEMPOOL_MINER_B_IDLE`

- Scheduler interval: `8–18 s`.
- Variants differ from Miner A: scarf/jacket settle, boot adjustment, rail/mechanism glance.
- Miner B must not run a mirrored Miner A idle clip.

### `MEMPOOL_MINER_A_CONTRIBUTE`

- Duration: `2.30 s`.
- Looping: no.
- Safe interrupt marker: `1.58 s`.
- Timeline:
  - `0–300 ms`: gaze and shoulder orient toward transaction object;
  - `300–720 ms`: knees/torso load weight and hands retrieve object;
  - `720–970 ms`: throw/place anticipation; object lags hands;
  - `970 ms`: marker `transaction_release`;
  - `970–1390 ms`: object follows weighted ballistic arc with slow rotation;
  - `1360 ms`: marker `cart_impact`;
  - `1360–1760 ms`: cart body compresses on suspension, wheels rotate slightly, load shifts;
  - `1420–1900 ms`: localized dust response;
  - `1680–2300 ms`: arms and torso follow through and return.
- Audio markers: `object_grip`, `transaction_release`, `metal_impact`, `cart_settle`.
- Reduced motion: `360 ms` arm indication and direct object arc; one cart compression.

### `MEMPOOL_MINER_B_CONTRIBUTE`

- Duration: `2.40 s`.
- Uses a closer placement motion rather than a mirrored throw: one step/lean, two-hand placement, release, hand lag, and cart reaction.
- Release: `1120 ms`; impact: `1320 ms`; return completes: `2400 ms`.

### `MEMPOOL_CART_PROGRESS`

- Duration: `260–520 ms` following accepted fill changes.
- Load density crossfades between sparse/medium/dense/full layers.
- Cart suspension responds with `heavySpring`, maximum vertical travel `5 dp`.
- Wheels rotate according to horizontal cart displacement only; no idle spinning.

### `MEMPOOL_CAPACITY_REACHED`

- Duration: `920 ms`.
- Motion: cart compresses once, latch shifts into near-ready position, and one localized amber indicator turns on.
- Flash maximum: `160 ms`, localized to mechanism and rail start.
- Reduced motion: `200 ms` indicator pulse.

## 11. Mempool settlement

### `MEMPOOL_SETTLEMENT`

- Duration: `3.35 s`.
- Looping: no.
- Interruptibility: theme change only; otherwise fast-forward to safe settle on lifecycle recovery.
- Timeline:
  - `0–260 ms`: both miners become alert; ambient idle stops;
  - `260–680 ms`: Miner A shifts rear foot and lowers center of mass;
  - `680–1120 ms`: pickaxe rises; hands lead, head and shoulders follow, tool head lags slightly;
  - `1120–1370 ms`: brief held anticipation with subtle body tension;
  - `1370–1600 ms`: accelerated downward swing; torso rotation leads arms, pickaxe follows;
  - `1600 ms`: marker `pickaxe_impact`;
  - `1600–1680 ms`: eight-frame-equivalent impact pause; hands stop first, body continues slightly;
  - `1680–1940 ms`: localized rock chips, sparks, and dust; orange pulse starts at mechanism;
  - `1810 ms`: marker `energy_release`;
  - `1810–2240 ms`: pulse travels along rail mask toward cart;
  - `2240 ms`: marker `cart_lock`;
  - `2240–2440 ms`: cart latch closes and body reacts with `heavySpring`;
  - `2440 ms`: marker `load_dispatch`;
  - `2440–2780 ms`: transaction load drops/fades behind cart occlusion; no explosion;
  - `2660–3180 ms`: miners and pickaxe follow through and recover;
  - `2780–3350 ms`: dust settles, indicator fades, cart returns empty and stable.
- Orange light:
  - confined to impact point, mechanism, short rail path, and cart latch;
  - peak screen coverage under `8%`;
  - no full-screen flash, lightning network, or persistent neon.
- Particle limits by quality:
  - LOW: 4 rock chips, 4 sparks, 8 dust motes;
  - MEDIUM: 7 rock chips, 9 sparks, 18 dust motes;
  - HIGH: 10 rock chips, 14 sparks, 28 dust motes.
- Audio markers: `pickaxe_whoosh`, `pickaxe_impact`, `energy_release`, `cart_lock`, `mechanical_clunk`, `load_dispatch`.
- Reduced motion: `620 ms` sequence consisting of anticipation silhouette, localized impact pulse, direct empty-cart transition, and one suspension settle.

## 12. Theme transitions

### `THEME_CHANGED`

- Duration: `360 ms` default.
- Old scene ambient freezes at the current frame.
- Old scene fades and scales by at most `1.5%`.
- New scene appears already synchronized to the current financial state.
- No contribution or settlement event is inferred solely from changing theme.
- Any queued event older than the transition is replaced by reconciliation.
- Reduced motion: `140 ms` crossfade only.

### `PARTICIPANT_VISUAL_CHANGED`

- Duration: `280 ms` color/identity transition.
- Participant color masks crossfade through neutral material response.
- Identity changes in Woodland use a scene-safe dissolve/cut while both characters are idle; no morph between species.
- No financial progress animation is triggered.

## 13. Scene event markers for future audio

The engine exposes markers but does not require an audio implementation:

```kotlin
data class ThemeAnimationMarker(
    val eventId: String,
    val clipId: String,
    val marker: String,
    val normalizedIntensity: Float,
    val participantId: String?
)
```

Markers are emitted once, are deduplicated by `(eventId, clipId, marker)`, and are not replayed after configuration change or lifecycle reconciliation.

## 14. Reduced-motion summary

| Event | Full motion | Reduced motion |
|---|---:|---:|
| Contribution | 1.8–2.75 s | 0.32–0.48 s |
| Settlement | 2.45–3.35 s | 0.48–0.62 s |
| Reconciliation | 0.65–0.90 s | 0.18 s |
| Capacity | 0.90–0.92 s | 0.20–0.22 s |
| Theme change | 0.36 s | 0.14 s |
| Character idle actions | Scheduled | Disabled |

Reduced motion keeps state legible and preserves participant/color cues without character locomotion, ballistic particles, repeated parallax, or continuous surface movement.

## 15. Testable animation requirements

- Given the same event ID and scene state, particle seeds and key poses are deterministic.
- Contribution side matches the persisted participant ID.
- Backlog origin produces exactly one reconciliation clip.
- Settlement clears pending contribution clips and ends at the empty current-cycle state.
- Recomposition does not change clip time or emit markers again.
- A scene hidden beyond the stale threshold returns through reconciliation.
- Reduced-motion mode never instantiates a full-motion clip.
- At key timestamps, all hero subjects remain inside the safe composition zone.
