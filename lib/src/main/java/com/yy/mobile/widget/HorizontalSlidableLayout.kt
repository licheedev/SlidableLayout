package com.yy.mobile.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.support.v4.app.Fragment
import android.support.v4.view.NestedScrollingChild2
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Created by 张宇 on 2019/4/11.
 * E-mail: zhangyu4@yy.com
 * YY: 909017428
 *
 * 支持上下滑的布局。
 * 使用 [setAdapter] 方法来构造上下滑切换的视图。
 *
 * 可以直接对 [View] 进行上下滑，参考 [SlideAdapter] 或者 [SlideViewAdapter]。
 * 可以对 [Fragment] 进行上下滑，参考 [SlideFragmentAdapter]。
 *
 */
class HorizontalSlidableLayout : SlidableLayout, NestedScrollingChild2 {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)


    override fun getGestureCallback(): MyGestureListener =
            object : MyGestureListener() {

                override fun onScroll(
                        e1: MotionEvent?, e2: MotionEvent,
                        distanceX: Float, distanceY: Float
                ): Boolean {
                    if (mState satisfy SlidableLayout.Mask.FLING) {
                        return waitForFling(distanceX, distanceY)
                    }
                    val topView = mCurrentView ?: return false
                    val delegate = mViewHolderDelegate
                            ?: return false
                    val adapter = delegate.adapter

                    var dyFromDownY = e2.y - downY
                    var dxFromDownX = e2.x - downX
                    val direction = when {
                        dxFromDownX < 0 -> SlideDirection.Next
                        dxFromDownX > 0 -> SlideDirection.Prev
                        else -> SlideDirection.Origin
                    }

                    val startToMove = mState satisfy SlidableLayout.Mask.IDLE &&
                            Math.abs(dxFromDownX) > 2 * Math.abs(dyFromDownY)
                    val changeDirectionToNext = mState satisfy SlidableLayout.Mask.PREV && dxFromDownX < 0
                    val changeDirectionToPrev = mState satisfy SlidableLayout.Mask.NEXT && dxFromDownX > 0

                    // todo 这里下面待修改
                    var dx = distanceX.toInt()
                    var dy = distanceY.toInt()
                    if (dispatchNestedPreScroll(dx, dy, mScrollConsumed, mScrollOffset)) {
                        dx -= mScrollConsumed[0]
                        dy -= mScrollConsumed[1]
                        dxFromDownX -= mScrollConsumed[0]
                        dyFromDownY -= mScrollConsumed[1]
                    }

                    if (startToMove) {
                        requestParentDisallowInterceptTouchEvent()
                    }

                    if (startToMove || changeDirectionToNext || changeDirectionToPrev) {
                        val directionMask =
                                if (direction == SlideDirection.Next) SlidableLayout.Mask.NEXT else SlidableLayout.Mask.PREV

                        if (!adapter.canSlideTo(direction)) {
                            mState = State.of(directionMask, SlidableLayout.Mask.SLIDE, SlidableLayout.Mask.REJECT)
                        } else {
                            mState = State.of(directionMask, SlidableLayout.Mask.SLIDE)
                            delegate.prepareBackup(direction)
                        }
                        log("onMove state = $mState, start = $startToMove, " +
                                "changeToNext = $changeDirectionToNext, changeToPrev = $changeDirectionToPrev")
                    }
                    if (mState satisfy SlidableLayout.Mask.REJECT) {
                        return dispatchNestedScroll(0, 0, dx, dy, mScrollOffset)

                    } else if (mState satisfy SlidableLayout.Mask.SLIDE) {
                        val backView = mBackupView ?: return false
                        topView.x = dxFromDownX
                        backView.x =
                                if (mState satisfy SlidableLayout.Mask.NEXT) dxFromDownX + measuredWidth
                                else dxFromDownX - measuredWidth
                        return dispatchNestedScroll(dx, 0, 0, dy, mScrollOffset)
                    }
                    return false
                }

                // 已修改
                override fun onFling(
                        e1: MotionEvent?, e2: MotionEvent,
                        velocityX: Float, velocityY: Float
                ): Boolean {
                    log("onFling ${e2.action} vX = $velocityX state = $mState")
                    onUp(velocityX, velocityY)
                    return true
                }

                // 已修改
                override fun onUp(velocityX: Float, velocityY: Float): Boolean {
                    if (!(mState satisfy SlidableLayout.Mask.SLIDE)) {
                        stopNestedScroll()
                        return false
                    }

                    val topView = mCurrentView ?: return resetTouch()
                    val currentOffsetX = topView.x.toInt()
                    // if state is reject, don't consume the fling.
                    val consumedFling = !(mState satisfy SlidableLayout.Mask.REJECT) || currentOffsetX != 0
                    if (!dispatchNestedPreFling(velocityX, velocityY)) {
                        dispatchNestedFling(velocityX, velocityY, consumedFling)
                    }
                    stopNestedScroll()

                    val backView = mBackupView ?: return resetTouch()
                    val delegate = mViewHolderDelegate
                            ?: return resetTouch()
                    var direction: SlideDirection? = null
                    var duration: Int? = null

                    val widgetWidth = measuredWidth
                    if (consumedFling) {
                        var dx: Int? = null
                        val highSpeed = Math.abs(velocityX) >= mMinFlingSpeed
                        val sameDirection = (mState == State.SLIDE_NEXT && velocityX < 0) ||
                                (mState == State.SLIDE_PREV && velocityX > 0)
                        val moveLongDistance = Math.abs(currentOffsetX) > widgetWidth / 3
                        if ((highSpeed && sameDirection) || (!highSpeed && moveLongDistance)) { //fling
                            if (mState == State.SLIDE_NEXT) {
                                direction = SlideDirection.Next
                                dx = -currentOffsetX - widgetWidth
                            } else if (mState == State.SLIDE_PREV) {
                                direction = SlideDirection.Prev
                                dx = widgetWidth - currentOffsetX
                            }
                        } else { //back to origin
                            direction = SlideDirection.Origin
                            dx = -currentOffsetX
                        }

                        if (dx != null) {
                            duration = calculateDuration(velocityX, widgetWidth, dx)
                            mScroller.startScroll(currentOffsetX, 0, dx, 0, duration)
                        }
                    }

                    if (direction != null && duration != null) { //perform fling animation
                        mAnimator?.cancel()
                        mAnimator = ValueAnimator.ofFloat(1f).apply {
                            setDuration(duration.toLong())
                            addUpdateListener {
                                if (mScroller.computeScrollOffset()) {
                                    val offset = mScroller.currX.toFloat()
                                    topView.x = offset
                                    backView.x =
                                            if (mState == State.FLING_NEXT) offset + widgetWidth
                                            else offset - widgetWidth
                                }
                            }
                            addListener(object : AnimatorListenerAdapter() {

                                override fun onAnimationCancel(animation: Animator?) =
                                        onAnimationEnd(animation)

                                override fun onAnimationEnd(animation: Animator?) {
                                    if (direction != SlideDirection.Origin) {
                                        delegate.swap()
                                    }
                                    delegate.onDismissBackup(direction)
                                    mState = State.of(SlidableLayout.Mask.IDLE)
                                    if (direction != SlideDirection.Origin) {
                                        delegate.onCompleteCurrent(direction)
                                    }
                                    delegate.finishSlide(direction)
                                }
                            })
                            start()
                        }

                        val directionMask = if (mState satisfy SlidableLayout.Mask.NEXT) SlidableLayout.Mask.NEXT else SlidableLayout.Mask.PREV
                        mState = State.of(directionMask, SlidableLayout.Mask.FLING)
                        return true
                    } else {
                        return resetTouch()
                    }
                }

                private fun resetTouch(): Boolean {
                    mState = State.of(SlidableLayout.Mask.IDLE)
                    mBackupView?.let(::removeView)
                    return false
                }


                // 已修改
                override fun onDown(e: MotionEvent): Boolean {
                    downY = e.y
                    downX = e.x
                    startNestedScroll(ViewCompat.SCROLL_AXIS_HORIZONTAL)
                    return true
                }

                // 已修改
                private fun waitForFling(dx: Float, dy: Float): Boolean {
                    //eat all the dy
                    val unconsumedY = dy.toInt()
                    val consumedX = dx.toInt()
                    if (!dispatchNestedPreScroll(consumedX, unconsumedY, mScrollConsumed,
                                    mScrollOffset, ViewCompat.TYPE_NON_TOUCH)) {
                        dispatchNestedScroll(consumedX, 0, 0, unconsumedY,
                                mScrollOffset, ViewCompat.TYPE_NON_TOUCH)
                    }
                    return true
                }
            }


    // 已修改
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val action = event.action and MotionEvent.ACTION_MASK
        log("onInterceptTouchEvent action = $action, state = $mState")
        var intercept = false

        if (action != MotionEvent.ACTION_MOVE) {
            if (mState != State.IDLE) {
                intercept = true
            }
        }
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                startNestedScroll(ViewCompat.SCROLL_AXIS_HORIZONTAL)
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = Math.abs(event.y - downY)
                val dx = Math.abs(event.x - downX)
                if (dx > mTouchSlop && dx > 2 * dy) {
                    log("onInterceptTouchEvent requestDisallow")
                    requestParentDisallowInterceptTouchEvent()
                    intercept = true
                }
            }
        }
        return intercept || super.onInterceptTouchEvent(event)
    }

    private fun requestParentDisallowInterceptTouchEvent() {
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        childHelper.isNestedScrollingEnabled = enabled
    }

    override fun hasNestedScrollingParent() = childHelper.hasNestedScrollingParent()

    override fun hasNestedScrollingParent(type: Int) = childHelper.hasNestedScrollingParent(type)

    override fun isNestedScrollingEnabled() = childHelper.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int) = childHelper.startNestedScroll(axes)

    override fun startNestedScroll(axes: Int, type: Int) = childHelper.startNestedScroll(axes, type)

    override fun stopNestedScroll(type: Int) = childHelper.stopNestedScroll(type)

    override fun stopNestedScroll() = childHelper.stopNestedScroll()

    override fun dispatchNestedPreScroll(
            dx: Int, dy: Int, consumed: IntArray?,
            offsetInWindow: IntArray?, type: Int
    ) = childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)

    override fun dispatchNestedPreScroll(
            dx: Int, dy: Int, consumed: IntArray?,
            offsetInWindow: IntArray?
    ) = childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean) =
            childHelper.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float) =
            childHelper.dispatchNestedPreFling(velocityX, velocityY)

    /**
     * 自动滑到 [direction] 方向的视图。
     * 当且仅当布局处于静止状态时有效。
     *
     * @param direction 滑行方向：[SlideDirection.Next] 或 [SlideDirection.Prev]
     *
     * @return true 表示开始滑动
     */
    override fun slideTo(direction: SlideDirection): Boolean {  // 已修改
        if (direction != SlideDirection.Origin &&
                mState satisfy Mask.IDLE) {

            val delegate = mViewHolderDelegate
                    ?: return false
            val adapter = delegate.adapter

            startNestedScroll(ViewCompat.SCROLL_AXIS_HORIZONTAL, ViewCompat.TYPE_NON_TOUCH)
            requestParentDisallowInterceptTouchEvent()

            //模拟在该方向上，以 mockSpeed 的速度滑行
            val directionMask =
                    if (direction == SlideDirection.Prev) Mask.PREV else Mask.NEXT
            val mockSpeed =
                    if (direction == SlideDirection.Prev) mMinFlingSpeed else -mMinFlingSpeed

            mState =
                    if (adapter.canSlideTo(direction)) {
                        delegate.prepareBackup(direction)
                        State.of(directionMask, Mask.SLIDE)
                    } else {
                        State.of(directionMask, Mask.SLIDE, Mask.REJECT)
                    }

            val canSlide = !(mState satisfy Mask.REJECT)
            log("Auto slide to $direction" + if (canSlide) "" else " but reject")
            mGestureCallback.onUp(mockSpeed, 0f)
            return canSlide
        }
        return false
    }


}