# Tab Groups inside Spaces (Chrome-style drag-and-drop grouping)

> Tracked as the implementation shipped in v1.6.0. Kept here as the feature's
> spec of record (the repository token has no Issues scope to open it on GitHub).

## Summary
Chrome-style tab grouping **inside** a Zerium Space. Hierarchy stays
`Zerium → Space → Tab Group → Tab`; groups are pure metadata on existing tabs
and never replace Spaces or rebuild the profile system.

## Interaction spec
- Long-press a tab, drag over another tab in the **same Space**, release → group is created.
- Drag additional tabs onto a group's header or a member tab → they join that group.
- Tap a group header → expand / collapse (collapsed groups keep running tabs).
- Long-press a header → rename, pick a colour (8-colour palette), ungroup all.
- Drop a tab on empty switcher space (or the "Ungrouped" header) → ungroup.
- Move tabs between groups in the same Space; Space and login profile never change.
- Drag a tab onto a Space chip → existing cross-space move (quick long-press without
  dragging still opens the classic move-to-space dialog).

## Hard requirements (implemented)
- Moving a tab between groups **preserves the live WebView** — only `Tab.groupId` changes.
- Groups persist per Space (`Prefs.tabGroupsJson`), ride the per-space tab archive (`"g"` field on each Entry),
  and are pruned when empty or when their Space is deleted.
- Incognito tabs are never groupable.
- New files: `TabGroups.java` (+ `TabGroupsTest`), `item_tab_group_header.xml`;
  reworked `TabsAdapter` (header rows + colour dots + `DragStarter` hook);
  drag engine in `MainActivity` (ItemTouchHelper, drop-on-chip / drop-on-tab /
  drop-on-header / drop-on-empty semantics).

## Acceptance
- Grouping, expanding, renaming, recolouring and ungrouping all survive process death.
- Sessions keep their scroll/history after any group move.
