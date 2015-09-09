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

    private static final String TAG = "CirclePickerView";

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
        private double mValuePerStep;

        /**
         * Angle offset for the zero value.
         */
        private int mWheelRotation;

        /**
         * Angle between two values
         */
        private double mDegreePerValue;

        /**
         * Helper angle used to compute differences while dragging the pointer
         */
        private double mLastAngle;

        /**
         * The originally set cycle value
         */
        private double mSetCycleValue;

        public AngleHelper(CirclePickerView owner)
        {
            mOwner = owner;
        }

        public void setWheelRotation(int zeroOffset)
        {
            mWheelRotation = zeroOffset;
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
            if (mSetCycleValue != 0) {
                return;
            }

            //Indeterminate size shouldn't be used
            if (minValue == -Float.MAX_VALUE || maxValue == Float.MAX_VALUE) {
                return;
            }

            double valuePerCycle = getCycleValueFromMinMax(mMaxValue, mMinValue);
            computeDegreePerValue(valuePerCycle);
        }

        private double getCycleValueFromMinMax(double max, double min)
        {
            if (min < 0 && max > 0) {
                return max - min + 1;
            } else {
                return Math.abs(max + min);
            }
        }

        public void setCycleValue(double valuePerCycle)
        {
            mSetCycleValue = valuePerCycle;
            if (valuePerCycle == 0) {
                //Indeterminate size isn't allowed here
                if (mMinValue == -Float.MAX_VALUE || mMaxValue == Float.MAX_VALUE) {
                    throw new IllegalStateException("Either the limits or the cycle value should be set");
                }
                valuePerCycle = getCycleValueFromMinMax(mMaxValue, mMinValue);
            }
            computeDegreePerValue(valuePerCycle);
        }

        private void computeDegreePerValue(double valuePerCycle)
        {
            int    stepsPerCycle = (int) (valuePerCycle / mValuePerStep);
            double degreePerStep = 360d / stepsPerCycle;
            mDegreePerValue = degreePerStep / mValuePerStep;
        }

        public void setStep(float step)
        {
            mValuePerStep = step;
            if (mDegreePerValue != 0) {
                setCycleValue(mSetCycleValue);
            }
            setAngle(mAngle);
        }

        private double getClosestValue(double value)
        {
            return ((int) Math.round(value / mValuePerStep)) * mValuePerStep;
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

            return degrees + 90 - mWheelRotation;
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

    private class CirclePickerRenderer
    {

        private final PointF mOrigin = new PointF(0, 0);

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
        private final Rect mTextBounds = new Rect();

        /**
         * Show a divider between values
         */
        private boolean mShowDivider;
        private boolean mShowValueText;
        private boolean mShowPointer;
        private float   mWheelRadius;

        /**
         * Number of pixels the origin of this view is moved in X- and Y-direction.
         * <p/>
         * Note: (Re)calculated in {@link #onMeasure(int, int)}.
         *
         * @see #onDraw(Canvas)
         */
        private float mTranslationOffset;

        public void draw(Canvas canvas)
        {
            canvas.translate(
                    mTranslationOffset + getPaddingLeft(),
                    mTranslationOffset + getPaddingTop()
            );
            canvas.rotate(mAngleHelper.mWheelRotation);

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
            canvas.rotate(-mAngleHelper.mWheelRotation);
            drawValueText(canvas, value);
        }

        private void drawDivider(Canvas canvas)
        {
            //Draw the divider lines if enabled
            if (mShowDivider) {
                double degreePerStep = mAngleHelper.mValuePerStep * mAngleHelper.mDegreePerValue;
                float length = mWheelColorPaint.getStrokeWidth() / 2 + 2;
                for (float i = 0; i < 360 - degreePerStep / 2; i += degreePerStep) {
                    canvas.rotate(i);
                    canvas.drawLine(
                            0,
                            -(mWheelRadius - length),
                            0,
                            -(mWheelRadius + length),
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
                mTextPaint.getTextBounds(
                        text,
                        0,
                        text.length(),
                        mTextBounds
                );
                canvas.drawText(
                        text,
                        mWheelRectangle.centerX() - mTextBounds.width() / 2,
                        mWheelRectangle.centerY() + mTextBounds.height() / 2,
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

        public void measure(int measuredWidth, int measuredHeight)
        {
            int smallerSize = Math.min(measuredWidth, measuredHeight);

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

        public void setWheelBackgroundStyle(int wheelBackgroundColor, float wheelRadius, float wheelWidth)
        {
            mWheelRadius = wheelRadius;

            mWheelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mWheelBackgroundPaint.setColor(wheelBackgroundColor);
            mWheelBackgroundPaint.setStyle(Style.STROKE);
            mWheelBackgroundPaint.setStrokeWidth(wheelWidth);
        }

        public void setWheelColorStyle(int wheelColor, float wheelWidth)
        {
            mWheelColorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mWheelColorPaint.setColor(wheelColor);
            mWheelColorPaint.setStyle(Style.STROKE);
            mWheelColorPaint.setStrokeWidth(wheelWidth);
        }

        public void setDividerStyle(int dividerColor, float dividerWidth)
        {
            mDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mDividerPaint.setColor(dividerColor);
            mDividerPaint.setStrokeWidth(dividerWidth);
        }

        public void setPointerStyle(int pointerColor, int pointerHaloColor, float pointerRadius, float pointerHaloWidth)
        {
            mPointerRadius = pointerRadius;
            mPointerHaloWidth = pointerHaloWidth;

            mPointerHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPointerHaloPaint.setStyle(Style.STROKE);
            mPointerHaloPaint.setStrokeWidth(pointerHaloWidth);
            mPointerHaloPaint.setColor(pointerHaloColor);

            mPointerColorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPointerColorPaint.setStyle(Style.FILL);
            mPointerColorPaint.setColor(pointerColor);
        }

        public void setValueTextSize(int textColor, int textSize)
        {
            mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
            mTextPaint.setColor(textColor);
            mTextPaint.setStyle(Style.FILL_AND_STROKE);
            mTextPaint.setTextAlign(Align.LEFT);
            mTextPaint.setTextSize(textSize);
        }
    }

    /*
     * Constants used to save/restore the instance state.
     */
    private static final String STATE_PARENT = "parent";
    private static final String STATE_ANGLE  = "angle";

    private static final int   TEXT_SIZE_DEFAULT_VALUE            = 25;
    private static final float COLOR_WHEEL_STROKE_WIDTH_DEF_VALUE = 8;
    private static final float DIVIDER_WIDTH_DEF_VALUE            = 2;
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
     * {@code true} if the user clicked on the pointer to start the move mode.
     * {@code false} once the user stops touching the screen.
     *
     * @see #onTouchEvent(MotionEvent)
     */
    private boolean mUserIsMovingPointer = false;

    /**
     * {@code ValueFormatter} used to format the displayed text
     */
    private ValueFormatter mValueFormatter = new SimpleValueFormatter("%.1f");

    private boolean mInteractionEnabled;

    private final AngleHelper          mAngleHelper = new AngleHelper(this);
    private final CirclePickerRenderer mRenderer    = new CirclePickerRenderer();

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

    /**
     * Set the {@code ValueFormatter} instance to format the value text with
     *
     * @param formatter
     */
    public void setValueFormatter(ValueFormatter formatter)
    {
        mValueFormatter = formatter;
        invalidate();
    }

    /**
     * Set the degree by which the wheel will be rotated.
     * <p/>
     * 0 is no rotation (0 value is at 12 o'clock position), positive numbers rotate clockwise.
     *
     * @param value
     */
    public void setWheelRotation(int value)
    {
        mAngleHelper.setWheelRotation(value);
        invalidate();
    }

    /**
     * Show or hide the value dividers
     *
     * @param enabled True to show, false to hide the dividers
     */
    public void setShowDivider(boolean enabled)
    {
        mRenderer.mShowDivider = enabled;
        invalidate();
    }

    /**
     * Show or hide the value text in the middle
     *
     * @param enabled
     */
    public void setShowValueText(boolean enabled)
    {
        mRenderer.mShowValueText = enabled;
        invalidate();
    }

    /**
     * Show or hide the pointer circle
     *
     * @param enabled
     */
    public void setShowPointer(boolean enabled)
    {
        mRenderer.mShowPointer = enabled;
        invalidate();
    }

    /**
     * Set the difference between two selectable values
     * <p/>
     * Note: this affects the cycle value
     *
     * @param step
     */
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

        setRendererStyles(a);

        mInteractionEnabled = a.getBoolean(R.styleable.CirclePickerView_interactive, true);

        mAngleHelper.setMaxValue(
                a.getFloat(R.styleable.CirclePickerView_max, MAX_POINT_DEF_VALUE)
        );
        mAngleHelper.setMinValue(
                a.getFloat(R.styleable.CirclePickerView_min, MIN_POINT_DEF_VALUE)
        );
        mAngleHelper.setStep(
                a.getFloat(R.styleable.CirclePickerView_step, STEP_DEF_VALUE)
        );
        mAngleHelper.setCycleValue(
                a.getFloat(R.styleable.CirclePickerView_cycleValue, CYCLE_DEF_VALUE)
        );
        mAngleHelper.setWheelRotation(
                a.getInteger(R.styleable.CirclePickerView_wheelRotation, ZERO_OFFSET_DEF_VALUE)
        );

        setValue(a.getFloat(R.styleable.CirclePickerView_value, 0));

        a.recycle();
    }

    private void setRendererStyles(TypedArray a)
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
        float pointerRadius = a.getDimension(
                R.styleable.CirclePickerView_pointerRadius,
                POINTER_RADIUS_DEF_VALUE
        );
        float pointerHaloWidth = a.getDimension(
                R.styleable.CirclePickerView_pointerHaloWidth,
                POINTER_HALO_WIDTH_DEF_VALUE
        );
        float wheelRadius = a.getDimension(
                R.styleable.CirclePickerView_wheelRadius,
                WHEEL_RADIUS_DEF_VALUE
        );

        mRenderer.mShowDivider = a.getBoolean(R.styleable.CirclePickerView_showDivider, false);
        mRenderer.mShowPointer = a.getBoolean(R.styleable.CirclePickerView_showPointer, true);
        mRenderer.mShowValueText = a.getBoolean(R.styleable.CirclePickerView_showValueText, true);

        mRenderer.setWheelBackgroundStyle(wheelBackgroundColor, wheelRadius, wheelWidth);
        mRenderer.setWheelColorStyle(wheelColor, wheelWidth);
        mRenderer.setDividerStyle(dividerColor, dividerWidth);
        mRenderer.setPointerStyle(pointerColor, pointerHaloColor, pointerRadius, pointerHaloWidth);
        mRenderer.setValueTextSize(textColor, textSize);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        mRenderer.draw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        mRenderer.measure(getMeasuredWidth(), getMeasuredHeight());
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event)
    {
        if (!mInteractionEnabled) {
            return false;
        }
        // Convert coordinates to our internal coordinate system
        float x = event.getX() - mRenderer.mTranslationOffset;
        float y = event.getY() - mRenderer.mTranslationOffset;

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
        if (mUserIsMovingPointer) {
            if (mOnValueChangeListener != null) {
                mOnValueChangeListener.onValueChanging(this, value);
            }
            invalidate();
        }
    }

    public void setOnValueChangeListener(OnValueChangeListener listener)
    {
        mOnValueChangeListener = listener;
    }
}
