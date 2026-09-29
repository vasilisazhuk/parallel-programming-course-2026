import java.util.Random;

public class ParetoGen {
    public static int [] generate(int size, long seed){
        int [] result = new int[size];
        Random random = new Random(seed);
        double uMin = Math.pow(1023, -1.15);
        for (int i = 0; i < size; i++) {
            double u = uMin + random.nextDouble() * (1 - uMin);
            result[i] = (int) (1 / Math.pow(u, 1.0 / 1.15));
        }
        return result;
    }
}
