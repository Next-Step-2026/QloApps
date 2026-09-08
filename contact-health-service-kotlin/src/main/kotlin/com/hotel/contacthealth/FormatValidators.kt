package com.hotel.contacthealth

object FormatValidators{
    private val EMAIL_REGEX = Regex("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$")
    private val E164_PHONE_REGEX = Regex("^\\+[1-9]\\d{7,14}$")


    fun isValidEmail(email: String): Boolean{
        if(email.contains(" ") || email.contains("\n")){
            return false;
        }
        return EMAIL_REGEX.matches(email)
    }

    fun isValidPhone(phone: String): Boolean{
        val clean = phone.replace(" ", ""). replace("-", "")
        return  E164_PHONE_REGEX.matches(phone);
    }

    fun maskEmail(email: String): String{
        val parts = email.split("@")
        if(parts.size != 2 || parts[0].length <= 2){
            return "***@${parts.getOrElse(1) { "" }}"
        }
        return "${parts[0].first()}***${parts[0].last()}@${parts[1]}"
    }

    fun maskPhone(phone: String): String {
        val digits = phone.filter {
            it.isDigit() || it == '+' }
        return if (digits.length >= 8) {
            digits
                .take(digits.length - 4)
                .replace(Regex("\\d"), "*")+
                    digits.takeLast(4)
        } else {
            "****"
        }
    }
}