import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;

/** Runtime handoff only. The caller MUST capture stdout in memory and never print it. */
public class LiveRuntimeCredentials {
    public static void main(String[] args) {
        try {
            new ObjectMapper().writeValue(System.out,
                    LiveInfrastructureReadOnly.runtimeCredentials(Path.of(args[0])));
        } catch (Exception error) {
            // No exception messages or values can reach logs on failed credential resolution.
            System.err.println("Runtime credential resolution failed: " + error.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
