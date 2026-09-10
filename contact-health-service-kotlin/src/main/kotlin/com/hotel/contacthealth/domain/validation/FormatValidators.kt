package com.hotel.contacthealth.domain.validation

object FormatValidators {
    private val EMAIL_REGEX = Regex("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$")
    private val E164_PHONE_REGEX = Regex("^\\+[1-9]\\d{7,14}$")

    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && !email.any(Char::isWhitespace) && EMAIL_REGEX.matches(email)
    }

    fun normalizePhone(phone: String): String {
        return phone.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
    }

    fun isValidPhone(phone: String): Boolean {
        val clean = normalizePhone(phone)
        return E164_PHONE_REGEX.matches(clean)
    }

    fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2 || parts[0].length <= 2) {
            return "***@${parts.getOrElse(1) { "" }}"
        }
        return "${parts[0].first()}***${parts[0].last()}@${parts[1]}"
    }

    fun maskPhone(phone: String): String {
        val clean = phone.filter { it.isDigit() || it == '+' }
        if (clean.length < 8) {
            return "****"
        }
        val prefixLen = if (clean.startsWith("+")) {
            minOf(5, clean.length - 4)
        } else {
            minOf(2, clean.length - 4)
        }
        val prefix = clean.take(prefixLen)
        val suffix = clean.takeLast(4)
        val middleLen = clean.length - prefixLen - 4
        val middle = "*".repeat(maxOf(0, middleLen))
        return "$prefix$middle$suffix"
    }
}
