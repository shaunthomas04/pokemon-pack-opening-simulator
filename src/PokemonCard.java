// Compile & Run (adjust path to your LWJGL folder as needed):
// javac -classpath ".;C:\Program Files\lwjgl-release-3.3.4-custom\*" PokemonCard.java
// java  -classpath ".;C:\Program Files\lwjgl-release-3.3.4-custom\*" -Djava.library.path="C:\Program Files\lwjgl-release-3.3.4-custom" PokemonCard
//
// Required image files in the same directory:
//   pokemon_card.jpg   (the card face — reused for all 10 cards)
//   card_pack.jpg      (the booster pack image)

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

    // ── window ────────────────────────────────────────────────────────────────
    private static final int   WIN_W = 1000;
    private static final int   WIN_H = 720;

    // ── card geometry ─────────────────────────────────────────────────────────
    private static final float CARD_W      = 0.72f;
    private static final float CARD_H      = 1.005f;
    private static final float CARD_Z_REST = -2.8f;
    private static final float CARD_Z_HOVER= -2.4f;

    // ── pack geometry (taller/narrower booster shape) ─────────────────────────
    private static final float PACK_W = 0.82f;
    private static final float PACK_H = 1.35f;
    private static final float PACK_Z = -2.8f;

    // ── spring physics ────────────────────────────────────────────────────────
    private static final float SK = 14f, SD = 6f, MAX_TILT = 22f;

    // ── state machine ─────────────────────────────────────────────────────────
    private static final int ST_PACK      = 0;   // showing pack, waiting for click
    private static final int ST_OPENING   = 1;   // pack-tear animation
    private static final int ST_DEAL      = 2;   // cards flying in from pack
    private static final int ST_CARDS     = 3;   // browsing card stack
    private static final int ST_WAIT      = 4;   // 10-second cooldown before pack returns

    private static final int   CARD_COUNT = 10;
    private static final float DEAL_DUR   = 0.18f;  // seconds per card deal
    private static final float OPEN_DUR   = 0.9f;   // pack-open animation duration
    private static final float WAIT_TIME  = 10f;

    // ── GL handles ────────────────────────────────────────────────────────────
    private long window;
    private int  cardProg, shadowProg, packProg, flatProg;
    private int  vaoQuad;
    private int  texCard, texPack;

    // ── mouse ─────────────────────────────────────────────────────────────────
    private double mouseX, mouseY;
    private boolean clickConsumed = false;
    private boolean mousePressed  = false;

    // ── card spring state (for top-card tilt) ─────────────────────────────────
    private float tiltX, tiltY, vtiltX, vtiltY;
    private float cardZ = CARD_Z_REST, vzCard;
    private boolean cardHovered;

    // ── pack state ────────────────────────────────────────────────────────────
    private int     state      = ST_PACK;
    private float   stateTimer = 0f;

    // pack-open animation
    private float   openT      = 0f;   // [0,1] progress
    private float   packShakeX = 0f;
    private float   packAlpha  = 1f;

    // top half tears upward; we animate its Y offset
    private float   tearY      = 0f;

    // card dealing
    private int     dealtCount = 0;    // how many cards have been placed
    private float   dealTimer  = 0f;
    private float[] cardDealT  = new float[CARD_COUNT];  // [0,1] deal progress per card

    // card browsing
    private int     topCard    = 0;    // index of current top card (0 = first dealt)
    private float   dismissT   = 0f;   // [0,1] dismiss animation of top card
    private boolean dismissing = false;

    // dismiss arc (card flies off to side)
    private float   dismissDX  = 0f;

    // wait
    private float   waitTimer  = 0f;
    private float   packFadeIn = 0f;

    // ── entry point ───────────────────────────────────────────────────────────
    public static void main(String[] a) { new PokemonCard().run(); }

    private void run() { init(); loop(); cleanup(); }

    //                                                                          
    //  Init
    //                                                                          
    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Cannot init GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_SAMPLES, 4);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        window = glfwCreateWindow(WIN_W, WIN_H, "Pokémon Pack Opening", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create window");

        glfwSetCursorPosCallback(window, (w, x, y) -> { mouseX = x; mouseY = y; });
        glfwSetMouseButtonCallback(window, (w, btn, action, mods) -> {
            if (btn == GLFW_MOUSE_BUTTON_LEFT) {
                mousePressed = (action == GLFW_PRESS);
                if (action == GLFW_PRESS) clickConsumed = false;
            }
        });

        try (MemoryStack st = stackPush()) {
            IntBuffer pw = st.mallocInt(1), ph = st.mallocInt(1);
            glfwGetWindowSize(window, pw, ph);
            GLFWVidMode vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(window, (vm.width()-pw.get(0))/2, (vm.height()-ph.get(0))/2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_MULTISAMPLE);

        buildShaders();
        buildQuad();
        texCard = loadTexture("pokemon_card.jpg");
        texPack = loadTexture("card_pack.jpg");
    }

    //                                                                          
    //  Main loop
    //                                                                          
    private void loop() {
        long prev = System.nanoTime();
        while (!glfwWindowShouldClose(window)) {
            long now = System.nanoTime();
            float dt = Math.min((now - prev) / 1e9f, 0.05f);
            prev = now;
            update(dt);
            render();
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    //                                                                          
    //  Update — state machine
    //                                                                          
    private void update(float dt) {
        stateTimer += dt;

        float nx = (float)(mouseX / WIN_W) * 2f - 1f;
        float ny = -((float)(mouseY / WIN_H) * 2f - 1f);
        boolean clicked = mousePressed && !clickConsumed;

        switch (state) {

            // ── Idle: show pack, wait for click ──────────────────────────────
            case ST_PACK: {
                boolean packHov = Math.abs(nx) < 0.35f && Math.abs(ny) < 0.6f;
                if (clicked && packHov) {
                    clickConsumed = true;
                    state = ST_OPENING;
                    stateTimer = 0f;
                    openT = 0f; tearY = 0f; packShakeX = 0f; packAlpha = 1f;
                }
                break;
            }

            // ── Pack-tear animation ───────────────────────────────────────────
            case ST_OPENING: {
                openT = Math.min(stateTimer / OPEN_DUR, 1f);
                float t = openT;

                // shake increases then decays
                packShakeX = (float)(Math.sin(t * 60f) * 0.04f * (1f - t));
                // tear the top off upward
                tearY = ease(t) * 1.8f;
                // pack body fades after halfway
                if (t > 0.6f) packAlpha = 1f - (t - 0.6f) / 0.4f;

                if (openT >= 1f) {
                    state = ST_DEAL;
                    stateTimer = 0f;
                    dealtCount = 0;
                    dealTimer = 0f;
                    for (int i = 0; i < CARD_COUNT; i++) cardDealT[i] = 0f;
                }
                break;
            }

            // ── Deal cards one by one ─────────────────────────────────────────
            case ST_DEAL: {
                dealTimer += dt;
                // trigger a new card every DEAL_DUR seconds
                int shouldHaveDealt = Math.min((int)(dealTimer / DEAL_DUR), CARD_COUNT);
                if (shouldHaveDealt > dealtCount) dealtCount = shouldHaveDealt;

                // animate each dealt card's arrival
                for (int i = 0; i < dealtCount; i++) {
                    cardDealT[i] = Math.min(cardDealT[i] + dt * 3.5f, 1f);
                }

                // move to browsing once all cards are dealt and settled
                if (dealtCount == CARD_COUNT && cardDealT[CARD_COUNT-1] >= 1f) {
                    state = ST_CARDS;
                    stateTimer = 0f;
                    topCard = 0;
                    dismissing = false;
                    dismissT = 0f;
                }
                break;
            }

            // ── Card browsing ─────────────────────────────────────────────────
            case ST_CARDS: {
                // spring physics for top card tilt
                float targetTX =  ny * MAX_TILT;
                float targetTY = -nx * MAX_TILT;
                cardHovered = (Math.abs(nx) < 0.38f && Math.abs(ny) < 0.55f);

                float axX = SK*(targetTX-tiltX) - SD*vtiltX;
                float axY = SK*(targetTY-tiltY) - SD*vtiltY;
                vtiltX += axX*dt; tiltX += vtiltX*dt;
                vtiltY += axY*dt; tiltY += vtiltY*dt;

                float tz = cardHovered ? CARD_Z_HOVER : CARD_Z_REST;
                float az = SK*(tz-cardZ) - SD*vzCard;
                vzCard += az*dt; cardZ += vzCard*dt;

                if (dismissing) {
                    dismissT = Math.min(dismissT + dt * 2.2f, 1f);
                    if (dismissT >= 1f) {
                        dismissing = false;
                        topCard++;
                        dismissT = 0f;
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

            // ── 10-second wait then pack reappears ────────────────────────────
            case ST_WAIT: {
                waitTimer += dt;
                packFadeIn = Math.min((waitTimer - (WAIT_TIME - 1f)), 1f);
                if (waitTimer >= WAIT_TIME) {
                    state = ST_PACK;
                    stateTimer = 0f;
                    packAlpha = 1f; packFadeIn = 0f;
                    tiltX = tiltY = vtiltX = vtiltY = 0f;
                    cardZ = CARD_Z_REST; vzCard = 0f;
                }
                break;
            }
        }
    }

    //                                                                          
    //  Render
    //                                                                          
    private void render() {
        glClearColor(0.06f, 0.06f, 0.10f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        float aspect = (float) WIN_W / WIN_H;
        float[] proj = makePerspective(45f, aspect, 0.1f, 100f);
        float[] view = identity();

        switch (state) {
            case ST_PACK  -> renderPack(proj, view, 1f);
            case ST_OPENING -> renderOpening(proj, view);
            case ST_DEAL  -> renderDeal(proj, view);
            case ST_CARDS -> renderCards(proj, view);
            case ST_WAIT  -> {
                if (packFadeIn > 0f) renderPack(proj, view, packFadeIn);
            }
        }
    }

    // ── Render the idle pack ──────────────────────────────────────────────────
    private void renderPack(float[] proj, float[] view, float alpha) {
        float nx = (float)(mouseX / WIN_W) * 2f - 1f;
        float ny = -((float)(mouseY / WIN_H) * 2f - 1f);
        boolean hov = (Math.abs(nx) < 0.35f && Math.abs(ny) < 0.6f);

        float zPack = hov ? PACK_Z + 0.35f : PACK_Z;
        float[] model = makeTranslate(0f, 0f, zPack);
        float scale = hov ? 1.04f : 1f;
        float[] s = makeScale(scale, scale, 1f);
        model = mul4(model, s);

        drawShadow(proj, view, 0f, -0.08f, PACK_Z - 0.3f,
                PACK_W * scale * 1.2f, PACK_H * scale * 1.05f, 0.38f * alpha);

        glUseProgram(packProg);
        setUniformMatrix(packProg, "uProj", proj);
        setUniformMatrix(packProg, "uView", view);
        setUniformMatrix(packProg, "uModel", model);
        setUniform1f(packProg, "uAlpha", alpha);
        setUniform2f(packProg, "uSize", PACK_W, PACK_H);
        setUniform2f(packProg, "uTilt", hov ? nx * 0.15f : 0f, hov ? ny * 0.1f : 0f);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texPack);
        setUniform1i(packProg, "uTex", 0);
        glBindVertexArray(vaoQuad);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }

    // ── Pack tear-open animation ──────────────────────────────────────────────
    private void renderOpening(float[] proj, float[] view) {
        // bottom half of pack (body) stays, fades
        if (packAlpha > 0f) {
            float[] model = makeTranslate(packShakeX, 0f, PACK_Z);
            glUseProgram(packProg);
            setUniformMatrix(packProg, "uProj", proj);
            setUniformMatrix(packProg, "uView", view);
            setUniformMatrix(packProg, "uModel", model);
            setUniform1f(packProg, "uAlpha", packAlpha);
            setUniform2f(packProg, "uSize", PACK_W, PACK_H);
            setUniform2f(packProg, "uTilt", 0f, 0f);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texPack);
            setUniform1i(packProg, "uTex", 0);
            glBindVertexArray(vaoQuad);
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        }

        // top-half tear flying upward (rendered as a flat colored rect for now,
        // ideally you'd clip the texture — here we use a semi-transparent strip)
        if (tearY < 1.8f && packAlpha > 0f) {
            float tearAlpha = packAlpha * (1f - tearY / 1.8f);
            float[] tm = makeTranslate(packShakeX * 1.5f, tearY, PACK_Z + 0.01f);
            float[] ts = makeScale(PACK_W, PACK_H * 0.3f, 1f);
            float[] tearModel = mul4(tm, ts);
            glUseProgram(flatProg);
            setUniformMatrix(flatProg, "uProj", proj);
            setUniformMatrix(flatProg, "uView", view);
            setUniformMatrix(flatProg, "uModel", tearModel);
            setUniform1f(flatProg, "uAlpha", tearAlpha);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texPack);
            setUniform1i(flatProg, "uTex", 0);
            glBindVertexArray(vaoQuad);
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        }
    }

    // ── Cards flying in from pack ─────────────────────────────────────────────
    private void renderDeal(float[] proj, float[] view) {
        for (int i = dealtCount - 1; i >= 0; i--) {
            float t = cardDealT[i];
            float et = ease(t);
            // cards start at pack position and fan down to stack position
            // stack offset: each card is slightly offset from centre
            float stackX = (i % 3 - 1) * 0.012f;
            float stackY = (i % 2 == 0 ? 1f : -1f) * 0.008f * i;
            float stackZ = CARD_Z_REST - i * 0.005f;

            float startX = 0f, startY = 0f, startZ = PACK_Z + 0.5f;
            float cx = lerp(startX, stackX, et);
            float cy = lerp(startY, stackY, et);
            float cz = lerp(startZ, stackZ, et);
            // slight arc upward at midpoint
            cy += (float)Math.sin(t * Math.PI) * 0.4f;

            float cardAlpha = Math.min(t * 4f, 1f);
            float rx = lerp(30f, 0f, et);
            float ry = lerp((i % 2 == 0 ? 15f : -15f), 0f, et);

            float[] model = makeCardModel(cx, cy, cz, rx, ry);
            drawCard(proj, view, model, 0f, 0f, cardAlpha, false);
        }
    }

    // ── Browsing the card stack ───────────────────────────────────────────────
    private void renderCards(float[] proj, float[] view) {
        int remaining = CARD_COUNT - topCard;
        if (remaining <= 0) return;

        // draw bottom cards of the stack (peeking offsets)
        for (int i = Math.min(remaining - 1, 4); i >= 1; i--) {
            int cardIdx = topCard + i;
            float offX = i * 0.012f;
            float offY = -i * 0.008f;
            float offZ = CARD_Z_REST - i * 0.005f;
            float[] model = makeCardModel(offX, offY, offZ, 0f, 0f);
            drawCard(proj, view, model, 0f, 0f, 1f, false);
        }

        // shadow for top card
        drawShadow(proj, view, 0.05f, -0.12f, CARD_Z_REST - 0.3f,
                CARD_W * 1.15f, CARD_H * 1.1f,
                0.45f + (CARD_Z_REST - cardZ) * 0.12f);

        // draw top card with dismiss animation or normal tilt
        float[] model;
        float tx = tiltX, ty = tiltY, alpha = 1f;
        if (dismissing) {
            float et = ease(dismissT);
            float dx = dismissDX * et * 2.5f;
            float dy = et * 0.4f;
            float dz = CARD_Z_REST + et * 0.3f;
            float dr = dismissDX * et * 35f;
            model = makeCardModel(dx, dy, dz, 0f, dr);
            alpha = 1f - et;
        } else {
            model = makeCardModel(0f, 0f, cardZ, tiltX, tiltY);
        }
        drawCard(proj, view, model, tiltX / MAX_TILT, tiltY / MAX_TILT, alpha, !dismissing && cardHovered);
    }

    // ── Draw a single card ────────────────────────────────────────────────────
    private void drawCard(float[] proj, float[] view, float[] model,
                          float tiltNX, float tiltNY, float alpha, boolean hovered) {
        glUseProgram(cardProg);
        setUniformMatrix(cardProg, "uProj",  proj);
        setUniformMatrix(cardProg, "uView",  view);
        setUniformMatrix(cardProg, "uModel", model);
        setUniform2f(cardProg, "uTilt", tiltNX, tiltNY);
        setUniform1f(cardProg, "uHover", hovered ? 1f : 0f);
        setUniform1f(cardProg, "uAlpha", alpha);
        setUniform2f(cardProg, "uSize", CARD_W, CARD_H);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texCard);
        setUniform1i(cardProg, "uTex", 0);
        glBindVertexArray(vaoQuad);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }

    // ── Draw a blurred shadow ellipse ─────────────────────────────────────────
    private void drawShadow(float[] proj, float[] view,
                            float x, float y, float z,
                            float sw, float sh, float alpha) {
        glDepthMask(false);
        float[] sm = makeTranslate(x, y, z);
        float[] ss = makeScale(sw, sh, 1f);
        float[] shadowModel = mul4(sm, ss);
        glUseProgram(shadowProg);
        setUniformMatrix(shadowProg, "uProj",  proj);
        setUniformMatrix(shadowProg, "uView",  view);
        setUniformMatrix(shadowProg, "uModel", shadowModel);
        setUniform1f(shadowProg, "uAlpha", alpha);
        glBindVertexArray(vaoQuad);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        glDepthMask(true);
    }

    //                                                                          
    //  Geometry — a single unit quad [-0.5, 0.5]
    //                                                                          
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

    //                                                                          
    //  Shaders
    //                                                                          
    private void buildShaders() {

        // shared vertex shader (scale by uSize, then MVP)
        String commonVert = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec2 aUV;
            layout(location=2) in vec3 aNorm;
            uniform mat4 uProj, uView, uModel;
            uniform vec2 uSize;
            out vec2 vUV;
            out vec3 vNormW, vPosW;
            void main(){
                vec3 p = vec3(aPos.x * uSize.x, aPos.y * uSize.y, aPos.z);
                vec4 wp = uModel * vec4(p, 1.0);
                vPosW  = wp.xyz;
                vNormW = mat3(uModel) * aNorm;
                vUV    = aUV;
                gl_Position = uProj * uView * wp;
            }
            """;

        // ── Card frag (holo reflections) ──────────────────────────────────────
        String cardFrag = """
            #version 330 core
            in vec2 vUV; in vec3 vNormW, vPosW;
            uniform sampler2D uTex;
            uniform vec2  uTilt;
            uniform float uHover, uAlpha;
            out vec4 fragColor;

            float roundedRect(vec2 uv, float r){
                vec2 q = abs(uv-0.5)-(0.5-r);
                return 1.0 - smoothstep(-0.005,0.005, length(max(q,0.0))-r);
            }
            vec3 holo(vec2 uv, vec2 tilt){
                vec2 s = uv + tilt*0.35;
                float h = fract(s.x*6.0+s.y*3.0);
                return clamp(abs(mod(h*6.0+vec3(0,4,2),6.0)-3.0)-1.0,0.0,1.0);
            }
            float spec(vec2 uv, vec2 tilt){
                float d = length(uv - (vec2(0.5)+tilt*0.6));
                return pow(max(1.0-d*2.2,0.0),3.5);
            }
            float fres(vec2 uv){
                vec2 e = min(uv,1.0-uv);
                return 1.0-smoothstep(0.0,0.18,min(e.x,e.y));
            }
            void main(){
                vec4 base = texture(uTex, vec2(vUV.x, 1.0-vUV.y));
                float mask = roundedRect(vUV, 0.045);
                vec3 rb = holo(vUV, uTilt);
                float tm = length(uTilt);
                float holoStr = 0.28+uHover*0.12;
                float sp = spec(vUV,uTilt)*(0.6+uHover*0.4);
                float fr = fres(vUV)*(0.3+tm*0.5);
                vec3 fc = mix(vec3(0.6,0.8,1.0),rb,0.5);
                vec3 col = base.rgb;
                col = mix(col, col*1.18, tm*0.4);
                col += rb*holoStr*tm;
                col += vec3(1.0)*sp*0.9;
                col += fc*fr*0.5;
                float vig = 1.0-smoothstep(0.35,0.75,length(vUV-0.5));
                col *= 0.85+vig*0.15;
                fragColor = vec4(col, base.a*mask*uAlpha);
            }
            """;

        cardProg = createProgram(commonVert, cardFrag);

        // ── Pack frag (glossy, slight shimmer) ────────────────────────────────
        String packFrag = """
            #version 330 core
            in vec2 vUV; in vec3 vNormW, vPosW;
            uniform sampler2D uTex;
            uniform vec2  uTilt;
            uniform float uAlpha;
            out vec4 fragColor;

            float roundedRect(vec2 uv, float r){
                vec2 q = abs(uv-0.5)-(0.5-r);
                return 1.0 - smoothstep(-0.006,0.006, length(max(q,0.0))-r);
            }
            float spec(vec2 uv, vec2 tilt){
                float d = length(uv-(vec2(0.5)+tilt*0.5));
                return pow(max(1.0-d*2.0,0.0),4.0)*0.55;
            }
            void main(){
                vec4 base = texture(uTex, vec2(vUV.x, 1.0-vUV.y));
                float mask = roundedRect(vUV, 0.04);
                float sp = spec(vUV, uTilt);
                vec3 col = base.rgb + vec3(1.0)*sp;
                float vig = 1.0-smoothstep(0.3,0.7,length(vUV-0.5));
                col *= 0.9+vig*0.1;
                fragColor = vec4(col, base.a*mask*uAlpha);
            }
            """;

        packProg = createProgram(commonVert, packFrag);

        // ── Flat textured frag (for the tear strip) ───────────────────────────
        String flatFrag = """
            #version 330 core
            in vec2 vUV;
            uniform sampler2D uTex;
            uniform float uAlpha;
            out vec4 fragColor;
            void main(){
                vec4 c = texture(uTex, vec2(vUV.x, 1.0-vUV.y));
                fragColor = vec4(c.rgb, c.a * uAlpha);
            }
            """;

        // flat vert doesn't use uSize — override with a simpler vert
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

        flatProg = createProgram(flatVert, flatFrag);

        // ── Shadow frag ───────────────────────────────────────────────────────
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

    //                                                                          
    //  Matrix helpers
    //                                                                          
    private float[] makeCardModel(float x, float y, float z, float degX, float degY) {
        float rx = (float)Math.toRadians(degX);
        float ry = (float)Math.toRadians(degY);
        float[] rX = identity();
        rX[5]=(float)Math.cos(rx); rX[6]=(float)Math.sin(rx);
        rX[9]=-(float)Math.sin(rx); rX[10]=(float)Math.cos(rx);
        float[] rY = identity();
        rY[0]=(float)Math.cos(ry); rY[2]=-(float)Math.sin(ry);
        rY[8]=(float)Math.sin(ry); rY[10]=(float)Math.cos(ry);
        float[] t = makeTranslate(x, y, z);
        return mul4(t, mul4(rX, rY));
    }

    private float[] makePerspective(float fovDeg, float aspect, float near, float far) {
        float f = 1f / (float)Math.tan(Math.toRadians(fovDeg) / 2.0);
        return new float[]{
                f/aspect,0,0,0,
                0,f,0,0,
                0,0,-(far+near)/(far-near),-1,
                0,0,-2f*far*near/(far-near),0
        };
    }

    private static float[] makeTranslate(float x, float y, float z) {
        float[] m = identity();
        m[12]=x; m[13]=y; m[14]=z;
        return m;
    }

    private static float[] makeScale(float x, float y, float z) {
        float[] m = identity();
        m[0]=x; m[5]=y; m[10]=z;
        return m;
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

    // ── Easing & lerp ─────────────────────────────────────────────────────────
    private static float ease(float t) {
        return t < 0.5f ? 2*t*t : 1 - (-2*t+2)*(-2*t+2)/2;
    }

    private static float lerp(float a, float b, float t) { return a + (b-a)*t; }

    //                                                                          
    //  Shader helpers
    //                                                                          
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
    private void setUniform1i(int p,String n,int v){int l=glGetUniformLocation(p,n);if(l>=0)glUniform1i(l,v);}
    private void setUniform1f(int p,String n,float v){int l=glGetUniformLocation(p,n);if(l>=0)glUniform1f(l,v);}
    private void setUniform2f(int p,String n,float x,float y){int l=glGetUniformLocation(p,n);if(l>=0)glUniform2f(l,x,y);}

    //                                                                          
    //  Cleanup
    //                                                                          
    private void cleanup() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }
}