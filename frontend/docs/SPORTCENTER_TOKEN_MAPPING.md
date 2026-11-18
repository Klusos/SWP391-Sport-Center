# SportCenter Shared Token Mapping — Phase 3C / 3D

Status: Phase 3C was a proposal. Phase 3D activates a small, portal-scoped alias set for `AdminSectionHeader`; the rest remains proposed. No global palette or cross-layer theme was activated.

## Current runtime ownership

| Layer | Runtime entry point / scope | Current token character |
| --- | --- | --- |
| Portal foundation | `src/main.jsx` imports `src/styles.css`; global `:root` | Light workspace palette, font, spacing, radii, focus, and shadows |
| Existing Public pages | `src/App.jsx` imports `src/public-site/styles.css`; values scoped to `.public-site` | Public-specific navy/blue/cyan palette and Bootstrap-compatible font variable; no Public dark theme |
| Landing Preview | `src/landing/LandingPage.jsx` imports `src/landing/landing.css`; values scoped to `.lp` and `.lp[data-theme="dark"]` | Independent Light/Dark editorial palette, typography, layout, and decorative effects |
| Legacy stylesheet | `src/index.css` | Not imported by the current entry points inspected; its values are not part of the active runtime contract |

## Phase 3D activated pilot

`src/tokens/admin-pilot.css` is imported by `src/main.jsx` immediately after `src/styles.css`. Its declarations are scoped to `.layout`, the authenticated portal shell used by both current `AdminSectionHeader` consumers. It contains no element selectors or Landing/Public selectors. The token values are aliases to existing portal variables with exact fallback values; because CSS custom properties are inert until consumed, the import adds no independent appearance rules.

| Active token | Definition within `.layout` | Existing exact value | Consumer |
| --- | --- | --- | --- |
| `--sc-space-2` | `var(--space-2, 8px)` | 8px | Header eyebrow gap and spacing |
| `--sc-space-3` | `var(--space-3, 12px)` | 12px | Header meta row gap and separator spacing |
| `--sc-space-4` | `var(--space-4, 16px)` | 16px | Header bottom margin at narrow widths |
| `--sc-color-action` | `var(--blue, #1769d2)` | `#1769d2` | Eyebrow and icon foreground |
| `--sc-color-text-primary` | `var(--navy, #0b2745)` | `#0b2745` | Header title and summary value |
| `--sc-color-text-secondary` | `var(--text-sub, #5b6e82)` | `#5b6e82` | Supporting description and summary text |

The component keeps its exact local values for nonmatching dimensions, icon/chip surfaces and borders, font sizing/weight, and its 3px focus outline. No radius, shadow, breakpoint, typography, or focus token was activated because there is no exact, suitable existing alias for this component's current declaration. The alias file is deliberately `.layout`-scoped rather than a global `:root` foundation; this is a Phase 3C proposal deviation that narrows availability to the verified portal context.

All other rows below describe inactive proposals unless specifically listed in the Phase 3D table above.

`styles.css` currently provides global portal values including `--navy`, `--blue`, `--bg`, `--surface`, `--border`, `--text-main`, `--text-sub`, state colors, `--font-sans`, `--space-1` through `--space-6`, `--radius-sm` through `--radius-lg`, `--focus-ring`, and card/menu shadows. The Google font import and `body` rules are also in this file.

`public-site/styles.css` defines a separate `.public-site` palette. Its font variable starts with Plus Jakarta Sans, as does the portal font stack, but the fallback lists differ. Similar leading font names do not prove identical computed typography on every platform.

`landing.css` defines `--lp-*` values, a separately loaded Be Vietnam Pro font, and a distinct dark palette. It also owns the Landing sticky-header/anchor dimensions, photo overlays, layered shadows, easing, reveal, tilt, and other editorial effects. These are intentionally local.

## Shared contract proposal

Use a future `--sc-*` namespace for cross-context concepts. Separate mechanical scales (font roles, spacing, control geometry, motion durations) from semantic color roles (background, surface, primary/secondary text, border, primary action, success, warning, danger, information). A shared semantic role may resolve to different values in `.lp`, `.public-site`, and the portal shell. Do not make Landing's dark theme a global theme, and do not map a role solely because two current variables have similar names.

Proposed mechanics include a 4px-based spacing vocabulary; named typography roles; control/card radius roles; focus-ring behavior; short interaction durations/easing; and responsive layout conventions. Exact values should be adopted only where an existing consumer can retain the same rendered value. The current portal spacing scale is a useful candidate baseline, but it does not establish that all existing page spacing should be normalized.

Suggested token categories, pending incremental implementation:

- `--sc-font-family-ui`, `--sc-font-size-*`, `--sc-line-height-*`, and `--sc-font-weight-*` for typography roles.
- `--sc-space-*` for spacing; retain local layout-specific `clamp()` values where they encode composition rather than a reusable gap.
- `--sc-radius-control`, `--sc-radius-card`, and `--sc-radius-pill` for geometry roles.
- `--sc-shadow-*` for elevation roles, with context-specific values.
- `--sc-focus-*` for focus visibility, while allowing contrast-safe context-specific colors.
- `--sc-motion-duration-*` and `--sc-motion-easing-*` for interaction timing; decorative Landing animation remains Landing-only.
- `--sc-color-*` semantic roles mapped independently at each context root, not a single cross-application palette.

These names are proposals, not active CSS variables.

## Existing-to-proposed token map

| Existing token/value | Current owner | Proposed shared role | Class | Visual impact if migrated | Priority |
| --- | --- | --- | --- | --- | --- |
| `--space-1..6`: 4, 8, 12, 16, 24, 32px | Portal `:root` | `--sc-space-1..6` aliases preserving exact values | ALIAS | Low if aliases only; high if consumers are rescaled | P1 |
| Existing Public and Landing gaps, often page-local or `clamp()` | `.public-site`, `.lp` | Spacing roles where the same use is confirmed | REVIEW | Could alter section rhythm and responsive composition | P2 |
| `--font-sans` and Public `--bs-body-font-family` | Portal `:root`, `.public-site` | `--sc-font-family-ui` as a vocabulary; preserve context fallback stacks initially | REVIEW | Font fallback or line wrapping may change | P1 |
| `--lp-font` / Be Vietnam Pro stack | `.lp` | None initially; keep as Landing typography identity | KEEP LOCAL | Sharing could change wrapping and brand character | P2 |
| Hardcoded heading/body sizes across layers | All layers | `--sc-font-size-*`, `--sc-line-height-*`, `--sc-font-weight-*` roles | SHARE (contract); REVIEW (values) | Broad if applied without per-component comparison | P2 |
| `--bg`, `--surface`, `--text-main`, `--text-sub`, `--border` | Portal `:root` | `--sc-color-background/surface/text-primary/text-secondary/border` | SHARE (semantic role); KEEP LOCAL (values) | Low with exact scoped aliases; high if palette is unified | P1 |
| Public `--navy`, `--blue`, `--cyan`, `--ice`, `--ink`, `--muted` | `.public-site` | Context mappings for semantic roles; retain brand-specific roles where meaning differs | KEEP LOCAL / REVIEW | Public appearance changes if values are replaced | P2 |
| `--lp-bg`, `--lp-surface`, `--lp-text`, `--lp-accent`, dark overrides | `.lp` and `.lp[data-theme="dark"]` | Map semantic roles only through a Landing-specific adapter if needed | KEEP LOCAL | High; current theme appearance is approved | P2 |
| Portal state colors and Public green/state variants | Portal `:root`, `.public-site` and components | `--sc-color-success/warning/danger/info` role vocabulary | SHARE (meaning); KEEP LOCAL (values) | State comprehension/contrast risk if values are unified | P1 |
| `--radius-sm/md/lg` (9/14/18px), Public and component radii, `--lp-radius` (28px) | Portal, Public, Landing | `--sc-radius-control/card/pill` aliases only after matching use | REVIEW | Medium; affects perceived density and card geometry | P2 |
| `--shadow-card`, `--shadow-menu`, `--lp-shadow`, `--lp-shadow-lg`, local shadows | Portal, Landing, component CSS | `--sc-shadow-*` role vocabulary with context values | REVIEW | Medium; Landing's layered elevation differs from portal utility | P2 |
| Portal `--focus-ring`; Public `:focus-visible`; Landing accent outline | Portal, Public, Landing | `--sc-focus-width/offset/style` behavior plus per-context color mapping | SHARE (behavior); REVIEW (visual values) | Medium; visibility and contrast can regress | P0 |
| Landing easing and reveal/tilt/parallax durations; local transitions elsewhere | `.lp`, component CSS | Common short interaction timing roles only | KEEP LOCAL / REVIEW | Decorative effects could leak into frequent portal workflows | P2 |
| Landing 1199/1023/899/767/639/539/379; Public 1200/991.98/900/767.98/600/575.98; portal-specific cutoffs | Each layer/page | Named layout conventions, not shared numeric cutoffs yet | REVIEW | High if changed globally; tables and navigation may reflow | P2 |
| Landing 1240px container and responsive gutters; Public 1280px/28px; portal content widths | `.lp`, `.public-site`, portal pages | `--sc-container-*` roles only for components with same constraints | REVIEW | Medium to high across page composition | P2 |
| `--home-*`, `--discipline-*`, `--cm-*`, `--sp-*`, `--rc-*`, `--cp-*`, `--mc-*`, `--mt-*`, `--lvl-*`, and Landing effect variables | Individual components/pages | No shared mapping without proven multi-owner meaning | KEEP LOCAL | Low while local; accidental aliasing risks unrelated page styling | P3 |

Classification meaning: **SHARE** means a common semantic/mechanical contract is appropriate; **ALIAS** means a new shared name can point to an existing value without changing output; **KEEP LOCAL** means the value or effect belongs to one visual context; **REVIEW** means consumers or rendered consequences need more evidence before adoption. A row can share semantics while retaining local values.

## Theme and stylesheet boundaries

Keep three explicit value owners: `.lp` for the Landing Preview including its local dark theme, `.public-site` for existing Public pages, and the authenticated application shell for portal values. Do not add a global theme toggle or place Landing variables on `:root`. Status colors, text contrast, and focus colors must be checked within each owner.

For future expansion, the proposed organization is:

1. Keep `src/tokens/admin-pilot.css` portal-scoped while this pilot is evaluated. If broader use is approved, split general mechanics into `src/tokens/foundation.css` with no `body`, element, or component appearance rules.
2. Add context mappings only when each layer adopts them: `.public-site`, `.lp`/`.lp[data-theme="dark"]`, and the authenticated portal shell. Keep context values distinct.
3. Existing layer styles remain the owners of component visuals until migrated deliberately.

Current pilot import strategy: `main.jsx` loads the scoped aliases after `styles.css`, before rendering `App`. This makes the existing portal variables available for alias resolution and does not alter Landing or Public token roots. For future layers, load shared definitions once and confirm the Vite CSS isolation behavior before adding scoped context mappings. Do not import `landing.css` globally or put theme values on unscoped `:root`.

## Adoption and equivalence checks

1. Review this single-component pilot and inventory every consumer before expanding it.
2. Compare screenshots at desktop and mobile, plus computed colors, font, spacing, border, radius, and focus. Require no meaningful rendered difference before extending adoption.
3. If accepted, continue to Public shared components, Public page-specific components, portal shared components, then role-specific pages. Migrate one bounded component at a time and check loading/error/empty states and keyboard focus.

Do not consolidate breakpoints, palette values, font stacks, radii, shadows, or motion merely to make token names uniform. Re-evaluate each mapping using its actual consumers.
