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
 * The 4dp frost/24dp lens tuning and selected-tab rounded indicator follow the visual
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
import android.graphics.BlendMode;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
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
 * API 31+ uses a cached blur source; API 33+ adds the AGSL lens. Older devices
 * keep a high-contrast themed surface while the dock's child controls remain usable.
 */
public final class GlassDockView extends ViewGroup {
    private static final String SHADER_ASSET = "liquidglass_effect.agsl";
    private static final String INDICATOR_SHADER_ASSET = "liquidglass_indicator.agsl";
    /** iOS 26 Liquid Glass geometry, expressed in dp so it survives density changes. */
    private static final float DOCK_HEIGHT_DP = 64f;
    private static final float DOCK_CORNER_DP = 4f;
    private static final float WECHAT_SATURATION = 1.5f;
    private static final float WECHAT_BLUR_DP = 4f;
    private static final float WECHAT_REFRACTION_DP = 24f;
    private static final float PRESS_PILL_HEIGHT_DP = 10f;
    private static final float PRESS_PILL_AMOUNT_DP = 14f;
    private static final float MAX_PANEL_DRIFT_DP = 4f;
    private static final float PRESSED_SCALE = 1.2f;
    private static final long SPRING_SETTLE_MS = 260L;
    private final RenderNode sourceNode = new RenderNode("LumaCurveGlassDockSource");
    private final RenderNode indicatorNode = new RenderNode("LumaCurveGlassDockIndicator");
    private final Path clipPath = new Path();
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bloomPaintA = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bloomPaintB = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF selectedRect = new RectF();
    private final RectF fromRect = new RectF();
    private final RectF toRect = new RectF();
    private final RectF bloomRect = new RectF();
    private final Matrix bloomMatrixA = new Matrix();
    private final Matrix bloomMatrixB = new Matrix();
    private final int[] sourceLocation = new int[2];
    private final int[] hostLocation = new int[2];
    private final ViewTreeObserver.OnPreDrawListener preDrawListener =
            new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    if (captureDirty || (source != null && source.isDirty())) recordSource();
                    return true;
                }
            };

    private ViewGroup source;
    private RuntimeShader shader;
    private RuntimeShader indicatorShader;
    private RenderEffect cachedBlur;
    private RenderEffect saturationEffect;
    private RenderEffect indicatorEffect;
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
    private boolean captureDirty = true;
    private boolean disposed;
    private boolean effectUnavailable;
    private boolean hasCapture;
    private boolean darkMode;
    private float downX, downY;
    private float lastX;
    private float dragPosition;
    private float velocityPxPerSecond;
    private float panelDrift;
    private boolean swiping;
    private boolean dragging;
    private int pressedTab = -1;
    private float pressProgress;
    private float pressAnimationFrom;
    private long pressAnimationStart;
    private boolean pressAnimationTarget;
    private int pageCount = 4;
    private int touchSlop;
    private SwipeListener swipeListener;
    private DragListener dragListener;
    private final float density;
    private int selectedIndex = -1;
    private int animatedFrom = -1;
    private int animatedTo = -1;
    private long selectionAnimationStart;
    private boolean ancestorsBlocked;

    public interface SwipeListener {
        /** Called with -1 for the previous page and +1 for the next page. */
        void onSwipe(int direction);
    }

    /** Continuous drag updates used by the liquid indicator before page commit. */
    public interface DragListener {
        void onDragPosition(float position);
        void onDragFinished(int targetIndex);
    }

    public GlassDockView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        density = getResources().getDisplayMetrics().density;
        cornerRadius = DOCK_CORNER_DP * density;
        refractionHeight = WECHAT_REFRACTION_DP * density;
        refractionOffset = -WECHAT_REFRACTION_DP * density;
        dispersion = 0f;
        blurRadius = WECHAT_BLUR_DP * density;
        selectionPaint.setStyle(Paint.Style.FILL);
        selectionEdgePaint.setStyle(Paint.Style.STROKE);
        selectionEdgePaint.setStrokeWidth(Math.max(0.75f, density));
        bloomPaintA.setStyle(Paint.Style.STROKE);
        bloomPaintB.setStyle(Paint.Style.STROKE);
        bloomPaintA.setStrokeWidth(density);
        bloomPaintB.setStrokeWidth(density);
        bloomPaintA.setBlendMode(BlendMode.PLUS);
        bloomPaintB.setBlendMode(BlendMode.PLUS);
        bloomPaintB.setAlpha(102);
        shadowPaint.setColor(0x1a000000);
        shadowPaint.setShadowLayer(24f * density, 0f, 4f * density, 0x1a000000);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setDarkMode(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                shader = new RuntimeShader(loadShader());
            } catch (RuntimeException ignored) {
                // Vendor AGSL implementations can reject optional shader features.
                // Keep the dock usable and fall back to the API 31 blur chain.
                shader = null;
            }
            try {
                indicatorShader = new RuntimeShader(loadShader(INDICATOR_SHADER_ASSET));
            } catch (RuntimeException ignored) {
                indicatorShader = null;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 can run the cached blur/saturation chain, but has no
            // RuntimeShader lens. Keep the layer hardware accelerated.
            setLayerType(LAYER_TYPE_HARDWARE, null);
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
        hasCapture = false;
        addListener();
        invalidate();
    }

    public void setSwipeListener(SwipeListener listener) {
        swipeListener = listener;
    }

    public void setDragListener(DragListener listener) {
        dragListener = listener;
    }

    public void setPageCount(int count) {
        pageCount = Math.max(1, count);
    }

    /** Sync the visual indicator after a tab click or an external page change. */
    public void setSelectedIndex(int index) {
        int target = Math.max(0, Math.min(pageCount - 1, index));
        if (dragging) {
            dragPosition = target;
            return;
        }
        if (selectedIndex == target && animatedTo >= 0) return;
        animatedFrom = selectedIndex < 0 ? target : selectedIndex;
        animatedTo = target;
        selectedIndex = target;
        selectionAnimationStart = android.os.SystemClock.uptimeMillis();
        dragPosition = target;
        invalidate();
    }

    /** Apply the platform night mode without changing child layout. */
    public void setDarkMode(boolean dark) {
        darkMode = dark;
        setTintColor(dark ? 0xff121212 : 0xfffafafa);
        tintAlpha = .40f;
        int fallbackAlpha = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ? 0xcc
                : Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ? 0x8c : 0x66;
        fallbackColor = (fallbackAlpha << 24) | (dark ? 0x121212 : 0xfafafa);
        selectionPaint.setColor(dark ? 0x1affffff : 0x1a000000);
        selectionEdgePaint.setColor(0x00ffffff);
        updateBloomShaders(darkMode);
        shadowPaint.setShadowLayer(24f * density, 0f, 4f * density, dark ? 0x33000000 : 0x1a000000);
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
        addListener();
        updateEffect();
    }

    @Override protected void onDetachedFromWindow() {
        disposed = true;
        removeListener();
        sourceNode.setRenderEffect(null);
        indicatorNode.setRenderEffect(null);
        indicatorEffect = null;
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateOutline();
        int pad = capturePadding();
        sourceNode.setPosition(-pad, -pad, Math.max(1, w + pad), Math.max(1, h + pad));
        indicatorNode.setPosition(0, 0, Math.max(1, w), Math.max(1, h));
        recordIndicatorSource();
        updateBloomShaders(darkMode);
        updateEffect();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.max(getSuggestedMinimumWidth(), Math.round(280f * density));
        int desiredHeight = Math.max(getSuggestedMinimumHeight(), Math.round(DOCK_HEIGHT_DP * density));
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
        updatePressAnimation();
        int save = canvas.save();
        if (Math.abs(panelDrift) > .01f) canvas.translate(panelDrift, 0f);
        // The shadow is painted before the capsule clip so it can fall outside
        // the material without creating a rectangular edge around the Dock.
        canvas.drawRoundRect(0f, 0f, getWidth(), getHeight(), cornerRadius, cornerRadius, shadowPaint);
        clipPath.reset();
        clipPath.addRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        // Always paint a themed translucent base.  RuntimeShader is an optional
        // enhancement: shader compilation can fail on vendor implementations or
        // the view can be software-rendered, and leaving the base conditional
        // makes the complete dock disappear in those cases.
        fallbackPaint.setColor(fallbackColor);
        canvas.drawRect(0, 0, getWidth(), getHeight(), fallbackPaint);
        boolean drawCapture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !effectUnavailable
                && canvas.isHardwareAccelerated() && source != null && !disposed && hasCapture;
        if (drawCapture) {
            canvas.drawRenderNode(sourceNode);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                fallbackPaint.setColor(fallbackColor);
                canvas.drawRect(0, 0, getWidth(), getHeight(), fallbackPaint);
            }
        }
        drawBloom(canvas);
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
        updatePressAnimation();
        ViewGroup tabRow = getChildCount() == 0 ? null : asGroup(getChildAt(0));
        if (tabRow == null || tabRow.getChildCount() == 0) return;
        if (selectedIndex < 0) {
            selectedIndex = Math.max(0, findSelectedTab(tabRow));
            if (selectedIndex < 0) selectedIndex = 0;
            dragPosition = selectedIndex;
            animatedFrom = selectedIndex;
            animatedTo = selectedIndex;
        }
        if (!dragging && animatedFrom >= 0 && animatedTo >= 0) {
            findTabRect(tabRow, animatedFrom, fromRect);
            findTabRect(tabRow, animatedTo, toRect);
            long elapsed = android.os.SystemClock.uptimeMillis() - selectionAnimationStart;
            float progress = selectionAnimationStart == 0L ? 1f
                    : Math.max(0f, Math.min(1f, elapsed / (float) SPRING_SETTLE_MS));
            float eased = criticalSpring(progress);
            selectedRect.left = fromRect.left + (toRect.left - fromRect.left) * eased;
            selectedRect.top = fromRect.top + (toRect.top - fromRect.top) * eased;
            selectedRect.right = fromRect.right + (toRect.right - fromRect.right) * eased;
            selectedRect.bottom = fromRect.bottom + (toRect.bottom - fromRect.bottom) * eased;
            dragPosition = animatedFrom + (animatedTo - animatedFrom) * eased;
            if (progress < 1f) postInvalidateOnAnimation();
        } else {
            setInterpolatedTabRect(tabRow, dragPosition, selectedRect);
        }
        float press = pressProgress;
        float velocity = Math.max(-0.2f, Math.min(0.2f, velocityPxPerSecond / 10f));
        float scaleX = 1f / (1f - velocity * .75f);
        float scaleY = 1f - velocity * .25f;
        float centerX = selectedRect.centerX();
        float centerY = selectedRect.centerY();
        selectedRect.set(centerX - selectedRect.width() * scaleX * .5f,
                centerY - selectedRect.height() * scaleY * .5f,
                centerX + selectedRect.width() * scaleX * .5f,
                centerY + selectedRect.height() * scaleY * .5f);
        float inset = 2f * density;
        selectedRect.inset(inset, inset);
        float radius = DOCK_CORNER_DP * density;
        drawIndicatorLens(canvas);
        int base = selectionPaint.getColor();
        int alpha = Math.max(0, Math.min(255, Math.round((1f - press) * 26f)));
        selectionPaint.setColor((base & 0x00ffffff) | (alpha << 24));
        canvas.drawRoundRect(selectedRect, radius, radius, selectionPaint);
        if (press > .001f) {
            selectionPaint.setColor(0x08000000);
            canvas.drawRoundRect(selectedRect, radius, radius, selectionPaint);
        }
        selectionPaint.setColor(base);
        int edge = selectionEdgePaint.getColor();
        if (press > .001f) {
            int edgeAlpha = Math.min(255, Math.round(128f * press));
            selectionEdgePaint.setColor((edge & 0x00ffffff) | (edgeAlpha << 24));
        }
        float half = selectionEdgePaint.getStrokeWidth() * .5f;
        canvas.drawRoundRect(selectedRect.left + half, selectedRect.top + half,
                selectedRect.right - half, selectedRect.bottom - half,
                Math.max(0f, radius - half), Math.max(0f, radius - half), selectionEdgePaint);
        selectionEdgePaint.setColor(edge);
        if (press > .001f) {
            Paint inner = selectionEdgePaint;
            inner.setStyle(Paint.Style.STROKE);
            inner.setStrokeWidth(Math.max(.5f * density, 8f * density * press));
            inner.setColor(Math.round(38f * press) << 24);
            canvas.drawRoundRect(selectedRect, radius, radius, inner);
            inner.setStrokeWidth(Math.max(0.75f, density));
            inner.setColor(edge);
        }
    }

    private void setInterpolatedTabRect(ViewGroup row, float position, RectF out) {
        int count = row.getChildCount();
        if (count == 0) {
            out.set(0f, 0f, getWidth(), getHeight());
            return;
        }
        float bounded = Math.max(0f, Math.min(count - 1f, position));
        int lower = (int) Math.floor(bounded);
        int upper = Math.min(count - 1, lower + 1);
        float fraction = bounded - lower;
        findTabRect(row, lower, fromRect);
        findTabRect(row, upper, toRect);
        out.left = fromRect.left + (toRect.left - fromRect.left) * fraction;
        out.top = fromRect.top + (toRect.top - fromRect.top) * fraction;
        out.right = fromRect.right + (toRect.right - fromRect.right) * fraction;
        out.bottom = fromRect.bottom + (toRect.bottom - fromRect.bottom) * fraction;
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
                lastX = downX;
                velocityPxPerSecond = 0f;
                panelDrift = 0f;
                swiping = false;
                dragging = false;
                pressedTab = tabAt(event.getX(), event.getY());
                startPress(true);
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!swiping && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    swiping = true;
                    dragging = true;
                    if (selectedIndex < 0) selectedIndex = 0;
                    dragPosition = selectedIndex;
                    updateDrag(event.getX());
                    return true;
                }
                return swiping;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                startPress(false);
                return swiping;
            default:
                return swiping;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        protectAncestors(event);
        if (dragging && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            updateDrag(event.getX());
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP && swiping) {
            updateDrag(event.getX());
            finishDrag(false);
            swiping = false;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            if (dragging) finishDrag(true);
            swiping = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            startPress(false);
        }
        return true;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pressedTab = tabAt(event.getX(), event.getY());
            startPress(true);
        } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                && !dragging) {
            startPress(false);
        }
        return super.dispatchTouchEvent(event);
    }

    private void updateDrag(float x) {
        float tabWidth = tabWidth();
        float direction = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? -1f : 1f;
        float delta = (x - downX) * direction / Math.max(1f, tabWidth);
        dragPosition = Math.max(0f, Math.min(pageCount - 1f, selectedIndex + delta));
        float fraction = Math.max(-1f, Math.min(1f, (x - downX) / Math.max(1f, getWidth())));
        panelDrift = MAX_PANEL_DRIFT_DP * density * fraction * (1f - Math.abs(fraction) * .5f);
        velocityPxPerSecond = (x - lastX) * 60f;
        lastX = x;
        if (dragListener != null) dragListener.onDragPosition(dragPosition);
        invalidate();
    }

    private void finishDrag(boolean cancelled) {
        int target = Math.max(0, Math.min(pageCount - 1, Math.round(dragPosition)));
        if (cancelled) target = selectedIndex;
        dragging = false;
        panelDrift = 0f;
        velocityPxPerSecond = 0f;
        startPress(false);
        if (dragListener != null) {
            dragListener.onDragFinished(target);
        } else if (!cancelled && swipeListener != null && target != selectedIndex) {
            swipeListener.onSwipe(target > selectedIndex ? 1 : -1);
        }
        setSelectedIndex(target);
        invalidate();
    }

    private float tabWidth() {
        if (getChildCount() == 0 || !(getChildAt(0) instanceof ViewGroup)) return getWidth();
        ViewGroup row = (ViewGroup) getChildAt(0);
        return row.getChildCount() == 0 ? getWidth() : row.getChildAt(0).getWidth();
    }

    private int tabAt(float x, float y) {
        if (getChildCount() == 0 || !(getChildAt(0) instanceof ViewGroup)) return -1;
        ViewGroup row = (ViewGroup) getChildAt(0);
        for (int i = 0; i < row.getChildCount(); i++) {
            View tab = row.getChildAt(i);
            if (tab.getVisibility() == VISIBLE && x >= row.getLeft() + tab.getLeft()
                    && x <= row.getLeft() + tab.getRight()
                    && y >= row.getTop() + tab.getTop()
                    && y <= row.getTop() + tab.getBottom()) return i;
        }
        return -1;
    }

    private void startPress(boolean target) {
        pressAnimationFrom = pressProgress;
        pressAnimationTarget = target;
        pressAnimationStart = android.os.SystemClock.uptimeMillis();
        invalidate();
    }

    private void updatePressAnimation() {
        float target = pressAnimationTarget ? 1f : 0f;
        float elapsed = android.os.SystemClock.uptimeMillis() - pressAnimationStart;
        float progress = Math.max(0f, Math.min(1f, elapsed / 180f));
        float eased = criticalSpring(progress);
        pressProgress = pressAnimationFrom + (target - pressAnimationFrom) * eased;
        if (shader != null) {
            try {
                shader.setFloatUniform("pressProgress", pressProgress);
            } catch (RuntimeException ignored) {
                // The cached blur/fallback path remains valid if a vendor shader drops out.
            }
        }
        if (pressedTab >= 0 && pressedTab < getChildCount() && getChildAt(0) instanceof ViewGroup) {
            ViewGroup row = (ViewGroup) getChildAt(0);
            if (pressedTab < row.getChildCount()) {
                View tab = row.getChildAt(pressedTab);
                if (tab instanceof ViewGroup && ((ViewGroup) tab).getChildCount() > 0) {
                    View icon = ((ViewGroup) tab).getChildAt(0);
                    float scale = 1f + (PRESSED_SCALE - 1f) * pressProgress;
                    icon.setScaleX(scale);
                    icon.setScaleY(scale);
                }
            }
        }
        if (progress < 1f) postInvalidateOnAnimation();
    }

    private void drawIndicatorLens(Canvas canvas) {
        if (indicatorShader == null || indicatorEffect == null || pressProgress <= .001f
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !canvas.isHardwareAccelerated()) return;
        try {
            indicatorShader.setFloatUniform("size", new float[]{getWidth(), getHeight()});
            indicatorShader.setFloatUniform("sampleSize", new float[]{getWidth(), getHeight()});
            indicatorShader.setFloatUniform("pillRect", new float[]{selectedRect.left, selectedRect.top,
                    selectedRect.right, selectedRect.bottom});
            indicatorShader.setFloatUniform("cornerRadius", DOCK_CORNER_DP * density);
            indicatorShader.setFloatUniform("lensHeight", PRESS_PILL_HEIGHT_DP * density * pressProgress);
            indicatorShader.setFloatUniform("lensAmount", -PRESS_PILL_AMOUNT_DP * density * pressProgress);
            indicatorShader.setFloatUniform("depthEffect", 1f);
            indicatorShader.setFloatUniform("chromaticAberration", .5f * pressProgress);
            indicatorShader.setFloatUniform("pressProgress", pressProgress);
            canvas.drawRenderNode(indicatorNode);
        } catch (RuntimeException ignored) {
            indicatorNode.setRenderEffect(null);
            indicatorEffect = null;
        }
    }

    private float criticalSpring(float progress) {
        if (progress >= 1f) return 1f;
        float time = progress * 5f;
        return Math.max(0f, Math.min(1f, 1f - (1f + time) * (float) Math.exp(-time)));
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
    }

    private void removeListener() {
        if (source != null && listenerAdded) {
            source.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
            listenerAdded = false;
        }
    }

    private void recordSource() {
        if (source == null || disposed || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || source.getWidth() <= 0 || source.getHeight() <= 0) return;
        int pad = capturePadding();
        int w = Math.max(1, getWidth() + pad * 2), h = Math.max(1, getHeight() + pad * 2);
        Canvas recording = null;
        try {
            recording = sourceNode.beginRecording(w, h);
            source.getLocationInWindow(sourceLocation);
            getLocationInWindow(hostLocation);
            recording.translate(sourceLocation[0] - hostLocation[0] + pad,
                    sourceLocation[1] - hostLocation[1] + pad);
            source.draw(recording);
            captureDirty = false;
            hasCapture = true;
        } catch (Throwable ignored) {
            // A few vendor renderers reject recording a ViewGroup while a
            // transition is being committed. Keep the last good frame and use
            // the themed base until the next attachment instead of hiding the
            // whole dock.
            captureDirty = false;
        } finally {
            if (recording != null) {
                try {
                    sourceNode.endRecording();
                } catch (Throwable ignored) {
                    captureDirty = false;
                }
            }
        }
    }

    private void recordIndicatorSource() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || getWidth() <= 0 || getHeight() <= 0) return;
        Canvas recording = null;
        try {
            recording = indicatorNode.beginRecording(getWidth(), getHeight());
            recording.drawRenderNode(sourceNode);
        } catch (Throwable ignored) {
            indicatorEffect = null;
        } finally {
            if (recording != null) {
                try {
                    indicatorNode.endRecording();
                } catch (Throwable ignored) {
                    indicatorEffect = null;
                }
            }
        }
    }

    private void updateEffect() {
        if (effectUnavailable || getWidth() <= 0 || getHeight() <= 0) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            if (shader != null) {
                shader.setFloatUniform("size", new float[]{getWidth(), getHeight()});
                int pad = capturePadding();
                shader.setFloatUniform("sampleSize", new float[]{getWidth() + pad * 2f, getHeight() + pad * 2f});
                shader.setFloatUniform("offset", new float[]{-pad, -pad});
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
                shader.setFloatUniform("pressProgress", pressProgress);
            }
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
            if (shader == null) {
                sourceNode.setRenderEffect(effect);
            } else {
                RenderEffect shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content");
                sourceNode.setRenderEffect(effect == null ? shaderEffect : RenderEffect.createChainEffect(shaderEffect, effect));
            }
            if (indicatorShader != null) {
                if (indicatorEffect == null) {
                    indicatorEffect = RenderEffect.createRuntimeShaderEffect(indicatorShader, "content");
                }
                indicatorNode.setRenderEffect(indicatorEffect);
            }
        } catch (RuntimeException ignored) {
            if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                shader = null;
                effectUnavailable = false;
                sourceNode.setRenderEffect(cachedBlur);
            } else {
                effectUnavailable = true;
                sourceNode.setRenderEffect(null);
            }
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

    private int capturePadding() {
        return Math.round(Math.max(40f, Math.abs(refractionOffset / density) + blurRadius / density) * density);
    }

    private void updateBloomShaders(boolean dark) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        int accent = dark ? 0xff0091ff : 0xff0088ff;
        int accentGlow = (Math.round(0.12f * 255f) << 24) | (accent & 0x00ffffff);
        int[] colors = new int[]{0x00ffffff, accentGlow, 0x24ffffff, accentGlow, 0x00ffffff};
        float[] stops = new float[]{0f, .32f, .50f, .68f, 1f};
        Shader first = new LinearGradient(0f, 0f, getWidth(), getHeight(), colors, stops, Shader.TileMode.MIRROR);
        Shader second = new LinearGradient(0f, getHeight(), getWidth(), 0f, colors, stops, Shader.TileMode.MIRROR);
        bloomPaintA.setShader(first);
        bloomPaintB.setShader(second);
        bloomRect.set(0.5f * density, 0.5f * density,
                getWidth() - 0.5f * density, getHeight() - 0.5f * density);
    }

    private void drawBloom(Canvas canvas) {
        if (bloomPaintA.getShader() == null || bloomPaintB.getShader() == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        float phase = (now % 9000L) / 9000f;
        float cx = getWidth() * .5f;
        float cy = getHeight() * .5f;
        bloomMatrixA.setRotate(-45f, cx, cy);
        bloomMatrixA.postTranslate(getWidth() * phase, getHeight() * phase);
        bloomMatrixB.setRotate(90f, cx, cy);
        bloomMatrixB.postTranslate(-getWidth() * phase, getHeight() * phase);
        bloomPaintA.getShader().setLocalMatrix(bloomMatrixA);
        bloomPaintB.getShader().setLocalMatrix(bloomMatrixB);
        canvas.drawRoundRect(bloomRect, cornerRadius, cornerRadius, bloomPaintA);
        canvas.drawRoundRect(bloomRect, cornerRadius, cornerRadius, bloomPaintB);
        if (isAttachedToWindow() && getVisibility() == VISIBLE) postInvalidateDelayed(50L);
    }

    private String loadShader() {
        return loadShader(SHADER_ASSET);
    }

    private String loadShader(String assetName) {
        StringBuilder code = new StringBuilder();
        try (InputStream input = getContext().getAssets().open(assetName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) code.append(line).append('\n');
        } catch (IOException error) {
            throw new IllegalStateException("Missing liquid glass shader asset", error);
        }
        return code.toString();
    }
}
