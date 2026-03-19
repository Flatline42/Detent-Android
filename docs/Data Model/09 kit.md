# 09 kit
## Entity — Kit

## Fields

| Field | Type | Nullable | Notes |
| --- | --- | --- | --- |
| id | integer | no | auto-generated primary key |
| name | string | no | user display name, e.g. "Street Kit", "Yosemite Bag" |
| cameraBodyId | integer | no | foreign key → CameraBody |
| lastUsedAt | timestamp | yes | updated when kit is loaded into a roll at roll setup. Null until first use. |
| notes | string | yes | free text |

## Notes

*   A Kit is a named template of gear — one body, one or more lenses, zero or more filters — used to pre-populate roll setup quickly.
*   Kit is a template only. It has no direct relationship to any Roll after roll setup is complete. Changing a Kit after a roll is loaded has no effect on that roll.
*   Lenses and filters in a kit are defined via KitLens and KitFilter join tables.
*   At roll setup, selecting a Kit pre-fills: camera body, all lenses (with primary lens), all filters. All fields remain editable before the roll is created.
*   `lastUsedAt` is updated when a roll is created using this kit. Enables "sort by last used" in the Gear Library Kits tab and helps identify active vs template kits.
*   **Copy kit:** Available via overflow menu (⋯) on Kit Detail screen. Creates a duplicate with "Copy of \[name\]" as default name, opens immediately for editing. Enables kit-bashing — build a base filter kit then copy and customize for specific shoots.
*   **Delete kit:** Available via overflow menu (⋯) on Kit Detail screen only. Not available from Kit Selector.
*   **Kit Selector** is a read-only selection surface — manage kits from Gear Library only. Exception: FAB in Kit Selector allows creating a new kit mid-roll-setup flow.

## Camera Body Change Cascade

When changing the camera body on a kit in Kit Detail / Edit:

1.  Lenses that don't match the new body's mount type are automatically removed.
2.  User is warned: "Switching will remove incompatible lenses. Review your filter selection afterward."
3.  Filters are NOT automatically removed — filter incompatibility is advisory, not a hard constraint. Null/slot filters work with any lens.

## Relationships

*   One Kit → one CameraBody
*   One Kit → many Lenses via KitLens (one marked isPrimary)
*   One Kit → many Filters via KitFilter