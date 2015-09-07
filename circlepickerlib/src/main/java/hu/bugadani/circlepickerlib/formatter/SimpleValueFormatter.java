package hu.bugadani.circlepickerlib.formatter;

public class SimpleValueFormatter implements ValueFormatter
{

    private final String mFormat;

    public SimpleValueFormatter(String format)
    {
        mFormat = format;
    }

    public SimpleValueFormatter()
    {
        this("%.0f");
    }

    @Override
    public String format(double angle)
    {
        return String.format(mFormat, angle);
    }
}
