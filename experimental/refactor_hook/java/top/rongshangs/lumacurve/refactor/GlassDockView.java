/*
 * MIT License
 *
 * Copyright (c) 2025-2026 Donny Yang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Adapted from https://github.com/QmDeve/AndroidLiquidGlassView.
 * This small host-side adapter intentionally has no AndroidX dependency.
 */
package top.rongshangs.lumacurve.refactor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * A compact liquid-glass surface for the bottom dock.
 *
 * The source is recorded directly into a RenderNode, so the glass does not
 * allocate a bitmap on every frame. RuntimeShader is available from Android 13;
 * older devices stay transparent while the dock's child controls remain usable.
 */
public final class GlassDockView extends ViewGroup {
    private static final String SHADER_ASSET = "liquidglass_effect.agsl";
    private final RenderNode sourceNode = new RenderNode("LumaCurveGlassDockSource");
    private final Path clipPath = new Path();
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] sourceLocation = new int[2];
    private final int[] hostLocation = new int[2];
    private final ViewTreeObserver.OnPreDrawListener preDrawListener =
            new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    recordSource();
                    return true;
                }
            };

    private ViewGroup source;
    private RuntimeShader shader;
    private RenderEffect cachedBlur;
    private float cornerRadius;
    private float refractionHeight;
    private float refractionOffset;
    private float dispersion;
    private float blurRadius;
    private float tintAlpha;
    private float tintRed = 1f;
    private float tintGreen = 1f;
    private float tintBlue = 1f;
    private int fallbackColor;
    private float lastBlur = Float.NaN;
    private long lastBlurUpdate;
    private boolean listenerAdded;
    private boolean disposed;
    private boolean effectUnavailable;
    private float downX, downY;
    private boolean swiping;
    private int touchSlop;
    private SwipeListener swipeListener;

    public interface SwipeListener {
        /** Called with -1 for the previous page and +1 for the next page. */
        void onSwipe(int direction);
    }

    public GlassDockView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        float density = getResources().getDisplayMetrics().density;
        cornerRadius = 32f * density;
        refractionHeight = 20f * density;
        refractionOffset = -52f * density;
        dispersion = .34f;
        blurRadius = 8f;
        setDarkMode(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                shader = new RuntimeShader(loadShader());
                setLayerType(LAYER_TYPE_HARDWARE, null);
            } catch (RuntimeException ignored) {
                // Vendor AGSL implementations can reject optional shader features.
                // Keep the dock usable and transparent when that happens.
                shader = null;
                effectUnavailable = true;
                setLayerType(LAYER_TYPE_SOFTWARE, null);
            }
        } else {
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }
        updateOutline();
    }

    /** Bind a sibling content surface to be sampled by the glass effect. */
    public void bind(ViewGroup content) {
        if (source == content) return;
        removeListener();
        source = content;
        addListener();
        invalidate();
    }

    public void setSwipeListener(SwipeListener listener) {
        swipeListener = listener;
    }

    /** Apply the platform night mode without changing child layout. */
    public void setDarkMode(boolean dark) {
        setTintColor(dark ? 0xff111318 : 0xffffffff);
        tintAlpha = dark ? .28f : .34f;
        fallbackColor = dark ? 0x45111318 : 0x32ffffff;
        updateEffect();
    }

    public void setCornerRadius(float px) {
        cornerRadius = Math.max(0f, Math.min(px, getHeight() > 0 ? getHeight() / 2f : px));
        updateOutline();
        updateEffect();
    }

    public void setRefractionHeight(float px) {
        refractionHeight = Math.max(12f, Math.min(50f, px));
        updateEffect();
    }

    public void setRefractionOffset(float px) {
        float magnitude = Math.max(20f, Math.min(120f, Math.abs(px)));
        refractionOffset = -magnitude;
        updateEffect();
    }

    public void setDispersion(float value) {
        dispersion = Math.max(0f, Math.min(1f, value));
        updateEffect();
    }

    public void setBlurRadius(float value) {
        blurRadius = Math.max(0f, Math.min(50f, value));
        updateEffect();
    }

    public void setTintColor(int color) {
        tintRed = Color.red(color) / 255f;
        tintGreen = Color.green(color) / 255f;
        tintBlue = Color.blue(color) / 255f;
        updateEffect();
    }

    public void setTintAlpha(float alpha) {
        tintAlpha = Math.max(0f, Math.min(1f, alpha));
        updateEffect();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        disposed = false;
        addListener();
    }

    @Override protected void onDetachedFromWindow() {
        disposed = true;
        removeListener();
        sourceNode.setRenderEffect(null);
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateOutline();
        sourceNode.setPosition(0, 0, Math.max(1, w), Math.max(1, h));
        updateEffect();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        int childWidth = MeasureSpec.makeMeasureSpec(Math.max(0, width - getPaddingLeft() - getPaddingRight()), MeasureSpec.EXACTLY);
        int childHeight = MeasureSpec.makeMeasureSpec(Math.max(0, height - getPaddingTop() - getPaddingBottom()), MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) child.measure(childWidth, childHeight);
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int l = getPaddingLeft(), t = getPaddingTop();
        int r = right - left - getPaddingRight(), b = bottom - top - getPaddingBottom();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) child.layout(l, t, r, b);
        }
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        int save = canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        // Always paint a themed translucent base.  RuntimeShader is an optional
        // enhancement: shader compilation can fail on vendor implementations or
        // the view can be software-rendered, and leaving the base conditional
        // makes the complete dock disappear in those cases.
        fallbackPaint.setColor(fallbackColor);
        canvas.drawRect(0, 0, getWidth(), getHeight(), fallbackPaint);
        if (shader != null && !effectUnavailable && canvas.isHardwareAccelerated() && source != null && !disposed) {
            canvas.drawRenderNode(sourceNode);
        }
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                swiping = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!swiping && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    swiping = true;
                    return true;
                }
                return swiping;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return swiping;
            default:
                return swiping;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP && swiping) {
            float dx = event.getX() - downX;
            if (swipeListener != null && Math.abs(dx) > touchSlop * 2f) {
                swipeListener.onSwipe(dx < 0f ? 1 : -1);
            }
            swiping = false;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) swiping = false;
        return true;
    }

    private void addListener() {
        if (source != null && !listenerAdded) {
            source.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
            listenerAdded = true;
        }
    }

    private void removeListener() {
        if (source != null && listenerAdded) {
            source.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
            listenerAdded = false;
        }
    }

    private void recordSource() {
        if (shader == null || source == null || disposed || source.getWidth() <= 0 || source.getHeight() <= 0) return;
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        Canvas recording = sourceNode.beginRecording(w, h);
        source.getLocationInWindow(sourceLocation);
        getLocationInWindow(hostLocation);
        recording.translate(sourceLocation[0] - hostLocation[0], sourceLocation[1] - hostLocation[1]);
        source.draw(recording);
        sourceNode.endRecording();
    }

    private void updateEffect() {
        if (shader == null || effectUnavailable || getWidth() <= 0 || getHeight() <= 0) return;
        try {
            shader.setFloatUniform("size", new float[]{getWidth(), getHeight()});
            shader.setFloatUniform("offset", new float[]{0f, 0f});
            shader.setFloatUniform("cornerRadii", new float[]{cornerRadius, cornerRadius, cornerRadius, cornerRadius});
            shader.setFloatUniform("refractionHeight", refractionHeight);
            shader.setFloatUniform("refractionAmount", refractionOffset);
            shader.setFloatUniform("depthEffect", .3f);
            shader.setFloatUniform("chromaticAberration", dispersion);
            shader.setFloatUniform("contrast", 0f);
            shader.setFloatUniform("whitePoint", 0f);
            shader.setFloatUniform("chromaMultiplier", 1f);
            shader.setFloatUniform("tintColor", new float[]{tintRed, tintGreen, tintBlue});
            shader.setFloatUniform("tintAlpha", tintAlpha);
            RenderEffect effect = null;
            if (blurRadius > .01f) {
                long now = System.currentTimeMillis();
                if (cachedBlur == null || Math.abs(blurRadius - lastBlur) > .3f || now - lastBlurUpdate > 120L) {
                    try {
                        cachedBlur = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP);
                        lastBlur = blurRadius;
                        lastBlurUpdate = now;
                    } catch (RuntimeException ignored) {
                        // Some vendor renderers reject blur; the refraction shader still works.
                    }
                }
                effect = cachedBlur;
            }
            RenderEffect shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content");
            sourceNode.setRenderEffect(effect == null ? shaderEffect : RenderEffect.createChainEffect(shaderEffect, effect));
        } catch (RuntimeException ignored) {
            effectUnavailable = true;
            sourceNode.setRenderEffect(null);
        }
    }

    private void updateOutline() {
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        setClipToOutline(true);
        invalidateOutline();
    }

    private String loadShader() {
        StringBuilder code = new StringBuilder();
        try (InputStream input = getContext().getAssets().open(SHADER_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) code.append(line).append('\n');
        } catch (IOException error) {
            throw new IllegalStateException("Missing liquid glass shader asset", error);
        }
        return code.toString();
    }
}
