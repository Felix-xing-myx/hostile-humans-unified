# Hostile Humans Unified 3.1.25-unified

[简体中文](README.md) | [English](README.en.md)

This is a community-maintained derivative of the original **Hostile Humans** mod, with firearm features from **Human Gunner** integrated into a unified version. It is a standalone mod and can be installed on its own; the original Hostile Humans and Human Gunner JARs are not required. This is a third-party maintained version, not an official update from the original author.

Designed for **Minecraft 1.20.1, Forge 47.4.16, and Java 17**. Install one unified mod JAR. TaCZ and Curios are optional integrations: TaCZ enables firearm support, while Curios adds a dedicated accessory slot for identity badges.

## Overview

The mod adds human units in four ranks: Roamers, Tier I, Tier II, and Tier III. Their relationship with players depends on rank, identity badges, and recruitment status. Wild humans may be hostile, neutral, or protective, while eligible humans can be hired as companions.

Identity badges work from any inventory slot. With Curios installed, they can also be placed in the dedicated badge slot. Higher-level badges affect more ranks, and the Ultimate Identity Badge makes all human ranks protect its holder.

Hired humans can be ordered to follow, guard an area, hold their position, or patrol. Their combat behavior can be set to Aggressive, Passive Protection, or Fully Neutral (self-defense only). You can open a hired human's inventory to manage equipment, choose whether they actively pick up items, or dismiss them.

Humans can fight with melee weapons, shields, bows, crossbows, tridents, and—when TaCZ is installed and enabled—compatible firearms. Ranged units adjust their combat behavior to their weapon. Hired companions may retreat to recover at low health and warn their owner when critically injured.

## Signal Items

- **Radio:** View your hired roster and command your companions to return or be located.
- **Reinforcement flares:** Call temporary friendly reinforcements. They leave when the support duration expires unless you formally hire them before then.
- **Hostile beacons:** Summon enemy human units at a distance to challenge the player.

## Configuration

The main configuration file is `config/hostile_humans_unified.json`. It covers rank attributes and ranged spread, damage multipliers, natural spawning, combat AI, hiring limits, PvP damage for hired humans, and TaCZ firearm settings. Restart the game after changing it. In multiplayer, the server configuration controls gameplay.

The detailed field-by-field configuration guide is currently available in [Chinese](CONFIGURATION.md).

## Compatibility

This unified version runs as a standalone Forge mod and does not require the original Hostile Humans or Human Gunner mod JARs. TaCZ and Curios are optional. The mod targets Minecraft 1.20.1 and Forge 47.4.16.
