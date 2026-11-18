# SportCenter Design System Foundation

Status: reference for review. The values below document the isolated Landing Preview and guide incremental adoption; they do not authorize a portal-wide redesign.

## Principles

- The public site presents SportCenter as an energetic, welcoming sports center: strong real imagery, clear editorial hierarchy, and restrained navy, blue, and cyan accents.
- Role-based portals prioritize readable data, predictable controls, task efficiency, and explicit consequential actions.
- Use real application data and established routes. Keep loading, error, empty, and validation feedback visible.
- Prefer scoped styles, accessible names, keyboard operation, visible focus, and reduced-motion support.
- Avoid unsupported claims, decorative dashboard metrics, excessive gradients, and animation that delays work.

## Landing Preview tokens

These variables are scoped to `.lp`. Do not consume them from authenticated portal pages or copy the Landing theme switch into a second global theme system.

| Purpose | Light | Dark |
| --- | --- | --- |
| Page background `--lp-bg` | `#FFFFFF` | `#090B0F` |
| Main surface `--lp-surface` | `#FFFFFF` | `#171C25` |
| Secondary surface `--lp-surface-2` | `#F4F6FA` | `#111318` |
| Main text `--lp-text` | `#0B2746` | `#F3F5F9` |
| Supporting text `--lp-sub` | `#4A5363` | `#A3ABBA` |
| Faint text `--lp-faint` | `#788191` | `#747C8B` |
| Input placeholder `--lp-placeholder` | `#788191` | `#929BAA` |
| Primary accent `--lp-accent` | `#1769D2` | `#4A9BF0` |
| Cyan accent `--lp-accent-2` | `#0BB5D8` | `#55CBE8` |
| Hero / consultation bridge | `#0B2746` | `#102540` |
| Border | `rgba(10,14,22,.08)` | `rgba(255,255,255,.07)` |
| Footer secondary text | `#AEB6C3` | `#B8C0CC` |
| Footer subtle text | `#929BAA` | `#A3ABBA` |

The Footer retains its charcoal surface in either Landing theme; Footer-specific text tokens therefore keep secondary copy readable on that surface. The Landing page uses Be Vietnam Pro with Segoe UI/system fallbacks. The main container is 1240px with 16/24/32px responsive side gutters. Existing section-specific spacing uses `clamp()`; use a 4px rhythm when adding new spacing rather than forcing a wholesale token rewrite.

Existing shared geometry includes a 28px public card radius, 12px input/button radius, layered `--lp-shadow` / `--lp-shadow-lg`, and `--lp-ease`. Reuse the existing shadow and radius variables where they fit; do not make every surface equally elevated.

## Shared token contract proposal

Phase 3C defines a vocabulary for future adoption; it does not change live CSS. Use namespaced `--sc-*` tokens for shared mechanics and semantic roles. Keep each experience's theme values mapped at its own root (`.lp`, `.public-site`, or the portal shell). A shared name describes the role, not a promise that every theme uses the same color or geometry.

The current-state inventory, adoption classifications, visual-risk notes, and import plan are in [SPORTCENTER_TOKEN_MAPPING.md](./SPORTCENTER_TOKEN_MAPPING.md). No existing variable should be renamed or replaced until its consumers and rendered effect have been verified.

## Component and interaction patterns

- **Section navigation:** `SectionLink` targets a real in-page ID, preserves hash navigation, uses the shared scroll helper, and respects reduced motion. Anchors use `--lp-nav-h` plus `--lp-anchor-gap` for sticky-header clearance.
- **Buttons:** use `.lp-btn` variants, clear text for important actions, visible `:focus-visible`, hover/pressed feedback, and disabled state. Avoid icon-only ambiguous actions.
- **Forms:** keep visible labels and existing validation/API behavior. `.lp-consult .form-control` uses theme surfaces, a scoped placeholder token, and an accent focus ring.
- **Cards and sections:** use `--lp-surface`, `--lp-surface-2`, border tokens, and restrained elevation. Keep photography scoped to public editorial sections; keep functional portal cards neutral and dense.
- **Feedback:** retain explicit loading, error, success, and empty states. Do not replace real records with illustrative data.
- **Motion:** existing transitions are short and scoped. The reduced-motion media query minimizes animation and reveals content without requiring motion.

Responsive breakpoints currently used by Landing Preview include 1199px, 1023px, 899px, 767px, 639px, and 539px. Prefer the closest existing breakpoint for a component; do not add a new breakpoint without a demonstrated layout need.

## Public and management experiences

The Public site may use full-bleed photography, bento/editorial composition, theme switching, scroll reveals, and more expressive spacing. These are presentation patterns, not defaults for role portals.

Member, Coach, Receptionist, and Admin interfaces should favor a stable light workspace, compact page headers, accessible tables and forms, clear status text, restrained elevation, and explicit actions. Keep role permissions, APIs, and business rules independent of visual styling. A shared component should expose suitable variants rather than importing Landing CSS or decorative effects.

## Incremental adoption plan

1. Review and approve this reference and the Landing Preview screenshots.
2. Extract only genuinely shared foundations (for example, neutral color aliases, focus behavior, and button primitives) into a portal-safe shared layer without changing page appearance or authorization.
3. Migrate one shared portal component at a time: buttons and form controls, then status badges, tables, and page headers. Add focused regression coverage before each adoption.
4. Apply the verified primitives to one representative page per role, then expand page by page after screenshot and responsive review.
5. Keep Public-only imagery, scroll effects, theme behavior, and section layouts in `.lp` unless a specific component is explicitly designed for both contexts.

For the proposed order and first controlled token-alias target, see the Phase 3C mapping document. This is a plan only; no shared stylesheet is active.

## Review notes

- Browser screenshot review is required before approving the latest Consultation overlay, placeholder, and Footer contrast adjustments.
- The preview currently contains contact literals `1900 6868`, `sportcenter@gmail.com`, and `Thành phố Hồ Chí Minh`. Their current presence in the component is confirmed; their business accuracy is not verified here and should be confirmed by the project owner.
- Footer links target the Landing sections for sports, coaches, facilities, and membership; the account link targets the existing login/dashboard destination. “Lên đầu trang” targets the Hero through the same anchor helper. The navbar calls the membership section “Hội viên,” and the matching Footer label now uses the same wording.
- No Footer-specific consultation link is currently present. The primary navbar and Hero consultation links continue to target the existing consultation section.

## UI/UX Review Principles and Anti-Generic-AI Design Guidelines

Evaluate proposed interface changes against visual hierarchy, spacing, alignment, contrast, typography, Gestalt grouping, and clear affordances. Consider target size and placement through Fitts's Law, choice complexity through Hick–Hyman Law, and the relevant Nielsen usability heuristics, including consistency, feedback, error prevention, and recognition over recall.

Before proposing a change, identify the concrete usability or visual issue and the evidence for it. Prefer the smallest effective correction; do not redesign a page simply because another composition is possible. Clearly distinguish source-code inspection and inferred risks from issues confirmed in a rendered browser or screenshot.

Avoid generic SaaS decoration: repetitive badges, excessive cards, generic gradients, unnecessary glow, and animation without a user or content purpose. Preserve SportCenter's established navy, blue, cyan, and neutral identity, and use authentic imagery and meaningful content instead of visual filler.

Public pages may use expressive editorial composition where it supports discovery. Admin, Member, Coach, and Receptionist interfaces must prioritize task completion, functional clarity, readable data, predictable controls, and explicit feedback. Do not apply landing-page decoration to operational dashboards.

Obtain approval before significant redesigns. For all visual work, preserve existing business logic, APIs, routes, permissions, and accessibility, including keyboard use, visible focus, readable contrast, responsive layouts, and reduced-motion preferences.
