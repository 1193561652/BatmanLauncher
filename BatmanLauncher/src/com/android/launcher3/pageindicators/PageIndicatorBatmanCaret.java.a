package com.android.launcher3.pageindicators;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Property;
import android.view.ViewConfiguration;
import android.widget.ImageView;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dynamicui.WallpaperColorInfo;
import com.android.launcher3.util.Themes;

public class PageIndicatorBatmanCaret extends PageIndicator {
    private static final String TAG = "PageIndicatorBatman";

    private Launcher mLauncher;
    private ImageView mAllAppsHandle;
    private ImageView mPageScroll;


    private ValueAnimator[] mAnimators = new ValueAnimator[3];
    private int mToAlpha;
    private final Handler mDelayedLineFadeHandler = new Handler(Looper.getMainLooper());

    private static final int LINE_ANIMATE_DURATION = ViewConfiguration.getScrollBarFadeDuration();
    private static final int LINE_FADE_DELAY = ViewConfiguration.getScrollDefaultDelay();
    private static final int LINE_ALPHA_ANIMATOR_INDEX = 0;
    private static final int NUM_PAGES_ANIMATOR_INDEX = 1;
    private static final int TOTAL_SCROLL_ANIMATOR_INDEX = 2;

    public static final int WHITE_ALPHA = (int) (0.70f * 255);
    public static final int BLACK_ALPHA = (int) (0.65f * 255);
    private Paint mLinePaint;


    private int mActiveAlpha = 0;
    private int mCurrentScroll;
    private int mTotalScroll;
    private float mNumPagesFloat;
    private  float mCurrentPage;

    private boolean mShouldAutoHide;

    private static final Property<PageIndicatorBatmanCaret, Integer> PAINT_ALPHA
            = new Property<PageIndicatorBatmanCaret, Integer>(Integer.class, "paint_alpha") {
        @Override
        public Integer get(PageIndicatorBatmanCaret obj) {
            return obj.mLinePaint.getAlpha();
        }

        @Override
        public void set(PageIndicatorBatmanCaret obj, Integer alpha) {
            obj.mLinePaint.setAlpha(alpha);
            int maxAlpha = Themes.getAlpha(obj.getContext(), android.R.attr.spotShadowAlpha);
            obj.getCaretDrawable().setCaretAndShadowAlpha(255 * (WHITE_ALPHA - Math.min(alpha, WHITE_ALPHA)) / WHITE_ALPHA, maxAlpha * (WHITE_ALPHA - Math.min(alpha, WHITE_ALPHA)) / WHITE_ALPHA);

            obj.mPageScroll.setAlpha((float)alpha);
            //Log.v(TAG, "Caret:" + 255 * (WHITE_ALPHA - Math.max(alpha, WHITE_ALPHA)) / WHITE_ALPHA + " Shadow:" + maxAlpha * (WHITE_ALPHA - alpha) / WHITE_ALPHA + " alpha:" +  alpha);
            //obj.invalidate();
            obj.mPageScroll.invalidate();
        }
    };


    private static final Property<PageIndicatorBatmanCaret, Float> NUM_PAGES
            = new Property<PageIndicatorBatmanCaret, Float>(Float.class, "num_pages") {
        @Override
        public Float get(PageIndicatorBatmanCaret obj) {
            return obj.mNumPagesFloat;
        }

        @Override
        public void set(PageIndicatorBatmanCaret obj, Float numPages) {
            obj.mNumPagesFloat = numPages;
            //Log.v(PageIndicatorBatmanCaret.TAG, "mNumPagesFloat:" + obj.mNumPagesFloat);
            obj.invalidate();
            //obj.mPageScroll.invalidate();
        }
    };

    private static final Property<PageIndicatorBatmanCaret, Integer> TOTAL_SCROLL
            = new Property<PageIndicatorBatmanCaret, Integer>(Integer.class, "total_scroll") {
        @Override
        public Integer get(PageIndicatorBatmanCaret obj) {
            return obj.mTotalScroll;
        }

        @Override
        public void set(PageIndicatorBatmanCaret obj, Integer totalScroll) {
            obj.mTotalScroll = totalScroll;
            obj.invalidate();
        }
    };


    public PageIndicatorBatmanCaret(Context context) {
        this(context, null);
    }

    public PageIndicatorBatmanCaret(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PageIndicatorBatmanCaret(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Resources res = context.getResources();

        mLauncher = Launcher.getLauncher(context);
        setCaretDrawable(new CaretDrawable(context));

        mLinePaint = new Paint();
        mLinePaint.setAlpha(0);

        boolean darkText = WallpaperColorInfo.getInstance(context).supportsDarkText();

        mActiveAlpha = darkText ? BLACK_ALPHA : WHITE_ALPHA;
    }


    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mAllAppsHandle = (ImageView) findViewById(R.id.all_apps_handle2);
        mAllAppsHandle.setImageDrawable(getCaretDrawable());
        mAllAppsHandle.setOnClickListener(mLauncher);
        mAllAppsHandle.setOnFocusChangeListener(mLauncher.mFocusHandler);
        mLauncher.setAllAppsButton(mAllAppsHandle);
        mAllAppsHandle.setAlpha(0f);

        mPageScroll = (ImageView)findViewById(R.id.all_apps_handle3);

        Bitmap logo = BitmapFactory.decodeResource(this.getContext().getResources(), R.drawable.batman_icon);
        mPageScroll.setImageDrawable(new BatmanDrawable(logo));

        mPageScroll.setAlpha(1.0f);
    }

    @Override
    public void setAccessibilityDelegate(AccessibilityDelegate delegate) {

    }

    @Override
    protected void onDraw(Canvas canvas) {

    }

    @Override
    public void setContentDescription(CharSequence contentDescription) {

    }

    @Override
    public void setScroll(int currentScroll, int totalScroll) {
        if (getAlpha() == 0) {
            //return;
        }
        //animateLineToAlpha(mActiveAlpha);
        //Log.v(TAG, "currentScroll:" + currentScroll + " totalScroll:" + totalScroll + " mNumPages:" + mNumPages);
        mCurrentScroll = currentScroll;
        if (mTotalScroll == 0) {
            mTotalScroll = totalScroll;
        } else if (mTotalScroll != totalScroll) {
            animateToTotalScroll(totalScroll);
        } else {
            //invalidate();
            mPageScroll.invalidate();
        }
        //0              1060            2120
        //0.0    0.5     1.0      1.5    2.0
        //1               2                3

        if(mNumPages-1 > 0 && mTotalScroll > 0){
            mCurrentPage = (float)mCurrentScroll / (mTotalScroll / (mNumPages-1));
        }

        //Log.v(TAG, "mCurrentPage:" + mCurrentPage);

        if (mShouldAutoHide) {
            //hideAfterDelay();   //自动影藏，没有动作后延时一段时间，然后设置不透明度
        }
    }

    @Override
    public void setActiveMarker(int activePage) {
    }

    @Override
    protected void onPageCountChanged() {
        if (Float.compare(mNumPages, mNumPagesFloat) != 0) {
            animateToNumPages(mNumPages);
        }
    }



    public void setShouldAutoHide(boolean shouldAutoHide) {
        mShouldAutoHide = shouldAutoHide;
        if (shouldAutoHide && mLinePaint.getAlpha() > 0) {
            hideAfterDelay();
        } else if (!shouldAutoHide) {
            mDelayedLineFadeHandler.removeCallbacksAndMessages(null);
        }
    }

    private Runnable mHideLineRunnable = new Runnable() {
        @Override
        public void run() {
            animateLineToAlpha(0);
        }
    };

    public void hideAfterDelay() {
        mDelayedLineFadeHandler.removeCallbacksAndMessages(null);
        mDelayedLineFadeHandler.postDelayed(mHideLineRunnable, LINE_FADE_DELAY);
    }

    private void animateLineToAlpha(int alpha) {
        if (alpha == mToAlpha) {
            // Ignore the new animation if it is going to the same alpha as the current animation.
            return;
        }
        mToAlpha = alpha;
        setupAndRunAnimation(ObjectAnimator.ofInt(this, PAINT_ALPHA, alpha), LINE_ALPHA_ANIMATOR_INDEX);
    }

    private void animateToNumPages(int numPages) {
        setupAndRunAnimation(ObjectAnimator.ofFloat(this, NUM_PAGES, numPages), NUM_PAGES_ANIMATOR_INDEX);
    }

    private void animateToTotalScroll(int totalScroll) {
        setupAndRunAnimation(ObjectAnimator.ofInt(this, TOTAL_SCROLL, totalScroll), TOTAL_SCROLL_ANIMATOR_INDEX);
    }

    private void setupAndRunAnimation(ValueAnimator animator, final int animatorIndex) {
        if (mAnimators[animatorIndex] != null) {
            mAnimators[animatorIndex].cancel();
        }
        mAnimators[animatorIndex] = animator;
        mAnimators[animatorIndex].addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimators[animatorIndex] = null;
            }
        });
        mAnimators[animatorIndex].setDuration(LINE_ANIMATE_DURATION);
        mAnimators[animatorIndex].start();
    }


    class BatmanDrawable extends Drawable{
        private Bitmap mBatLogo = null;
        Camera camera = new Camera();
        Paint paint = new Paint();

        BatmanDrawable(Bitmap logo) {
            super();
            this.mBatLogo = logo;

            paint.setAntiAlias(true);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(30);
        }

        private String int2Roman(int num) {
            final String I[] = new String[] { "0","I","II","III","IV","V","VI","VII","VIII","IX", "X"};
            if(num >= I.length)
                num = I.length-1;
            if(num < 0)
                num = 0;
            return I[num];
        }

        @Override
        public void draw(@NonNull Canvas canvas) {

            int currentPageInt = (int)mCurrentPage;
            float currentPagedecimals = mCurrentPage - currentPageInt;

            String strPageNum = "";

            int w = canvas.getWidth();
            int h = canvas.getHeight();
            camera.save();
            final Matrix matrix = new Matrix();
            matrix.reset();
            if(currentPagedecimals < 0.5) {
                camera.rotateY(90 * (currentPagedecimals / 0.5f));
                strPageNum = int2Roman(currentPageInt+1);
            } else {
                camera.rotateY(-90 * ((1.0f - currentPagedecimals)/0.5f));
                strPageNum = int2Roman(currentPageInt + 2);
            }
            camera.getMatrix(matrix);
            //matrix.postScale(0.2f, 1.0f);
            matrix.preTranslate(-w/2, -h/2);
            matrix.postTranslate(w/2, h/2);
            camera.restore();

            int srcw = mBatLogo.getWidth();
            int srch = mBatLogo.getHeight();
            Rect destRect = new Rect(0, 0, w, h);
            if(w * srch < srcw * h) {   //w / h < srcw / srch
                int hh = w * srch / srcw;
                destRect.top +=  (h - hh) / 2;
                destRect.bottom -= (h - hh) / 2;
            } else if(w * srch > srcw * h) {    //w / h > srcw / srch
                int ww = h * srcw / srch;
                destRect.left += (w - ww) / 2;
                destRect.right -= (w - ww) / 2;
            }

            canvas.save();

            canvas.concat(matrix);


            canvas.drawBitmap(mBatLogo, new Rect(0, 0, srcw, srch), destRect, paint);

            final Paint.FontMetrics fontMetrics = paint.getFontMetrics();
            final float top = fontMetrics.top;        //为基线到字体上边框的距离,即上图中的top
            final float bottom = fontMetrics.bottom;  //为基线到字体下边框的距离,即上图中的bottom
            final int baseLineY = (int) (h/2 - top/2 - bottom/2);//基线中间点的y轴计算公式
            canvas.drawText(strPageNum, w/2, baseLineY, paint);

//            Rect eraseRect = new Rect(destRect);
//            eraseRect.left += destRect.width() / 4;
//            eraseRect.right -= destRect.width() / 4;
//            canvas.clipRect(eraseRect);
//            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return 0;
        }
    }

}
