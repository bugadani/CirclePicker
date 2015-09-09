package hu.bugadani.circlepicker;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import hu.bugadani.circlepickerlib.CirclePickerView;
import hu.bugadani.circlepickerlib.formatter.SimpleValueFormatter;

public class DemoActivity extends AppCompatActivity
{

    private CirclePickerView mMainPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        mMainPicker = (CirclePickerView) findViewById(R.id.picker);
        CirclePickerView rotationPicker = (CirclePickerView) findViewById(R.id.rotation);
        CirclePickerView stepPicker     = (CirclePickerView) findViewById(R.id.steps);
        CheckBox showDivider = (CheckBox) findViewById(R.id.show_dividers);
        CheckBox showValueText = (CheckBox) findViewById(R.id.show_value_text);
        CheckBox showPointer = (CheckBox) findViewById(R.id.show_pointer);

        rotationPicker.setValueFormatter(new SimpleValueFormatter("%.0f°"));
        rotationPicker.setOnValueChangeListener(new CirclePickerView.OnValueChangeListener()
        {
            @Override
            public void onValueChanging(CirclePickerView pickerView, double value)
            {
                mMainPicker.setWheelRotation((int) value);
            }

            @Override
            public void onValueChanged(CirclePickerView pickerView, double value)
            {

            }
        });

        stepPicker.setValueFormatter(new SimpleValueFormatter("%.1f"));
        stepPicker.setOnValueChangeListener(new CirclePickerView.OnValueChangeListener()
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

        showDivider.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                mMainPicker.setShowDivider(isChecked);
            }
        });

        showValueText.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                mMainPicker.setShowValueText(isChecked);
            }
        });

        showPointer.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                mMainPicker.setShowPointer(isChecked);
            }
        });
    }
}
