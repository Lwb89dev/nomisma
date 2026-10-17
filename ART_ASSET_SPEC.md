# Nomisma Theme Art Asset Specification

Status: production-art contract with a first runtime delivery implemented.

The repository now contains optimized environment plates, eight transparent woodland characters, two transparent human miners, and a transparent glass pitcher under `app/src/main/assets/themes/`. The current vertical slices combine those assets with runtime liquid, containers, carts, particles, lighting, and event animation. The more granular rig and mask tables below remain the target for later bone-level animation refinement.

The exact built-in image-generation prompts used for this delivery are recorded in [IMAGEGEN_PROMPTS.md](IMAGEGEN_PROMPTS.md). Asset provenance is stored beside the packaged files in `app/src/main/assets/themes/ASSET_PROVENANCE.md`.

## 1. Art direction

All theme artwork must use realistic cartoon / painterly realism:

- believable anatomy and physical construction;
- strong silhouettes at phone size;
- slightly exaggerated proportions without chibi features;
- rich but controlled color;
- grounded shadows and localized highlights;
- clear material separation;
- painterly texture without photorealism;
- no emoji styling, flat Material illustration, clip-art, generic 3D rendering, or excessive neon/bloom.

The three themes must feel like parts of one premium illustrated product. Contrast, edge sharpness, and saturation should concentrate around the financial subject rather than the outer frame.

## 2. Runtime folder layout

```text
app/src/main/assets/themes/
├── common/
│   ├── particle_soft.webp
│   ├── particle_speck.webp
│   └── particle_streak.webp
├── vessel/
│   ├── scene.json
│   ├── backgrounds/
│   ├── vessel/
│   ├── particles/
│   └── effects/
├── woodland/
│   ├── scene.json
│   ├── backgrounds/
│   ├── animals/
│   ├── container/
│   ├── props/
│   └── effects/
└── mempool/
    ├── scene.json
    ├── backgrounds/
    ├── miners/
    ├── cart/
    ├── props/
    └── effects/
```

Editable source files should live outside the APK under `art-source/themes/` and preserve named layers. Source format may be PSD, PSB, or ORA. Runtime files are exported from those sources and are the only files packaged into the app.

## 3. Technical standards

### Design space

- Master scene coordinates: `1536 × 1536` square.
- Background bleed master: `2048 × 2048`.
- Primary safe zone: x `10–90%`, y `7–91%`.
- Hero subject safe zone: x `18–82%`, y `12–86%`.
- The renderer uses center-crop for backgrounds and fit-within-safe-zone for hero subjects.
- Important content must survive viewport aspect ratios from `0.78` to `1.25` width/height.

### Runtime formats

- Opaque backgrounds: WebP, visually lossless, quality 88–92, sRGB.
- Transparent painted layers: lossless WebP RGBA, sRGB.
- Grayscale masks requiring exact values: PNG, 8-bit grayscale or RGBA.
- Small repeated particles: lossless WebP RGBA.
- No baked text, participant colors, amounts, or currency symbols.
- Premultiplied-alpha fringes must be checked against black and the scene background.

### Layer rules

- Trim transparent bounds unless a shared registration canvas is explicitly required.
- Every trimmed layer must declare a normalized pivot and design-space attachment point in `scene.json`.
- Bone-connected layers need 12–24 px of painted overlap at runtime resolution.
- Color customization must use a neutral grayscale mask, never a fully recolored character.
- Shadows must be separate from subjects.
- Lighting overlays must use transparent additive-compatible artwork without baked black backgrounds.

### Quality variants

Each manifest may reference three variants:

- `low`: maximum subject dimension 768 px;
- `medium`: maximum subject dimension 1280 px;
- `high`: maximum subject dimension 1792 px.

Do not upscale a low-resolution export at runtime. Backgrounds may share medium/high artwork if measured memory remains acceptable.

## 4. Common assets

| File | Size | Format | Alpha | Pivot | Layers / use | Visual description |
|---|---:|---|---|---|---|---|
| `common/particle_soft.webp` | 128×128 | Lossless WebP | Yes | 0.50, 0.50 | Single sprite | Soft radial mote with irregular painterly edge; neutral white for tinting. |
| `common/particle_speck.webp` | 96×96 | Lossless WebP | Yes | 0.50, 0.50 | Single sprite | Small dust/debris fleck with non-circular silhouette. |
| `common/particle_streak.webp` | 64×192 | Lossless WebP | Yes | 0.50, 0.82 | Single sprite | Narrow motion streak for droplets or sparks; soft tail and crisp head. |

## 5. Vessel assets

The vessel is an elegant Mediterranean/classical decorative pitcher, not fantasy glassware. Its construction must read clearly: thick base, open rim, pouring lip, attached handle, slightly imperfect hand-formed glass, and believable contact with the surface.

| File | Size | Format | Alpha/background | Pivot | Layers / animation | Visual description |
|---|---:|---|---|---|---|---|
| `vessel/backgrounds/studio.webp` | 2048×2048 | WebP q90 | Opaque | 0.50, 0.50 | Static, center-crop | Dark warm studio alcove with restrained Mediterranean plaster/stone character and clear central value grouping. |
| `vessel/backgrounds/table.webp` | 2048×768 | Lossless WebP | Yes | 0.50, 1.00 | Foreground plane | Matte stone/wood surface with subtle texture; no strong pattern under the vessel. |
| `vessel/vessel/contact_shadow.webp` | 1024×320 | Lossless WebP | Yes | 0.50, 0.50 | Independent opacity/scale | Soft grounded shadow, darker beneath thick base and handle side. |
| `vessel/vessel/body_back.webp` | 1152×1408 | Lossless WebP | Yes | 0.47, 0.86 | Behind liquid | Rear glass, handle rear edge, inner reflections, restrained distortion. |
| `vessel/vessel/liquid_mask.png` | 1152×1408 | PNG | Transparent mask | 0.47, 0.86 | Exact liquid clipping mask | White interior volume excluding glass wall, handle, open mouth, and base thickness. |
| `vessel/vessel/body_front.webp` | 1152×1408 | Lossless WebP | Yes | 0.47, 0.86 | In front of liquid | Front glass thickness, base refraction, side distortion, imperfections, and contact edge. |
| `vessel/vessel/rim.webp` | 768×320 | Lossless WebP | Yes | 0.42, 0.58 | Separate highlight animation | Open elliptical rim and pouring lip with thickness and inner dark edge. |
| `vessel/vessel/handle_front.webp` | 512×1024 | Lossless WebP | Yes | 0.16, 0.50 | Slight highlight drift | Front handle edge and attachment thickness. |
| `vessel/effects/highlight_mask.png` | 1152×1408 | PNG | Transparent grayscale | 0.47, 0.86 | Shader/light mask | Broad imperfect highlight bands; no hard white vector stripe. |
| `vessel/effects/refraction_mask.png` | 1152×1408 | PNG | Transparent grayscale | 0.47, 0.86 | Optional HIGH distortion pass | Low-frequency normal-like distortion information for liquid and background sampling. |
| `vessel/particles/droplet.webp` | 128×192 | Lossless WebP | Yes | 0.50, 0.72 | Tintable particle | Elongated water droplet with bright edge and translucent core. |
| `vessel/particles/bubble.webp` | 96×96 | Lossless WebP | Yes | 0.50, 0.50 | Tintable particle | Thin irregular ring, tiny highlight, transparent center. |
| `vessel/particles/residue.webp` | 96×160 | Lossless WebP | Yes | 0.50, 0.32 | Wall adhesion/slide | Soft, deformed droplet for inside-glass residue. |

Liquid color and mixing are rendered, not baked. Participant color A and B enter as separate shader inputs. Transition regions may contain derived secondary color, but neither source color may disappear at normal contribution ratios.

## 6. Woodland environment and container

| File | Size | Format | Alpha/background | Pivot | Layers / animation | Visual description |
|---|---:|---|---|---|---|---|
| `woodland/backgrounds/forest_far.webp` | 2048×2048 | WebP q90 | Opaque | 0.50, 0.50 | Parallax depth 0 | Soft distant trunks and foliage, warm daylight, low detail. |
| `woodland/backgrounds/forest_mid.webp` | 2048×1536 | Lossless WebP | Yes | 0.50, 0.65 | Parallax depth 1 | Midground trunks, branches, ferns, and depth fog openings. |
| `woodland/backgrounds/forest_floor.webp` | 2048×1024 | Lossless WebP | Yes | 0.50, 0.82 | Subject plane | Earth, roots, moss, stones, restrained mushrooms, and contact texture. |
| `woodland/backgrounds/foreground.webp` | 2048×768 | Lossless WebP | Yes | 0.50, 1.00 | Parallax depth 3 | Edge-framing leaves and grass; center remains visually clear. |
| `woodland/effects/light_shafts.png` | 1536×1536 | PNG | Transparent grayscale | 0.50, 0.00 | Additive/tinted overlay | Two or three soft irregular shafts, never a fantasy glow curtain. |
| `woodland/container/body.webp` | 896×768 | Lossless WebP | Yes | 0.50, 0.82 | Static base | Hand-carved hollow-log savings container with aged wood, bark remnants, scratches, moss, and a readable opening. |
| `woodland/container/lid.webp` | 896×320 | Lossless WebP | Yes | 0.50, 0.78 | Hinge pivot in manifest | Wooden lid/mechanism with thickness and worn hinge hardware. |
| `woodland/container/interior.webp` | 704×416 | Lossless WebP | Yes | 0.50, 0.58 | Behind contents | Dark wooden cavity with subtle bounced light. |
| `woodland/container/contents_sparse.webp` | 704×352 | Lossless WebP | Yes | 0.50, 0.72 | 0–25% crossfade | A few grounded contribution objects with visible gaps. |
| `woodland/container/contents_medium.webp` | 704×352 | Lossless WebP | Yes | 0.50, 0.72 | 25–50% crossfade | Clearly accumulated objects, still below midpoint. |
| `woodland/container/contents_dense.webp` | 704×352 | Lossless WebP | Yes | 0.50, 0.72 | 50–75% crossfade | Dense layered mass with limited individual silhouettes. |
| `woodland/container/contents_full.webp` | 704×352 | Lossless WebP | Yes | 0.50, 0.72 | 75–100% crossfade | Near-full contents approaching the opening without implausible overflow. |
| `woodland/container/contact_shadow.webp` | 1024×320 | Lossless WebP | Yes | 0.50, 0.50 | Independent reaction | Grounded soft shadow with moss/stone occlusion. |
| `woodland/props/acorn.webp` | 128×160 | Lossless WebP | Yes | 0.50, 0.56 | Ballistic rotation | Detailed acorn with cap, slight asymmetry, readable at 20–36 px. |
| `woodland/props/hazelnut.webp` | 128×128 | Lossless WebP | Yes | 0.50, 0.50 | Ballistic rotation | Warm natural shell, non-spherical silhouette. |
| `woodland/props/berry.webp` | 96×96 | Lossless WebP | Yes | 0.50, 0.50 | Ballistic/cluster | Deep red berry with restrained specular highlight and tiny stem. |
| `woodland/props/chestnut.webp` | 128×128 | Lossless WebP | Yes | 0.50, 0.50 | Ballistic rotation | Glossy chestnut with pale base; no spiked shell unless separately supplied. |
| `woodland/props/token.webp` | 128×128 | Lossless WebP | Yes | 0.50, 0.50 | Raccoon contribution | Small found brass/steel object, abstract and not currency-branded. |
| `woodland/props/honey_drop.webp` | 128×192 | Lossless WebP | Yes | 0.50, 0.32 | Viscous strand head | Amber translucent droplet; strand body is procedural. |

## 7. Woodland character packs

Every character pack is layered and riggable. Eyes must remain proportionate to the skull. Paws, ears, and tails must follow real animal structure even where behavior is selectively anthropomorphic.

### Required core pack layout

| Layer file within each animal folder | Typical size | Alpha | Pivot meaning |
|---|---:|---|---|
| `shadow.webp` | 512×192 | Yes | Center of ground contact |
| `body.webp` | 640×512 | Yes | Center of mass / spine base |
| `head.webp` | 384×384 | Yes | Neck attachment |
| `ear_near.webp` | 192×256 | Yes | Ear base |
| `ear_far.webp` | 192×256 | Yes | Ear base |
| `leg_front_near.webp` | 192×384 | Yes | Shoulder/hip joint |
| `leg_front_far.webp` | 192×384 | Yes | Shoulder/hip joint |
| `leg_hind_near.webp` | 256×384 | Yes | Hip joint |
| `leg_hind_far.webp` | 256×384 | Yes | Hip joint |
| `paw_near.webp` | 160×160 | Yes | Wrist/ankle joint |
| `paw_far.webp` | 160×160 | Yes | Wrist/ankle joint |
| `eye_open.webp` | 96×96 | Yes | Eye center |
| `eye_closed.webp` | 96×48 | Yes | Eye center |
| `accent_mask.png` | Match body | Yes | Same as body; neutral tint mask |

Actual trimmed dimensions may be smaller, but the manifest must preserve the listed joint semantics.

### Species-specific requirements

| Folder | Additional layers and dimensions | Primary pivot / attachments | Visual description |
|---|---|---|---|
| `woodland/animals/bear/` | `muzzle.webp` 256×192; `honey_container.webp` 192×256; `honey_paw.webp` 192×256 | Body 0.48,0.64; head at neck; container grip marker | Young adult woodland bear, powerful shoulder mass, realistic muzzle and paws, friendly posture without teddy-bear proportions. Dense fur clumps and grounded weight. |
| `woodland/animals/squirrel/` | `tail_base.webp` 384×512; `tail_mid.webp` 448×576; `tail_tip.webp` 384×512 | Tail segments pivot at 0.50,0.88; acorn grip marker on near paw | Realistic squirrel torso and skull, expressive but small eyes, large layered tail with believable spine and fur flow. This is the first production vertical-slice character. |
| `woodland/animals/fox/` | `tail_base.webp` 448×320; `tail_tip.webp` 448×320; `muzzle.webp` 256×160 | Tail root at hip; muzzle at skull front | Lean red fox with long legs, narrow muzzle, pointed ears, cream chest, and weighted brush tail. No upright mascot stance in idle. |
| `woodland/animals/rabbit/` | `ear_near.webp` and `ear_far.webp` 192×448; `nose.webp` 96×96; `tail.webp` 160×160 | Ear bases at skull; hind-leg compression marker | Crouched rabbit with powerful haunches, long ears, subtle nose motion, and natural forepaw use. Avoid round toy body. |
| `woodland/animals/raccoon/` | `mask.webp` 256×160; `tail_base.webp` 384×256; `tail_tip.webp` 384×256; `inspect_paw.webp` 192×256 | Mask registered to head; tail root at hip | Stocky raccoon with recognizable face mask, ringed tail, dexterous paws, and curious object-focused gaze. |
| `woodland/animals/hedgehog/` | `quill_back.webp` 640×448; `face_body.webp` 512×384; `nose.webp` 96×96 | Quill mass at body center; face at neck | Low grounded hedgehog silhouette with layered quills, pointed face, tiny legs, and no oversized eyes. |

### Existing additional identities

The current app also exposes Owl and Otter. They must remain available as procedural fallback until production packs are approved.

| Folder | Required special layers | Visual description |
|---|---|---|
| `woodland/animals/owl/` | `body`, `head`, `wing_near`, `wing_far`, `eye_left_open/closed`, `eye_right_open/closed`, `beak`, `talons`, `accent_mask` | Grounded woodland owl with broad facial disk, layered feather groups, normal eye size, and stable perch. |
| `woodland/animals/otter/` | `body`, `head`, four limbs, `tail_base`, `tail_tip`, `muzzle`, `accent_mask` | Long, low river-otter anatomy with dense wet-looking fur accents and tapered muscular tail. |

## 8. Mempool environment

| File | Size | Format | Alpha/background | Pivot | Layers / animation | Visual description |
|---|---:|---|---|---|---|---|
| `mempool/backgrounds/tunnel_far.webp` | 2048×2048 | WebP q90 | Opaque | 0.50, 0.50 | Static/parallax 0 | Grounded rock tunnel, charcoal and dark brown, no cyberpunk geometry. |
| `mempool/backgrounds/supports_mid.webp` | 2048×1536 | Lossless WebP | Yes | 0.50, 0.58 | Parallax 1 | Heavy timber supports, bolts, cables, and restrained equipment silhouettes. |
| `mempool/backgrounds/ground_rails.webp` | 2048×896 | Lossless WebP | Yes | 0.50, 0.82 | Subject plane | Dusty ground, steel rails, sleepers, grease, and contact shadows. |
| `mempool/backgrounds/foreground_rock.webp` | 2048×640 | Lossless WebP | Yes | 0.50, 1.00 | Parallax 3 | Edge-framing rock and tools; center kept clear. |
| `mempool/effects/lantern_glow.png` | 512×512 | PNG | Transparent grayscale | 0.50, 0.50 | Warm tinted overlay | Localized soft amber halo with no baked flare streak. |
| `mempool/effects/rail_pulse_mask.png` | 1536×512 | PNG | Transparent grayscale | 0.50, 0.50 | Settlement pulse | Narrow irregular path following rails/mechanism; no full-screen light. |
| `mempool/props/impact_rock.webp` | 512×512 | Lossless WebP | Yes | 0.50, 0.68 | Pickaxe target | Layered rock/mechanical switch with visible strike face and small fracture details. |
| `mempool/props/transaction_ingot.webp` | 192×128 | Lossless WebP | Yes | 0.50, 0.50 | Ballistic object | Small sealed metal ingot/plate with abstract engraving and no Bitcoin logo. |
| `mempool/props/rock_chip.webp` | 96×96 | Lossless WebP | Yes | 0.50, 0.50 | Impact particle | Angular rock fragment with one warm lit edge. |
| `mempool/props/spark.webp` | 64×192 | Lossless WebP | Yes | 0.50, 0.88 | Impact particle | Fine warm-orange spark with short bright head. |

## 9. Mine cart assets

| File | Size | Format | Alpha | Pivot | Layers / animation | Visual description |
|---|---:|---|---|---|---|---|
| `mempool/cart/shadow.webp` | 1024×320 | Lossless WebP | Yes | 0.50, 0.50 | Independent suspension reaction | Heavy ground/contact shadow aligned to rails. |
| `mempool/cart/body_back.webp` | 896×640 | Lossless WebP | Yes | 0.50, 0.68 | Behind load | Steel cart rear wall, dents, seams, restrained rust, and inner darkness. |
| `mempool/cart/load_sparse.webp` | 704×320 | Lossless WebP | Yes | 0.50, 0.70 | 0–25% | Few transaction ingots resting with gaps. |
| `mempool/cart/load_medium.webp` | 704×320 | Lossless WebP | Yes | 0.50, 0.70 | 25–50% | Mid-level load mass. |
| `mempool/cart/load_dense.webp` | 704×352 | Lossless WebP | Yes | 0.50, 0.72 | 50–75% | Dense load with several readable foreground objects. |
| `mempool/cart/load_full.webp` | 704×384 | Lossless WebP | Yes | 0.50, 0.74 | 75–100% | Near-full load with believable stacking. |
| `mempool/cart/body_front.webp` | 896×640 | Lossless WebP | Yes | 0.50, 0.68 | Front occlusion | Front steel wall, braces, rivets, scratches, and participant-neutral material. |
| `mempool/cart/wheel_left.webp` | 256×256 | Lossless WebP | Yes | 0.50, 0.50 | Rotation/suspension | Heavy steel wheel with grease and readable hub. |
| `mempool/cart/wheel_right.webp` | 256×256 | Lossless WebP | Yes | 0.50, 0.50 | Rotation/suspension | Dedicated layer matching left wheel. |
| `mempool/cart/accent_mask.png` | 896×640 | PNG | Yes | 0.50, 0.68 | Low-opacity tint | Small equipment marking only; never recolor the whole cart. |

## 10. Miner packs

Miner A and Miner B require separate art. Mirroring Miner A is not sufficient because the brief explicitly requires distinguishable workers.

| Layer file within each miner folder | Size | Alpha | Pivot / attachment |
|---|---:|---|---|
| `shadow.webp` | 512×192 | Yes | Ground center |
| `torso.webp` | 448×512 | Yes | Pelvis / body center |
| `head.webp` | 256×320 | Yes | Neck |
| `helmet.webp` | 320×192 | Yes | Crown / head registration |
| `helmet_lamp.webp` | 128×128 | Yes | Lamp mount |
| `upper_arm_near.webp` | 160×320 | Yes | Shoulder |
| `forearm_near.webp` | 160×320 | Yes | Elbow |
| `hand_near.webp` | 128×128 | Yes | Wrist / pickaxe grip |
| `upper_arm_far.webp` | 160×320 | Yes | Shoulder |
| `forearm_far.webp` | 160×320 | Yes | Elbow |
| `hand_far.webp` | 128×128 | Yes | Wrist / pickaxe grip |
| `thigh_near.webp` | 192×352 | Yes | Hip |
| `shin_near.webp` | 192×352 | Yes | Knee |
| `boot_near.webp` | 256×160 | Yes | Ankle |
| `thigh_far.webp` | 192×352 | Yes | Hip |
| `shin_far.webp` | 192×352 | Yes | Knee |
| `boot_far.webp` | 256×160 | Yes | Ankle |
| `pickaxe_handle.webp` | 128×768 | Yes | Lower grip |
| `pickaxe_head.webp` | 512×192 | Yes | Handle socket |
| `accent_mask.png` | Match relevant garment | Yes | Same registration as garment |

### Miner A

- Folder: `mempool/miners/miner_a/`.
- Master character bounds: `768 × 1280`.
- Body pivot: `0.50, 0.70`.
- Silhouette: broad shoulders, sturdy stance, rolled work-shirt sleeves, dark blue overalls, heavy brown boots.
- Participant accent: narrow helmet stripe and small chest patch only.
- Fabric must show folds and dust; exposed skin detail remains broad and phone-readable.

### Miner B

- Folder: `mempool/miners/miner_b/`.
- Master character bounds: `768 × 1280`.
- Body pivot: `0.50, 0.70`.
- Silhouette: leaner build, jacket/scarf layer, different helmet profile, separate face and posture.
- Participant accent: scarf edge, lamp rim, or tool marking only.
- Must not be a recolored or mirrored Miner A.

## 11. Manifest requirements

Each `scene.json` must provide:

```json
{
  "schema": 1,
  "theme": "woodland",
  "designWidth": 1536,
  "designHeight": 1536,
  "safeZone": [0.10, 0.07, 0.90, 0.91],
  "layers": [
    {
      "id": "squirrel.body",
      "file": "animals/squirrel/body.webp",
      "size": [640, 512],
      "pivot": [0.48, 0.64],
      "position": [0.20, 0.65],
      "quality": ["low", "medium", "high"]
    }
  ]
}
```

Required validation:

- referenced file exists;
- dimensions match the manifest;
- pivot and position values are finite and normalized;
- layer IDs are unique;
- z-order is deterministic;
- required attachment markers exist;
- runtime memory estimate stays below the tier budget;
- alpha assets contain no opaque matte fringe.

## 12. Memory budgets

Decoded bitmap targets per active theme:

| Tier | Static scene assets | Character/prop assets | Total target |
|---|---:|---:|---:|
| LOW | ≤ 10 MB | ≤ 6 MB | ≤ 16 MB |
| MEDIUM | ≤ 18 MB | ≤ 10 MB | ≤ 28 MB |
| HIGH | ≤ 28 MB | ≤ 16 MB | ≤ 44 MB |

Only the selected theme and shared particles remain decoded. The previous theme may stay warm for at most one transition and must then be released. Source PSD/ORA files are never packaged in the APK.

## 13. Delivery checklist

Each art delivery must include:

- editable layered source;
- exported runtime assets;
- updated `scene.json`;
- contact sheet on dark and light checker backgrounds;
- pivot/attachment overlay image;
- safe-zone preview at compact, tall, and tablet aspect ratios;
- LOW/MEDIUM/HIGH memory estimate;
- confirmation that no text, currency, participant color, or branding is baked into the artwork.
