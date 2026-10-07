/*
 * MIT License
 *
 * Copyright (c) 2025-2026 Donny Yang
 *
 * Adapted from https://github.com/QmDeve/AndroidLiquidGlassView.
 * This host-side surface deliberately has no AndroidX dependency.  It keeps
 * the visual fallback in the view itself so a vendor that rejects AGSL never
 * makes the controls disappear.
 */
package top.rongshangs.lumacurve.refactor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * A reusable rounded liquid-glass surface for cards and panels.
 *
 * The surface applies the upstream AGSL refraction shader to its own rendered
 * content.  This is intentionally scoped to the surface's layer: it avoids
 * recursively recording the whole activity for every card, which is the main
 * source of frame drops when many glass panels are on screen.  The parent
 * background remains visible through the translucent base, while text and
 * controls receive the same refraction/dispersion treatment.  On Android 12
 * and below, or on devices whose renderer rejects the shader, the same shape
 * falls back to a light themed fill and two subtle highlight strokes.
 */
public final class GlassSurface extends LinearLayout {
    private static final String SHADER_ASSET = "liquidglass_effect.agsl";
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private RuntimeShader shader;
    private RenderEffect effect;
    private float radius;
    private float refractionHeight;
    private float refractionAmount;
    private float dispersion;
    private float tintAlpha;
    private float tintRed = 1f;
    private float tintGreen = 1f;
    private float tintBlue = 1f;
    private int surfaceColor = Color.WHITE;
    private boolean dark;
    private boolean effectUnavailable;

    public GlassSurface(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setWillNotDraw(false);
        setClipChildren(true);
        setClipToPadding(true);
        float density = getResources().getDisplayMetrics().density;
        radius = 18f * density;
        refractionHeight = 16f * density;
        refractionAmount = -26f * density;
        dispersion = .16f;
        setDarkMode(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                shader = new RuntimeShader(loadShader());
                // RuntimeShader/RenderEffect need a hardware layer. Keep the
                // fallback on the normal rendering path on older or rejected
                // vendor renderers so hidden cards do not allocate GPU layers.
                setLayerType(View.LAYER_TYPE_HARDWARE, null);
            } catch (RuntimeException unavailable) {
                effectUnavailable = true;
                setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            }
        } else {
            effectUnavailable = true;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        updateOutline();
    }

    public void setDarkMode(boolean value) {
        dark = value;
        tintRed = dark ? 0.08f : 1f;
        tintGreen = dark ? 0.09f : 1f;
        tintBlue = dark ? 0.11f : 1f;
        tintAlpha = dark ? .18f : .10f;
        surfaceColor = dark ? 0xff1b2028 : 0xffffffff;
        updateEffect();
        invalidate();
    }

    /** Set the source color while preserving the theme-aware alpha. */
    public void setGlassColor(int color) {
        surfaceColor = color;
        int alpha = Color.alpha(color);
        // Opaque source colors are material tints, not opaque panels. This
        // keeps a user-selected image visible through the glass layer.
        if (alpha == 0 || alpha == 255) alpha = dark ? 0x98 : 0xc4;
        surfaceColor = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        invalidate();
    }

    public void setCornerRadius(float pixels) {
        radius = Math.max(0f, pixels);
        updateOutline();
        updateEffect();
        invalidate();
    }

    public void setRefraction(float height, float amount, float chromaticDispersion) {
        refractionHeight = Math.max(8f, height);
        refractionAmount = -Math.abs(amount);
        dispersion = Math.max(0f, Math.min(1f, chromaticDispersion));
        updateEffect();
    }

    public boolean hasRuntimeEffect() {
        return shader != null && effect != null && !effectUnavailable;
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateOutline();
        updateEffect();
    }

    @Override protected void onDraw(Canvas canvas) {
        clipPath.reset();
        clipPath.addRoundRect(0f, 0f, getWidth(), getHeight(), radius, radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clipPath);

        int alpha = Color.alpha(surfaceColor);
        if (alpha == 0) alpha = dark ? 0x98 : 0xc4;
        int r = Color.red(surfaceColor), g = Color.green(surfaceColor), b = Color.blue(surfaceColor);
        int topAlpha = Math.min(255, alpha + (dark ? 14 : 20));
        int bottomAlpha = Math.max(0, alpha - (dark ? 12 : 20));
        fillPaint.setShader(new LinearGradient(0f, 0f, 0f, Math.max(1, getHeight()),
                Color.argb(topAlpha, r, g, b), Color.argb(bottomAlpha, r, g, b),
                Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), fillPaint);
        fillPaint.setShader(null);

        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(getResources().getDisplayMetrics().density);
        edgePaint.setColor(dark ? 0x38ffffff : 0x64ffffff);
        canvas.drawRoundRect(.5f, .5f, Math.max(.5f, getWidth() - .5f),
                Math.max(.5f, getHeight() - .5f), radius, radius, edgePaint);
        edgePaint.setColor(dark ? 0x30000000 : 0x18000000);
        canvas.drawRoundRect(1.5f, 1.5f, Math.max(1.5f, getWidth() - 1.5f),
                Math.max(1.5f, getHeight() - 1.5f), Math.max(0f, radius - 1f), radius - 1f, edgePaint);
        canvas.restoreToCount(save);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(clipPath);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    private void updateOutline() {
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
        invalidateOutline();
        clipPath.reset();
        clipPath.addRoundRect(0f, 0f, getWidth(), getHeight(), radius, radius, Path.Direction.CW);
    }

    private void updateEffect() {
        if (shader == null || effectUnavailable || getWidth() <= 0 || getHeight() <= 0) return;
        try {
            shader.setFloatUniform("size", new float[]{getWidth(), getHeight()});
            shader.setFloatUniform("offset", new float[]{0f, 0f});
            shader.setFloatUniform("cornerRadii", new float[]{radius, radius, radius, radius});
            shader.setFloatUniform("refractionHeight", refractionHeight);
            shader.setFloatUniform("refractionAmount", refractionAmount);
            shader.setFloatUniform("depthEffect", .22f);
            shader.setFloatUniform("chromaticAberration", dispersion);
            shader.setFloatUniform("contrast", dark ? .04f : .02f);
            shader.setFloatUniform("whitePoint", 0f);
            shader.setFloatUniform("chromaMultiplier", 1.02f);
            shader.setFloatUniform("tintColor", new float[]{tintRed, tintGreen, tintBlue});
            shader.setFloatUniform("tintAlpha", tintAlpha);
            effect = RenderEffect.createRuntimeShaderEffect(shader, "content");
            setRenderEffect(effect);
        } catch (RuntimeException rejected) {
            effectUnavailable = true;
            effect = null;
            setRenderEffect(null);
        }
    }

    private String loadShader() {
        StringBuilder code = new StringBuilder();
        try (InputStream input = getContext().getAssets().open(SHADER_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) code.append(line).append('\n');
        } catch (IOException missing) {
            throw new IllegalStateException("Missing liquid glass shader asset", missing);
        }
        return code.toString();
    }
}
