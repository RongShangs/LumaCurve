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
 * The backdrop shader is adapted from https://github.com/QmDeve/AndroidLiquidGlassView.
 * The 4dp frost/24dp lens tuning and selected-tab capsule follow the visual
 * treatment in https://github.com/liuran001/WeChat-LiquidGlass (MIT, copyright
 * liuran001). Its WeChat/QQ Xposed bridge is not part of this app; this class
 * is a host-side adapter with no AndroidX dependency.
 */
package top.rongshangs.lumacurve.refactor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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
    /** KernelSU/WeChat LiquidGlassPanel tuning: 4dp frost + a 24dp lens rim. */
    private static final float WECHAT_BLUR_DP = 4f;
    private static final float WECHAT_REFRACTION_DP = 24f;
    private static final float WECHAT_SATURATION = 1.5f;
    private final RenderNode sourceNode = new RenderNode("LumaCurveGlassDockSource");
    private final Path clipPath = new Path();
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF selectedRect = new RectF();
    private final RectF fromRect = new RectF();
    private final RectF toRect = new RectF();
    private final int[] sourceLocation = new int[2];
    private final int[] hostLocation = new int[2];
    private final ViewTreeObserver.OnPreDrawListener preDrawListener =
            new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    if (captureDirty) recordSource();
                    return true;
                }
            };
    private final ViewTreeObserver.OnDrawListener sourceDrawListener =
            new ViewTreeObserver.OnDrawListener() {
                @Override public void onDraw() {
                    captureDirty = true;
                }
            };

    private ViewGroup source;
    private RuntimeShader shader;
    private RenderEffect cachedBlur;
    private RenderEffect saturationEffect;
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
    private boolean drawListenerAdded;
    private boolean captureDirty = true;
    private boolean disposed;
    private boolean effectUnavailable;
    private boolean captureUnavailable;
    private float downX, downY;
    private boolean swiping;
    private int touchSlop;
    private SwipeListener swipeListener;
    private final float density;
    private int selectedIndex = -1;
    private int animatedFrom = -1;
    private int animatedTo = -1;
    private long selectionAnimationStart;
    private static final long SELECTION_ANIMATION_MS = 260L;
    private boolean ancestorsBlocked;

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
        density = getResources().getDisplayMetrics().density;
        cornerRadius = 32f * density;
        refractionHeight = WECHAT_REFRACTION_DP * density;
        refractionOffset = -WECHAT_REFRACTION_DP * density;
        dispersion = .34f;
        blurRadius = WECHAT_BLUR_DP * density;
        selectionPaint.setStyle(Paint.Style.FILL);
        selectionEdgePaint.setStyle(Paint.Style.STROKE);
        selectionEdgePaint.setStrokeWidth(Math.max(0.75f, density));
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
        captureDirty = true;
        captureUnavailable = false;
        addListener();
        invalidate();
    }

    public void setSwipeListener(SwipeListener listener) {
        swipeListener = listener;
    }

    /** Apply the platform night mode without changing child layout. */
    public void setDarkMode(boolean dark) {
        setTintColor(dark ? 0xff111318 : 0xffffffff);
        tintAlpha = dark ? .30f : .34f;
        fallbackColor = dark ? 0x5a111318 : 0x42ffffff;
        selectionPaint.setColor(dark ? 0x3dffffff : 0x66ffffff);
        selectionEdgePaint.setColor(dark ? 0x38ffffff : 0x80ffffff);
        updateEffect();
        invalidate();
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
        captureUnavailable = false;
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
        int desiredWidth = Math.max(getSuggestedMinimumWidth(), Math.round(280f * density));
        int desiredHeight = Math.max(getSuggestedMinimumHeight(), Math.round(70f * density));
        int width = resolveSize(desiredWidth, widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
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
        drawSelection(canvas);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    /**
     * Draw the WeChat/KernelSU-style selected capsule below the real tab
     * controls. Keeping this as one parent layer means the tab icons and labels
     * remain native, while the moving material has no per-tab RenderEffect.
     */
    private void drawSelection(Canvas canvas) {
        ViewGroup tabRow = getChildCount() == 0 ? null : asGroup(getChildAt(0));
        if (tabRow == null || tabRow.getChildCount() == 0) return;
        int target = findSelectedTab(tabRow);
        if (target < 0) target = selectedIndex >= 0 ? selectedIndex : 0;
        if (target != selectedIndex) {
            if (selectedIndex >= 0) {
                findTabRect(tabRow, selectedIndex, fromRect);
            } else {
                findTabRect(tabRow, target, fromRect);
            }
            findTabRect(tabRow, target, toRect);
            animatedFrom = selectedIndex >= 0 ? selectedIndex : target;
            animatedTo = target;
            selectedIndex = target;
            selectionAnimationStart = android.os.SystemClock.uptimeMillis();
        } else if (animatedTo < 0) {
            findTabRect(tabRow, target, toRect);
            fromRect.set(toRect);
            animatedFrom = target;
            animatedTo = target;
        }
        if (animatedFrom >= 0 && animatedTo >= 0) {
            findTabRect(tabRow, animatedFrom, fromRect);
            findTabRect(tabRow, animatedTo, toRect);
            long elapsed = android.os.SystemClock.uptimeMillis() - selectionAnimationStart;
            float progress = Math.max(0f, Math.min(1f, elapsed / (float) SELECTION_ANIMATION_MS));
            // Decelerate interpolation matches the droplet settling curve
            // without keeping a ValueAnimator alive after the tab is settled.
            float eased = 1f - (1f - progress) * (1f - progress);
            selectedRect.left = fromRect.left + (toRect.left - fromRect.left) * eased;
            selectedRect.top = fromRect.top + (toRect.top - fromRect.top) * eased;
            selectedRect.right = fromRect.right + (toRect.right - fromRect.right) * eased;
            selectedRect.bottom = fromRect.bottom + (toRect.bottom - fromRect.bottom) * eased;
            if (progress < 1f) postInvalidateOnAnimation();
        } else {
            selectedRect.set(toRect);
        }
        float inset = Math.max(density * 4f, 1f);
        selectedRect.inset(inset, inset);
        float radius = Math.min(selectedRect.height() * .5f, density * 22f);
        selectionPaint.setShader(new LinearGradient(0f, selectedRect.top, 0f, selectedRect.bottom,
                selectionPaint.getColor() | 0x18000000, selectionPaint.getColor(), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(selectedRect, radius, radius, selectionPaint);
        selectionPaint.setShader(null);
        float half = selectionEdgePaint.getStrokeWidth() * .5f;
        canvas.drawRoundRect(selectedRect.left + half, selectedRect.top + half,
                selectedRect.right - half, selectedRect.bottom - half,
                Math.max(0f, radius - half), Math.max(0f, radius - half), selectionEdgePaint);
    }

    private ViewGroup asGroup(View child) {
        return child instanceof ViewGroup ? (ViewGroup) child : null;
    }

    private int findSelectedTab(ViewGroup row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && child.isSelected()) return i;
        }
        return -1;
    }

    private boolean findTabRect(ViewGroup row, int index, RectF out) {
        if (index < 0 || index >= row.getChildCount()) return false;
        View tab = row.getChildAt(index);
        out.set(row.getLeft() + tab.getLeft(), row.getTop() + tab.getTop(),
                row.getLeft() + tab.getRight(), row.getTop() + tab.getBottom());
        return out.width() > 0f && out.height() > 0f;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        protectAncestors(event);
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
        protectAncestors(event);
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

    /** Keep a parent pager from stealing a horizontal dock swipe. */
    private void protectAncestors(MotionEvent event) {
        android.view.ViewParent viewParent = getParent();
        if (viewParent == null) return;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            ancestorsBlocked = true;
            viewParent.requestDisallowInterceptTouchEvent(true);
        } else if (action == MotionEvent.ACTION_MOVE && ancestorsBlocked) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (Math.abs(dy) > touchSlop * 2f && Math.abs(dy) > Math.abs(dx)) {
                ancestorsBlocked = false;
                viewParent.requestDisallowInterceptTouchEvent(false);
            }
        } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                && ancestorsBlocked) {
            ancestorsBlocked = false;
            viewParent.requestDisallowInterceptTouchEvent(false);
        }
    }

    private void addListener() {
        if (source != null && !listenerAdded) {
            source.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
            listenerAdded = true;
        }
        if (source != null && !drawListenerAdded) {
            source.getViewTreeObserver().addOnDrawListener(sourceDrawListener);
            drawListenerAdded = true;
        }
    }

    private void removeListener() {
        if (source != null && listenerAdded) {
            source.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
            listenerAdded = false;
        }
        if (source != null && drawListenerAdded) {
            source.getViewTreeObserver().removeOnDrawListener(sourceDrawListener);
            drawListenerAdded = false;
        }
    }

    private void recordSource() {
        if (shader == null || source == null || disposed
                || source.getWidth() <= 0 || source.getHeight() <= 0) return;
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        Canvas recording = null;
        try {
            recording = sourceNode.beginRecording(w, h);
            source.getLocationInWindow(sourceLocation);
            getLocationInWindow(hostLocation);
            recording.translate(sourceLocation[0] - hostLocation[0], sourceLocation[1] - hostLocation[1]);
            source.draw(recording);
            captureDirty = false;
            captureUnavailable = false;
        } catch (Throwable ignored) {
            // A few vendor renderers reject recording a ViewGroup while a
            // transition is being committed. Keep the last good frame and use
            // the themed base until the next attachment instead of hiding the
            // whole dock.
            captureUnavailable = true;
        } finally {
            if (recording != null) {
                try {
                    sourceNode.endRecording();
                } catch (Throwable ignored) {
                    captureUnavailable = true;
                }
            }
        }
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
                        if (saturationEffect == null) {
                            ColorMatrix saturation = new ColorMatrix();
                            saturation.setSaturation(WECHAT_SATURATION);
                            saturationEffect = RenderEffect.createColorFilterEffect(
                                    new ColorMatrixColorFilter(saturation));
                        }
                        cachedBlur = RenderEffect.createBlurEffect(blurRadius, blurRadius,
                                saturationEffect, Shader.TileMode.CLAMP);
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
