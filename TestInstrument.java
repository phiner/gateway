import com.dukascopy.api.Instrument;
import java.lang.reflect.Method;
public class TestInstrument {
    public static void main(String[] args) {
        for (Method m : Instrument.class.getMethods()) {
            if (m.getName().toLowerCase().contains("amount") || m.getName().toLowerCase().contains("min") || m.getName().toLowerCase().contains("vol") || m.getName().toLowerCase().contains("lot") || m.getName().toLowerCase().contains("margin")) {
                System.out.println(m.getName() + " -> " + m.getReturnType().getSimpleName());
            }
        }
    }
}
