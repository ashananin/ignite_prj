import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JavaProcess {
    public static Process exec(final Class clazz, final List<String> args) throws IOException {
        return exec(null, clazz, args);
    }

    public static Process exec(final String classpathOverrides, final Class clazz, final List<String> args) throws IOException {
//        final String javaHome = System.getProperty("java.home");
        final String javaHome = "/home/alex/jdk1.8.0_291/";
        final String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        if (classpathOverrides != null) {
            classpath = classpathOverrides + File.pathSeparatorChar + classpath;
        }
        final String className = clazz.getName();
        final List<String> command = new ArrayList<>(Arrays.asList(javaBin, "-cp", classpath, className));
        command.addAll(args);
        return new ProcessBuilder(command).inheritIO().start();
    }
}
