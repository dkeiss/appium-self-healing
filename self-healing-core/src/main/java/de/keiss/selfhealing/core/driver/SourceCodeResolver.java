package de.keiss.selfhealing.core.driver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves Java source code from the call stack — used to provide the LLM with the Page Object and Step Definition
 * source for context-aware healing.
 */
@Slf4j
@RequiredArgsConstructor
public class SourceCodeResolver {

    private final Path sourceBasePath;

    public CallerInfo resolveFromStackTrace(StackTraceElement[] stackTrace) {
        String pageObjectSource = null;
        String pageObjectClassName = null;
        String stepDefinitionSource = null;
        String stepName = null;

        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();

            // Detect Page Object classes
            if (className.endsWith("Page") || className.contains(".pages.")) {
                if (pageObjectSource == null) {
                    pageObjectSource = readSourceFile(className);
                    pageObjectClassName = className.substring(className.lastIndexOf('.') + 1);
                }
            }

            // Detect Step Definition classes
            if (className.endsWith("Steps") || className.contains(".steps.")) {
                if (stepDefinitionSource == null) {
                    stepDefinitionSource = readSourceFile(className);
                    stepName = element.getMethodName();
                }
            }
        }

        return new CallerInfo(pageObjectSource, pageObjectClassName, stepDefinitionSource, stepName);
    }

    private String readSourceFile(String className) {
        // Convert class name to file path: de.keiss.example.MyClass -> de/keiss/example/MyClass.java
        String relativePath = className.replace('.', '/') + ".java";
        Path filePath = sourceBasePath.resolve(relativePath);

        try {
            if (Files.exists(filePath)) {
                return Files.readString(filePath);
            }
        } catch (IOException e) {
            log.warn("Could not read source file: {}", filePath, e);
        }

        // Try common test source paths
        for (String prefix : new String[]{"src/test/java/", "src/main/java/"}) {
            Path alternate = sourceBasePath.resolve(prefix).resolve(relativePath);
            try {
                if (Files.exists(alternate)) {
                    return Files.readString(alternate);
                }
            } catch (IOException e) {
                log.warn("Could not read source file: {}", alternate, e);
            }
        }

        log.debug("Source file not found for class: {}", className);
        return null;
    }

    public record CallerInfo(String pageObjectSource, String pageObjectClassName, String stepDefinitionSource,
            String stepName) {
    }
}
