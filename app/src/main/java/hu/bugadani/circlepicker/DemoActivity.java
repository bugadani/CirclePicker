package hu.bugadani.circlepicker;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import hu.bugadani.circlepickerlib.CirclePickerView;
import hu.bugadani.circlepickerlib.formatter.SimpleValueFormatter;

public class DemoActivity extends AppCompatActivity
{

    private CirclePickerView mRotationPicker;
    private CirclePickerView mMainPicker;
    private CheckBox         mShowDivider;
    private CirclePickerView mStepPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        mMainPicker = (CirclePickerView) findViewById(R.id.picker);
        mRotationPicker = (CirclePickerView) findViewById(R.id.rotation);
        mStepPicker = (CirclePickerView) findViewById(R.id.steps);
        mShowDivider = (CheckBox) findViewById(R.id.show_dividers);

        mRotationPicker.setValueFormatter(new SimpleValueFormatter("%.0f°"));
        mRotationPicker.setOnValueChangeListener(new CirclePickerView.OnValueChangeListener()
        {
            @Override
            public void onValueChanging(CirclePickerView pickerView, double value)
            {
                mMainPicker.setZeroOffset((int) value);
            }

            @Override
            public void onValueChanged(CirclePickerView pickerView, double value)
            {

            }
        });

        mStepPicker.setValueFormatter(new SimpleValueFormatter("%.1f"));
        mStepPicker.setOnValueChangeListener(new CirclePickerView.OnValueChangeListener()
        {
            @Override
            public void onValueChanging(CirclePickerView pickerView, double value)
            {
                mMainPicker.setSteps((float) value);
            }

            @Override
            public void onValueChanged(CirclePickerView pickerView, double value)
            {

            }
        });

        mShowDivider.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                mMainPicker.setDividerEnabled(isChecked);
            }
        });
    }
}
