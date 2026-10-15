import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Log {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("HH:mm:ss");

    // força UTF-8 na saída, independente do locale da VM (evita "?" nos acentos)
    private static final PrintStream SAIDA =
            new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

    public static void log(String mensagem) {
        SAIDA.println(LocalTime.now().format(FORMATO) + " - " + mensagem);
    }
}
