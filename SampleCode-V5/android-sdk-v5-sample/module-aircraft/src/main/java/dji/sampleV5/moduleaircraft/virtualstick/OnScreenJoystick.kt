package dji.sampleV5.moduleaircraft.virtualstick

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.View.OnTouchListener
import dji.sampleV5.moduleaircraft.R
import dji.v5.utils.common.LogUtils
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class OnScreenJoystick(context: Context?, attrs: AttributeSet) : SurfaceView(context, attrs),
    SurfaceHolder.Callback, OnTouchListener {
    private var mJoystick: Bitmap? = null
    private lateinit var mHolder: SurfaceHolder
    private var mKnobBounds: Rect? = null
    private var mThread: JoystickThread? = null
    private var mKnobX = 0
    private var mKnobY = 0
    private var mKnobSize = 0
    private var mBackgroundSize = 0
    private var mRadius = 0f
    private var mJoystickListener: OnScreenJoystickListener? = null
    private var isAutoCentering = true

    private fun initGraphics() {
        val res = context.resources
        mJoystick = BitmapFactory.decodeResource(res, R.mipmap.joystick)
    }

    private fun initBounds(pCanvas: Canvas?) {
        mBackgroundSize = pCanvas!!.height
        mKnobSize = (mBackgroundSize * 0.4f).roundToInt()
        mKnobBounds = Rect()
        mRadius = mBackgroundSize * 0.5f
        mKnobX = ((mBackgroundSize - mKnobSize) * 0.5f).roundToInt()
        mKnobY = ((mBackgroundSize - mKnobSize) * 0.5f).roundToInt()
    }

    private fun init() {
        mHolder = holder
        mHolder.addCallback(this)
        mThread = JoystickThread()
        setZOrderOnTop(true)
        mHolder.setFormat(PixelFormat.TRANSPARENT)
        setOnTouchListener(this)
        isEnabled = true
        isAutoCentering = true
    }

    fun setJoystickListener(pJoystickListener: OnScreenJoystickListener?
    ) {
        mJoystickListener = pJoystickListener
    }

    override fun surfaceCreated(arg0: SurfaceHolder) {
        if (!mThread!!.isRunning()) {
            try {
                LogUtils.e("OnScreenJoystick","surfaceCreated","mThread!!.start()",mThread == null)
                mThread!!.start()
            }catch (e :Exception){
                LogUtils.e("OnScreenJoystick","surfaceCreated",e.message,e.cause)
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        //暂时实现
    }

    override fun surfaceDestroyed(arg0: SurfaceHolder) {
        LogUtils.e("OnScreenJoystick", "surfaceDestroyed")
        var retry = true
        mThread!!.setRunning(false)
        while (retry) {
            try {
                // code to kill Thread
                mThread!!.join()
                retry = false
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                LogUtils.e("surfaceDestroyed", e.message)
            }
        }
    }

    fun doDraw(pCanvas: Canvas?) {
        if (mKnobBounds == null) {
            initBounds(pCanvas)
        }
        mKnobBounds!![mKnobX, mKnobY, mKnobX + mKnobSize] = mKnobY + mKnobSize
        pCanvas!!.drawBitmap(mJoystick!!, null, mKnobBounds!!, null)
    }

    override fun onTouch(arg0: View, pEvent: MotionEvent): Boolean {
        val x = pEvent.x
        val y = pEvent.y
        when (pEvent.action) {
            MotionEvent.ACTION_UP -> if (isAutoCentering) {
                mKnobX = ((mBackgroundSize - mKnobSize) * 0.5f).roundToInt()
                mKnobY = ((mBackgroundSize - mKnobSize) * 0.5f).roundToInt()
            }
            else ->
                // Check if coordinates are in bounds. If they aren't move the knob
                // to the closest coordinate inbounds.
                if (checkBounds(x, y)) {
                    mKnobX = (x - mKnobSize * 0.5f).roundToInt()
                    mKnobY = (y - mKnobSize * 0.5f).roundToInt()
                } else {
                    val angle = Math.atan2((y - mRadius).toDouble(), (x - mRadius).toDouble())
                    mKnobX =
                        ((mRadius + (mRadius - mKnobSize * 0.5f) * cos(angle)).roundToInt() - mKnobSize * 0.5f).toInt()
                    mKnobY =
                        ((mRadius + (mRadius - mKnobSize * 0.5f) * sin(angle)).roundToInt() - mKnobSize * 0.5f).toInt()
                }
        }
        pushTouchEvent()
        return true
    }

    private fun checkBounds(pX: Float, pY: Float): Boolean {
        return (mRadius - pX).toDouble().pow(2.0) + (mRadius - pY).toDouble().pow(2.0) <= Math
            .pow((mRadius - mKnobSize * 0.5f).toDouble(), 2.0)
    }

    private fun pushTouchEvent() {
        if (mJoystickListener != null) {
            mJoystickListener!!.onTouch(
                this,
                (0.5f - mKnobX.toFloat() / (mBackgroundSize - mKnobSize)) * -2,
                (0.5f - mKnobY.toFloat() / (mBackgroundSize - mKnobSize)) * 2
            )
        }
    }

    private inner class JoystickThread : Thread() {
        private val running = AtomicBoolean(false)

        @Synchronized
        override fun start() {
            if (running.compareAndSet(false, true)) {
                super.start()
            }
        }

        fun isRunning(): Boolean {
            return running.get()
        }

        fun setRunning(pRunning: Boolean) {
            running.set(pRunning)
        }

        override fun run() {
            while (isRunning()) {
                // draw everything to the canvas
                var canvas: Canvas? = null
                try {
                    canvas = mHolder.lockCanvas(null)
                    synchronized(mHolder) {

                        // reset canvas
                        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                        doDraw(canvas)
                    }
                } catch (e: Exception) {
                    LogUtils.e("drawColor", e.message)
                } finally {
                    if (canvas != null) {
                        mHolder.unlockCanvasAndPost(canvas)
                    }
                }
            }
        }
    }

    init {
        initGraphics()
        init()
    }
}