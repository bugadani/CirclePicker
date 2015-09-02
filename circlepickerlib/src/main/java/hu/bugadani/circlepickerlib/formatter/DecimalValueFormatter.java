package hu.bugadani.circlepickerlib.formatter;

import java.text.DecimalFormat;

public class DecimalValueFormatter implements ValueFormatter
{

    private final DecimalFormat mFormat;

    public DecimalValueFormatter(int digits)
    {
        StringBuilder b = new StringBuilder("###,###,###,##0");
        for (int i = 0; i < digits; i++) {
            if (i == 0) {
                b.append(".");
            }
            b.append("0");
        }

        mFormat = new DecimalFormat(b.toString());
    }

    public DecimalValueFormatter(DecimalFormat format)
    {
        mFormat = format;
    }

    @Override
    public String format(double angle)
    {
        return mFormat.format(angle);
    }
}
