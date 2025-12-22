package io.github.qishr.cascara.macos.files;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * A native listener for macOS 'Open Document' (odoc) Apple Events.
 * <p>
 * This class uses JNA to hook into the Carbon Apple Event Manager, allowing Java
 * applications to respond to file-open requests from the Finder or the "Open With..."
 * menu without relying on internal JavaFX/AWT APIs.
 * </p>
 */
public class MacosOpenFileListener {
    static Path filePath = null;
    static OpenFileHandler openFileHandler = null;

    /**
     * Interface for macOS Foundation framework interactions.
     */
    public interface Foundation extends Library {
        Foundation INSTANCE = Native.load("Foundation", Foundation.class);
        Pointer objc_getClass(String className);
        Pointer sel_registerName(String selectorName);
        Pointer objc_msgSend(Pointer receiver, Pointer selector, Object... args);
    }

    /**
     * Interface for macOS Carbon framework Apple Event handling.
     */
    public interface Carbon extends Library {
        Carbon INSTANCE = Native.load("Carbon", Carbon.class);

        /**
         * Installs an event handler into the application's event table.
         * @param eventClass The four-character code for the event class (e.g., 'aevt').
         * @param eventID    The four-character code for the event ID (e.g., 'odoc').
         * @param handler    The callback to be executed when the event occurs.
         * @param refcon     A pointer to user-defined data.
         * @param isSysHandler Whether this is a system-wide handler.
         * @return An OSStatus error code (0 for success).
         */
        int AEInstallEventHandler(int eventClass, int eventID, Callback handler, Pointer refcon, boolean isSysHandler);

        int AEGetParamDesc(Pointer appleEvent, int keyword, int desiredType, AEDesc resultDesc);
        int AECountItems(AEDesc descList, IntByReference count);
        int AEGetNthPtr(AEDesc descList, int index, int desiredType, Pointer keyword, Pointer actualType, byte[] buffer, int bufferSize, IntByReference actualSize);
    }

    /**
     * Represents an Apple Event Descriptor (AEDesc) structure.
     */
    public static class AEDesc extends Structure {
        public int descriptorType;
        public Pointer dataHandle;
        @Override protected List<String> getFieldOrder() { return List.of("descriptorType", "dataHandle"); }
    }

    /**
     * Functional interface for handling file open events.
     */
    @FunctionalInterface
    public interface OpenFileHandler {
        /**
         * Invoked when the OS requests the application to open a specific file.
         * * @param filePath The {@link Path} of the file to be opened.
         */
        void openFile(Path filePath);
    }

    /**
     * The internal JNA callback that intercepts the 'odoc' Apple Event.
     */
    public static final Callback ODOC_CALLBACK = new Callback() {
        public int callback(Pointer appleEvent, Pointer reply, Pointer refcon) {
            extractPath(appleEvent);
            return 0;
        }
    };

    /**
     * Gets the most recently received file path.
     * * @return The {@link Path} received, or null if no event has occurred.
     */
    public static Path getFilePath() {
        return filePath;
    }

    /**
     * Registers a handler to respond to macOS file-open events.
     * <p>
     * This should ideally be called in a static block or very early in the application
     * lifecycle to capture events sent during application startup.
     * </p>
     * * @param handler The handler to invoke when a file is opened.
     */
    public static void setHandler(OpenFileHandler handler) {
        if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
            return;
        }
        openFileHandler = handler;
        try {
            // Register 'aevt' (0x61657674) / 'odoc' (0x6f646f63) via Carbon
            Carbon.INSTANCE.AEInstallEventHandler(0x61657674, 0x6f646f63, ODOC_CALLBACK, null, false);
        } catch (Exception e) {
            // Silently fail if native hooks cannot be established
        }
    }

    /**
     * Extracts the file path from the Apple Event pointer.
     * * @param event The native pointer to the Apple Event.
     */
    static void extractPath(Pointer event) {
        System.out.println("extractPath");
        try {
            AEDesc listDesc = new AEDesc();
            // Get 'list' from event
            if (Carbon.INSTANCE.AEGetParamDesc(event, 0x2d2d2d2d, 0x6c697374, listDesc) == 0) {
                IntByReference count = new IntByReference();
                Carbon.INSTANCE.AECountItems(listDesc, count);
                if (count.getValue() > 0) {
                    byte[] buffer = new byte[1024];
                    IntByReference size = new IntByReference();
                    // Get 'furl' (URL) from list
                    if (Carbon.INSTANCE.AEGetNthPtr(listDesc, 1, 0x6675726c, null, null, buffer, buffer.length, size) == 0) {
                        String uriString = new String(buffer, 0, size.getValue());
                        filePath = Paths.get(new URI(uriString));
                        openFileHandler.openFile(filePath);
                    }
                }
            }
        } catch (Exception e) {
            // Handle parsing errors or malformed URIs
        }
    }
}