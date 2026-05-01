# Pokémon Pack Opening Simulator

![Pack Opening Demo](demo.gif)

---

## What Is This?

A real-time 3D Pokémon pack opening simulator built entirely in Java using **LWJGL** (*"Lu-Jig-Wuhl"*) — Lightweight Java Game Library.

You pick a pack, watch it shake open, and flip through the cards one by one — complete with holographic foil shaders, spring-physics tilt that follows your cursor, per-rarity visual effects, and pull rates pulled directly from the real Pokémon TCG Pocket game.

---

## Why Did I Build This?

1. I wanted the adrenaline rush of opening Pokémon packs without paying the exorbitant costs. Unfortunately you cannot keep the cards but what can we really do about it in this economy.
2. My graphics class made me realize how easy it is to make something cool and unique so I wanted to do this project.

---

## Features

- Six booster sets, each with their own card pool loaded from disk
- Realistic pull rates per slot based on real TCG Pocket data
- Seven rarity tiers with distinct holographic shaders — from flat matte commons up to animated gold prismatic hyper rares
- Spring-physics cursor tracking so cards and packs tilt and lift as you move your mouse
- Full pack opening sequence: pack selection → center slide → shake → deal → deck flip → card browsing
- Painter's algorithm rendering so the card stack always draws correctly at any angle
- Procedurally generated sparkle and gold textures used by the higher rarity shaders

---

## Tech Stack

- **Java 17+**
- **LWJGL 3.3.4** (OpenGL 3.3 core profile, GLFW, STBImage)
- No game engine — raw OpenGL with hand-written shaders

---

## How to Run

Setup LWJGL library (I will not be showing that because I'm too lazy to go over that setup here and realistically no one will clone or fork this repo to try it out so why bother)

```
cd src
javac -classpath ".;C:\Program Files\lwjgl-release-3.3.4-custom\*" PokemonCard.java
java  -classpath ".;C:\Program Files\lwjgl-release-3.3.4-custom\*" PokemonCard
```