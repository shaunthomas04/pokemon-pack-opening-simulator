// javac -classpath ".;C:\Program Files\lwjgl-release-3.3.4-custom\*" PokemonCard.java
// java  -classpath ".;C:\Program Files\lwjgl-release-3.3.4-custom\*" -Djava.library.path="C:\Program Files\lwjgl-release-3.3.4-custom" PokemonCard

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

    private static final int   WIN_W       = 1000;
    private static final int   WIN_H       = 720;
    private static final float CARD_W      = 0.72f;
    private static final float CARD_H      = 1.005f;
    private static final float CARD_Z_REST = -2.8f;
    private static final float CARD_Z_HOVER= -2.4f;
    private static final float PACK_W      = 0.82f;
    private static final float PACK_H      = 1.35f;
    private static final float PACK_Z      = -2.8f;
    private static final float SK          = 14f;
    private static final float SD          = 6f;
    private static final float MAX_TILT    = 22f;
    private static final int   ST_PACK     = 0;
    private static final int   ST_OPENING  = 1;
    private static final int   ST_DEAL     = 2;
    private static final int   ST_CARDS    = 3;
    private static final int   ST_WAIT     = 4;
    private static final int   CARD_COUNT  = 10;
    private static final float DEAL_DUR    = 0.22f;
    private static final float OPEN_DUR    = 0.9f;
    private static final float WAIT_TIME   = 5f;
    private static final float FLIP_DUR    = 0.55f;

    private long    window;
    private int     holoProg, shadowProg, flatProg;
    private int     vaoQuad;
    private int     texCard, texPack, texBack;

    private double  mouseX, mouseY;
    private boolean clickConsumed = false;
    private boolean mousePressed  = false;

    private float   packTiltX, packTiltY, packVtiltX, packVtiltY;
    private float   packZ = PACK_Z, packVz;
    private boolean packHovered;

    private float   tiltX, tiltY, vtiltX, vtiltY;
    private float   cardZ = CARD_Z_REST, vzCard;
    private boolean cardHovered;

    private int     state      = ST_PACK;
    private float   stateTimer = 0f;

    private float   openT      = 0f;
    private float   packShakeX = 0f;
    private float   packAlpha  = 1f;
    private float   tearY      = 0f;

    private int     dealtCount = 0;
    private float   dealTimer  = 0f;
    private float[] cardDealT  = new float[CARD_COUNT];
    private float[] cardFlipT  = new float[CARD_COUNT];

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
        texBack = loadTexture("back_card.jpg");
    }

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

    private void update(float dt) {
        stateTimer += dt;
        float nx = (float)(mouseX / WIN_W) * 2f - 1f;
        float ny = -((float)(mouseY / WIN_H) * 2f - 1f);
        boolean clicked = mousePressed && !clickConsumed;

        switch (state) {
            case ST_PACK: {
                packHovered = Math.abs(nx) < 0.38f && Math.abs(ny) < 0.65f;
                float targetTX =  ny * MAX_TILT;
                float targetTY = -nx * MAX_TILT;
                if (!packHovered) { targetTX = 0; targetTY = 0; }
                float axX = SK*(targetTX-packTiltX) - SD*packVtiltX;
                float axY = SK*(targetTY-packTiltY) - SD*packVtiltY;
                packVtiltX += axX*dt; packTiltX += packVtiltX*dt;
                packVtiltY += axY*dt; packTiltY += packVtiltY*dt;
                float tz = packHovered ? PACK_Z + 0.35f : PACK_Z;
                float az = SK*(tz-packZ) - SD*packVz;
                packVz += az*dt; packZ += packVz*dt;
                if (clicked && packHovered) {
                    clickConsumed = true;
                    state = ST_OPENING;
                    stateTimer = 0f;
                    openT = 0f; tearY = 0f; packShakeX = 0f; packAlpha = 1f;
                }
                break;
            }
            case ST_OPENING: {
                openT = Math.min(stateTimer / OPEN_DUR, 1f);
                float t = openT;
                packShakeX = (float)(Math.sin(t * 60f) * 0.04f * (1f - t));
                tearY = ease(t) * 1.8f;
                if (t > 0.6f) packAlpha = 1f - (t - 0.6f) / 0.4f;
                if (openT >= 1f) {
                    state = ST_DEAL;
                    stateTimer = 0f;
                    dealtCount = 0;
                    dealTimer = 0f;
                    for (int i = 0; i < CARD_COUNT; i++) { cardDealT[i] = 0f; cardFlipT[i] = 0f; }
                }
                break;
            }
            case ST_DEAL: {
                dealTimer += dt;
                int shouldHaveDealt = Math.min((int)(dealTimer / DEAL_DUR), CARD_COUNT);
                if (shouldHaveDealt > dealtCount) dealtCount = shouldHaveDealt;
                for (int i = 0; i < dealtCount; i++) {
                    cardDealT[i] = Math.min(cardDealT[i] + dt * 3.5f, 1f);
                    if (cardDealT[i] >= 1f) {
                        cardFlipT[i] = Math.min(cardFlipT[i] + dt / FLIP_DUR, 1f);
                    }
                }
                if (dealtCount == CARD_COUNT && cardFlipT[CARD_COUNT-1] >= 1f) {
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
                packFadeIn = Math.max(0f, Math.min((waitTimer - (WAIT_TIME - 1f)), 1f));
                if (waitTimer >= WAIT_TIME) {
                    state = ST_PACK;
                    stateTimer = 0f;
                    packAlpha = 1f; packFadeIn = 0f;
                    packTiltX = packTiltY = packVtiltX = packVtiltY = 0f;
                    packZ = PACK_Z; packVz = 0f;
                    tiltX = tiltY = vtiltX = vtiltY = 0f;
                    cardZ = CARD_Z_REST; vzCard = 0f;
                }
                break;
            }
        }
    }

    private void render() {
        glClearColor(0.06f, 0.06f, 0.10f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        float aspect = (float) WIN_W / WIN_H;
        float[] proj = makePerspective(45f, aspect, 0.1f, 100f);
        float[] view = identity();
        switch (state) {
            case ST_PACK    -> renderPack(proj, view, 1f, packTiltX, packTiltY, packZ);
            case ST_OPENING -> renderOpening(proj, view);
            case ST_DEAL    -> renderDeal(proj, view);
            case ST_CARDS   -> renderCards(proj, view);
            case ST_WAIT    -> { if (packFadeIn > 0f) renderPack(proj, view, packFadeIn, 0f, 0f, PACK_Z); }
        }
    }

    private void renderPack(float[] proj, float[] view, float alpha,
                            float tX, float tY, float pz) {
        float nx = (float)(mouseX / WIN_W) * 2f - 1f;
        float ny = -((float)(mouseY / WIN_H) * 2f - 1f);
        float[] model = makeCardModel(0f, 0f, pz, tX, tY);
        float scale = packHovered ? 1.04f : 1f;
        model = mul4(model, makeScale(scale, scale, 1f));
        drawShadow(proj, view, 0f, -0.08f, PACK_Z - 0.3f,
                PACK_W * scale * 1.2f, PACK_H * scale * 1.05f, 0.38f * alpha);
        drawHolo(proj, view, model, PACK_W, PACK_H,
                texPack, tX / MAX_TILT, tY / MAX_TILT, packHovered ? 1f : 0f, alpha, 0.04f);
    }

    private void renderOpening(float[] proj, float[] view) {
        if (packAlpha > 0f) {
            float[] model = makeCardModel(packShakeX, 0f, PACK_Z, 0f, 0f);
            drawHolo(proj, view, model, PACK_W, PACK_H,
                    texPack, 0f, 0f, 0f, packAlpha, 0.04f);
        }
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

    private void renderDeal(float[] proj, float[] view) {
        for (int i = dealtCount - 1; i >= 0; i--) {
            float t   = cardDealT[i];
            float ft  = cardFlipT[i];
            float et  = ease(t);
            float stackX = (i % 3 - 1) * 0.012f;
            float stackY = (i % 2 == 0 ? 1f : -1f) * 0.008f * i;
            float stackZ = CARD_Z_REST - i * 0.005f;
            float cx = lerp(0f, stackX, et);
            float cy = lerp(0f, stackY, et) + (float)Math.sin(t * Math.PI) * 0.4f;
            float cz = lerp(PACK_Z + 0.5f, stackZ, et);
            float cardAlpha = Math.min(t * 4f, 1f);
            float rx = lerp(30f, 0f, et);
            float ry = lerp((i % 2 == 0 ? 15f : -15f), 0f, et);

            float flipAngle = lerp(180f, 0f, ease(ft));
            float[] model = makeCardModel(cx, cy, cz, rx, ry + flipAngle);

            boolean showingFront = flipAngle < 90f;
            int tex = showingFront ? texCard : texBack;
            float tiltNX = showingFront ? 0f : 0f;
            float tiltNY = showingFront ? 0f : 0f;
            drawHolo(proj, view, model, CARD_W, CARD_H, tex, tiltNX, tiltNY, 0f, cardAlpha, 0.045f);
        }
    }

    private void renderCards(float[] proj, float[] view) {
        int remaining = CARD_COUNT - topCard;
        if (remaining <= 0) return;

        for (int i = Math.min(remaining - 1, 4); i >= 1; i--) {
            float offX = i * 0.012f;
            float offY = -i * 0.008f;
            float offZ = CARD_Z_REST - i * 0.005f;
            float[] model = makeCardModel(offX, offY, offZ, 0f, 0f);
            drawHolo(proj, view, model, CARD_W, CARD_H, texCard, 0f, 0f, 0f, 1f, 0.045f);
        }

        drawShadow(proj, view, 0.05f, -0.12f, CARD_Z_REST - 0.3f,
                CARD_W * 1.15f, CARD_H * 1.1f,
                0.45f + (CARD_Z_REST - cardZ) * 0.12f);

        float[] model;
        float alpha = 1f;
        if (dismissing) {
            float et = ease(dismissT);
            float dx = dismissDX * et * 2.5f;
            float dy = et * 0.4f;
            float dz = CARD_Z_REST + et * 0.3f;
            float dr = dismissDX * et * 35f;
            model = makeCardModel(dx, dy, dz, 0f, dr);
            alpha = 1f - et;
            drawHolo(proj, view, model, CARD_W, CARD_H, texCard, 0f, 0f, 0f, alpha, 0.045f);
        } else {
            model = makeCardModel(0f, 0f, cardZ, tiltX, tiltY);
            drawHolo(proj, view, model, CARD_W, CARD_H, texCard,
                    tiltX / MAX_TILT, tiltY / MAX_TILT, cardHovered ? 1f : 0f, 1f, 0.045f);
        }
    }

    private void drawHolo(float[] proj, float[] view, float[] model,
                          float w, float h, int tex,
                          float tiltNX, float tiltNY, float hover, float alpha, float cornerR) {
        glUseProgram(holoProg);
        setUniformMatrix(holoProg, "uProj",    proj);
        setUniformMatrix(holoProg, "uView",    view);
        setUniformMatrix(holoProg, "uModel",   model);
        setUniform2f(holoProg,    "uSize",     w, h);
        setUniform2f(holoProg,    "uTilt",     tiltNX, tiltNY);
        setUniform1f(holoProg,    "uHover",    hover);
        setUniform1f(holoProg,    "uAlpha",    alpha);
        setUniform1f(holoProg,    "uCornerR",  cornerR);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tex);
        setUniform1i(holoProg, "uTex", 0);
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
            uniform vec2  uTilt;
            uniform float uHover, uAlpha, uCornerR;
            out vec4 fragColor;

            float roundedRect(vec2 uv, float r){
                vec2 q = abs(uv-0.5)-(0.5-r);
                return 1.0 - smoothstep(-0.005, 0.005, length(max(q,0.0))-r);
            }
            vec3 holo(vec2 uv, vec2 tilt){
                vec2 s = uv + tilt*0.35;
                float h = fract(s.x*6.0 + s.y*3.0);
                return clamp(abs(mod(h*6.0+vec3(0,4,2),6.0)-3.0)-1.0, 0.0, 1.0);
            }
            float spec(vec2 uv, vec2 tilt){
                float d = length(uv - (vec2(0.5)+tilt*0.6));
                return pow(max(1.0-d*2.2, 0.0), 3.5);
            }
            float fres(vec2 uv){
                vec2 e = min(uv, 1.0-uv);
                return 1.0 - smoothstep(0.0, 0.18, min(e.x, e.y));
            }
            void main(){
                vec4 base = texture(uTex, vec2(vUV.x, 1.0-vUV.y));
                float mask = roundedRect(vUV, uCornerR);
                vec3 rb   = holo(vUV, uTilt);
                float tm  = length(uTilt);
                float sp  = spec(vUV, uTilt) * (0.6 + uHover*0.4);
                float fr  = fres(vUV) * (0.3 + tm*0.5);
                vec3  fc  = mix(vec3(0.6,0.8,1.0), rb, 0.5);
                vec3  col = base.rgb;
                col = mix(col, col*1.18, tm*0.4);
                col += rb * (0.28+uHover*0.12) * tm;
                col += vec3(1.0)*sp*0.9;
                col += fc*fr*0.5;
                float vig = 1.0 - smoothstep(0.35, 0.75, length(vUV-0.5));
                col *= 0.85 + vig*0.15;
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

    private void cleanup() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }
}