package dev.strategos.core.content;

/**
 * One problem in the content (04 §8.6): the file, the JSON pointer inside it, the severity and the message, printed as
 * {@code spearman.json: /Attack/Melee/damage/hack: expected a number}.
 */
public record TemplateProblem(String file, String pointer, Severity severity, String message) {
    public enum Severity {
        ERROR,
        WARNING
    }

    public static TemplateProblem error(String file, String pointer, String message) {
        return new TemplateProblem(file, pointer, Severity.ERROR, message);
    }

    @Override
    public String toString() {
        return file + ": " + (pointer.isEmpty() ? "/" : pointer) + ": " + message;
    }
}
