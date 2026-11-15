package com.braintrustappliedresearch.businessrules;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;

/**
 * Checks a request against {@link BusinessRulesProperties.Limits}, and shortens text that is echoed back.
 *
 * <p>The limits keep one caller from making the endpoint hold, evaluate or repeat unbounded text: an enormous value is
 * otherwise copied into the engine and then returned twice (as {@code result} and in {@code decisions}), and a long
 * name was echoed in error messages. Violations are described without repeating the offending content.
 */
final class RequestLimits {

    /** Longest piece of caller-supplied text repeated in a response. */
    private static final int ECHO_LIMIT = 64;

    private final BusinessRulesProperties.Limits limits;

    RequestLimits(BusinessRulesProperties.Limits limits) {
        this.limits = limits;
    }

    /**
     * Returns the largest request body that will be read.
     *
     * @return the cap in bytes
     */
    int maxBodyBytes() {
        return limits.maxBodyBytes();
    }

    /**
     * Returns what is wrong with the request, or {@code null} if it is within the limits.
     *
     * @param decisionName the value of the {@code evaluate} key
     * @param model        the value of the {@code model} key, or {@code null} if absent
     * @param inputs       every other key of the request
     * @return a message that names the rule that was broken without echoing the offending content, or {@code null}
     */
    String violation(String decisionName, Object model, Map<String, Object> inputs) {
        if (decisionName.length() > limits.maxNameLength()) {
            return "'" + RulesController.EVALUATE + "' is longer than " + limits.maxNameLength() + " characters";
        }
        if (model != null) {
            if (!(model instanceof String modelName)) {
                return "'" + RulesController.MODEL + "' must be a string";
            }
            if (modelName.length() > limits.maxNameLength()) {
                return "'" + RulesController.MODEL + "' is longer than " + limits.maxNameLength() + " characters";
            }
        }
        if (inputs.size() > limits.maxInputs()) {
            return "Too many inputs: at most " + limits.maxInputs() + " are accepted";
        }
        for (Map.Entry<String, Object> input : inputs.entrySet()) {
            if (input.getKey().length() > limits.maxNameLength()) {
                return "An input name is longer than " + limits.maxNameLength() + " characters";
            }
            if (hasTextOverLimit(input.getValue())) {
                return "The value of input '" + shorten(input.getKey()) + "' contains text longer than "
                        + limits.maxValueLength() + " characters";
            }
        }
        return null;
    }

    /** Walks a value without recursion, so a deeply nested one cannot overflow the stack. */
    private boolean hasTextOverLimit(Object value) {
        Deque<Object> pending = new ArrayDeque<>();
        pending.push(value == null ? Boolean.TRUE : value);   // ArrayDeque rejects null; a null has no text anyway
        while (!pending.isEmpty()) {
            Object current = pending.pop();
            if (current instanceof String text) {
                if (text.length() > limits.maxValueLength()) {
                    return true;
                }
            } else if (current instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() instanceof String key && key.length() > limits.maxNameLength()) {
                        return true;
                    }
                    if (entry.getValue() != null) {
                        pending.push(entry.getValue());
                    }
                }
            } else if (current instanceof Collection<?> items) {
                for (Object item : items) {
                    if (item != null) {
                        pending.push(item);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Shortens caller-supplied text before it is repeated in a response.
     *
     * @param text the text to repeat
     * @return the text, or its first characters followed by {@code ...} if it is longer than 64 characters
     */
    static String shorten(String text) {
        return text.length() <= ECHO_LIMIT ? text : text.substring(0, ECHO_LIMIT - 3) + "...";
    }
}
