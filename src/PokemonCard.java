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
    private static final float CENTER_DUR  = 0.45f;
    private static final int   CARD_COUNT  = 10;
    private static final int   PACK_COUNT  = 6;
    private static final float DEAL_DUR    = 0.22f;
    private static final float OPEN_DUR    = 0.75f;
    private static final float WAIT_TIME   = 10f;
    private static final float FLIP_DUR    = 0.75f;

    private static final float[] PACK_X = { -1.3f, 0.0f, 1.3f,  -1.3f, 0.0f, 1.3f };
    private static final float[] PACK_Y = {  0.75f, 0.75f, 0.75f, -0.75f, -0.75f, -0.75f };
    private static final int[]   PACK_RARITY = { 1, 1, 1, 1, 1, 1 };

    private static final int[]   PACK_CARD_RARITY = {
            0, // chaos_rising        → cards are COMMON
            1, // ascended_heroes     → cards are UNCOMMON
            2, // phantasmal_flames   → cards are RARE
            3, // mega_evolution      → cards are ULTRA_RARE
            4, // destined_rivals     → cards are ILLUS_RARE
            6  // prismatic_evolutions→ cards are HYPER
    };

    private static final int RARITY_COMMON     = 0;
    private static final int RARITY_UNCOMMON   = 1;
    private static final int RARITY_RARE       = 2;
    private static final int RARITY_ULTRA_RARE = 3;
    private static final int RARITY_ILLUS_RARE = 4;
    private static final int RARITY_COSMOS     = 5;
    private static final int RARITY_HYPER      = 6;

    private int     WIN_W, WIN_H;
    private long    window;
    private int     holoProg, shadowProg, flatProg;
    private int     vaoQuad;
    private int[]   texPacks = new int[6];
    private int     texBack, texSparkle, texGold;
    private int[]   cardTextures;
    private int[]   deck         = new int[CARD_COUNT];
    private int[]   deckRarities = new int[CARD_COUNT];

    private float   uTime = 0f;

    private double  mouseX, mouseY;
    private boolean clickConsumed = false;
    private boolean mousePressed  = false;

    // Per-pack hover/tilt state
    private float[] packTiltX  = new float[PACK_COUNT];
    private float[] packTiltY  = new float[PACK_COUNT];
    private float[] packVtiltX = new float[PACK_COUNT];
    private float[] packVtiltY = new float[PACK_COUNT];
    private float[] packZArr   = new float[PACK_COUNT];
    private float[] packVz     = new float[PACK_COUNT];
    private boolean[] packHov  = new boolean[PACK_COUNT];

    private int     activePack = -1; // which pack is being opened

    private float   tiltX, tiltY, vtiltX, vtiltY;
    private float   cardZ = CARD_Z_REST, vzCard;
    private boolean cardHovered;

    private int     state      = ST_PACK;
    private float   stateTimer = 0f;

    private float   packCenterT = 0f;
    private float   packStartX  = 0f;
    private float   packStartY  = 0f;

    private float   openT      = 0f;
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
    private float   packFadeIn = 0f;

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

        cardTextures = loadAllCardTextures("cards");

        String[] packFiles = {
                "packs/chaos_rising.jpg",
                "packs/ascended_heroes.jpg",
                "packs/phantasmal_flames.jpg",
                "packs/mega_evolution.jpg",
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

        for (int i = 0; i < PACK_COUNT; i++) packZArr[i] = PACK_Z;
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

    // Convert screen pixel coords to normalised [-1,1] in the projection plane at PACK_Z
    // We just use simple [-1,1] NDC for hit-testing packs.
    private float screenNX() { return (float)(mouseX / WIN_W) * 2f - 1f; }
    private float screenNY() { return -((float)(mouseY / WIN_H) * 2f - 1f); }

    // Convert world X at PACK_Z to NDC X (using same perspective as render)
    private float worldToNDCX(float wx) {
        float aspect = (float) WIN_W / WIN_H;
        float fov = 45f;
        float f = 1f / (float)Math.tan(Math.toRadians(fov) / 2.0);
        // perspective: ndcX = (wx / -wz) * (f / aspect)  but wz is PACK_Z (negative)
        return wx / (-PACK_Z) * (f / aspect);
    }

    private float worldToNDCY(float wy) {
        float fov = 45f;
        float f = 1f / (float)Math.tan(Math.toRadians(fov) / 2.0);
        return wy / (-PACK_Z) * f;
    }

    // Half-extents of a pack in NDC
    private float packHalfW() { return PACK_W / 2f / (-PACK_Z) * (1f / (float)Math.tan(Math.toRadians(45f) / 2.0)) / ((float) WIN_W / WIN_H); }
    private float packHalfH() { return PACK_H / 2f / (-PACK_Z) * (1f / (float)Math.tan(Math.toRadians(45f) / 2.0)); }

    private boolean isPackHovered(int i) {
        float nx = screenNX();
        float ny = screenNY();
        float cx = worldToNDCX(PACK_X[i]);
        float cy = worldToNDCY(PACK_Y[i]);
        float hw = packHalfW();
        float hh = packHalfH();
        return Math.abs(nx - cx) < hw && Math.abs(ny - cy) < hh;
    }

    private void update(float dt) {
        stateTimer += dt;
        float nx = screenNX();
        float ny = screenNY();
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
                        openT = 0f; packShakeX = 0f; packAlpha = 1f;
                    }
                }
                break;
            }
            case ST_CENTER: {
                packCenterT = Math.min(stateTimer / CENTER_DUR, 1f);
                if (packCenterT >= 1f) {
                    state = ST_OPENING;
                    stateTimer = 0f;
                    openT = 0f; packShakeX = 0f; packAlpha = 1f;
                }
                break;
            }
            case ST_OPENING: {
                openT = Math.min(stateTimer / OPEN_DUR, 1f);
                float t = openT;
                // Only shake, no tear movement
                packShakeX = (float)(Math.sin(t * 60f) * 0.04f * (1f - t));
                if (t > 0.55f) packAlpha = 1f - (t - 0.55f) / 0.45f;
                if (openT >= 1f) {
                    state = ST_DEAL;
                    stateTimer = 0f;
                    dealtCount = 0;
                    dealTimer = 0f;
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
                    topCard = 0;
                    dismissing = false;
                    dismissT = 0f;
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
                            waitTimer = 0f;
                        }
                    }
                } else if (clicked && cardHovered) {
                    clickConsumed = true;
                    dismissing = true;
                    dismissT = 0f;
                    dismissDX = (nx > 0f) ? 1f : -1f;
                }
                break;
            }
            case ST_WAIT: {
                waitTimer += dt;
                if (waitTimer >= 2f) {
                    state = ST_PACK;
                    stateTimer = 0f;
                    packAlpha = 1f; packFadeIn = 0f; waitTimer = 0f;
                    for (int i = 0; i < PACK_COUNT; i++) {
                        packTiltX[i] = packTiltY[i] = packVtiltX[i] = packVtiltY[i] = 0f;
                        packZArr[i] = PACK_Z; packVz[i] = 0f;
                        packHov[i] = false;
                    }
                    tiltX = tiltY = vtiltX = vtiltY = 0f;
                    cardZ = CARD_Z_REST; vzCard = 0f;
                    activePack = -1;
                }
                break;
            }
        }
    }

    private int[] loadAllCardTextures(String folderPath) {
        java.io.File folder = new java.io.File(folderPath);
        java.io.File[] files = folder.listFiles((dir, name) ->
                name.endsWith(".png") || name.endsWith(".jpg"));
        if (files == null || files.length == 0)
            throw new RuntimeException("No images found in /" + folderPath);
        int[] textures = new int[files.length];
        for (int i = 0; i < files.length; i++)
            textures[i] = loadTexture(files[i].getAbsolutePath());
        return textures;
    }

    private void generateRandomDeck() {
        java.util.Random rand = new java.util.Random();
        int rarity = PACK_CARD_RARITY[activePack];
        for (int i = 0; i < CARD_COUNT; i++) {
            deckRarities[i] = rarity;
            deck[i] = cardTextures[rand.nextInt(cardTextures.length)];
        }
    }

    private void render() {
        glClearColor(0.06f, 0.06f, 0.10f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        float aspect = (float) WIN_W / WIN_H;
        float[] proj = makePerspective(45f, aspect, 0.1f, 100f);
        float[] view = identity();
        switch (state) {
            case ST_PACK    -> renderAllPacks(proj, view, 1f);
            case ST_CENTER  -> renderCenter(proj, view);
            case ST_OPENING -> renderOpening(proj, view);
            case ST_DEAL    -> renderDeal(proj, view);
            case ST_FLIP    -> renderFlip(proj, view);
            case ST_CARDS   -> renderCards(proj, view);
            case ST_WAIT    -> renderAllPacks(proj, view, 1f);
        }
    }

    private void renderAllPacks(float[] proj, float[] view, float alpha) {
        for (int i = 0; i < PACK_COUNT; i++) renderOnePack(proj, view, i, alpha);
    }

    private void renderAllPacksExcept(float[] proj, float[] view, float alpha, int skip) {
        for (int i = 0; i < PACK_COUNT; i++) if (i != skip) renderOnePack(proj, view, i, alpha);
    }

    private void renderOnePack(float[] proj, float[] view, int i, float alpha) {
        float tX = (state == ST_WAIT) ? 0f : packTiltX[i];
        float tY = (state == ST_WAIT) ? 0f : packTiltY[i];
        float pz = (state == ST_WAIT) ? PACK_Z : packZArr[i];
        float scale = packHov[i] ? 1.04f : 1f;
        float[] model = makeCardModel(PACK_X[i], PACK_Y[i], pz, tX, tY);
        model = mul4(model, makeScale(scale, scale, 1f));
        drawShadow(proj, view, PACK_X[i], PACK_Y[i] - 0.08f, PACK_Z - 0.3f,
                PACK_W * scale * 1.2f, PACK_H * scale * 1.05f, 0.38f * alpha);
        drawHolo(proj, view, model, PACK_W, PACK_H,
                texPacks[i], tX / MAX_TILT, tY / MAX_TILT,
                packHov[i] ? 1f : 0f, alpha, 0.04f, PACK_RARITY[i]);
    }

    private void renderCenter(float[] proj, float[] view) {
        float et = ease(packCenterT);
        float otherAlpha = 1f - et;
        for (int i = 0; i < PACK_COUNT; i++) {
            if (i != activePack) renderOnePack(proj, view, i, otherAlpha);
        }
        float cx = lerp(packStartX, 0f, et);
        float cy = lerp(packStartY, 0f, et);
        float[] model = makeCardModel(cx, cy, PACK_Z, 0f, 0f);
        drawShadow(proj, view, cx, cy - 0.08f, PACK_Z - 0.3f,
                PACK_W * 1.2f, PACK_H * 1.05f, 0.38f);
        drawHolo(proj, view, model, PACK_W, PACK_H, texPacks[activePack],
                0f, 0f, 0f, 1f, 0.04f, PACK_RARITY[activePack]);
    }

    private void renderOpening(float[] proj, float[] view) {
        if (packAlpha > 0f) {
            float[] model = makeCardModel(packShakeX, 0f, PACK_Z, 0f, 0f);
            drawHolo(proj, view, model, PACK_W, PACK_H, texPacks[activePack],
                    0f, 0f, 0f, packAlpha, 0.04f, PACK_RARITY[activePack]);
        }
    }

    private void renderDeal(float[] proj, float[] view) {
        for (int i = dealtCount - 1; i >= 0; i--) {
            float et = ease(cardDealT[i]);
            float stackX = (i % 3 - 1) * 0.012f;
            float stackY = (i % 2 == 0 ? 1f : -1f) * 0.008f * i;
            float stackZ = CARD_Z_REST - i * 0.005f;
            float srcX   = 0f;
            float cx = lerp(srcX, stackX, et);
            float cy = lerp(0f, stackY, et) + (float)Math.sin(cardDealT[i] * Math.PI) * 0.4f;
            float cz = lerp(PACK_Z + 0.5f, stackZ, et);
            float rx = lerp(30f, 0f, et);
            float ry = lerp((i % 2 == 0 ? 15f : -15f), 0f, et);
            float cardAlpha = Math.min(cardDealT[i] * 4f, 1f);
            float[] model = makeCardModel(cx, cy, cz, rx, ry);
            drawHolo(proj, view, model, CARD_W, CARD_H, texBack, 0f, 0f, 0f, cardAlpha, 0.045f, RARITY_COMMON);
        }
    }

    private void renderFlip(float[] proj, float[] view) {
        float flipAngle = lerp(180f, 0f, ease(deckFlipT));
        for (int i = Math.min(CARD_COUNT - 1, 4); i >= 1; i--) {
            float offX = i * 0.012f;
            float offY = -i * 0.008f;
            float offZ = CARD_Z_REST - i * 0.005f;
            int deckTex = flipAngle < 90f ? deck[i] : texBack;
            int rar = flipAngle < 90f ? deckRarities[i] : RARITY_COMMON;
            float[] model = makeCardModel(offX, offY, offZ, 0f, flipAngle);
            drawHolo(proj, view, model, CARD_W, CARD_H, deckTex, 0f, 0f, 0f, 1f, 0.045f, rar);
        }
        int tex = flipAngle < 90f ? deck[0] : texBack;
        int topRar = flipAngle < 90f ? deckRarities[0] : RARITY_COMMON;
        float[] model = makeCardModel(0f, 0f, CARD_Z_REST, 0f, flipAngle);
        drawHolo(proj, view, model, CARD_W, CARD_H, tex, 0f, 0f, 0f, 1f, 0.045f, topRar);
    }

    private void renderCards(float[] proj, float[] view) {
        int remaining = CARD_COUNT - topCard;
        if (remaining <= 0) return;

        drawShadow(proj, view, 0.05f, -0.12f, CARD_Z_REST - 0.3f,
                CARD_W * 1.15f, CARD_H * 1.1f,
                0.45f + (CARD_Z_REST - cardZ) * 0.12f);

        for (int i = Math.min(remaining - 1, 4); i >= 1; i--) {
            float offX = tiltY * 0.001f * i;
            float offY = -tiltX * 0.001f * i;
            float offZ = cardZ - i * 0.005f;
            float[] model = makeCardModel(offX, offY, offZ, tiltX, tiltY);
            drawHolo(proj, view, model, CARD_W, CARD_H, deck[topCard + i], 0f, 0f, 0f, 1f, 0.045f, deckRarities[topCard + i]);
        }

        if (dismissing) {
            float et = ease(dismissT);
            float dx = dismissDX * et * 2.5f;
            float dy = et * 0.4f;
            float dz = cardZ + et * 0.3f;
            float dr = dismissDX * et * 35f;
            float[] model = makeCardModel(dx, dy, dz, tiltX * (1f-et), tiltY * (1f-et) + dr);
            drawHolo(proj, view, model, CARD_W, CARD_H, deck[topCard], 0f, 0f, 0f, 1f - et, 0.045f, deckRarities[topCard]);
        } else {
            float[] model = makeCardModel(0f, 0f, cardZ, tiltX, tiltY);
            drawHolo(proj, view, model, CARD_W, CARD_H, deck[topCard],
                    tiltX / MAX_TILT, tiltY / MAX_TILT, cardHovered ? 1f : 0f, 1f, 0.045f, deckRarities[topCard]);
        }
    }

    private void drawHolo(float[] proj, float[] view, float[] model,
                          float w, float h, int tex,
                          float tiltNX, float tiltNY, float hover, float alpha, float cornerR,
                          int rarity) {
        glUseProgram(holoProg);
        setUniformMatrix(holoProg, "uProj",   proj);
        setUniformMatrix(holoProg, "uView",   view);
        setUniformMatrix(holoProg, "uModel",  model);
        setUniform2f(holoProg, "uSize",       w, h);
        setUniform2f(holoProg, "uTilt",       tiltNX, tiltNY);
        setUniform1f(holoProg, "uHover",      hover);
        setUniform1f(holoProg, "uAlpha",      alpha);
        setUniform1f(holoProg, "uCornerR",    cornerR);
        setUniform1i(holoProg, "uRarity",     rarity);
        setUniform1f(holoProg, "uTime",       uTime);
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
        float[] shadowModel = mul4(makeTranslate(x, y, z), makeScale(sw, sh, 1f));
        glUseProgram(shadowProg);
        setUniformMatrix(shadowProg, "uProj",  proj);
        setUniformMatrix(shadowProg, "uView",  view);
        setUniformMatrix(shadowProg, "uModel", shadowModel);
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
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3*Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5*Float.BYTES);
        glEnableVertexAttribArray(2);
        glBindVertexArray(0);
    }

    private void buildShaders() {
        String holoVert = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec2 aUV;
            layout(location=2) in vec3 aNorm;
            uniform mat4 uProj, uView, uModel;
            uniform vec2 uSize;
            out vec2 vUV;
            out vec3 vNormW, vPosW;
            void main(){
                vec3 p = vec3(aPos.x*uSize.x, aPos.y*uSize.y, aPos.z);
                vec4 wp = uModel * vec4(p, 1.0);
                vPosW  = wp.xyz;
                vNormW = mat3(uModel) * aNorm;
                vUV    = aUV;
                gl_Position = uProj * uView * wp;
            }
            """;

        String holoFrag = """
            #version 330 core
            in vec2 vUV; in vec3 vNormW, vPosW;
            uniform sampler2D uTex;
            uniform sampler2D uSparkle;
            uniform sampler2D uGold;
            uniform vec2  uTilt;
            uniform float uHover, uAlpha, uCornerR, uTime;
            uniform int   uRarity;
            out vec4 fragColor;

            float roundedRect(vec2 uv, float r){
                vec2 q = abs(uv-0.5)-(0.5-r);
                return 1.0 - smoothstep(-0.005, 0.005, length(max(q,0.0))-r);
            }
            vec3 rainbow(vec2 uv, vec2 tilt){
                vec2 s = uv + tilt*0.35;
                float h = fract(s.x*6.0 + s.y*3.0);
                return clamp(abs(mod(h*6.0+vec3(0,4,2),6.0)-3.0)-1.0, 0.0, 1.0);
            }
            float spec(vec2 uv, vec2 tilt){
                float d = length(uv-(vec2(0.5)+tilt*0.6));
                return pow(max(1.0-d*2.2,0.0),3.5);
            }
            float fres(vec2 uv){
                vec2 e = min(uv,1.0-uv);
                return 1.0-smoothstep(0.0,0.18,min(e.x,e.y));
            }
            float sparkleVal(vec2 uv, float t){
                vec2 s1 = texture(uSparkle, uv*3.0 + vec2(t*0.08, t*0.05)).r * vec2(1.0);
                float v = texture(uSparkle, uv*3.0 + vec2(t*0.08, t*0.05)).r;
                v += texture(uSparkle, uv*5.0 - vec2(t*0.06, t*0.09)).r * 0.5;
                return v / 1.5;
            }
            vec3 goldTint(vec2 uv){
                return texture(uGold, uv).rgb;
            }

            void main(){
                vec4 base = texture(uTex, vec2(vUV.x, 1.0-vUV.y));
                float mask = roundedRect(vUV, uCornerR);
                float tm   = length(uTilt);
                vec3  col  = base.rgb;
                float vig  = 1.0 - smoothstep(0.35, 0.75, length(vUV-0.5));

                if(uRarity == 0){
                    // COMMON — matte, almost no sheen
                    float softSpec = pow(max(1.0-length(vUV-(vec2(0.5)+uTilt*0.4))*3.0,0.0),6.0)*0.15;
                    col += vec3(softSpec);
                    col *= 0.92 + vig*0.08;

                } else if(uRarity == 1){
                    // UNCOMMON — satin silver shimmer
                    float sp = spec(vUV, uTilt)*0.5;
                    float fr = fres(vUV)*(0.2+tm*0.3);
                    vec3 silver = vec3(0.75,0.82,0.9);
                    col += silver * sp;
                    col += silver * fr * 0.4;
                    col *= 0.93 + vig*0.07;

                } else if(uRarity == 2){
                    // RARE — holo on artwork strip (top 65% of card)
                    float artMask = smoothstep(0.28, 0.35, vUV.y);
                    vec3 rb  = rainbow(vUV, uTilt);
                    float sp = spec(vUV, uTilt)*(0.5+uHover*0.3);
                    float fr = fres(vUV)*(0.25+tm*0.4);
                    vec3 fc  = mix(vec3(0.6,0.8,1.0), rb, 0.5);
                    col = mix(col, col*1.15, tm*0.35*artMask);
                    col += rb*(0.22+uHover*0.1)*tm*artMask;
                    col += vec3(1.0)*sp*0.7;
                    col += fc*fr*0.4;
                    col *= 0.88 + vig*0.12;

                } else if(uRarity == 3){
                    // ULTRA RARE — full card holo + animated shimmer
                    vec3 rb  = rainbow(vUV, uTilt);
                    float sp = spec(vUV, uTilt)*(0.6+uHover*0.4);
                    float fr = fres(vUV)*(0.3+tm*0.5);
                    vec3 fc  = mix(vec3(0.6,0.8,1.0), rb, 0.5);
                    float animShimmer = sin(vUV.x*8.0 + uTime*2.0)*0.5+0.5;
                    animShimmer *= sin(vUV.y*6.0 - uTime*1.5)*0.5+0.5;
                    col = mix(col, col*1.18, tm*0.4);
                    col += rb*(0.28+uHover*0.12)*tm;
                    col += vec3(1.0)*sp*0.9;
                    col += fc*fr*0.5;
                    col += vec3(0.15,0.2,0.3)*animShimmer*0.12;
                    col *= 0.85 + vig*0.15;

                } else if(uRarity == 4){
                    // ILLUS RARE — full holo + twinkling sparkle dots
                    vec3 rb  = rainbow(vUV, uTilt);
                    float sp = spec(vUV, uTilt)*(0.65+uHover*0.4);
                    float fr = fres(vUV)*(0.35+tm*0.55);
                    vec3 fc  = mix(vec3(0.7,0.85,1.0), rb, 0.6);
                    float sparkle = sparkleVal(vUV, uTime);
                    sparkle = pow(sparkle, 2.5) * (0.6+tm*0.8);
                    col = mix(col, col*1.2, tm*0.4);
                    col += rb*(0.3+uHover*0.14)*tm;
                    col += vec3(1.0)*sp*0.9;
                    col += fc*fr*0.55;
                    col += vec3(0.9,0.95,1.0)*sparkle;
                    col *= 0.85 + vig*0.15;

                } else if(uRarity == 5){
                    // COSMOS RARE — deep blue/purple holo, large slow stars
                    vec2 cosmosTilt = uTilt*0.5;
                    vec3 cosmosRb = rainbow(vUV + vec2(uTime*0.015,0.0), cosmosTilt);
                    cosmosRb = mix(vec3(0.1,0.0,0.3), cosmosRb, 0.5); // bias toward purple
                    float sp = spec(vUV, uTilt)*(0.5+uHover*0.5);
                    float fr = fres(vUV)*(0.4+tm*0.6);
                    float sparkle = sparkleVal(vUV*0.4, uTime*0.5);
                    sparkle = pow(sparkle,3.0)*(0.8+tm);
                    col = mix(col, col*1.2, 0.3);
                    col += cosmosRb*(0.35)*max(tm,0.1);
                    col += vec3(1.0)*sp*1.0;
                    col += vec3(0.5,0.6,1.0)*fr*0.6;
                    col += vec3(0.8,0.9,1.0)*sparkle*1.2;
                    col *= 0.82 + vig*0.18;

                } else {
                    // HYPER/CROWN — gold prismatic sweep + emboss grid
                    vec3 rb   = rainbow(vUV, uTilt);
                    vec3 gold = goldTint(vUV*2.0);
                    float sweep = sin((vUV.x-vUV.y)*4.0 + uTime*3.0)*0.5+0.5;
                    float sp  = spec(vUV, uTilt)*(0.8+uHover*0.5);
                    float fr  = fres(vUV)*(0.5+tm*0.7);
                    float sparkle = sparkleVal(vUV, uTime*0.8);
                    sparkle = pow(sparkle,2.0)*(0.5+tm);
                    // emboss grid
                    vec2 grid = abs(fract(vUV*22.0)-0.5);
                    float emboss = smoothstep(0.45,0.5,max(grid.x,grid.y))*0.07;
                    col = mix(col, col*1.25, 0.5);
                    col = mix(col, col*gold*1.4, 0.45);
                    col += rb*sweep*0.35;
                    col += vec3(1.0,0.9,0.5)*sp*1.2;
                    col += vec3(1.0,0.85,0.4)*fr*0.7;
                    col += vec3(0.9,0.95,1.0)*sparkle*0.8;
                    col -= vec3(emboss);
                    col *= 0.82 + vig*0.18;
                }

                fragColor = vec4(col, base.a*mask*uAlpha);
            }
            """;

        holoProg = createProgram(holoVert, holoFrag);

        String flatVert = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec2 aUV;
            layout(location=2) in vec3 aNorm;
            uniform mat4 uProj, uView, uModel;
            out vec2 vUV;
            void main(){
                vUV = aUV;
                gl_Position = uProj * uView * uModel * vec4(aPos, 1.0);
            }
            """;

        String flatFrag = """
            #version 330 core
            in vec2 vUV;
            uniform sampler2D uTex;
            uniform float uAlpha;
            out vec4 fragColor;
            void main(){
                vec4 c = texture(uTex, vec2(vUV.x, 1.0-vUV.y));
                fragColor = vec4(c.rgb, c.a*uAlpha);
            }
            """;

        flatProg = createProgram(flatVert, flatFrag);

        String shadowVert = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec2 aUV;
            layout(location=2) in vec3 aNorm;
            uniform mat4 uProj, uView, uModel;
            out vec2 vUV;
            void main(){
                vUV = aUV;
                gl_Position = uProj * uView * uModel * vec4(aPos, 1.0);
            }
            """;

        String shadowFrag = """
            #version 330 core
            in vec2 vUV;
            uniform float uAlpha;
            out vec4 fragColor;
            void main(){
                vec2 q = abs(vUV-0.5)-vec2(0.44,0.44);
                float d = length(max(q,0.0));
                float a = (1.0-smoothstep(0.0,0.12,d))*uAlpha;
                fragColor = vec4(0.0,0.0,0.0,a);
            }
            """;

        shadowProg = createProgram(shadowVert, shadowFrag);
    }

    private float[] makeCardModel(float x, float y, float z, float degX, float degY) {
        float rx = (float)Math.toRadians(degX);
        float ry = (float)Math.toRadians(degY);
        float[] rX = identity();
        rX[5] =(float)Math.cos(rx); rX[6] =(float)Math.sin(rx);
        rX[9] =-(float)Math.sin(rx); rX[10]=(float)Math.cos(rx);
        float[] rY = identity();
        rY[0] =(float)Math.cos(ry); rY[2] =-(float)Math.sin(ry);
        rY[8] =(float)Math.sin(ry); rY[10]=(float)Math.cos(ry);
        return mul4(makeTranslate(x, y, z), mul4(rX, rY));
    }

    private float[] makePerspective(float fovDeg, float aspect, float near, float far) {
        float f = 1f / (float)Math.tan(Math.toRadians(fovDeg) / 2.0);
        return new float[]{ f/aspect,0,0,0, 0,f,0,0,
                0,0,-(far+near)/(far-near),-1, 0,0,-2f*far*near/(far-near),0 };
    }

    private static float[] makeTranslate(float x, float y, float z) {
        float[] m = identity(); m[12]=x; m[13]=y; m[14]=z; return m;
    }

    private static float[] makeScale(float x, float y, float z) {
        float[] m = identity(); m[0]=x; m[5]=y; m[10]=z; return m;
    }

    private static float[] identity() {
        return new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
    }

    private static float[] mul4(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c=0;c<4;c++) for (int row=0;row<4;row++) for (int k=0;k<4;k++)
            r[c*4+row] += a[k*4+row]*b[c*4+k];
        return r;
    }

    private static float ease(float t) {
        return t < 0.5f ? 2*t*t : 1-((-2*t+2)*(-2*t+2))/2;
    }

    private static float lerp(float a, float b, float t) { return a+(b-a)*t; }

    private int createProgram(String vs, String fs) {
        int v = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(v, vs); glCompileShader(v);
        if (glGetShaderi(v,GL_COMPILE_STATUS)==GL_FALSE)
            throw new RuntimeException("VS: "+glGetShaderInfoLog(v));
        int f = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(f, fs); glCompileShader(f);
        if (glGetShaderi(f,GL_COMPILE_STATUS)==GL_FALSE)
            throw new RuntimeException("FS: "+glGetShaderInfoLog(f));
        int p = glCreateProgram();
        glAttachShader(p,v); glAttachShader(p,f); glLinkProgram(p);
        if (glGetProgrami(p,GL_LINK_STATUS)==GL_FALSE)
            throw new RuntimeException("Link: "+glGetProgramInfoLog(p));
        glDeleteShader(v); glDeleteShader(f);
        return p;
    }

    private int loadTexture(String path) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        try (MemoryStack st = stackPush()) {
            IntBuffer w=st.mallocInt(1),h=st.mallocInt(1),ch=st.mallocInt(1);
            org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load(false);
            ByteBuffer data = org.lwjgl.stb.STBImage.stbi_load(path,w,h,ch,4);
            if (data==null) throw new RuntimeException("Cannot load: "+path
                    +"\n"+org.lwjgl.stb.STBImage.stbi_failure_reason());
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w.get(0),h.get(0),0,GL_RGBA,GL_UNSIGNED_BYTE,data);
            glGenerateMipmap(GL_TEXTURE_2D);
            org.lwjgl.stb.STBImage.stbi_image_free(data);
        }
        return id;
    }

    private void setUniformMatrix(int p, String n, float[] m) {
        int loc = glGetUniformLocation(p,n); if (loc<0) return;
        FloatBuffer b = memAllocFloat(16); b.put(m).flip();
        glUniformMatrix4fv(loc,false,b); memFree(b);
    }
    private void setUniform1i(int p,String n,int v)  {int l=glGetUniformLocation(p,n);if(l>=0)glUniform1i(l,v);}
    private void setUniform1f(int p,String n,float v){int l=glGetUniformLocation(p,n);if(l>=0)glUniform1f(l,v);}
    private void setUniform2f(int p,String n,float x,float y){int l=glGetUniformLocation(p,n);if(l>=0)glUniform2f(l,x,y);}

    private int loadTextureOrFallback(String path, int fallback) {
        try { return loadTexture(path); }
        catch (Exception e) { return fallback; }
    }

    private int generateSparkleTexture() {
        int size = 256;
        java.util.Random rand = new java.util.Random(42);
        ByteBuffer buf = memAlloc(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float v = 0f;
                // scatter bright sparkle points
                if (rand.nextFloat() < 0.018f) {
                    v = 0.7f + rand.nextFloat() * 0.3f;
                } else {
                    v = rand.nextFloat() * 0.04f;
                }
                byte bv = (byte)(Math.min(v, 1f) * 255);
                buf.put(bv).put(bv).put(bv).put((byte)255);
            }
        }
        buf.flip();
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glGenerateMipmap(GL_TEXTURE_2D);
        memFree(buf);
        return id;
    }

    private int generateGoldTexture() {
        int size = 128;
        ByteBuffer buf = memAlloc(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float fx = (float)x / size;
                float fy = (float)y / size;
                float v = (float)(Math.sin(fx * 12.0 + fy * 8.0) * 0.5 + 0.5);
                v = 0.7f + v * 0.3f;
                byte r = (byte)(Math.min(v * 1.0f, 1f) * 255);
                byte g = (byte)(Math.min(v * 0.8f, 1f) * 255);
                byte b = (byte)(Math.min(v * 0.3f, 1f) * 255);
                buf.put(r).put(g).put(b).put((byte)255);
            }
        }
        buf.flip();
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glGenerateMipmap(GL_TEXTURE_2D);
        memFree(buf);
        return id;
    }

    private void cleanup() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }
}