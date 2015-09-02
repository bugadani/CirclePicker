package hu.bugadani.circlepicker;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import hu.bugadani.circlepickerlib.CirclePickerView;

public class DemoActivity extends AppCompatActivity
{

    private CirclePickerView mRotationPicker;
    private CirclePickerView mMainPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        mMainPicker = (CirclePickerView) findViewById(R.id.picker);
        mRotationPicker = (CirclePickerView) findViewById(R.id.rotation);
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
    }
}
