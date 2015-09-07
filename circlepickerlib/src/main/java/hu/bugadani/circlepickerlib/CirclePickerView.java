package hu.bugadani.circlepickerlib;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.biowink.clue.ArcUtils;

import hu.bugadani.circlepickerlib.formatter.SimpleValueFormatter;
import hu.bugadani.circlepickerlib.formatter.ValueFormatter;

public class CirclePickerView extends View
{

    private static final String TAG     = "CirclePickerView";
    private              PointF mOrigin = new PointF(0, 0);

    public interface OnValueChangeListener
    {

        void onValueChanging(CirclePickerView pickerView, double value);

        void onValueChanged(CirclePickerView pickerView, double value);
    }

    private static class AngleHelper
    {

        /**
         * Reference to the view
         */
        private final CirclePickerView mOwner;

        /**
         * The current angle
         */
        private double mAngle;

        /**
         * The maximum allowed value
         */
        private double mMaxValue;

        /**
         * The minimum allowed value
         */
        private double mMinValue;

        /**
         * Difference of two consequent values
         */
        private double mStep;

        /**
         * Angle offset for the zero value.
         */
        private int mZeroOffset;

        /**
         * Indicator to show if the cycle value is explicitly set
         */
        private boolean mCycleValueSet = false;

        /**
         * Angle between two values
         */
        private double mDegreePerValue;

        /**
         * Helper angle used to compute differences while dragging the pointer
         */
        private double mLastAngle;

        public AngleHelper(CirclePickerView owner)
        {
            mOwner = owner;
        }

        public void setZeroOffset(int zeroOffset)
        {
            mZeroOffset = zeroOffset;
        }

        public void setMinValue(double minValue)
        {
            mMinValue = minValue;
            computeCycleValue(mMinValue, mMaxValue);
        }

        public void setMaxValue(double maxValue)
        {
            mMaxValue = maxValue;
            computeCycleValue(mMinValue, mMaxValue);
        }

        private void computeCycleValue(double minValue, double maxValue)
        {
            //Don't overwrite explicit settings
            if (mCycleValueSet) {
                return;
            }

            //Indeterminate size shouldn't be used
            if (minValue == -Float.MAX_VALUE || maxValue == Float.MAX_VALUE) {
                return;
            }

            double cycleValue = mMaxValue - mMinValue + 1;
            mDegreePerValue = 360d / cycleValue;
        }

        public void setCycleValue(double cycleValue)
        {
            mCycleValueSet = !(cycleValue == 0);
            if (!mCycleValueSet) {
                //Indeterminate size isn't allowed here
                if (mMinValue == -Float.MAX_VALUE || mMaxValue == Float.MAX_VALUE) {
                    throw new IllegalStateException("Either the limits or the cycle value should be set");
                }
                cycleValue = mMaxValue - mMinValue + 1;
            }
            mDegreePerValue = 360d / cycleValue;
        }

        public void setStep(float step)
        {
            mStep = step;
            setAngle(mAngle);
        }

        private double getClosestValue(double value)
        {
            return ((int) Math.round(value / mStep)) * mStep;
        }

        public void handleTouch(float x, float y)
        {
            final double currentAngleInCycle = getCurrentAngleInCycle(x, y);
            final double computedAngle       = computeAngleForTouch(currentAngleInCycle);

            setAngle(computedAngle);
        }

        public void handleDrag(float x, float y)
        {
            final double currentAngleInCycle = getCurrentAngleInCycle(x, y);
            final double computedAngle       = computeAngleForMove(currentAngleInCycle);

            setAngle(computedAngle);
        }

        private double getCurrentAngleInCycle(float x, float y)
        {
            final double atg     = Math.atan2(y, x);
            final double degrees = Math.toDegrees(atg);

            return degrees + 90 - mZeroOffset;
        }

        private double computeAngleForTouch(double angle)
        {
            double diff = mod360(angle) - mod360(mAngle);

            //For touch, the 180° separates incrementing and decrementing
            if (diff < -180) {
                diff += 360;
            } else if (diff > 180) {
                diff -= 360;
            }

            mLastAngle = mAngle + diff;

            return limit(mAngle + diff, valueToDegree(mMinValue), valueToDegree(mMaxValue));
        }

        private double computeAngleForMove(double angle)
        {
            double diff = mod360(angle) - mod360(mLastAngle);

            if (diff < -90) {
                diff += 360;
            } else if (diff > 270) {
                diff -= 360;
            }

            mLastAngle = mLastAngle + diff;

            if (mLastAngle > mAngle + 360) {
                mLastAngle -= 360;
            } else if (mLastAngle < mAngle - 360) {
                mLastAngle += 360;
            }

            return limit(mLastAngle, valueToDegree(mMinValue), valueToDegree(mMaxValue));
        }

        private double mod360(double angle)
        {
            double mod = (angle % 360);
            if (mod < 0) {
                mod += 360;
            }
            return mod;
        }

        private void setAngle(double angle)
        {
            double value = degreeToValue(angle);
            setValue(value);
        }

        public void setValue(double value)
        {
            value = getClosestValue(value);
            mAngle = valueToDegree(value);
            value = limit(value, mMinValue, mMaxValue);

            mOwner.updateValue(value);
        }

        private double limit(double number, double min, double max)
        {
            if (number < min) {
                return min;
            } else if (number > max) {
                return max;
            } else {
                return number;
            }
        }

        public double getValue()
        {
            return degreeToValue(getAngle());
        }

        private double degreeToValue(double angle)
        {
            return angle / mDegreePerValue;
        }

        private double valueToDegree(double value)
        {
            return value * mDegreePerValue;
        }

        public double getAngle()
        {
            return limit(mAngle, valueToDegree(mMinValue), valueToDegree(mMaxValue));
        }
    }

    /*
     * Constants used to save/restore the instance state.
     */
    private static final String STATE_PARENT = "parent";
    private static final String STATE_ANGLE  = "angle";

    private static final int   TEXT_SIZE_DEFAULT_VALUE            = 25;
    private static final float COLOR_WHEEL_STROKE_WIDTH_DEF_VALUE = 8;
    private static final float DIVIDER_WIDTH_DEF_VALUE            = 3;
    private static final float POINTER_RADIUS_DEF_VALUE           = 8;
    private static final float WHEEL_RADIUS_DEF_VALUE             = 0;
    private static final float MAX_POINT_DEF_VALUE                = Float.MAX_VALUE;
    private static final float MIN_POINT_DEF_VALUE                = -Float.MAX_VALUE;
    private static final float CYCLE_DEF_VALUE                    = 0;
    private static final float POINTER_HALO_WIDTH_DEF_VALUE       = 10;
    private static final int   ZERO_OFFSET_DEF_VALUE              = 0;
    private static final float STEP_DEF_VALUE                     = 0.1f;

    private OnValueChangeListener mOnValueChangeListener;

    /**
     * {@code Paint} instance used to draw the wheel background.
     */
    private Paint mWheelBackgroundPaint;

    /**
     * {@code Paint} instance used to draw the color wheel.
     */
    private Paint mWheelColorPaint;

    /**
     * {@code Paint} instance used to draw the pointer's "halo".
     */
    private Paint mPointerHaloPaint;

    /**
     * {@code Paint} instance used to draw the pointer (the selected color).
     */
    private Paint mPointerColorPaint;

    /**
     * {@code Paint} instance used to draw the value text.
     */
    private Paint mTextPaint;

    /**
     * {@code Paint} instance used to draw the divider lines.
     */
    private Paint mDividerPaint;

    /**
     * The radius of the pointer (in pixels).
     */
    private float mPointerRadius;

    /**
     * The width of the pointer halo
     */
    private float mPointerHaloWidth;

    /**
     * The rectangle enclosing the color wheel.
     */
    private final RectF mWheelRectangle = new RectF();

    /**
     * Bounding box for the value text.
     */
    private final Rect mBounds = new Rect();

    /**
     * {@code true} if the user clicked on the pointer to start the move mode.
     * {@code false} once the user stops touching the screen.
     *
     * @see #onTouchEvent(MotionEvent)
     */
    private boolean mUserIsMovingPointer = false;

    /**
     * Number of pixels the origin of this view is moved in X- and Y-direction.
     * <p/>
     * Note: (Re)calculated in {@link #onMeasure(int, int)}.
     *
     * @see #onDraw(Canvas)
     */
    private float mTranslationOffset;

    /**
     * {@code ValueFormatter} used to format the displayed text
     */
    private ValueFormatter mValueFormatter = new SimpleValueFormatter("%.1f");

    /**
     * Show a divider between values
     */
    private boolean mShowDivider;
    private boolean mShowValueText;
    private boolean mShowPointer;
    private float   mWheelRadius;
    private boolean mInteractionEnabled;
    private AngleHelper mAngleHelper = new AngleHelper(this);

    public CirclePickerView(Context context)
    {
        super(context);
        init(null, 0);
    }

    public CirclePickerView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init(attrs, 0);
    }

    public CirclePickerView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    public void setValueFormatter(ValueFormatter formatter)
    {
        mValueFormatter = formatter;
    }

    public void setZeroOffset(int value)
    {
        mAngleHelper.setZeroOffset(value);
        invalidate();
    }

    public void setDividerEnabled(boolean enabled)
    {
        mShowDivider = enabled;
        invalidate();
    }

    public void setSteps(float step)
    {
        mAngleHelper.setStep(step);
        invalidate();
    }

    private void init(AttributeSet attrs, int defStyle)
    {
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs,
                R.styleable.CirclePickerView,
                defStyle,
                0
        );
        mPointerRadius = a.getDimension(
                R.styleable.CirclePickerView_pointerRadius,
                POINTER_RADIUS_DEF_VALUE
        );
        mPointerHaloWidth = a.getDimension(
                R.styleable.CirclePickerView_pointerHaloWidth,
                POINTER_HALO_WIDTH_DEF_VALUE
        );
        mWheelRadius = a.getDimension(
                R.styleable.CirclePickerView_wheelRadius,
                WHEEL_RADIUS_DEF_VALUE
        );

        mShowValueText = a.getBoolean(R.styleable.CirclePickerView_showValueText, true);
        mShowDivider = a.getBoolean(R.styleable.CirclePickerView_showDivider, false);
        mShowPointer = a.getBoolean(R.styleable.CirclePickerView_showPointer, true);
        mInteractionEnabled = a.getBoolean(R.styleable.CirclePickerView_interactive, true);

        mAngleHelper.setMaxValue(
                a.getFloat(R.styleable.CirclePickerView_max, MAX_POINT_DEF_VALUE)
        );
        mAngleHelper.setMinValue(
                a.getFloat(R.styleable.CirclePickerView_min, MIN_POINT_DEF_VALUE)
        );
        mAngleHelper.setCycleValue(
                a.getFloat(R.styleable.CirclePickerView_cycleValue, CYCLE_DEF_VALUE)
        );
        mAngleHelper.setZeroOffset(
                a.getInteger(R.styleable.CirclePickerView_zeroOffset, ZERO_OFFSET_DEF_VALUE)
        );
        mAngleHelper.setStep(
                a.getFloat(R.styleable.CirclePickerView_step, STEP_DEF_VALUE)
        );

        createPaintObjects(a);

        setValue(a.getFloat(R.styleable.CirclePickerView_value, 0));

        a.recycle();
    }

    private void createPaintObjects(TypedArray a)
    {
        //Get size values
        int textSize = a.getDimensionPixelSize(
                R.styleable.CirclePickerView_textSize,
                TEXT_SIZE_DEFAULT_VALUE
        );
        float wheelWidth = a.getDimension(
                R.styleable.CirclePickerView_wheelStrokeWidth,
                COLOR_WHEEL_STROKE_WIDTH_DEF_VALUE
        );
        float dividerWidth = a.getDimension(
                R.styleable.CirclePickerView_dividerWidth,
                DIVIDER_WIDTH_DEF_VALUE
        );

        //Get color values
        int wheelColor = a.getColor(
                R.styleable.CirclePickerView_wheelActiveColor,
                Color.CYAN
        );
        int wheelBackgroundColor = a.getColor(
                R.styleable.CirclePickerView_wheelBackgroundColor,
                Color.DKGRAY
        );
        int dividerColor = a.getColor(
                R.styleable.CirclePickerView_dividerColor,
                Color.DKGRAY
        );
        int pointerColor = a.getColor(
                R.styleable.CirclePickerView_pointerColor,
                wheelColor
        );
        int pointerHaloColor = a.getColor(
                R.styleable.CirclePickerView_pointerHaloColor,
                wheelBackgroundColor
        );
        int textColor = a.getColor(
                R.styleable.CirclePickerView_textColor,
                wheelColor
        );

        mWheelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWheelBackgroundPaint.setColor(wheelBackgroundColor);
        mWheelBackgroundPaint.setStyle(Style.STROKE);
        mWheelBackgroundPaint.setStrokeWidth(wheelWidth);

        mDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDividerPaint.setColor(dividerColor);
        mDividerPaint.setStrokeWidth(dividerWidth);

        mWheelColorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWheelColorPaint.setColor(wheelColor);
        mWheelColorPaint.setStyle(Style.STROKE);
        mWheelColorPaint.setStrokeWidth(wheelWidth);

        mPointerHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPointerHaloPaint.setStyle(Style.STROKE);
        mPointerHaloPaint.setStrokeWidth(mPointerHaloWidth);
        mPointerHaloPaint.setColor(pointerHaloColor);

        mPointerColorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPointerColorPaint.setStyle(Style.FILL);
        mPointerColorPaint.setColor(pointerColor);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
        mTextPaint.setColor(textColor);
        mTextPaint.setStyle(Style.FILL_AND_STROKE);
        mTextPaint.setTextAlign(Align.LEFT);
        mTextPaint.setTextSize(textSize);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        canvas.translate(
                mTranslationOffset + getPaddingLeft(), mTranslationOffset + getPaddingTop());
        canvas.rotate(mAngleHelper.mZeroOffset);

        final float  colorStartAngle = (float) -90;
        final double value           = mAngleHelper.getValue();

        float colorSweepAngle = (float) mAngleHelper.getAngle() % 360;

        if (colorSweepAngle < 0) {
            colorSweepAngle += 360;
        } else if (value > 0 && colorSweepAngle == 0) {
            colorSweepAngle = 360;
        }

        float backgroundStartAngle = (colorStartAngle + colorSweepAngle) % 360;
        float backgroundSweepAngle = (360 - colorSweepAngle);

        if (value == 0) {
            // Draw the wheel.
            ArcUtils.drawArc(
                    canvas,
                    mOrigin,
                    mWheelRadius,
                    0,
                    360,
                    mWheelBackgroundPaint
            );
        } else if (value > 0) {
            // Draw the "background" of the wheel.
            ArcUtils.drawArc(
                    canvas,
                    mOrigin,
                    mWheelRadius,
                    backgroundStartAngle,
                    backgroundSweepAngle,
                    mWheelBackgroundPaint
            );
            // Draw the wheel.
            ArcUtils.drawArc(
                    canvas,
                    mOrigin,
                    mWheelRadius,
                    colorStartAngle,
                    colorSweepAngle,
                    mWheelColorPaint
            );
        } else {
            // Draw the "background" of the wheel.
            ArcUtils.drawArc(
                    canvas,
                    mOrigin,
                    mWheelRadius,
                    backgroundStartAngle,
                    backgroundSweepAngle,
                    mWheelColorPaint
            );
            // Draw the wheel.
            ArcUtils.drawArc(
                    canvas,
                    mOrigin,
                    mWheelRadius,
                    colorStartAngle,
                    colorSweepAngle,
                    mWheelBackgroundPaint
            );
        }

        drawDivider(canvas);
        drawPointer(canvas, backgroundStartAngle);
        canvas.rotate(-mAngleHelper.mZeroOffset);
        drawValueText(canvas, value);
    }

    private void drawDivider(Canvas canvas)
    {
        //Draw the divider lines if enabled
        if (mShowDivider) {
            double degreePerStep = mAngleHelper.mStep * mAngleHelper.mDegreePerValue;
            float length = mWheelColorPaint.getStrokeWidth() / 2 + 2;
            for (int i = 0; i < 360; i += degreePerStep) {
                canvas.rotate(i);
                canvas.drawLine(
                        0,
                        mWheelRadius + length,
                        0,
                        mWheelRadius - length,
                        mDividerPaint
                );
                canvas.rotate(-i);
            }
        }
    }

    private void drawValueText(Canvas canvas, double value)
    {
        //Draw the value text if enabled
        if (mShowValueText) {
            final String text = mValueFormatter.format(value);
            mTextPaint.getTextBounds(text, 0, text.length(), mBounds);
            canvas.drawText(
                    text,
                    mWheelRectangle.centerX() - mBounds.width() / 2,
                    mWheelRectangle.centerY() + mBounds.height() / 2,
                    mTextPaint
            );
        }
    }

    private void drawPointer(Canvas canvas, float angle)
    {
        if (mShowPointer) {
            final double backgroundStartRadians = Math.toRadians(angle);

            float pointerX = (float) (mWheelRadius * Math.cos(backgroundStartRadians));
            float pointerY = (float) (mWheelRadius * Math.sin(backgroundStartRadians));

            // Draw the pointer's "halo"
            canvas.drawCircle(
                    pointerX,
                    pointerY,
                    mPointerRadius,
                    mPointerHaloPaint
            );

            // Draw the pointer (using the currently selected color)
            canvas.drawCircle(
                    pointerX,
                    pointerY,
                    mPointerRadius,
                    mPointerColorPaint
            );
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int smallerSize = Math.min(getMeasuredWidth(), getMeasuredHeight());

        if (mWheelRadius == 0) {
            mWheelRadius = smallerSize / 2 - mPointerRadius - mPointerHaloWidth;

            mWheelRadius -= Math.max(
                    (getPaddingBottom() + getPaddingTop()) / 2,
                    (getPaddingRight() + getPaddingLeft()) / 2
            );

            mTranslationOffset = smallerSize / 2;
            Log.d(TAG, "Automatic radius: " + mWheelRadius);
        } else {
            mWheelRadius = smallerSize > 0 ? Math.min(smallerSize, mWheelRadius) : mWheelRadius;

            mTranslationOffset = (mWheelRadius + mPointerRadius + mPointerHaloWidth);
            setMeasuredDimension(
                    (int) mTranslationOffset * 2 + (getPaddingBottom() + getPaddingTop()),
                    (int) mTranslationOffset * 2 + (getPaddingRight() + getPaddingLeft())
            );
        }

        mWheelRectangle.set(
                -mWheelRadius,
                -mWheelRadius,
                mWheelRadius,
                mWheelRadius
        );
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event)
    {
        if (!mInteractionEnabled) {
            return false;
        }
        // Convert coordinates to our internal coordinate system
        float x = event.getX() - mTranslationOffset;
        float y = event.getY() - mTranslationOffset;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Check whether the user pressed on (or near) the pointer
                mAngleHelper.handleTouch(x, y);

                mUserIsMovingPointer = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mUserIsMovingPointer) {
                    mAngleHelper.handleDrag(x, y);
                }
                break;
            case MotionEvent.ACTION_UP:
                mUserIsMovingPointer = false;
                if (mOnValueChangeListener != null) {
                    mOnValueChangeListener.onValueChanged(this, mAngleHelper.getValue());
                }
                break;
        }
        // Fix scrolling
        if (event.getAction() == MotionEvent.ACTION_MOVE && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }

        return true;
    }

    @Override
    protected Parcelable onSaveInstanceState()
    {
        Parcelable superState = super.onSaveInstanceState();

        Bundle state = new Bundle();
        state.putParcelable(STATE_PARENT, superState);
        state.putDouble(STATE_ANGLE, mAngleHelper.getAngle());

        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state)
    {
        Bundle savedState = (Bundle) state;

        Parcelable superState = savedState.getParcelable(STATE_PARENT);
        super.onRestoreInstanceState(superState);

        mAngleHelper.setAngle(savedState.getDouble(STATE_ANGLE));
    }

    /**
     * Get the selected value
     *
     * @return the value between 0 and mMaxValue
     */
    public double getValue()
    {
        return mAngleHelper.getValue();
    }

    public void setValue(double value)
    {
        mAngleHelper.setValue(value);
    }

    private void updateValue(double value)
    {
        if (mOnValueChangeListener != null) {
            mOnValueChangeListener.onValueChanging(this, value);
        }
        invalidate();
    }

    public void setOnValueChangeListener(OnValueChangeListener listener)
    {
        mOnValueChangeListener = listener;
    }

}
