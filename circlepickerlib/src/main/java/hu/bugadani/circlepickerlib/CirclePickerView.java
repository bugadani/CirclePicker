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
import android.os.Handler;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.biowink.clue.ArcUtils;

import hu.bugadani.circlepickerlib.formatter.SimpleValueFormatter;
import hu.bugadani.circlepickerlib.formatter.ValueFormatter;

public class CirclePickerView extends View {

    private static final String TAG = "CirclePickerView";

    public enum LabelPosition {
        None,
        Above,
        Below,
        Start,
        End
    }

    private enum TouchPosition {
        OnWheel,
        Inside,
        Outside
    }

    public interface OnValueChangeListener {

        void onValueChanging(CirclePickerView pickerView, double value);

        void onValueChanged(CirclePickerView pickerView, double value);
    }

    private static class AngleHelper {

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
         * The wheel radius
         */
        private double mWheelRadius;

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

        public AngleHelper(CirclePickerView owner) {
            mOwner = owner;
        }

        public void setWheelRotation(int zeroOffset) {
            mWheelRotation = zeroOffset;
        }

        public void setWheelRadius(double radius) {
            mWheelRadius = radius;
        }

        public void setMinValue(double minValue) {
            mMinValue = minValue;
            computeCycleValue(mMinValue, mMaxValue);
        }

        public void setMaxValue(double maxValue) {
            mMaxValue = maxValue;
            computeCycleValue(mMinValue, mMaxValue);
        }

        private void computeCycleValue(double minValue, double maxValue) {
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

        private double getCycleValueFromMinMax(double max, double min) {
            if (min < 0 && max > 0) {
                return max - min + 1;
            } else {
                return Math.abs(max + min);
            }
        }

        public void setCycleValue(double valuePerCycle) {
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

        private void computeDegreePerValue(double valuePerCycle) {
            int stepsPerCycle = (int) (valuePerCycle / mValuePerStep);
            double degreePerStep = 360d / stepsPerCycle;
            mDegreePerValue = degreePerStep / mValuePerStep;
        }

        public void setStep(float step) {
            mValuePerStep = step;
            if (mDegreePerValue != 0) {
                setCycleValue(mSetCycleValue);
            }
            setAngle(mAngle);
        }

        private double getClosestValue(double value) {
            return ((int) Math.round(value / mValuePerStep)) * mValuePerStep;
        }

        public TouchPosition handleTouch(float x, float y) {
            double distFromOrigin = Math.hypot(x, y);

            if (distFromOrigin < mWheelRadius * 0.6) {
                return TouchPosition.Inside;
            } else if (distFromOrigin > mWheelRadius * 1.4) {
                return TouchPosition.Outside;
            } else {
                final double currentAngleInCycle = getCurrentAngleInCycle(x, y);
                final double computedAngle = computeAngleForTouch(currentAngleInCycle);

                setAngle(computedAngle);

                return TouchPosition.OnWheel;
            }
        }

        public void handleDrag(float x, float y) {
            final double currentAngleInCycle = getCurrentAngleInCycle(x, y);
            final double computedAngle = computeAngleForMove(currentAngleInCycle);

            setAngle(computedAngle);
        }

        private double getCurrentAngleInCycle(float x, float y) {
            final double atg = Math.atan2(y, x);
            final double degrees = Math.toDegrees(atg);

            return degrees + 90 - mWheelRotation;
        }

        private double computeAngleForTouch(double angle) {
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

        private double computeAngleForMove(double angle) {
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

        private double mod360(double angle) {
            double mod = (angle % 360);
            if (mod < 0) {
                mod += 360;
            }
            return mod;
        }

        private void setAngle(double angle) {
            double value = degreeToValue(angle);
            setValue(value);
        }

        public void setValue(double value) {
            value = getClosestValue(value);
            mAngle = valueToDegree(value);
            value = limit(value, mMinValue, mMaxValue);

            mOwner.updateValue(value);
        }

        private double limit(double number, double min, double max) {
            if (number < min) {
                return min;
            } else if (number > max) {
                return max;
            } else {
                return number;
            }
        }

        public double getValue() {
            return degreeToValue(getAngle());
        }

        private double degreeToValue(double angle) {
            return angle / mDegreePerValue;
        }

        private double valueToDegree(double value) {
            return value * mDegreePerValue;
        }

        public double getAngle() {
            return limit(mAngle, valueToDegree(mMinValue), valueToDegree(mMaxValue));
        }
    }

    private class CirclePickerRenderer {

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
         * {@code Paint} instance used to draw the label.
         */
        private Paint mLabelPaint;

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
         * Bounding box for the label.
         */
        private final Rect mLabelBounds = new Rect();

        /**
         * Show a divider between values
         */
        private boolean mShowDivider;
        private boolean mShowValueText;
        private boolean mShowPointer;
        private float mWheelRadius;
        private LabelPosition mLabelPosition;
        private String mLabel;

        /**
         * Number of pixels the origin of this view is moved in X- and Y-direction.
         * <p/>
         * Note: (Re)calculated in {@link #onMeasure(int, int)}.
         *
         * @see #onDraw(Canvas)
         */
        private float mTranslationOffsetX;
        private float mTranslationOffsetY;

        public void draw(Canvas canvas) {
            canvas.translate(
                    mTranslationOffsetX + getPaddingLeft(),
                    mTranslationOffsetY + getPaddingTop()
            );
            canvas.rotate(mAngleHelper.mWheelRotation);

            final float colorStartAngle = (float) -90;
            final double value = mAngleHelper.getValue();

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
            drawText(canvas, value);
        }

        private void drawDivider(Canvas canvas) {
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

        private void drawText(Canvas canvas, double value) {
            if (!mShowValueText && mLabelPosition == LabelPosition.None) {
                return;
            }
            //Get the text bounds
            final float labelLineHeight;
            final float textLineHeight;

            float labelWidth;
            float textWidth;
            float textBaseBelowCenter;
            float labelBaseBelowCenter;

            String text = "";
            if (!mShowValueText) {
                mTextBounds.setEmpty();
                textWidth = 0;
                textLineHeight = 0;
                textBaseBelowCenter = 0;
            } else {
                text = mValueFormatter.format(value);
                mTextPaint.getTextBounds(
                        text,
                        0,
                        text.length(),
                        mTextBounds
                );
                final Paint.FontMetrics textFontMetrics = mTextPaint.getFontMetrics();
                textLineHeight = mTextBounds.height();
                textBaseBelowCenter = (textFontMetrics.bottom - textLineHeight) / 2f;
                textWidth = mTextPaint.measureText(text);
            }

            //Get the label bounds
            if (mLabelPosition == LabelPosition.None) {
                mLabelBounds.setEmpty();
                labelWidth = 0;
                labelLineHeight = 0;
                labelBaseBelowCenter = 0;
            } else {
                final Paint.FontMetrics labelFontMetrics = mLabelPaint.getFontMetrics();
                mLabelPaint.getTextBounds(
                        mLabel,
                        0,
                        mLabel.length(),
                        mLabelBounds
                );
                labelLineHeight = mLabelBounds.height();
                labelBaseBelowCenter = (labelFontMetrics.bottom - labelLineHeight) / 2f;
                labelWidth = mLabelPaint.measureText(mLabel);
            }

            final float boxHeight = labelLineHeight + textLineHeight;

            final float top = mWheelRectangle.centerY() - boxHeight / 2f;
            final float bottom = mWheelRectangle.centerY() + boxHeight / 2f;

            //Get the text positions
            float textX = 0;
            float textY = 0;
            float labelX = 0;
            float labelY = 0;

            //Common coordinates
            switch (mLabelPosition) {
                case None:
                case Above:
                case Below:
                    labelX = mWheelRectangle.centerX() - labelWidth / 2f;
                    textX = mWheelRectangle.centerX() - textWidth / 2f;
                    break;
                case Start:
                case End:
                    labelY = mWheelRectangle.centerY() + labelBaseBelowCenter;
                    textY = mWheelRectangle.centerY() + textBaseBelowCenter;
                    break;
            }

            //Differentiating coordinates
            switch (mLabelPosition) {
                default:
                case None:
                    textY = mWheelRectangle.centerY() - textBaseBelowCenter;
                    break;
                case Above:
                    labelY = top + labelLineHeight / 2f - labelBaseBelowCenter;
                    textY = bottom + textLineHeight / 2f + textBaseBelowCenter;
                    break;
                case Below:
                    labelY = bottom + labelLineHeight / 2f + labelBaseBelowCenter;
                    textY = top + textLineHeight / 2f - textBaseBelowCenter;
                    break;
                case Start:
                    labelX = mWheelRectangle.centerX() - Math.max(labelWidth, textWidth) / 2f;
                    textX = labelX + labelWidth;
                    break;
                case End:
                    textX = mWheelRectangle.centerX() - Math.max(labelWidth, textWidth) / 2f;
                    labelX = textX + textWidth;
                    break;
            }

            //Draw the value text if enabled
            if (mShowValueText) {
                canvas.drawText(
                        text,
                        textX,
                        textY,
                        mTextPaint
                );
            }
            if (mLabelPosition != LabelPosition.None) {
                canvas.drawText(
                        mLabel,
                        labelX,
                        labelY,
                        mLabelPaint
                );
            }
        }

        private void drawPointer(Canvas canvas, float angle) {
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

        public void measure(int measuredWidth, int measuredHeight) {
            int smallerSize = Math.min(measuredWidth, measuredHeight);

            float radius;
            if (mWheelRadius == 0) {
                radius = smallerSize / 2 - mPointerRadius - mPointerHaloWidth;

                radius -= Math.max(
                        (getPaddingBottom() + getPaddingTop()) / 2,
                        (getPaddingRight() + getPaddingLeft()) / 2
                );

                mTranslationOffsetX = measuredWidth / 2;
                mTranslationOffsetY = measuredHeight / 2;
                Log.d(TAG, "Automatic radius: " + radius);
            } else {
                radius = smallerSize > 0 ? Math.min(smallerSize, mWheelRadius) : mWheelRadius;

                mTranslationOffsetX = (radius + mPointerRadius + mPointerHaloWidth);
                mTranslationOffsetY = (radius + mPointerRadius + mPointerHaloWidth);

                setMeasuredDimension(
                        (int) mTranslationOffsetX * 2 + (getPaddingBottom() + getPaddingTop()),
                        (int) mTranslationOffsetY * 2 + (getPaddingRight() + getPaddingLeft())
                );
            }

            if (smallerSize > 0) {
                CirclePickerView.this.setWheelRadius(radius);
            }
            mWheelRectangle.set(
                    -mWheelRadius,
                    -mWheelRadius,
                    mWheelRadius,
                    mWheelRadius
            );
        }

        public void setWheelBackgroundStyle(int wheelBackgroundColor, float wheelWidth) {
            mWheelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mWheelBackgroundPaint.setColor(wheelBackgroundColor);
            mWheelBackgroundPaint.setStyle(Style.STROKE);
            mWheelBackgroundPaint.setStrokeWidth(wheelWidth);
        }

        public void setWheelColorStyle(int wheelColor, float wheelWidth) {
            mWheelColorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mWheelColorPaint.setColor(wheelColor);
            mWheelColorPaint.setStyle(Style.STROKE);
            mWheelColorPaint.setStrokeWidth(wheelWidth);
        }

        public void setDividerStyle(int dividerColor, float dividerWidth) {
            mDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mDividerPaint.setColor(dividerColor);
            mDividerPaint.setStrokeWidth(dividerWidth);
        }

        public void setPointerStyle(int pointerColor, int pointerHaloColor, float pointerRadius, float pointerHaloWidth) {
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

        public void setValueTextStyle(int textColor, int textSize) {
            mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
            mTextPaint.setColor(textColor);
            mTextPaint.setStyle(Style.FILL_AND_STROKE);
            mTextPaint.setTextAlign(Align.LEFT);
            mTextPaint.setTextSize(textSize);
        }

        public void setLabelStyle(LabelPosition labelPosition, int labelColor, int labelSize) {
            mLabelPosition = labelPosition;

            mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
            mLabelPaint.setColor(labelColor);
            mLabelPaint.setStyle(Style.FILL_AND_STROKE);
            mLabelPaint.setTextAlign(Align.LEFT);
            mLabelPaint.setTextSize(labelSize);
        }

        public void setLabel(String label) {
            mLabel = label != null ? label : "";
        }

        public void setWheelRadius(float wheelRadius) {
            mWheelRadius = wheelRadius;
        }
    }

    /*
     * Constants used to save/restore the instance state.
     */
    private static final String STATE_PARENT = "parent";
    private static final String STATE_ANGLE = "angle";

    private static final int TEXT_SIZE_DEFAULT_VALUE = 25;
    private static final float COLOR_WHEEL_STROKE_WIDTH_DEF_VALUE = 8;
    private static final float DIVIDER_WIDTH_DEF_VALUE = 2;
    private static final float POINTER_RADIUS_DEF_VALUE = 8;
    private static final float WHEEL_RADIUS_DEF_VALUE = 0;
    private static final float MAX_POINT_DEF_VALUE = Float.MAX_VALUE;
    private static final float MIN_POINT_DEF_VALUE = -Float.MAX_VALUE;
    private static final float CYCLE_DEF_VALUE = 0;
    private static final float POINTER_HALO_WIDTH_DEF_VALUE = 10;
    private static final int ZERO_OFFSET_DEF_VALUE = 0;
    private static final float STEP_DEF_VALUE = 0.1f;

    private OnValueChangeListener mOnValueChangeListener;

    /**
     *
     */
    private final Handler mHandler = new Handler();

    /**
     * {@code true} if the user clicked on the pointer to start the move mode.
     * {@code false} once the user stops touching the screen.
     *
     * @see #onTouchEvent(MotionEvent)
     */
    private boolean mUserIsMovingPointer = false;

    /**
     * {@code true} if the used touched the inside of the wheel
     */
    private boolean mPressed = false;
    private boolean mLongPressed = false;

    /**
     * {@code ValueFormatter} used to format the displayed text
     */
    private ValueFormatter mValueFormatter = new SimpleValueFormatter("%.1f");

    private boolean mInteractionEnabled;

    private final AngleHelper mAngleHelper = new AngleHelper(this);
    private final CirclePickerRenderer mRenderer = new CirclePickerRenderer();

    public CirclePickerView(Context context) {
        super(context);
        init(null, 0);
    }

    public CirclePickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public CirclePickerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    /**
     * Set the {@code ValueFormatter} instance to format the value text with
     *
     * @param formatter
     */
    public void setValueFormatter(ValueFormatter formatter) {
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
    public void setWheelRotation(int value) {
        mAngleHelper.setWheelRotation(value);
        invalidate();
    }

    /**
     * Set the wheel radius
     */
    public void setWheelRadius(float radius) {
        mAngleHelper.setWheelRadius(radius);
        mRenderer.setWheelRadius(radius);
        invalidate();
    }

    /**
     * Show or hide the value dividers
     *
     * @param enabled True to show, false to hide the dividers
     */
    public void setShowDivider(boolean enabled) {
        mRenderer.mShowDivider = enabled;
        invalidate();
    }

    /**
     * Show or hide the value text in the middle
     *
     * @param enabled
     */
    public void setShowValueText(boolean enabled) {
        mRenderer.mShowValueText = enabled;
        invalidate();
    }

    public void setLabelPosition(LabelPosition labelPosition) {
        mRenderer.mLabelPosition = labelPosition;
        invalidate();
    }

    /**
     * Show or hide the pointer circle
     *
     * @param enabled
     */
    public void setShowPointer(boolean enabled) {
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
    public void setSteps(float step) {
        mAngleHelper.setStep(step);
        invalidate();
    }

    private void init(AttributeSet attrs, int defStyle) {
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

    private void setRendererStyles(TypedArray a) {
        //Get size values
        int textSize = a.getDimensionPixelSize(
                R.styleable.CirclePickerView_textSize,
                TEXT_SIZE_DEFAULT_VALUE
        );
        int labelSize = a.getDimensionPixelSize(
                R.styleable.CirclePickerView_labelSize,
                textSize
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
        int labelColor = a.getColor(
                R.styleable.CirclePickerView_labelColor,
                textColor
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
        final String label = a.getString(R.styleable.CirclePickerView_label);
        int labelPosition = a.getInt(
                R.styleable.CirclePickerView_labelPosition,
                (label == null || label.equals(""))
                        ? LabelPosition.None.ordinal()
                        : LabelPosition.Above.ordinal()
        );

        mAngleHelper.setWheelRadius(wheelRadius);
        mRenderer.setWheelRadius(wheelRadius);

        mRenderer.mShowDivider = a.getBoolean(R.styleable.CirclePickerView_showDivider, false);
        mRenderer.mShowPointer = a.getBoolean(R.styleable.CirclePickerView_showPointer, true);
        mRenderer.mShowValueText = a.getBoolean(R.styleable.CirclePickerView_showValueText, true);

        mRenderer.setWheelBackgroundStyle(wheelBackgroundColor, wheelWidth);
        mRenderer.setWheelColorStyle(wheelColor, wheelWidth);
        mRenderer.setDividerStyle(dividerColor, dividerWidth);
        mRenderer.setPointerStyle(pointerColor, pointerHaloColor, pointerRadius, pointerHaloWidth);
        mRenderer.setValueTextStyle(textColor, textSize);
        mRenderer.setLabel(label);
        mRenderer.setLabelStyle(LabelPosition.values()[labelPosition], labelColor, labelSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mRenderer.draw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        mRenderer.measure(getMeasuredWidth(), getMeasuredHeight());
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!mInteractionEnabled) {
            return false;
        }
        // Convert coordinates to our internal coordinate system
        float x = event.getX() - mRenderer.mTranslationOffsetX;
        float y = event.getY() - mRenderer.mTranslationOffsetY;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Check whether the user pressed on (or near) the pointer
                switch (mAngleHelper.handleTouch(x, y)) {
                    case OnWheel:
                        mUserIsMovingPointer = true;
                        mLongPressed = false;
                        mPressed = false;
                        break;
                    case Inside:
                        mUserIsMovingPointer = false;
                        mLongPressed = false;
                        mPressed = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (mPressed && performLongClick()) {
                                    mLongPressed = true;
                                }
                            }
                        }, ViewConfiguration.getLongPressTimeout());
                        break;
                }
            case MotionEvent.ACTION_MOVE:
                if (mUserIsMovingPointer) {
                    mAngleHelper.handleDrag(x, y);
                    // Fix scrolling
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                mUserIsMovingPointer = false;
                mPressed = false;
                mLongPressed = false;
                break;
            case MotionEvent.ACTION_UP:
                if (mUserIsMovingPointer) {
                    mUserIsMovingPointer = false;
                    if (mOnValueChangeListener != null) {
                        mOnValueChangeListener.onValueChanged(this, mAngleHelper.getValue());
                    }
                } else if (mPressed && !mLongPressed) {
                    performClick();
                }
                break;
        }

        return true;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        Bundle state = new Bundle();
        state.putParcelable(STATE_PARENT, superState);
        state.putDouble(STATE_ANGLE, mAngleHelper.getAngle());

        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
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
    public double getValue() {
        return mAngleHelper.getValue();
    }

    public void setValue(double value) {
        mAngleHelper.setValue(value);
    }

    private void updateValue(double value) {
        if (mUserIsMovingPointer) {
            if (mOnValueChangeListener != null) {
                mOnValueChangeListener.onValueChanging(this, value);
            }
            invalidate();
        }
    }

    public void setOnValueChangeListener(OnValueChangeListener listener) {
        mOnValueChangeListener = listener;
    }
}
