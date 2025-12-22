/**
 * A clean, modular, JNA-powered bridge for handling macOS FileOpen events in JavaFX and Swing.
 */
module cascara.macos.files {
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires java.logging;
    exports io.github.qishr.cascara.macos.files;
    opens io.github.qishr.cascara.macos.files to com.sun.jna;
}
