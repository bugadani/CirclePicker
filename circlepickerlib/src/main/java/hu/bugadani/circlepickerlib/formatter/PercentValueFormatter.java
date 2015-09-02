package hu.bugadani.circlepickerlib.formatter;

public class PercentValueFormatter extends DecimalValueFormatter
{

    public PercentValueFormatter()
    {
        super(1);
    }

    @Override
    public String format(double angle)
    {
        return super.format(angle) + " %";
    }
}
