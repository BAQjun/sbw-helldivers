# Artwork requirements

- Keep related models and their geometry, animations, textures and audio references in sync. Recreate previews from the final accepted model.
- Rocket reload: fixed tray and spare ammunition, launcher descends 4.80 units, reload is silent. Narrow guides have real slots. Other turrets have no reload animation.
- Pod open/deploy includes the actual cover ejection and sound; do not replace it with hiding the cover.
- Use one shared sound manifest and registry. Original event actions override community labels. Unconfirmed original game trigger conditions remain explicit.
- New sentry textures use clean Minecraft pixel graphics and orderly shading. Do not add wear, chipped paint, scratches or noisy surface baking from the original asset. Recreate the original markings with clean shapes.
- Legacy scripts under work may target archived paths or reinsert rejected recorded audio. Do not run them blindly; use the current delivery as the source for future edits.

- Grenadier Battlement stratagem icon: user-approved white brick wall, green ground, uniform green border and dark background. Use content/ui/shared/stratagem/icon_strat_hud_defensive_emplacements_coverwall; never use the rejected unused turrets_manned_grenade glyph. Reference: codex-clipboard-e38d972b-ae9e-4da4-b259-c77bb73437fe.png.
- Reused game-test scenes must remove previous test turrets, collision proxies and projectiles before spawning, verify a single turret, and clean again before saving/exiting. Never stack test turrets at the same position across runs.

- Target marking uses X: creatures use a symmetric diamond, only block/ground marks use the eye, and deployed stratagem marks use their own pure-white foreground glyph without item-card backgrounds/borders. The enlarged bottom arrow tip points at the target centre; turret marks are informational and never sentry attack candidates. Mark/clear feedback uses confirmed ui_mission events.

- Eagle red stratagem cards: use the native R channel for the red-orange aircraft and G channel for the cream payload/effect; never merge them into one white silhouette. User reference codex-clipboard-35cb2745-2ae5-4eff-9246-262792014994.png palette: aircraft/border RGB 220,100,85; payload RGB 255,255,238; background RGB 25,3,1.

