// Compile & Run (adjust path to your LWJGL folder as needed):
// javac -classpath ".;C:\Program Files\lwjgl-release-3.3.4-custom\*" PokemonCard.java
// java  -classpath ".;C:\Program Files\lwjgl-release-3.3.4-custom\*" -Djava.library.path="C:\Program Files\lwjgl-release-3.3.4-custom" PokemonCard

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.io.*;
import java.nio.*;
import java.nio.file.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class PokemonCard {

    private static final int WIN_W = 900;
    private static final int WIN_H = 700;
    private static final String TITLE = "Holographic Pokémon Card";

    private static final float CARD_W = 0.72f;
    private static final float CARD_H = 1.005f;
    private static final float CORNER_R = 0.045f;
    private static final float CARD_Z_REST = -2.5f;
    private static final float CARD_Z_HOVER = -2.1f;

    private static final float SPRING_K = 14f;
    private static final float SPRING_D = 6f;
    private static final float MAX_TILT = 22f;

    private long window;
    private int prog, shadowProg;
    private int vaoCard, vaoQuad;
    private int texCard;

    private double mouseX, mouseY;
    private float tiltX, tiltY;
    private float vtiltX, vtiltY;
    private float cardZ = CARD_Z_REST;
    private float vzCard;
    private boolean hovered = false;

    public static void main(String[] args) { new PokemonCard().run(); }

    private void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Cannot init GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_SAMPLES, 4);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        window = glfwCreateWindow(WIN_W, WIN_H, TITLE, NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create window");

        glfwSetCursorPosCallback(window, (w, x, y) -> { mouseX = x; mouseY = y; });

        try (MemoryStack stack = stackPush()) {
            IntBuffer pw = stack.mallocInt(1), ph = stack.mallocInt(1);
            glfwGetWindowSize(window, pw, ph);
            GLFWVidMode vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(window,
                    (vm.width()  - pw.get(0)) / 2,
                    (vm.height() - ph.get(0)) / 2);
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
        buildGeometry();
        texCard = loadTexture("pokemon_card.jpg");
    }

    private void loop() {
        long prev = System.nanoTime();

        while (!glfwWindowShouldClose(window)) {
            long now = System.nanoTime();
            float dt = Math.min((now - prev) / 1_000_000_000f, 0.05f);
            prev = now;

            update(dt);
            render();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void update(float dt) {
        float nx = (float)(mouseX / WIN_W) * 2f - 1f;
        float ny = -((float)(mouseY / WIN_H) * 2f - 1f);

        hovered = (Math.abs(nx) < 0.45f && Math.abs(ny) < 0.65f);

        float targetX =  ny * MAX_TILT;
        float targetY = -nx * MAX_TILT;

        float axX = SPRING_K * (targetX - tiltX) - SPRING_D * vtiltX;
        float axY = SPRING_K * (targetY - tiltY) - SPRING_D * vtiltY;
        vtiltX += axX * dt;  tiltX += vtiltX * dt;
        vtiltY += axY * dt;  tiltY += vtiltY * dt;

        float targetZ = hovered ? CARD_Z_HOVER : CARD_Z_REST;
        float azZ = SPRING_K * (targetZ - cardZ) - SPRING_D * vzCard;
        vzCard += azZ * dt;  cardZ += vzCard * dt;
    }

    private void render() {
        glClearColor(0.07f, 0.07f, 0.10f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        float aspect = (float) WIN_W / WIN_H;

        float fov  = (float) Math.toRadians(45);
        float near = 0.1f, far = 100f;
        float f    = 1f / (float) Math.tan(fov / 2f);
        float[] proj = {
                f / aspect, 0,  0,  0,
                0,          f,  0,  0,
                0,          0, -(far + near)/(far - near), -1,
                0,          0, -2f*far*near/(far - near),   0
        };

        float rx = (float) Math.toRadians(tiltX);
        float ry = (float) Math.toRadians(tiltY);

        float[] rotX = identity();
        rotX[5]  =  (float) Math.cos(rx);
        rotX[6]  =  (float) Math.sin(rx);
        rotX[9]  = -(float) Math.sin(rx);
        rotX[10] =  (float) Math.cos(rx);

        float[] rotY = identity();
        rotY[0]  =  (float) Math.cos(ry);
        rotY[2]  = -(float) Math.sin(ry);
        rotY[8]  =  (float) Math.sin(ry);
        rotY[10] =  (float) Math.cos(ry);

        float[] rot   = mul4(rotX, rotY);
        float[] trans = identity();
        trans[12] = 0f;  trans[13] = 0f;  trans[14] = cardZ;
        float[] model = mul4(trans, rot);

        float[] view = identity();

        glDepthMask(false);
        glUseProgram(shadowProg);
        setUniformMatrix(shadowProg, "uProj",  proj);
        setUniformMatrix(shadowProg, "uView",  view);
        float[] shadowModel = identity();
        shadowModel[12] = 0.05f;  shadowModel[13] = -0.12f;  shadowModel[14] = CARD_Z_REST - 0.3f;
        float shadowScale = 1f + (CARD_Z_REST - cardZ) * 0.15f;
        shadowModel[0] *= shadowScale * 1.15f;
        shadowModel[5] *= shadowScale * 1.1f;
        setUniformMatrix(shadowProg, "uModel", shadowModel);
        setUniform1f(shadowProg, "uAlpha", 0.45f + (CARD_Z_REST - cardZ) * 0.12f);
        glBindVertexArray(vaoCard);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
        glDepthMask(true);

        glUseProgram(prog);
        setUniformMatrix(prog, "uProj",  proj);
        setUniformMatrix(prog, "uView",  view);
        setUniformMatrix(prog, "uModel", model);
        setUniform1i(prog, "uTex", 0);
        setUniform2f(prog, "uTilt", tiltX / MAX_TILT, tiltY / MAX_TILT);
        setUniform1f(prog, "uHover", hovered ? 1f : 0f);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texCard);

        glBindVertexArray(vaoCard);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }

    private void buildGeometry() {
        float hw = CARD_W / 2f, hh = CARD_H / 2f;
        float[] verts = {
                -hw, -hh, 0,  0,0,  0,0,1,
                hw, -hh, 0,  1,0,  0,0,1,
                hw,  hh, 0,  1,1,  0,0,1,
                -hw,  hh, 0,  0,1,  0,0,1,
        };
        int[] idx = { 0,1,2, 2,3,0 };

        vaoCard = glGenVertexArrays();
        glBindVertexArray(vaoCard);

        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, verts, GL_STATIC_DRAW);

        int ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STATIC_DRAW);

        int stride = 8 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);
    }

    private void buildShaders() {
        String vert = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec2 aUV;
            layout(location=2) in vec3 aNorm;

            uniform mat4 uModel, uView, uProj;

            out vec2 vUV;
            out vec3 vNormW;
            out vec3 vPosW;

            void main(){
                vec4 worldPos = uModel * vec4(aPos, 1.0);
                vPosW  = worldPos.xyz;
                vNormW = mat3(uModel) * aNorm;
                vUV    = aUV;
                gl_Position = uProj * uView * worldPos;
            }
            """;

        String frag = """
            #version 330 core
            in vec2 vUV;
            in vec3 vNormW;
            in vec3 vPosW;

            uniform sampler2D uTex;
            uniform vec2  uTilt;
            uniform float uHover;

            out vec4 fragColor;

            float roundedRect(vec2 uv, float r){
                vec2 q = abs(uv - 0.5) - (0.5 - r);
                float d = length(max(q, 0.0)) - r;
                return 1.0 - smoothstep(-0.005, 0.005, d);
            }

            vec3 holo(vec2 uv, vec2 tilt){
                vec2 shifted = uv + tilt * 0.35;
                float bands  = shifted.x * 6.0 + shifted.y * 3.0;
                float h      = fract(bands);
                vec3 c = clamp(abs(mod(h*6.0+vec3(0,4,2),6.0)-3.0)-1.0, 0.0, 1.0);
                return c;
            }

            float specular(vec2 uv, vec2 tilt){
                vec2 lightUV  = vec2(0.5) + tilt * 0.6;
                float dist    = length(uv - lightUV);
                return pow(max(1.0 - dist * 2.2, 0.0), 3.5);
            }

            float fresnel(vec2 uv){
                vec2 edgeDist = min(uv, 1.0 - uv);
                float e = 1.0 - smoothstep(0.0, 0.18, min(edgeDist.x, edgeDist.y));
                return e;
            }

            void main(){
                vec4 base = texture(uTex, vec2(vUV.x, 1.0 - vUV.y));

                float mask = roundedRect(vUV, 0.045);

                vec3 rainbow = holo(vUV, uTilt);
                float holoStr = 0.28 + uHover * 0.12;
                float tiltMag = length(uTilt);

                float spec = specular(vUV, uTilt) * (0.6 + uHover * 0.4);

                float fres = fresnel(vUV) * (0.3 + tiltMag * 0.5);
                vec3 fresnelCol = mix(vec3(0.6,0.8,1.0), rainbow, 0.5);

                vec3 col = base.rgb;
                col = mix(col, col * 1.18, tiltMag * 0.4);
                col += rainbow * holoStr * tiltMag;
                col += vec3(1.0) * spec * 0.9;
                col += fresnelCol * fres * 0.5;

                float vig = 1.0 - smoothstep(0.35, 0.75, length(vUV - 0.5));
                col *= 0.85 + vig * 0.15;

                fragColor = vec4(col, base.a * mask);
            }
            """;

        prog = createProgram(vert, frag);

        String shadowVert = """
            #version 330 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec2 aUV;

            uniform mat4 uModel, uView, uProj;
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

            float shadowShape(vec2 uv){
                vec2 q = abs(uv - 0.5) - vec2(0.44, 0.44);
                float d = length(max(q, 0.0));
                return 1.0 - smoothstep(0.0, 0.12, d);
            }

            void main(){
                float a = shadowShape(vUV) * uAlpha;
                fragColor = vec4(0.0, 0.0, 0.0, a);
            }
            """;

        shadowProg = createProgram(shadowVert, shadowFrag);
    }

    private int createProgram(String vertSrc, String fragSrc) {
        int v = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(v, vertSrc);
        glCompileShader(v);
        checkShader(v, "VERTEX");

        int f = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(f, fragSrc);
        glCompileShader(f);
        checkShader(f, "FRAGMENT");

        int p = glCreateProgram();
        glAttachShader(p, v);
        glAttachShader(p, f);
        glLinkProgram(p);
        if (glGetProgrami(p, GL_LINK_STATUS) == GL_FALSE)
            throw new RuntimeException("Link error:\n" + glGetProgramInfoLog(p));

        glDeleteShader(v);
        glDeleteShader(f);
        return p;
    }

    private void checkShader(int id, String type) {
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException(type + " shader error:\n" + glGetShaderInfoLog(id));
    }

    private int loadTexture(String path) {
        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        try (MemoryStack stack = stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), ch = stack.mallocInt(1);

            org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load(false);
            ByteBuffer data = org.lwjgl.stb.STBImage.stbi_load(path, w, h, ch, 4);
            if (data == null)
                throw new RuntimeException("Could not load texture: " + path
                        + "\n" + org.lwjgl.stb.STBImage.stbi_failure_reason()
                        + "\nMake sure pokemon_card.jpg is in the working directory.");

            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w.get(0), h.get(0),
                    0, GL_RGBA, GL_UNSIGNED_BYTE, data);
            glGenerateMipmap(GL_TEXTURE_2D);
            org.lwjgl.stb.STBImage.stbi_image_free(data);
        }
        return texId;
    }

    private void setUniformMatrix(int prog, String name, float[] m) {
        int loc = glGetUniformLocation(prog, name);
        if (loc < 0) return;
        FloatBuffer buf = MemoryUtil.memAllocFloat(16);
        buf.put(m).flip();
        glUniformMatrix4fv(loc, false, buf);
        MemoryUtil.memFree(buf);
    }

    private void setUniform1i(int prog, String name, int v) {
        int loc = glGetUniformLocation(prog, name);
        if (loc >= 0) glUniform1i(loc, v);
    }

    private void setUniform1f(int prog, String name, float v) {
        int loc = glGetUniformLocation(prog, name);
        if (loc >= 0) glUniform1f(loc, v);
    }

    private void setUniform2f(int prog, String name, float x, float y) {
        int loc = glGetUniformLocation(prog, name);
        if (loc >= 0) glUniform2f(loc, x, y);
    }

    private static float[] identity() {
        return new float[]{ 1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1 };
    }

    private static float[] mul4(float[] a, float[] b) {
        float[] r = new float[16];
        for (int col = 0; col < 4; col++)
            for (int row = 0; row < 4; row++)
                for (int k = 0; k < 4; k++)
                    r[col*4+row] += a[k*4+row] * b[col*4+k];
        return r;
    }

    private void cleanup() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }
}