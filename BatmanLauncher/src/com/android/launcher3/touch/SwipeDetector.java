/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.touch;

import static android.view.MotionEvent.INVALID_POINTER_ID;
import android.content.Context;
import android.graphics.PointF;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;

import java.lang.reflect.Method;

/**
 * One dimensional scroll/drag/swipe gesture detector.
 *
 * Definition of swipe is different from android system in that this detector handles
 * 'swipe to dismiss', 'swiping up/down a container' but also keeps scrolling state before
 * swipe action happens
 */
public class SwipeDetector {

    private static final boolean DBG = false;
    private static final String TAG = "SwipeDetector";

    private int mScrollConditions;
    public static final int DIRECTION_POSITIVE = 1 << 0;
    public static final int DIRECTION_NEGATIVE = 1 << 1;
    public static final int DIRECTION_BOTH = DIRECTION_NEGATIVE | DIRECTION_POSITIVE;

    private static final float ANIMATION_DURATION = 1200;
    private static final float FAST_FLING_PX_MS = 10;

    protected int mActivePointerId = INVALID_POINTER_ID;

    /**
     * The minimum release velocity in pixels per millisecond that triggers fling..
     */
    public static final float RELEASE_VELOCITY_PX_MS = 1.0f;

    /**
     * The time constant used to calculate dampening in the low-pass filter of scroll velocity.
     * Cutoff frequency is set at 10 Hz.
     */
    public static final float SCROLL_VELOCITY_DAMPENING_RC = 1000f / (2f * (float) Math.PI * 10);

    /* Scroll state, this is set to true during dragging and animation. */
    private ScrollState mState = ScrollState.IDLE;

    enum ScrollState {
        IDLE,
        DRAGGING,      // onDragStart, onDrag
        SETTLING,      // onDragEnd
        DRAGGING_DOWN   // onDragStartDown, onDragDown
    }

    public static abstract class Direction {

        abstract float getDisplacement(MotionEvent ev, int pointerIndex, PointF refPoint);

        /**
         * Distance in pixels a touch can wander before we think the user is scrolling.
         */
        abstract float getActiveTouchSlop(MotionEvent ev, int pointerIndex, PointF downPos);
    }

    public static final Direction VERTICAL = new Direction() {

        @Override
        float getDisplacement(MotionEvent ev, int pointerIndex, PointF refPoint) {
            return ev.getY(pointerIndex) - refPoint.y;
        }

        @Override
        float getActiveTouchSlop(MotionEvent ev, int pointerIndex, PointF downPos) {
            return Math.abs(ev.getX(pointerIndex) - downPos.x);
        }
    };

    public static final Direction HORIZONTAL = new Direction() {

        @Override
        float getDisplacement(MotionEvent ev, int pointerIndex, PointF refPoint) {
            return ev.getX(pointerIndex) - refPoint.x;
        }

        @Override
        float getActiveTouchSlop(MotionEvent ev, int pointerIndex, PointF downPos) {
            return Math.abs(ev.getY(pointerIndex) - downPos.y);
        }
    };

    //------------------- ScrollState transition diagram -----------------------------------
    //
    // IDLE ->      (mDisplacement > mTouchSlop) -> DRAGGING
    // DRAGGING -> (MotionEvent#ACTION_UP, MotionEvent#ACTION_CANCEL) -> SETTLING
    // SETTLING -> (MotionEvent#ACTION_DOWN) -> DRAGGING
    // SETTLING -> (View settled) -> IDLE

    private void setState(ScrollState newState) {
        if (DBG) {
            Log.d(TAG, "setState:" + mState + "->" + newState);
        }
        // onDragStart and onDragEnd is reported ONLY on state transition
        if (newState == ScrollState.DRAGGING) {
            initializeDragging();
            if (mState == ScrollState.IDLE) {
                reportDragStart(false /* recatch */);
            } else if (mState == ScrollState.SETTLING) {
                reportDragStart(true /* recatch */);
            }
        } else if(newState == ScrollState.DRAGGING_DOWN) {
            initializeDragging();
            if (mState == ScrollState.IDLE) {
                reportDragStartDown(false /* recatch */);
            } else if (mState == ScrollState.SETTLING) {
                reportDragStartDown(true /* recatch */);
            }
        }

        boolean downEnd = false;
        if (newState == ScrollState.SETTLING) {
            if(mState == ScrollState.DRAGGING) {
                reportDragEnd();
            } else if(mState == ScrollState.DRAGGING_DOWN) {
                downEnd = true;
            } else {
                reportDragEnd();
            }
        }

        mState = newState;

        if(downEnd) {
            reportDragEndDown();
        }
    }

    public boolean isDraggingOrSettling() {
        return mState == ScrollState.DRAGGING || mState == ScrollState.SETTLING;
    }

    /**
     * There's no touch and there's no animation.
     */
    public boolean isIdleState() {
        return mState == ScrollState.IDLE;
    }

    public boolean isSettlingState() {
        return mState == ScrollState.SETTLING;
    }

    public boolean isDraggingState() {
        return mState == ScrollState.DRAGGING;
    }

    public boolean isDraggingDownState() {
        return mState == ScrollState.DRAGGING_DOWN;
    }

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private final Direction mDir;

    private final float mTouchSlop;

    /* Client of this gesture detector can register a callback. */
    private final Listener mListener;   //由AllAppsTransitionController实现

    private long mCurrentMillis;

    private float mVelocity;
    private float mLastDisplacement;
    private float mDisplacement;

    private float mSubtractDisplacement;
    private boolean mIgnoreSlopWhenSettling;
    private boolean mTwoDirection = false;

    public interface Listener {
        void onDragStart(boolean start);
        boolean onDrag(float displacement, float velocity);
        void onDragEnd(float velocity, boolean fling);

        void onDragStartDown(boolean start);
        boolean onDragDown(float displacement, float velocity);
        void onDragEndDown(float velocity, boolean fling);
    }

    public SwipeDetector(@NonNull Context context, @NonNull Listener l, @NonNull Direction dir) {
        this(ViewConfiguration.get(context).getScaledTouchSlop(), l, dir);
    }

    @VisibleForTesting
    protected SwipeDetector(float touchSlope, @NonNull Listener l, @NonNull Direction dir) {
        mTouchSlop = touchSlope;
        mListener = l;
        mDir = dir;
    }

    public void setTwoDirection(boolean twoDirection) {
        mTwoDirection = twoDirection;
    }

    public void setDetectableScrollConditions(int scrollDirectionFlags, boolean ignoreSlop) {
        mScrollConditions = scrollDirectionFlags;
        mIgnoreSlopWhenSettling = ignoreSlop;
    }

    private boolean shouldScrollStart(MotionEvent ev, int pointerIndex) {
        //判断是否应该开始拖动
        // reject cases where the angle or slop condition is not met.
        if (Math.max(mDir.getActiveTouchSlop(ev, pointerIndex, mDownPos), mTouchSlop) > Math.abs(mDisplacement)) {
            return false;
        }

        // Check if the client is interested in scroll in current direction.
        if (((mScrollConditions & DIRECTION_NEGATIVE) > 0 && mDisplacement > 0) ||
                ((mScrollConditions & DIRECTION_POSITIVE) > 0 && mDisplacement < 0)) {
            //负坐标系 and 移动距离为正
            //正坐标系 and 移动距离为负
            //即向上拖动的时候
            return true;
        }
        return false;
    }

    private boolean shouldScrollStartDown(MotionEvent ev, int pointerIndex) {
        //判断是否应该开始拖动
        // reject cases where the angle or slop condition is not met.
        if (Math.max(mDir.getActiveTouchSlop(ev, pointerIndex, mDownPos), mTouchSlop) > Math.abs(mDisplacement)) {
            return false;
        }

        // Check if the client is interested in scroll in current direction.
        if (((mScrollConditions & DIRECTION_NEGATIVE) > 0 && mDisplacement < 0) ||
                ((mScrollConditions & DIRECTION_POSITIVE) > 0 && mDisplacement > 0)) {
            //负坐标系 and 移动距离为负
            //正坐标系 and 移动距离为正
            //即向下拖动的时候
            return true;
        }
        return false;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);  //当前活动的pointid，取第0个点为活动点
                mDownPos.set(ev.getX(), ev.getY());     //downpoint
                mLastPos.set(mDownPos);                 //last down point
                mLastDisplacement = 0;                  //last置换？
                mDisplacement = 0;                      //置换？替代？
                mVelocity = 0;

                if (mState == ScrollState.SETTLING && mIgnoreSlopWhenSettling) {
                    setState(ScrollState.DRAGGING);
                }
                break;
            //case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP: //当某个点抬起
                int ptrIdx = ev.getActionIndex();
                int ptrId = ev.getPointerId(ptrIdx);
                if (ptrId == mActivePointerId) {
                    //如果抬起的点是活动点，将下一个点设置为活动点
                    //同时设置这个点的按下的位置，
                    //用当前位置-上一个活动点的移动距离得到起始位置，实际不是真实的起始位置。
                    final int newPointerIdx = ptrIdx == 0 ? 1 : 0;
                    mDownPos.set(ev.getX(newPointerIdx) - (mLastPos.x - mDownPos.x), ev.getY(newPointerIdx) - (mLastPos.y - mDownPos.y));
                    mLastPos.set(ev.getX(newPointerIdx), ev.getY(newPointerIdx));

                    //设置这个点为活动点
                    mActivePointerId = ev.getPointerId(newPointerIdx);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                //更具当前活动点的id查找point的索引
                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == INVALID_POINTER_ID) {
                    break;
                }
                //根据当前的点和初次按下的位置，计算mDisplacement，y相减，计算距离？
                mDisplacement = mDir.getDisplacement(ev, pointerIndex, mDownPos);

                //计算速度？将当前的点和上次的点，以及时间发生的时间计算从当前位置移动到目标位置需要的速度
                mVelocity = computeVelocity(mDir.getDisplacement(ev, pointerIndex, mLastPos), ev.getEventTime());
                //Log.v(TAG, "MOVE v:" + mVelocity + " state:" + mState);

                // handle state and listener calls.
                if (mState != ScrollState.DRAGGING && (mState != ScrollState.DRAGGING_DOWN || mTwoDirection == false) && shouldScrollStart(ev, pointerIndex)) {
                    //若应该开始拖动，但当前状态不是看是拖动
                    setState(ScrollState.DRAGGING); //设置标志，开始拖动
                } else if(mState != ScrollState.DRAGGING && mState != ScrollState.DRAGGING_DOWN && mTwoDirection == true && shouldScrollStartDown(ev, pointerIndex)) {
                    setState(ScrollState.DRAGGING_DOWN);
                }

                if(mTwoDirection == false && mState == ScrollState.IDLE) {
                    if(mVelocity > 1.0f) {
                        reportDragStartDown(false);
                    }
                }

                if (mState == ScrollState.DRAGGING) {
                    reportDragging();   //若开始拖动，移动视图，设置透明度等
                } else if(mState == ScrollState.DRAGGING_DOWN && mTwoDirection == true) {
                    reportDraggingDown();
                }

                //设置最后移动点的坐标
                mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // These are synthetic events and there is no need to update internal values.
                if (mState == ScrollState.DRAGGING || mState == ScrollState.DRAGGING_DOWN) {
                    //当触点全部抬起时，设置状态为SETTLING
                    setState(ScrollState.SETTLING);
                }
                break;
            default:
                break;
        }
        return true;
    }


    //完成滚动？
    public void finishedScrolling() {
        setState(ScrollState.IDLE);
    }

    private boolean reportDragStart(boolean recatch) {
        mListener.onDragStart(!recatch);
        if (DBG) {
            Log.d(TAG, "onDragStart recatch:" + recatch);
        }
        return true;
    }

    private boolean reportDragStartDown(boolean recatch) {
        mListener.onDragStartDown(!recatch);
        if (DBG) {
            Log.d(TAG, "onDragStartDown recatch:" + recatch);
        }
        return true;
    }

    private void initializeDragging() {
        if (mState == ScrollState.SETTLING && mIgnoreSlopWhenSettling) {
            mSubtractDisplacement = 0;
        }
        if (mDisplacement > 0) {
            mSubtractDisplacement = mTouchSlop;
        } else {
            mSubtractDisplacement = -mTouchSlop;
        }
    }

    private boolean reportDragging() {
        if (mDisplacement != mLastDisplacement) {
            //当前距离 ！= 最后的距离，消除原地抖动？
            if (DBG) {
                Log.d(TAG, String.format("onDrag disp=%.1f, velocity=%.1f", mDisplacement, mVelocity));
            }

            mLastDisplacement = mDisplacement;
            return mListener.onDrag(mDisplacement - mSubtractDisplacement, mVelocity);
        }
        return true;
    }

    private boolean reportDraggingDown() {
        if (mDisplacement != mLastDisplacement) {
            //当前距离 ！= 最后的距离，消除原地抖动？
            if (DBG) {
                Log.d(TAG, String.format("reportDraggingDown disp=%.1f, velocity=%.1f", mDisplacement, mVelocity));
            }

            mLastDisplacement = mDisplacement;
            return mListener.onDragDown(mDisplacement - mSubtractDisplacement, mVelocity);
        }
        return true;
    }

    private void reportDragEnd() {
        if (DBG) {
            Log.d(TAG, String.format("onScrollEnd disp=%.1f, velocity=%.1f", mDisplacement, mVelocity));
        }
        mListener.onDragEnd(mVelocity, Math.abs(mVelocity) > RELEASE_VELOCITY_PX_MS);

    }

    private void reportDragEndDown() {
        if (DBG) {
            Log.d(TAG, String.format("reportDragEndDown disp=%.1f, velocity=%.1f", mDisplacement, mVelocity));
        }
        mListener.onDragEndDown(mVelocity, Math.abs(mVelocity) > RELEASE_VELOCITY_PX_MS);
    }

    /**
     * Computes the damped velocity.
     */
    public float computeVelocity(float delta, long currentMillis) {
        long previousMillis = mCurrentMillis;
        mCurrentMillis = currentMillis;

        float deltaTimeMillis = mCurrentMillis - previousMillis;    //变化时间，当前时间-前一次的时间
        float velocity = (deltaTimeMillis > 0) ? (delta / deltaTimeMillis) : 0; //速度 = 变化量 / 变化时间，除0异常
        if (Math.abs(mVelocity) < 0.001f) {
            mVelocity = velocity;   //速度足够小，直接赋值
        } else {
            //速度过大，计算alpha值
            float alpha = computeDampeningFactor(deltaTimeMillis);
            //再通过alpha计算速度，实现加速过程？
            mVelocity = interpolate(mVelocity, velocity, alpha);
        }
        return mVelocity;
    }

    /**
     * Returns a time-dependent dampening factor using delta time.
     */
    private static float computeDampeningFactor(float deltaTime) {
        return deltaTime / (SCROLL_VELOCITY_DAMPENING_RC + deltaTime);
    }

    /**
     * Returns the linear interpolation between two values
     */
    private static float interpolate(float from, float to, float alpha) {
        return (1.0f - alpha) * from + alpha * to;
    }

    public static long calculateDuration(float velocity, float progressNeeded) {
        // TODO: make these values constants after tuning.
        float velocityDivisor = Math.max(2f, Math.abs(0.5f * velocity));    //速度的1/2，若速度太小，则取2
        float travelDistance = Math.max(0.2f, progressNeeded);      //移动的比例，至少移动0.2
        long duration = (long) Math.max(100, ANIMATION_DURATION / velocityDivisor * travelDistance);
        if (DBG) {
            Log.d(TAG, String.format("calculateDuration=%d, v=%f, d=%f", duration, velocity, progressNeeded));
        }
        return duration;
    }

    public static class ScrollInterpolator implements Interpolator {

        boolean mSteeper;

        public void setVelocityAtZero(float velocity) {
            mSteeper = velocity > FAST_FLING_PX_MS;
        }

        public float getInterpolation(float t) {
            t -= 1.0f;
            float output = t * t * t;
            if (mSteeper) {
                output *= t * t; // Make interpolation initial slope steeper
            }
            return output + 1;
        }
    }
}
