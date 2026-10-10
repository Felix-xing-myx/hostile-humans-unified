# Hostile Humans Unified

Optional First Aid integration: humans can use plaster (default 20 total HP), bandage (default 40 total HP), and morphine (default Resistance I for 90 seconds). Effects and pickup quality are configurable in global `first_aid`. Medical supplies follow First Aid's public application and healing timing API and share a full-course cooldown. Morphine is not reused during its cooldown or an existing Resistance effect. Supply these through pickup or soldier inventory; default spawn kits are unchanged. Player body-part treatment remains First Aid's own behavior.

[简体中文](README.md) | [English](README.en.md)

Derived from [Hostile Humans](https://www.curseforge.com/minecraft/mc-mods/hostile-humans), this mod brings together content from **Human Gunner** and adds original gameplay. It is a standalone Forge mod; the original Hostile Humans and Human Gunner JARs are not required. The mod enhances hostile humans' attributes, combat, and survival mechanics, and is suited to modded worlds and modpacks. GPT assisted with modifications and builds.

## Gameplay

The mod adds Roamers and Tier I, II, and III humans, each with distinct attributes, equipment, and combat capabilities. Identity badges and faction relationships determine how humans treat players: as enemies, neutrals, or people to protect.

Players can hire humans with contracts and manage their roster through a radio. Hired soldiers can follow, guard an area, hold a position, or patrol. Their combat modes include Active Attack, Passive Protection, and Fully Neutral. Players can open a soldier's inventory to manage weapons, armor, and other equipment, change item pickup behavior, or dismiss the soldier.

Humans use melee weapons, shields, bows, crossbows, tridents, or firearms according to their equipment. At low health they retreat and try to recover. Ranged units look for firing positions while melee units pursue their targets. Signal flares summon temporary allies or hostile humans; the radio shows soldier status and can recall them.

Bows and crossbows consume Human Arrows (stack size 64): one per successful bow shot or crossbow volley, including Multishot. TaCZ reloads consume Universal Magazines (stack size 24): pistol/SMG 1, rifle/shotgun 2, sniper 3, machine gun 4, heavy weapon 5. Insufficient reserves prevent reloads, while remaining loaded rounds can still fire. Exhausted gunners prefer a supplied bow/crossbow, then an existing melee weapon. Put supplies in the soldier's backpack to restore ranged availability. Tridents remain unlimited.

Idle reloads require magazine ammunition strictly below 25% of capacity; exactly 25% does not trigger. A 30-round magazine reloads at 7 rounds, but not 8. During combat, reloads start only after all loaded rounds, including any chambered round, are spent. Thresholds and magazine payment share the same accepted reload entrypoint.

Shapeless crafting-table recipes: 4 vanilla arrows + 1 stick + 1 feather yield 32 Human Arrows; 3 gunpowder + 2 iron ingots yield 2 Universal Magazines. The magazine recipe loads only with TaCZ installed.

New ranged loadouts receive reserves by tier: Roamer 32–128 arrows / 6–12 magazines; Tier I 64–128 / 12–24; Tier II 128–192 / 32–48; Tier III 192 / 48–72. Units owning both weapon types receive both supplies. The initial loaded firearm does not incur another magazine charge. Existing saved units need resupply; loading saves or hiring does not replenish ammunition. These tokens are for humans only; ordinary player ammunition does not substitute for them.

## Mod Compatibility

- **TaCZ**: Optional firearm integration. Gunners can hold and aim firearms and display basic reload/fire motions without Player Animator. With Player Animator installed and matching gun-pack assets available, they use the gun pack's third-person keyframe animations.
- **Better Combat**: Optional melee integration. Soldiers can use its attack cooldowns, ranges, and area attacks. Without it, they fall back to this mod's combat logic. The integration can be adjusted in the configuration.
- **Curios**: Optional accessory integration. Identity badges can be placed in a dedicated badge slot.
- **Spartan Weaponry, Spartan Shields, and Immersive Armors**: Compatible weapons, shields, and armor are detected automatically, with no extra configuration.

These are optional integrations, not required dependencies.

## Screenshots

**Soldier commands**

![Soldier command panel with movement orders, combat modes, and active pickup](docs/images/en/soldier-commands.png)

**Soldier inventory and equipment**

![Soldier inventory and equipment screen](docs/images/en/soldier-inventory.png)

## Installation and Configuration

Requires Minecraft 1.20.1 and Forge. Place the unified mod JAR in the instance's `mods` folder. Do not install separate Hostile Humans or Human Gunner JARs alongside it.

Configuration lives in `config/hostile_humans_unified/`, using five files: global.json and roamer/tier1/tier2/tier3.json. Attributes, AI, equipment and starting ammunition are independent by rank; global switches and shared rules live in global.json, with Chinese and English descriptions. No legacy migration files are generated. Options include tier unlock dates, day-based equipment progression, equipment pools for all four ranks, attributes, weapon spread, combat behavior, recruitment costs, and firearm lists. Equipment customization uses configuration files instead of the legacy equipment datapacks. See the [configuration guide](CONFIGURATION.md) for details. In multiplayer, the server configuration controls gameplay.

## License and Credits

The unified release is licensed under the GNU GPL v2.0; see the full text in the repository root [`LICENSE`](LICENSE). Source incorporated from Human Gunner retains its original MPL-2.0 notices and, where permitted by MPL-2.0, is additionally distributed under GPL terms as part of this larger work. See [`THIRD_PARTY_NOTICES.md`](src/main/resources/THIRD_PARTY_NOTICES.md) for component provenance and license details.


Shared growth settings live in global `growth`; each rank owns its time curve and equipment details under `growth_profile`: equipment and birth levels share the personal online-day curve, while hired soldiers earn cultivation from actual damage after a confirmed target death, without needing the final hit. Levels 0–5 default to 50–100% attributes; experience starts at 90–110% and can reach 125%. Levels change attributes only on upgrade; experience changes them per percentage point. Firearms use a separate experience policy: each five-point band reduces NPC-added spread by 5% of baseline, down to 75%; damage scales linearly from no bonus at 100% experience to +10% at the default 125% cap. Stage levels do not scale firearm damage. Turning growth off applies 100% stage and experience factors, stops cultivation and hides growth UI while retaining saved progress and existing equipment. The server controls calculations, rewards and UI visibility; local client settings do not override it. See the [configuration guide](CONFIGURATION.md).

NPC First Aid healing, morphine effects and pickup values are configurable in global `first_aid`. Pursuit jump attacks use global `jump_attack`; ordinary movement hops remain rank AI settings. See [configuration](CONFIGURATION.md).
