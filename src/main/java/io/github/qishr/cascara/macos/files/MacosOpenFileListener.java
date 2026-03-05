package io.github.qishr.cascara.macos.files;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class MacosOpenFileListener {
    static String uri = null;
    static OpenFileHandler openFileHandler = null;

    public interface Foundation extends Library {
        Foundation INSTANCE = Native.load("Foundation", Foundation.class);
        Pointer objc_getClass(String className);
        Pointer sel_registerName(String selectorName);
        Pointer objc_msgSend(Pointer receiver, Pointer selector, Object... args);
    }

    /**
     * Interface for macOS Carbon framework Apple Event handling.
     * This is your original shape, plus AEGetParamPtr added.
     */
    public interface Carbon extends Library {
        Carbon INSTANCE = Native.load("Carbon", Carbon.class);

        int AEInstallEventHandler(int eventClass,
                                  int eventID,
                                  Callback handler,
                                  Pointer refcon,
                                  boolean isSysHandler);

        int AEGetParamDesc(Pointer appleEvent,
                           int keyword,
                           int desiredType,
                           AEDesc resultDesc);

        int AECountItems(AEDesc descList,
                         IntByReference count);

        int AEGetNthPtr(AEDesc descList,
                        int index,
                        int desiredType,
                        Pointer keyword,
                        Pointer actualType,
                        byte[] buffer,
                        int bufferSize,
                        IntByReference actualSize);

        // Added for GURL
        int AEGetParamPtr(Pointer appleEvent,
                          int keyword,
                          int desiredType,
                          Pointer actualType,
                          byte[] dataPtr,
                          int maximumSize,
                          IntByReference actualSize);
    }

    public static class AEDesc extends Structure {
        public int descriptorType;
        public Pointer dataHandle;
        @Override protected List<String> getFieldOrder() {
            return List.of("descriptorType", "dataHandle");
        }
    }

    @FunctionalInterface
    public interface OpenFileHandler {
        void openFile(String uriString);
    }

    public static final Callback ODOC_CALLBACK = new Callback() {
        public int callback(Pointer appleEvent, Pointer reply, Pointer refcon) {
            extractPath(appleEvent);
            return 0;
        }
    };

    public static final Callback GURL_CALLBACK = new Callback() {
        public int callback(Pointer appleEvent, Pointer reply, Pointer refcon) {
            extractUrl(appleEvent);
            return 0;
        }
    };

    public static String getUri() {
        return uri;
    }

    public static void setHandler(OpenFileHandler handler) {
        if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
            return;
        }
        openFileHandler = handler;
        try {
            // 'aevt' / 'odoc' – file open
            Carbon.INSTANCE.AEInstallEventHandler(0x61657674, 0x6f646f63, ODOC_CALLBACK, null, false);
            // 'GURL' / 'GURL' – URL open
            Carbon.INSTANCE.AEInstallEventHandler(0x4755524c, 0x4755524c, GURL_CALLBACK, null, false);
        } catch (Exception e) {
            // ignore
        }
    }

    static void extractPath(Pointer event) {
        System.out.println("extractPath");
        try {
            AEDesc listDesc = new AEDesc();

            // keyDirectObject '----' (0x2d2d2d2d), typeAEList 'list' (0x6c697374)
            if (Carbon.INSTANCE.AEGetParamDesc(event, 0x2d2d2d2d, 0x6c697374, listDesc) != 0) {
                return;
            }

            IntByReference countRef = new IntByReference();
            if (Carbon.INSTANCE.AECountItems(listDesc, countRef) != 0) {
                return;
            }

            int count = countRef.getValue();
            System.out.println("extractPath: item count = " + count);

            for (int i = 1; i <= count; i++) {
                byte[] buffer = new byte[2048];
                IntByReference size = new IntByReference();

                // 'furl' (0x6675726c)
                int err = Carbon.INSTANCE.AEGetNthPtr(
                        listDesc,
                        i,
                        0x6675726c,
                        null,
                        null,
                        buffer,
                        buffer.length,
                        size
                );

                if (err != 0) {
                    System.out.println("extractPath: AEGetNthPtr failed for index " + i + " err=" + err);
                    continue;
                }

                String uriString = new String(buffer, 0, size.getValue());
                System.out.println("extractPath: item " + i + " = " + uriString);

                if (uri == null) {
                    uri = uriString;
                }

                if (openFileHandler != null) {
                    openFileHandler.openFile(uriString);
                }
            }

        } catch (Exception e) {
            // Ignore
        }
    }


    static void extractUrl(Pointer event) {
        System.out.println("extractUrl 1");
        try {
            byte[] buffer = new byte[2048];
            IntByReference size = new IntByReference();

            // keyDirectObject '----' (0x2d2d2d2d), typeUTF8Text 'utf8' (0x75746638)
            int err = Carbon.INSTANCE.AEGetParamPtr(
                    event,
                    0x2d2d2d2d,
                    0x75746638,
                    null,
                    buffer,
                    buffer.length,
                    size
            );

            if (err != 0) {
                System.out.println("extractUrl: AEGetParamPtr failed: " + err);
                return;
            }

            String url = new String(buffer, 0, size.getValue(), StandardCharsets.UTF_8);

            uri = url;
            if (openFileHandler != null) {
                openFileHandler.openFile(url);
            }
        } catch (Exception e) {
            // Ignore
        }
    }
}
