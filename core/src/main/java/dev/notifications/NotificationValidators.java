package dev.notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Local validation for the built-in channels. Results identify fields, not their contents. */
public final class NotificationValidators {
    private static final String DOT_ATOM_PART = "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+";
    private static final String DOMAIN_LABEL = "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?";
    private static final Pattern MAILBOX = Pattern.compile(DOT_ATOM_PART + "(?:\\." + DOT_ATOM_PART + ")*");
    private static final Pattern DOMAIN = Pattern.compile(DOMAIN_LABEL + "(?:\\." + DOMAIN_LABEL + ")+");
    private static final Pattern E164 = Pattern.compile("\\+[1-9][0-9]{7,14}");
    private static final int MAX_SMS_CODE_POINTS = 1600;

    private NotificationValidators() { }

    /** Validates common ASCII internet mailboxes (not mailbox existence or ownership). */
    public static List<String> validateEmail(EmailNotification email) {
        var violations = new ArrayList<String>();
        if (!validEmailAddress(email.recipient())) {
            violations.add("recipient");
        }
        requireText(email.subject(), "subject", violations);
        requireText(email.body(), "body", violations);
        return List.copyOf(violations);
    }

    /** Validates recipient syntax and the body length in Unicode code points. */
    public static List<String> validateSms(SmsNotification sms) {
        var violations = new ArrayList<String>();
        if (!validPhoneNumber(sms.recipient())) {
            violations.add("recipient");
        }
        var body = sms.body();
        if (blank(body) || body.codePointCount(0, body.length()) > MAX_SMS_CODE_POINTS) {
            violations.add("body");
        }
        return List.copyOf(violations);
    }

    /** Validates required text and string data entries without contacting a provider. */
    public static List<String> validatePush(PushNotification push) {
        var violations = new ArrayList<String>();
        requireText(push.token(), "token", violations);
        requireText(push.title(), "title", violations);
        requireText(push.body(), "body", violations);
        if (!validData(push.data())) {
            violations.add("data");
        }
        return List.copyOf(violations);
    }

    /**
     * Checks the supported unquoted ASCII mailbox syntax with a dotted DNS domain.
     * Null is invalid. Does not resolve DNS or verify mailbox existence or ownership.
     */
    public static boolean validEmailAddress(String address) {
        if (address == null || address.length() > 254) {
            return false;
        }
        var at = address.indexOf('@');
        return at > 0 && at <= 64 && MAILBOX.matcher(address.substring(0, at)).matches()
                && DOMAIN.matcher(address.substring(at + 1)).matches();
    }

    /**
     * Checks local E.164 form: {@code +} and 8–15 ASCII digits, starting with a nonzero digit.
     * Null is invalid. Does not verify number reachability or ownership.
     */
    public static boolean validPhoneNumber(String number) {
        return number != null && E164.matcher(number).matches();
    }

    private static boolean validData(Map<?, ?> data) {
        return data.entrySet().stream()
                .allMatch(entry -> entry.getKey() instanceof String && entry.getValue() instanceof String);
    }

    private static void requireText(String value, String field, List<String> violations) {
        if (blank(value)) {
            violations.add(field);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
