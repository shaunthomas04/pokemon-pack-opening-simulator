// javac -classpath ".;C:\Program Files\lwjgl-release-3.3.4-custom\*" PokemonCard.java
// java  -classpath ".;C:\Program Files\lwjgl-release-3.3.4-custom\*" PokemonCard

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import java.nio.*;
import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class PokemonCard {

    private static final float CARD_W      = 0.72f;
    private static final float CARD_H      = 1.005f;
    private static final float CARD_Z_REST = -2.8f;
    private static final float CARD_Z_HOVER= -2.4f;
    private static final float PACK_W      = 0.80f;
    private static final float PACK_H      = 1.32f;
    private static final float PACK_Z      = -4.2f;
    private static final float SK          = 14f;
    private static final float SD          = 6f;
    private static final float MAX_TILT    = 22f;
    private static final int   ST_PACK     = 0;
    private static final int   ST_OPENING  = 1;
    private static final int   ST_DEAL     = 2;
    private static final int   ST_FLIP     = 3;
    private static final int   ST_CARDS    = 4;
    private static final int   ST_WAIT     = 5;
    private static final int   ST_CENTER   = 6;
    // CENTER_DUR is split: first 40% = fade others out, last 60% = slide to center
    private static final float CENTER_DUR  = 0.75f;
    private static final float CENTER_FADE = 0.40f; // fraction of CENTER_DUR for fade phase
    private static final int   CARD_COUNT  = 10;
    private static final int   PACK_COUNT  = 6;
    private static final float DEAL_DUR    = 0.22f;
    private static final float OPEN_DUR    = 0.75f;
    private static final float FLIP_DUR    = 0.75f;

    private static final float[] PACK_X = { -1.3f, 0.0f, 1.3f, -1.3f, 0.0f, 1.3f };
    private static final float[] PACK_Y = {  0.75f, 0.75f, 0.75f, -0.75f, -0.75f, -0.75f };

    // Rarity tiers — match folder names and shader branches
    private static final int RARITY_ENERGY          = 0; // shader: common (flat)
    private static final int RARITY_ENERGY_HOLO     = 1; // shader: uncommon (silver shimmer)
    private static final int RARITY_COMMON           = 2; // shader: common
    private static final int RARITY_UNCOMMON         = 3; // shader: uncommon
    private static final int RARITY_RARE             = 4; // shader: rare (artwork holo)
    private static final int RARITY_DOUBLE_RARE      = 5; // shader: ultra rare
    private static final int RARITY_ART_RARE         = 6; // shader: illus rare (sparkle)
    private static final int RARITY_SPECIAL_ART_RARE = 7; // shader: cosmos
    private static final int RARITY_HYPER            = 8; // shader: hyper (gold)
    private static final int RARITY_PACK             = 9; // shader: pack gloss
    private static final int RARITY_COUNT            = 9; // excludes RARITY_PACK

    // Map each rarity tier to its visual shader branch (0-7 as before)
    private static final int[] RARITY_TO_SHADER = {
            0, // ENERGY          → common/matte
            1, // ENERGY_HOLO     → satin silver
            0, // COMMON          → common/matte
            1, // UNCOMMON        → satin silver
            2, // RARE            → artwork holo
            3, // DOUBLE_RARE     → full holo + shimmer
            4, // ART_RARE        → sparkle
            5, // SPECIAL_ART_RARE→ cosmos
            6, // HYPER           → gold prismatic
    };

    // Set names — index matches PACK_X/Y order
    private static final String[] SET_NAMES = {
            "mega_evolution",
            "ascended_heroes",
            "phantasmal_flames",
            "chaos_rising",
            "destined_rivals",
            "prismatic_evolutions"
    };

    private static final String[][] RARITY_FOLDER_PATHS = {
            // { primary path, fallback path or null }
            // ENERGY (0)
            { "energy" },
            // ENERGY_HOLO (1) — same folder, holo is determined by roll not separate folder
            { "energy" },
            // COMMON (2)
            { "pokemon/common", "trainer/common" },
            // UNCOMMON (3)
            { "pokemon/uncommon", "trainer/uncommon" },
            // RARE (4)
            { "pokemon/rare" },
            // DOUBLE_RARE (5)
            { "pokemon/double_rare", "pokemon/ultra_rare", "trainer/ultra_rare" },
            // ART_RARE / ILLUSTRATION_RARE (6)
            { "pokemon/illustration_rare" },
            // SPECIAL_ART_RARE (7)
            { "pokemon/special_illustration_rare", "trainer/special_illustration_rare" },
            // HYPER (8)
            { "pokemon/hyper_rare" },
    };

    private static final int[][] PULL_WEIGHTS = {
            //   en   eH    cm    uc    ra    dr    ar   sar    hy

            {10000,    0,    0,    0,    0,    0,    0,    0,    0 }, // slot 0 - energy (fixed)

            {    0,    0,10000,    0,    0,    0,    0,    0,    0 }, // slot 1 - common (fixed)

            {    0,    0,10000,    0,    0,    0,    0,    0,    0 }, // slot 2 - common (fixed)

            {    0,    0, 9500,  500,    0,    0,    0,    0,    0 }, // slot 3 - common/uncommon (almost always common)

            // ---- early "fake excitement" slot (still mostly bulk) ----
            {    0,    0,    0, 9700,  300,    0,    0,    0,    0 }, // slot 4

            // ---- REAL hit region starts here (VERY low odds) ----
            {    0,    0,    0, 8500, 1400,  100,    0,    0,    0 }, // slot 5 - rare appearance (~10% rare max)

            {    0,    0,    0, 9000,  900,   90,   10,    0,    0 }, // slot 6 - tiny AR chance (~1%)

            {    0,    0,    0, 9200,  700,   80,   18,    2,    0 }, // slot 7 - AR/SAR extremely rare

            {    0,    0,    0, 9500,  450,   40,    8,    2,    0 }, // slot 8 - peak hit slot (~0.1–0.3% SAR)

            // ---- final slot (holo / reverse / occasional hit) ----
            {    0,    0,    0,    0, 9000,  900,   90,    9,    1 }, // slot 9 - holo dominated, ultra rare SAR
    };

    private int     WIN_W, WIN_H;
    private long    window;
    private int     holoProg, shadowProg;
    private int     vaoQuad;
    private int[]   texPacks      = new int[PACK_COUNT];
    private int     texBack, texSparkle, texGold;
    // [set][rarity] → array of texture IDs
    private int[][][] setCardTextures = new int[PACK_COUNT][RARITY_COUNT][];
    private int[]   deck          = new int[CARD_COUNT];
    private int[]   deckRarities  = new int[CARD_COUNT]; // stores RARITY_TO_SHADER index
    private float   uTime         = 0f;

    private double  mouseX, mouseY;
    private boolean clickConsumed = false;
    private boolean mousePressed  = false;

    private float[] packTiltX  = new float[PACK_COUNT];
    private float[] packTiltY  = new float[PACK_COUNT];
    private float[] packVtiltX = new float[PACK_COUNT];
    private float[] packVtiltY = new float[PACK_COUNT];
    private float[] packZArr   = new float[PACK_COUNT];
    private float[] packVz     = new float[PACK_COUNT];
    private boolean[] packHov  = new boolean[PACK_COUNT];
    // alpha of each non-active pack, driven during ST_CENTER fade phase
    private float[] packFadeAlpha = new float[PACK_COUNT];

    private int     activePack  = -1;

    private float   tiltX, tiltY, vtiltX, vtiltY;
    private float   cardZ = CARD_Z_REST, vzCard;
    private boolean cardHovered;

    private int     state      = ST_PACK;
    private float   stateTimer = 0f;

    private float   packCenterT = 0f;
    private float   packStartX  = 0f;
    private float   packStartY  = 0f;

    private float   packShakeX = 0f;
    private float   packAlpha  = 1f;

    private int     dealtCount = 0;
    private float   dealTimer  = 0f;
    private float[] cardDealT  = new float[CARD_COUNT];

    private float   deckFlipT  = 0f;

    private int     topCard    = 0;
    private float   dismissT   = 0f;
    private boolean dismissing = false;
    private float   dismissDX  = 0f;

    private float   waitTimer  = 0f;

    public static void main(String[] a) { new PokemonCard().run(); }
    private void run() { init(); loop(); cleanup(); }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Cannot init GLFW");

        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode vm = glfwGetVideoMode(monitor);
        WIN_W = vm.width();
        WIN_H = vm.height();

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_SAMPLES, 4);

        window = glfwCreateWindow(WIN_W, WIN_H, "Pokemon Pack Opening", monitor, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create window");

        glfwSetCursorPosCallback(window, (w, x, y) -> { mouseX = x; mouseY = y; });
        glfwSetMouseButtonCallback(window, (w, btn, action, mods) -> {
            if (btn == GLFW_MOUSE_BUTTON_LEFT) {
                mousePressed = (action == GLFW_PRESS);
                if (action == GLFW_PRESS) clickConsumed = false;
            }
        });
        glfwSetKeyCallback(window, (w, key, sc, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS)
                glfwSetWindowShouldClose(w, true);
        });

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_MULTISAMPLE);
        glViewport(0, 0, WIN_W, WIN_H);

        buildShaders();
        buildQuad();

        loadAllSetTextures();

        String[] packFiles = {
                "packs/mega_evolution.jpg",
                "packs/ascended_heroes.jpg",
                "packs/phantasmal_flames.jpg",
                "packs/chaos_rising.jpg",
                "packs/destined_rivals.jpg",
                "packs/prismatic_evolutions.jpg"
        };
        for (int i = 0; i < PACK_COUNT; i++) {
            try { texPacks[i] = loadTexture(packFiles[i]); }
            catch (Exception e) {
                texPacks[i] = loadTexture("packs/prismatic_evolutions.jpg");
            }
        }
        texBack    = loadTexture("back_card.jpg");
        texSparkle = loadTextureOrFallback("textures/sparkle.png", generateSparkleTexture());
        texGold    = loadTextureOrFallback("textures/gold.png",    generateGoldTexture());

        for (int i = 0; i < PACK_COUNT; i++) {
            packZArr[i]       = PACK_Z;
            packFadeAlpha[i]  = 1f;
        }
    }

    private void loop() {
        long prev = System.nanoTime();
        while (!glfwWindowShouldClose(window)) {
            long now = System.nanoTime();
            float dt = Math.min((now - prev) / 1e9f, 0.05f);
            prev = now;
            uTime += dt;
            update(dt);
            render();
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private float screenNX() { return (float)(mouseX / WIN_W) * 2f - 1f; }
    private float screenNY() { return -((float)(mouseY / WIN_H) * 2f - 1f); }

    private float worldToNDCX(float wx) {
        float aspect = (float) WIN_W / WIN_H;
        float f = 1f / (float)Math.tan(Math.toRadians(45f) / 2.0);
        return wx / (-PACK_Z) * (f / aspect);
    }

    private float worldToNDCY(float wy) {
        float f = 1f / (float)Math.tan(Math.toRadians(45f) / 2.0);
        return wy / (-PACK_Z) * f;
    }

    private float packHalfW() {
        float f = 1f / (float)Math.tan(Math.toRadians(45f) / 2.0);
        return PACK_W / 2f / (-PACK_Z) * f / ((float) WIN_W / WIN_H);
    }

    private float packHalfH() {
        float f = 1f / (float)Math.tan(Math.toRadians(45f) / 2.0);
        return PACK_H / 2f / (-PACK_Z) * f;
    }

    private boolean isPackHovered(int i) {
        float nx = screenNX(), ny = screenNY();
        float cx = worldToNDCX(PACK_X[i]);
        float cy = worldToNDCY(PACK_Y[i]);
        return Math.abs(nx - cx) < packHalfW() && Math.abs(ny - cy) < packHalfH();
    }

    private void resetPackState() {
        for (int i = 0; i < PACK_COUNT; i++) {
            packTiltX[i] = packTiltY[i] = packVtiltX[i] = packVtiltY[i] = 0f;
            packZArr[i]  = PACK_Z;
            packVz[i]    = 0f;
            packHov[i]   = false;
            packFadeAlpha[i] = 1f;
        }
    }

    private void update(float dt) {
        stateTimer += dt;
        float nx = screenNX(), ny = screenNY();
        boolean clicked = mousePressed && !clickConsumed;

        switch (state) {
            case ST_PACK: {
                for (int i = 0; i < PACK_COUNT; i++) {
                    packHov[i] = isPackHovered(i);
                    float targetTX = packHov[i] ?  ny * MAX_TILT : 0f;
                    float targetTY = packHov[i] ? -nx * MAX_TILT : 0f;
                    float axX = SK*(targetTX-packTiltX[i]) - SD*packVtiltX[i];
                    float axY = SK*(targetTY-packTiltY[i]) - SD*packVtiltY[i];
                    packVtiltX[i] += axX*dt; packTiltX[i] += packVtiltX[i]*dt;
                    packVtiltY[i] += axY*dt; packTiltY[i] += packVtiltY[i]*dt;
                    float tz = packHov[i] ? PACK_Z + 0.35f : PACK_Z;
                    float az = SK*(tz-packZArr[i]) - SD*packVz[i];
                    packVz[i] += az*dt; packZArr[i] += packVz[i]*dt;

                    if (clicked && packHov[i]) {
                        clickConsumed = true;
                        activePack = i;
                        generateRandomDeck();
                        state = ST_CENTER;
                        stateTimer = 0f;
                        packCenterT = 0f;
                        packStartX  = PACK_X[i];
                        packStartY  = PACK_Y[i];
                        packShakeX  = 0f;
                        packAlpha   = 1f;
                        // initialise fade alphas for other packs
                        for (int j = 0; j < PACK_COUNT; j++) packFadeAlpha[j] = 1f;
                    }
                }
                break;
            }
            case ST_CENTER: {
                packCenterT = Math.min(stateTimer / CENTER_DUR, 1f);
                // fade phase: 0 → CENTER_FADE
                float fadeProgress = Math.min(packCenterT / CENTER_FADE, 1f);
                for (int j = 0; j < PACK_COUNT; j++) {
                    if (j != activePack) packFadeAlpha[j] = 1f - ease(fadeProgress);
                }
                if (packCenterT >= 1f) {
                    // ensure others are fully gone
                    for (int j = 0; j < PACK_COUNT; j++) packFadeAlpha[j] = 0f;
                    state = ST_OPENING;
                    stateTimer = 0f;
                    packShakeX = 0f;
                    packAlpha  = 1f;
                }
                break;
            }
            case ST_OPENING: {
                float t = Math.min(stateTimer / OPEN_DUR, 1f);
                packShakeX = (float)(Math.sin(t * 60f) * 0.04f * (1f - t));
                if (t > 0.55f) packAlpha = 1f - (t - 0.55f) / 0.45f;
                if (t >= 1f) {
                    state = ST_DEAL;
                    stateTimer = 0f;
                    dealtCount = 0;
                    dealTimer  = 0f;
                    for (int i = 0; i < CARD_COUNT; i++) cardDealT[i] = 0f;
                }
                break;
            }
            case ST_DEAL: {
                dealTimer += dt;
                int shouldHaveDealt = Math.min((int)(dealTimer / DEAL_DUR), CARD_COUNT);
                if (shouldHaveDealt > dealtCount) dealtCount = shouldHaveDealt;
                for (int i = 0; i < dealtCount; i++)
                    cardDealT[i] = Math.min(cardDealT[i] + dt * 3.5f, 1f);
                if (dealtCount == CARD_COUNT && cardDealT[CARD_COUNT-1] >= 1f) {
                    state = ST_FLIP;
                    stateTimer = 0f;
                    deckFlipT = 0f;
                    tiltX = tiltY = vtiltX = vtiltY = 0f;
                    cardZ = CARD_Z_REST; vzCard = 0f;
                }
                break;
            }
            case ST_FLIP: {
                deckFlipT = Math.min(stateTimer / FLIP_DUR, 1f);
                if (deckFlipT >= 1f) {
                    state = ST_CARDS;
                    stateTimer = 0f;
                    topCard   = 0;
                    dismissing = false;
                    dismissT   = 0f;
                }
                break;
            }
            case ST_CARDS: {
                cardHovered = (Math.abs(nx) < 0.38f && Math.abs(ny) < 0.55f);
                float targetTX =  ny * MAX_TILT;
                float targetTY = -nx * MAX_TILT;
                float axX = SK*(targetTX-tiltX) - SD*vtiltX;
                float axY = SK*(targetTY-tiltY) - SD*vtiltY;
                vtiltX += axX*dt; tiltX += vtiltX*dt;
                vtiltY += axY*dt; tiltY += vtiltY*dt;
                float tz2 = cardHovered ? CARD_Z_HOVER : CARD_Z_REST;
                float az2 = SK*(tz2-cardZ) - SD*vzCard;
                vzCard += az2*dt; cardZ += vzCard*dt;
                if (dismissing) {
                    dismissT = Math.min(dismissT + dt * 2.2f, 1f);
                    if (dismissT >= 1f) {
                        dismissing = false;
                        topCard++;
                        dismissT = 0f;
                        tiltX = tiltY = vtiltX = vtiltY = 0f;
                        cardZ = CARD_Z_REST; vzCard = 0f;
                        if (topCard >= CARD_COUNT) {
                            state = ST_WAIT;
                            stateTimer = 0f;
                            waitTimer  = 0f;
                        }
                    }
                } else if (clicked && cardHovered) {
                    clickConsumed = true;
                    dismissing = true;
                    dismissT   = 0f;
                    dismissDX  = (nx > 0f) ? 1f : -1f;
                }
                break;
            }
            case ST_WAIT: {
                // Render nothing — just wait a moment then snap back
                waitTimer += dt;
                if (waitTimer >= 1.2f) {
                    state = ST_PACK;
                    stateTimer = 0f;
                    waitTimer  = 0f;
                    activePack = -1;
                    resetPackState();
                    tiltX = tiltY = vtiltX = vtiltY = 0f;
                    cardZ = CARD_Z_REST; vzCard = 0f;
                }
                break;
            }
        }
    }

    private void loadAllSetTextures() {
        for (int s = 0; s < PACK_COUNT; s++) {
            for (int r = 0; r < RARITY_COUNT; r++) {
                String[] paths = RARITY_FOLDER_PATHS[r];
                java.util.List<Integer> texList = new java.util.ArrayList<>();
                for (String subPath : paths) {
                    String fullPath = "cards/" + SET_NAMES[s] + "/" + subPath;
                    java.io.File folder = new java.io.File(fullPath);
                    java.io.File[] files = folder.listFiles((dir, name) ->
                            name.endsWith(".jpg") || name.endsWith(".png"));
                    if (files != null) {
                        for (java.io.File f : files) {
                            try { texList.add(loadTexture(f.getAbsolutePath())); }
                            catch (Exception e) { /* skip unloadable files */ }
                        }
                    }
                }
                setCardTextures[s][r] = texList.stream().mapToInt(Integer::intValue).toArray();
            }
        }
    }

    private int pickCard(java.util.Random rand, int set, int rarity) {
        for (int r = rarity; r >= 0; r--) {
            int[] pool = setCardTextures[set][r];
            if (pool.length > 0) return pool[rand.nextInt(pool.length)];
        }
        for (int r = rarity + 1; r < RARITY_COUNT; r++) {
            int[] pool = setCardTextures[set][r];
            if (pool.length > 0) return pool[rand.nextInt(pool.length)];
        }
        return texBack;
    }

    private int rollRarity(java.util.Random rand, int slot) {
        int[] weights = PULL_WEIGHTS[slot];
        int total = 0; for (int w : weights) total += w;
        int roll = rand.nextInt(total), cumulative = 0;
        for (int r = 0; r < weights.length; r++) {
            cumulative += weights[r];
            if (roll < cumulative) return r;
        }
        return weights.length - 1;
    }

    private void generateRandomDeck() {
        java.util.Random rand = new java.util.Random();
        for (int i = 0; i < CARD_COUNT; i++) {
            int rarity      = rollRarity(rand, i);
            deck[i]         = pickCard(rand, activePack, rarity);
            deckRarities[i] = RARITY_TO_SHADER[rarity];
        }
    }

    private void render() {
        glClearColor(0.06f, 0.06f, 0.10f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        float aspect = (float) WIN_W / WIN_H;
        float[] proj = makePerspective(45f, aspect, 0.1f, 100f);
        float[] view = identity();
        switch (state) {
            case ST_PACK    -> renderAllPacks(proj, view);
            case ST_CENTER  -> renderCenter(proj, view);
            case ST_OPENING -> renderOpening(proj, view);
            case ST_DEAL    -> renderDeal(proj, view);
            case ST_FLIP    -> renderFlip(proj, view);
            case ST_CARDS   -> renderCards(proj, view);
            case ST_WAIT    -> {} // render nothing — blank screen briefly before packs return
        }
    }

    private void renderAllPacks(float[] proj, float[] view) {
        for (int i = 0; i < PACK_COUNT; i++) renderOnePack(proj, view, i, 1f);
    }

    private void renderOnePack(float[] proj, float[] view, int i, float alpha) {
        if (alpha <= 0f) return;
        float tX    = packTiltX[i];
        float tY    = packTiltY[i];
        float pz    = packZArr[i];
        float scale = packHov[i] ? 1.04f : 1f;
        float[] model = makeCardModel(PACK_X[i], PACK_Y[i], pz, tX, tY);
        model = mul4(model, makeScale(scale, scale, 1f));
        drawShadow(proj, view, PACK_X[i], PACK_Y[i] - 0.08f, PACK_Z - 0.3f,
                PACK_W * scale * 1.2f, PACK_H * scale * 1.05f, 0.38f * alpha);
        drawHolo(proj, view, model, PACK_W, PACK_H, texPacks[i],
                tX / MAX_TILT, tY / MAX_TILT, packHov[i] ? 1f : 0f, alpha, 0.04f, RARITY_PACK);
    }

    private void renderCenter(float[] proj, float[] view) {
        // Draw non-active packs using their individually tracked fade alpha
        for (int i = 0; i < PACK_COUNT; i++) {
            if (i != activePack && packFadeAlpha[i] > 0f)
                renderOnePack(proj, view, i, packFadeAlpha[i]);
        }

        // Slide phase only starts once fade is complete (CENTER_FADE fraction of duration)
        float slideStart = CENTER_FADE;
        float slideProgress = Math.max(0f, (packCenterT - slideStart) / (1f - slideStart));
        float cx = lerp(packStartX, 0f, ease(slideProgress));
        float cy = lerp(packStartY, 0f, ease(slideProgress));

        float[] model = makeCardModel(cx, cy, PACK_Z, 0f, 0f);
        drawShadow(proj, view, cx, cy - 0.08f, PACK_Z - 0.3f,
                PACK_W * 1.2f, PACK_H * 1.05f, 0.38f);
        drawHolo(proj, view, model, PACK_W, PACK_H, texPacks[activePack],
                0f, 0f, 0f, 1f, 0.04f, RARITY_PACK);
    }

    private void renderOpening(float[] proj, float[] view) {
        if (packAlpha > 0f) {
            float[] model = makeCardModel(packShakeX, 0f, PACK_Z, 0f, 0f);
            drawHolo(proj, view, model, PACK_W, PACK_H, texPacks[activePack],
                    0f, 0f, 0f, packAlpha, 0.04f, RARITY_PACK);
        }
    }

    private void renderDeal(float[] proj, float[] view) {
        for (int i = dealtCount - 1; i >= 0; i--) {
            float et     = ease(cardDealT[i]);
            float stackX = (i % 3 - 1) * 0.012f;
            float stackY = (i % 2 == 0 ? 1f : -1f) * 0.008f * i;
            float stackZ = CARD_Z_REST - i * 0.005f;
            float cx = lerp(0f, stackX, et);
            float cy = lerp(0f, stackY, et) + (float)Math.sin(cardDealT[i] * Math.PI) * 0.4f;
            float cz = lerp(PACK_Z + 0.5f, stackZ, et);
            float rx = lerp(30f, 0f, et);
            float ry = lerp((i % 2 == 0 ? 15f : -15f), 0f, et);
            float[] model = makeCardModel(cx, cy, cz, rx, ry);
            drawHolo(proj, view, model, CARD_W, CARD_H, texBack,
                    0f, 0f, 0f, Math.min(cardDealT[i] * 4f, 1f), 0.045f, RARITY_COMMON);
        }
    }

    private void renderFlip(float[] proj, float[] view) {
        float flipAngle = lerp(180f, 0f, ease(deckFlipT));
        boolean topShowsFront = flipAngle < 90f;

        // Backing cards always show back until the entire flip is done
        // This prevents glimpsing a different card's front mid-animation
        glDisable(GL_DEPTH_TEST);
        for (int i = Math.min(CARD_COUNT - 1, 4); i >= 1; i--) {
            float[] model = makeCardModel(i * 0.012f, -i * 0.008f, CARD_Z_REST - i * 0.02f, 0f, flipAngle);
            drawHolo(proj, view, model, CARD_W, CARD_H, texBack,
                    0f, 0f, 0f, 1f, 0.045f, RARITY_COMMON);
        }

        // Top card flips normally — back → front at 90°
        int tex = topShowsFront ? deck[0] : texBack;
        int rar = topShowsFront ? deckRarities[0] : RARITY_COMMON;
        float[] model = makeCardModel(0f, 0f, CARD_Z_REST, 0f, flipAngle);
        drawHolo(proj, view, model, CARD_W, CARD_H, tex,
                0f, 0f, 0f, 1f, 0.045f, rar);
        glEnable(GL_DEPTH_TEST);
    }

    private void renderCards(float[] proj, float[] view) {
        int remaining = CARD_COUNT - topCard;
        if (remaining <= 0) return;

        drawShadow(proj, view, 0.05f, -0.12f, CARD_Z_REST - 0.3f,
                CARD_W * 1.15f, CARD_H * 1.1f,
                0.45f + (CARD_Z_REST - cardZ) * 0.12f);

        // Disable depth test and draw back-to-front (painter's algorithm).
        // This guarantees correct ordering regardless of tilt angle,
        // preventing back cards bleeding through the top card.
        glDisable(GL_DEPTH_TEST);

        int stackSize = Math.min(remaining, 5);
        for (int i = stackSize - 1; i >= 1; i--) {
            // Larger Z separation (0.02 instead of 0.005) so cards don't z-fight
            float offZ  = cardZ - i * 0.02f;
            float offX  = tiltY * 0.001f * i;
            float offY  = -tiltX * 0.001f * i;
            float[] model = makeCardModel(offX, offY, offZ, tiltX, tiltY);
            drawHolo(proj, view, model, CARD_W, CARD_H, deck[topCard + i],
                    0f, 0f, 0f, 1f, 0.045f, deckRarities[topCard + i]);
        }

        if (dismissing) {
            float et = ease(dismissT);
            float[] model = makeCardModel(
                    dismissDX * et * 2.5f, et * 0.4f, cardZ + et * 0.3f,
                    tiltX * (1f - et), tiltY * (1f - et) + dismissDX * et * 35f);
            drawHolo(proj, view, model, CARD_W, CARD_H, deck[topCard],
                    0f, 0f, 0f, 1f - et, 0.045f, deckRarities[topCard]);
        } else {
            float[] model = makeCardModel(0f, 0f, cardZ, tiltX, tiltY);
            drawHolo(proj, view, model, CARD_W, CARD_H, deck[topCard],
                    tiltX / MAX_TILT, tiltY / MAX_TILT, cardHovered ? 1f : 0f, 1f, 0.045f, deckRarities[topCard]);
        }

        glEnable(GL_DEPTH_TEST);
    }

    private void drawHolo(float[] proj, float[] view, float[] model,
                          float w, float h, int tex,
                          float tiltNX, float tiltNY, float hover, float alpha, float cornerR,
                          int rarity) {
        glUseProgram(holoProg);
        setUniformMatrix(holoProg, "uProj",  proj);
        setUniformMatrix(holoProg, "uView",  view);
        setUniformMatrix(holoProg, "uModel", model);
        setUniform2f(holoProg, "uSize",      w, h);
        setUniform2f(holoProg, "uTilt",      tiltNX, tiltNY);
        setUniform1f(holoProg, "uHover",     hover);
        setUniform1f(holoProg, "uAlpha",     alpha);
        setUniform1f(holoProg, "uCornerR",   cornerR);
        setUniform1i(holoProg, "uRarity",    rarity == RARITY_PACK ? 7 : rarity);
        setUniform1f(holoProg, "uTime",      uTime);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tex);
        setUniform1i(holoProg, "uTex", 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, texSparkle);
        setUniform1i(holoProg, "uSparkle", 1);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, texGold);
        setUniform1i(holoProg, "uGold", 2);
        glBindVertexArray(vaoQuad);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }

    private void drawShadow(float[] proj, float[] view,
                            float x, float y, float z,
                            float sw, float sh, float alpha) {
        glDepthMask(false);
        float[] sm = mul4(makeTranslate(x, y, z), makeScale(sw, sh, 1f));
        glUseProgram(shadowProg);
        setUniformMatrix(shadowProg, "uProj",  proj);
        setUniformMatrix(shadowProg, "uView",  view);
        setUniformMatrix(shadowProg, "uModel", sm);
        setUniform1f(shadowProg, "uAlpha", alpha);
        glBindVertexArray(vaoQuad);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        glDepthMask(true);
    }

    private void buildQuad() {
        float[] v = {
                -0.5f,-0.5f,0, 0,0, 0,0,1,
                0.5f,-0.5f,0, 1,0, 0,0,1,
                0.5f, 0.5f,0, 1,1, 0,0,1,
                -0.5f, 0.5f,0, 0,1, 0,0,1,
        };
        int[] idx = {0,1,2, 2,3,0};
        vaoQuad = glGenVertexArrays();
        glBindVertexArray(vaoQuad);
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, v, GL_STATIC_DRAW);
        int ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STATIC_DRAW);
        int stride = 8 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0); glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3*Float.BYTES); glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5*Float.BYTES); glEnableVertexAttribArray(2);
        glBindVertexArray(0);
    }

    private void buildShaders() {
        String vert = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec2 aUV;
            layout(location=2) in vec3 aNorm;
            uniform mat4 uProj, uView, uModel;
            uniform vec2 uSize;
            out vec2 vUV; out vec3 vNormW, vPosW;
            void main(){
                vec3 p = vec3(aPos.x*uSize.x, aPos.y*uSize.y, aPos.z);
                vec4 wp = uModel * vec4(p, 1.0);
                vPosW = wp.xyz; vNormW = mat3(uModel)*aNorm; vUV = aUV;
                gl_Position = uProj * uView * wp;
            }
            """;

        String frag = """
            #version 330 core
            in vec2 vUV; in vec3 vNormW, vPosW;
            uniform sampler2D uTex, uSparkle, uGold;
            uniform vec2  uTilt;
            uniform float uHover, uAlpha, uCornerR, uTime;
            uniform int   uRarity;
            out vec4 fragColor;

            float roundedRect(vec2 uv, float r){
                vec2 q = abs(uv-0.5)-(0.5-r);
                return 1.0 - smoothstep(-0.005,0.005,length(max(q,0.0))-r);
            }
            vec3 rainbow(vec2 uv, vec2 tilt){
                vec2 s = uv+tilt*0.35;
                float h = fract(s.x*6.0+s.y*3.0);
                return clamp(abs(mod(h*6.0+vec3(0,4,2),6.0)-3.0)-1.0,0.0,1.0);
            }
            float spec(vec2 uv, vec2 tilt){
                return pow(max(1.0-length(uv-(vec2(0.5)+tilt*0.6))*2.2,0.0),3.5);
            }
            float fres(vec2 uv){
                vec2 e=min(uv,1.0-uv);
                return 1.0-smoothstep(0.0,0.18,min(e.x,e.y));
            }
            float sparkleVal(vec2 uv, float t){
                float v  = texture(uSparkle, uv*3.0+vec2(t*0.08,t*0.05)).r;
                      v += texture(uSparkle, uv*5.0-vec2(t*0.06,t*0.09)).r*0.5;
                return v/1.5;
            }

            void main(){
                vec4  base = texture(uTex, vec2(vUV.x,1.0-vUV.y));
                float mask = roundedRect(vUV, uCornerR);
                float tm   = length(uTilt);
                vec3  col  = base.rgb;
                float vig  = 1.0-smoothstep(0.35,0.75,length(vUV-0.5));

                if(uRarity == 0){
                    float ss = pow(max(1.0-length(vUV-(vec2(0.5)+uTilt*0.4))*3.0,0.0),6.0)*0.15;
                    col += vec3(ss);
                    col *= 0.92+vig*0.08;

                } else if(uRarity == 1){
                    float sp = spec(vUV,uTilt)*0.5;
                    float fr = fres(vUV)*(0.2+tm*0.3);
                    vec3 sv  = vec3(0.75,0.82,0.9);
                    col += sv*sp; col += sv*fr*0.4;
                    col *= 0.93+vig*0.07;

                } else if(uRarity == 2){
                    float am = smoothstep(0.28,0.35,vUV.y);
                    vec3  rb = rainbow(vUV,uTilt);
                    float sp = spec(vUV,uTilt)*(0.5+uHover*0.3);
                    float fr = fres(vUV)*(0.25+tm*0.4);
                    vec3  fc = mix(vec3(0.6,0.8,1.0),rb,0.5);
                    col  = mix(col,col*1.15,tm*0.35*am);
                    col += rb*(0.22+uHover*0.1)*tm*am;
                    col += vec3(sp*0.7); col += fc*fr*0.4;
                    col *= 0.88+vig*0.12;

                } else if(uRarity == 3){
                    vec3  rb = rainbow(vUV,uTilt);
                    float sp = spec(vUV,uTilt)*(0.6+uHover*0.4);
                    float fr = fres(vUV)*(0.3+tm*0.5);
                    vec3  fc = mix(vec3(0.6,0.8,1.0),rb,0.5);
                    float sh = sin(vUV.x*8.0+uTime*2.0)*0.5+0.5;
                          sh *= sin(vUV.y*6.0-uTime*1.5)*0.5+0.5;
                    col  = mix(col,col*1.18,tm*0.4);
                    col += rb*(0.28+uHover*0.12)*tm;
                    col += vec3(sp*0.9); col += fc*fr*0.5;
                    col += vec3(0.15,0.2,0.3)*sh*0.12;
                    col *= 0.85+vig*0.15;

                } else if(uRarity == 4){
                    vec3  rb = rainbow(vUV,uTilt);
                    float sp = spec(vUV,uTilt)*(0.65+uHover*0.4);
                    float fr = fres(vUV)*(0.35+tm*0.55);
                    vec3  fc = mix(vec3(0.7,0.85,1.0),rb,0.6);
                    float sk = pow(sparkleVal(vUV,uTime),2.5)*(0.6+tm*0.8);
                    col  = mix(col,col*1.2,tm*0.4);
                    col += rb*(0.3+uHover*0.14)*tm;
                    col += vec3(sp*0.9); col += fc*fr*0.55;
                    col += vec3(0.9,0.95,1.0)*sk;
                    col *= 0.85+vig*0.15;

                } else if(uRarity == 5){
                    vec3  rb = rainbow(vUV+vec2(uTime*0.015,0.0),uTilt*0.5);
                          rb = mix(vec3(0.1,0.0,0.3),rb,0.5);
                    float sp = spec(vUV,uTilt)*(0.5+uHover*0.5);
                    float fr = fres(vUV)*(0.4+tm*0.6);
                    float sk = pow(sparkleVal(vUV*0.4,uTime*0.5),3.0)*(0.8+tm);
                    col  = mix(col,col*1.2,0.3);
                    col += rb*0.35*max(tm,0.1);
                    col += vec3(sp); col += vec3(0.5,0.6,1.0)*fr*0.6;
                    col += vec3(0.8,0.9,1.0)*sk*1.2;
                    col *= 0.82+vig*0.18;

                } else if(uRarity == 6){
                    vec3  rb   = rainbow(vUV,uTilt);
                    vec3  gold = texture(uGold,vUV*2.0).rgb;
                    float sw   = sin((vUV.x-vUV.y)*4.0+uTime*3.0)*0.5+0.5;
                    float sp   = spec(vUV,uTilt)*(0.8+uHover*0.5);
                    float fr   = fres(vUV)*(0.5+tm*0.7);
                    float sk   = pow(sparkleVal(vUV,uTime*0.8),2.0)*(0.5+tm);
                    vec2  grd  = abs(fract(vUV*22.0)-0.5);
                    float em   = smoothstep(0.45,0.5,max(grd.x,grd.y))*0.07;
                    col  = mix(col,col*1.25,0.5);
                    col  = mix(col,col*gold*1.4,0.45);
                    col += rb*sw*0.35;
                    col += vec3(1.0,0.9,0.5)*sp*1.2;
                    col += vec3(1.0,0.85,0.4)*fr*0.7;
                    col += vec3(0.9,0.95,1.0)*sk*0.8;
                    col -= vec3(em);
                    col *= 0.82+vig*0.18;

                } else {
                        // RARITY_PACK — subtle gloss, easy to read
                        float tm2 = length(uTilt);
                
                        float sp = pow(max(1.0 - length(vUV - (vec2(0.5) + uTilt * 0.5)) * 2.8, 0.0), 6.0) * 0.5;
                        float sp2 = pow(max(1.0 - length(vUV - (vec2(0.5) - uTilt * 0.35)) * 3.5, 0.0), 3.0) * 0.08;
                
                        vec2 edgeDist = min(vUV, 1.0 - vUV);
                        float rim = 1.0 - smoothstep(0.0, 0.2, min(edgeDist.x, edgeDist.y));
                        rim *= (0.15 + tm2 * 0.3);
                
                        float bandY = vUV.y + uTilt.x * 0.15;
                        float band  = exp(-pow((bandY - 0.65) * 12.0, 2.0)) * 0.10;
                
                        col = mix(col, col * 1.04, tm2 * 0.2 + uHover * 0.06);
                        col += vec3(1.0) * sp;
                        col += vec3(0.9, 0.94, 1.0) * sp2;
                        col += vec3(0.9, 0.95, 1.0) * rim * 0.3;
                        col += vec3(1.0) * band;
                
                        float v2 = 1.0 - smoothstep(0.15, 0.75, length(vUV - 0.5));
                        col *= 0.95 + v2 * 0.05;
                    }

                fragColor = vec4(col, base.a*mask*uAlpha);
            }
            """;

        holoProg = createProgram(vert, frag);

        String shadowVert = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec2 aUV;
            layout(location=2) in vec3 aNorm;
            uniform mat4 uProj, uView, uModel;
            out vec2 vUV;
            void main(){ vUV=aUV; gl_Position=uProj*uView*uModel*vec4(aPos,1.0); }
            """;

        String shadowFrag = """
            #version 330 core
            in vec2 vUV; uniform float uAlpha; out vec4 fragColor;
            void main(){
                vec2 q=abs(vUV-0.5)-vec2(0.44,0.44);
                float a=(1.0-smoothstep(0.0,0.12,length(max(q,0.0))))*uAlpha;
                fragColor=vec4(0.0,0.0,0.0,a);
            }
            """;

        shadowProg = createProgram(shadowVert, shadowFrag);
    }

    private float[] makeCardModel(float x, float y, float z, float degX, float degY) {
        float rx = (float)Math.toRadians(degX), ry = (float)Math.toRadians(degY);
        float[] rX = identity();
        rX[5]=(float)Math.cos(rx); rX[6]=(float)Math.sin(rx);
        rX[9]=-(float)Math.sin(rx); rX[10]=(float)Math.cos(rx);
        float[] rY = identity();
        rY[0]=(float)Math.cos(ry); rY[2]=-(float)Math.sin(ry);
        rY[8]=(float)Math.sin(ry); rY[10]=(float)Math.cos(ry);
        return mul4(makeTranslate(x, y, z), mul4(rX, rY));
    }

    private float[] makePerspective(float fovDeg, float aspect, float near, float far) {
        float f = 1f / (float)Math.tan(Math.toRadians(fovDeg) / 2.0);
        return new float[]{ f/aspect,0,0,0, 0,f,0,0,
                0,0,-(far+near)/(far-near),-1, 0,0,-2f*far*near/(far-near),0 };
    }

    private static float[] makeTranslate(float x, float y, float z) {
        float[] m=identity(); m[12]=x; m[13]=y; m[14]=z; return m;
    }

    private static float[] makeScale(float x, float y, float z) {
        float[] m=identity(); m[0]=x; m[5]=y; m[10]=z; return m;
    }

    private static float[] identity() {
        return new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
    }

    private static float[] mul4(float[] a, float[] b) {
        float[] r=new float[16];
        for(int c=0;c<4;c++) for(int row=0;row<4;row++) for(int k=0;k<4;k++)
            r[c*4+row]+=a[k*4+row]*b[c*4+k];
        return r;
    }

    private static float ease(float t) {
        return t<0.5f ? 2*t*t : 1-((-2*t+2)*(-2*t+2))/2;
    }

    private static float lerp(float a, float b, float t) { return a+(b-a)*t; }

    private int createProgram(String vs, String fs) {
        int v=glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(v,vs); glCompileShader(v);
        if(glGetShaderi(v,GL_COMPILE_STATUS)==GL_FALSE)
            throw new RuntimeException("VS: "+glGetShaderInfoLog(v));
        int f=glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(f,fs); glCompileShader(f);
        if(glGetShaderi(f,GL_COMPILE_STATUS)==GL_FALSE)
            throw new RuntimeException("FS: "+glGetShaderInfoLog(f));
        int p=glCreateProgram();
        glAttachShader(p,v); glAttachShader(p,f); glLinkProgram(p);
        if(glGetProgrami(p,GL_LINK_STATUS)==GL_FALSE)
            throw new RuntimeException("Link: "+glGetProgramInfoLog(p));
        glDeleteShader(v); glDeleteShader(f);
        return p;
    }

    private int loadTexture(String path) {
        int id=glGenTextures();
        glBindTexture(GL_TEXTURE_2D,id);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        try(MemoryStack st=stackPush()){
            IntBuffer w=st.mallocInt(1),h=st.mallocInt(1),ch=st.mallocInt(1);
            org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load(false);
            ByteBuffer data=org.lwjgl.stb.STBImage.stbi_load(path,w,h,ch,4);
            if(data==null) throw new RuntimeException("Cannot load: "+path
                    +"\n"+org.lwjgl.stb.STBImage.stbi_failure_reason());
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w.get(0),h.get(0),0,GL_RGBA,GL_UNSIGNED_BYTE,data);
            glGenerateMipmap(GL_TEXTURE_2D);
            org.lwjgl.stb.STBImage.stbi_image_free(data);
        }
        return id;
    }

    private int loadTextureOrFallback(String path, int fallback) {
        try { return loadTexture(path); }
        catch(Exception e) { return fallback; }
    }

    private int generateSparkleTexture() {
        int size=256;
        java.util.Random rand=new java.util.Random(42);
        ByteBuffer buf=memAlloc(size*size*4);
        for(int y=0;y<size;y++) for(int x=0;x<size;x++){
            float v=rand.nextFloat()<0.018f ? 0.7f+rand.nextFloat()*0.3f : rand.nextFloat()*0.04f;
            byte bv=(byte)(Math.min(v,1f)*255);
            buf.put(bv).put(bv).put(bv).put((byte)255);
        }
        buf.flip();
        int id=glGenTextures();
        glBindTexture(GL_TEXTURE_2D,id);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,size,size,0,GL_RGBA,GL_UNSIGNED_BYTE,buf);
        glGenerateMipmap(GL_TEXTURE_2D);
        memFree(buf);
        return id;
    }

    private int generateGoldTexture() {
        int size=128;
        ByteBuffer buf=memAlloc(size*size*4);
        for(int y=0;y<size;y++) for(int x=0;x<size;x++){
            float fx=(float)x/size, fy=(float)y/size;
            float v=0.7f+(float)(Math.sin(fx*12.0+fy*8.0)*0.5+0.5)*0.3f;
            buf.put((byte)(Math.min(v,1f)*255))
                    .put((byte)(Math.min(v*0.8f,1f)*255))
                    .put((byte)(Math.min(v*0.3f,1f)*255))
                    .put((byte)255);
        }
        buf.flip();
        int id=glGenTextures();
        glBindTexture(GL_TEXTURE_2D,id);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,size,size,0,GL_RGBA,GL_UNSIGNED_BYTE,buf);
        glGenerateMipmap(GL_TEXTURE_2D);
        memFree(buf);
        return id;
    }

    private void setUniformMatrix(int p,String n,float[] m){
        int loc=glGetUniformLocation(p,n); if(loc<0) return;
        FloatBuffer b=memAllocFloat(16); b.put(m).flip();
        glUniformMatrix4fv(loc,false,b); memFree(b);
    }
    private void setUniform1i(int p,String n,int v)  {int l=glGetUniformLocation(p,n);if(l>=0)glUniform1i(l,v);}
    private void setUniform1f(int p,String n,float v){int l=glGetUniformLocation(p,n);if(l>=0)glUniform1f(l,v);}
    private void setUniform2f(int p,String n,float x,float y){int l=glGetUniformLocation(p,n);if(l>=0)glUniform2f(l,x,y);}

    private void cleanup(){
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }
}