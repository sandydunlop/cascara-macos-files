# Cascara macOS Files

**A clean, modular, JNA-powered bridge for handling macOS `FileOpen` events in JavaFX and Swing.**

### Why this exists

Standard Java/JavaFX APIs for handling macOS "Open File" events (like double-clicking a file in Finder) are often:

1. **Buggy:** The official `java.awt.Desktop` handler frequently fails to trigger on "Cold Starts."

2. **Brittle:** Common workarounds rely on `com.sun.glass` reflection, which requires dangerous `--add-opens` flags and breaks on new JDK releases.

3. **Non-Modular:** Most solutions don't play nice with JPMS (Java Platform Module System).

**Cascara macOS Files** provides a native listener that registers early and works without hacking JDK internals.

### Installation (Gradle)

```gradle
implementation 'io.github.qishr:cascara-macos-files:1.0.0'
```

### Installation (Maven)

```xml
<dependency>
    <groupId>io.github.qishr</groupId>
    <artifactId>cascara-macos-files</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Usage

Simply register your handler in your main class's static block to ensure it captures events during app initialization:

Java

```
static {
    MacosOpenFileListener.setHandler(path -> {
        // This is called when a file is opened via Finder
        System.out.println("Opening: " + path);
    });
}
```


