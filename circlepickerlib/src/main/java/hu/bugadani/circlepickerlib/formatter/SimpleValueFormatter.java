package hu.bugadani.circlepickerlib.formatter;

public class SimpleValueFormatter implements ValueFormatter
{

    @Override
    public String format(double angle)
    {
        return String.valueOf((int) angle);
    }
}
