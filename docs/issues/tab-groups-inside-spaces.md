# Tab Groups inside Spaces (Chrome-style grouped cards + detail grid)

> Shipped in v1.6.0 (flat header rows) and redesigned to the Chrome-style
> grouped-card model in v1.8.0, mirroring Zerium Browser 1.14.0. Kept here as
> the feature's spec of record (the repository token has no Issues scope to
> open it on GitHub).

## Summary
Chrome-style tab grouping **inside** a Zerium Space. Hierarchy stays
`Zerium G → Space → Tab Group → Tab`; groups are pure metadata on existing
tabs and never replace Spaces or rebuild the profile system.

## Interaction spec (v1.8 grouped-card model)
- The overview renders **one compact group card per group** — colour strip,
  name/count chip row, up to four member page-preview tiles — followed by the
  ungrouped cards and the private cards. There are no full-width header rows
  any more.
- **Tap a group card** → the dedicated group-detail grid opens: colour-coded
  header (back arrow, group chip with live count, overflow menu) above a grid
  of exactly that group's member tabs, plus a "New tab in group" action. New
  tabs created there join that group **and the current Space**.
- **Group overflow menu** (long-press a group card, or the detail header's
  menu button): Rename, Group colour (eight-colour swatch palette), Ungroup
  all, Delete group (closes every member tab and removes the group after a
  confirmation).
- **"Add to group" context menu**: a bare long-press on a tab card opens the
  tab menu — add to an existing group of its Space (or "New group…", which
  opens the creation dialog with an editable title and the eight-colour
  palette), remove from its group, or move to another Space (classic dialog).
- **Drag and drop**: long-press starts a drag. Drop on another tab → pair
  them into a new group, or join the target's group. Drop on a group card →
  join that group. Drop on **empty grid space → ungroup** — in the detail
  grid this is the drag-out gesture; the tab keeps running and returns to the
  overview. Drop on a Space chip → the existing cross-space move.
- While the switcher's tab search is active the overview flattens to matching
  tab cards, so no match can hide behind a group card.
- Move tabs between groups in the same Space; Space and engine profile never
  change. Switching Space still restarts into the target space (groups ride
  the per-space archive); an open detail closes first. Back closes the detail
  first, then the switcher.

## Hard requirements (implemented)
- Moving a tab between groups **preserves the live GeckoSession** — only `Tab.groupId` changes.
- Groups persist per Space (`Prefs.tabGroupsJson`), ride the per-space tab archive (`"g"` field on each Entry),
  and are pruned when empty or when their Space is deleted.
- Private tabs are never groupable (drop rejected with the spaces toast).
- Files: `TabGroups.java` (+ `TabGroupsTest`), `GroupRowPlanner.java` +
  `GroupOps.java` (pure-JVM row planning + drag/drop decisions, unit-tested by
  `GroupRowPlannerTest` + `GroupOpsTest`), `item_tab_group.xml` (group card),
  `view_group_detail.xml` (dedicated detail surface), reworked `TabsAdapter`
  (grouped-card + detail modes, `DragStarter` hook, view-holder close-button
  type fix), drag engine in `MainActivity` (one ItemTouchHelper per grid:
  overview + detail).

## Acceptance
- Grouping, renaming, recolouring, ungrouping and group deletion all survive process death.
- Sessions keep their scroll/history after any group move.
- Detail "New tab in group" lands in the group and the active Space.
- Search, Space switching and private-tab restrictions behave as specified above.
